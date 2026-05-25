package com.bank;

import com.bank.model.PaymentDTO;
import com.bank.service.PaymentValidator;
import jakarta.xml.bind.JAXBContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;

import java.util.concurrent.ConcurrentHashMap;

/**
 * ==============================================================================
 * Bank Payment Fraud Detection PoC
 * ==============================================================================
 *
 * BUSINESS WORKFLOW (DATA PIPELINE):
 *
 * [Client]
 * │
 * ├── (JSON Request) ──> [PPS API Gateway]
 * │
 * ├── Enforces JSON-only & UUID Validation
 * │
 * └──> [Broker System]
 * │
 * ├── Translates JSON to Legacy XML
 * │
 * └── (ActiveMQ) ──> [Fraud Check System]
 * │
 * ├── Evaluates AML Rules
 * ├── Checks Blacklists
 * │
 * <── (JSON Response) <── (Converts Result to JSON) <── (ActiveMQ) <──
 *
 *
 * API ENDPOINTS (SOLUTIONS):
 * Solution 1: POST /api/v1/payments/secure      — async JMS (Returns 202 Accepted)
 * Solution 2: POST /api/v1/payments             — sync REST (Returns 200 OK / 400 / 403)
 * Status Chk: GET  /api/v1/payments/{id}/status — async status polling
 * ==============================================================================
 */
public class PaymentApp extends RouteBuilder {

    /*
     * ARCHITECTURE NOTE: We use a ConcurrentHashMap to simulate a high-speed,
     * thread-safe database for checking asynchronous payment statuses.
     */
    private static final ConcurrentHashMap<String, String> STATUS_STORE = new ConcurrentHashMap<>();

    @Override
    public void configure() throws Exception {

        // Set up XML parsing (JAXB) for the internal Broker/Fraud systems
        JAXBContext context = JAXBContext.newInstance(PaymentDTO.class);
        JaxbDataFormat jaxb = new JaxbDataFormat(context);

        // Instantiate our custom validation logic (UUIDs, ISO codes, etc.)
        PaymentValidator validator = new PaymentValidator();

        /* ==============================================================================
         * 1. GLOBAL ERROR HANDLER
         * DEFENSE EXPLANATION: We intercept all exceptions here. Even if a user sends
         * XML to a JSON endpoint, or fails a UUID check, we catch it and force a strict
         * JSON error response to ensure API contract compliance.
         * ============================================================================== */
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    exchange.getMessage().getHeaders().clear();

                    String errorMessage = "Invalid Request";
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    if (cause != null && cause.getMessage() != null) {
                        errorMessage = cause.getMessage();
                    }

                    // Force response to be JSON format
                    exchange.getMessage().setBody(
                            "{\"status\":\"REJECTED\",\"message\":\"" + errorMessage + "\"}"
                    );

                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
                    exchange.getMessage().setHeader("Connection", "close");
                });

        /* ==============================================================================
         * 2. REST API CONFIGURATION
         * DEFENSE EXPLANATION: We expose port 8080. We explicitly set the routes to
         * consume and produce only application/json, acting as our first line of defense.
         * ============================================================================== */
        restConfiguration()
                .component("netty-http")
                .host("0.0.0.0")
                .port(8080);

        rest("/api/v1/payments")
                .consumes("application/json")
                .produces("application/json")
                .post()                 // Synchronous processing
                .to("seda:pps-sync")
                .post("/secure")        // Asynchronous processing
                .to("seda:pps-async")
                .get("/{transactionId}/status") // Status polling
                .to("direct:get-status");

        /* ==============================================================================
         * 3. ASYNC STATUS LOOKUP (GET ROUTE)
         * DEFENSE EXPLANATION: A lightweight route that allows clients to poll for the
         * status of queued payments without blocking backend resources.
         * ============================================================================== */
        from("direct:get-status")
                .routeId("status-check")
                .process(exchange -> {
                    String txId = exchange.getIn().getHeader("transactionId", String.class);
                    String status = STATUS_STORE.getOrDefault(txId, "NOT_FOUND");

                    exchange.getMessage().getHeaders().clear();

                    if ("NOT_FOUND".equals(status)) {
                        exchange.getMessage().setBody("{\"transactionId\":\"" + txId + "\",\"status\":\"NOT_FOUND\"}");
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                    } else {
                        exchange.getMessage().setBody("{\"transactionId\":\"" + txId + "\",\"status\":\"" + status + "\"}");
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                    }

                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                    exchange.getMessage().setHeader("Connection", "close");
                });

        /* ==============================================================================
         * 4. PPS COMPONENT: SYNCHRONOUS ROUTE
         * DEFENSE EXPLANATION: Blocks invalid Content-Types, unmarshals JSON, runs business
         * logic validation, and passes it to the Broker System.
         * ============================================================================== */
        from("seda:pps-sync")
                .routeId("pps-sync")
                .wireTap("jms:queue:auditQueue") // Send copy to audit log silently
                .process(exchange -> {
                    String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
                    if (contentType != null && !contentType.contains("json")) {
                        throw new IllegalArgumentException("Unsupported Media Type. PPS only accepts application/json");
                    }
                })
                .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
                .bean(validator, "validate")
                .marshal().json(JsonLibrary.Jackson)
                .to("direct:bs-sync");

        /* ==============================================================================
         * 5. PPS COMPONENT: ASYNCHRONOUS ROUTE (CORE REQUIREMENT)
         * DEFENSE EXPLANATION: Drops the validated message into an ActiveMQ queue for
         * background processing, and immediately returns a 202 Accepted to free up the client.
         * ============================================================================== */
        from("seda:pps-async")
                .routeId("pps-async")
                .wireTap("jms:queue:auditQueue")
                .process(exchange -> {
                    String contentType = exchange.getIn().getHeader(Exchange.CONTENT_TYPE, String.class);
                    if (contentType != null && !contentType.contains("json")) {
                        throw new IllegalArgumentException("Unsupported Media Type. PPS only accepts application/json");
                    }
                })
                .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
                .bean(validator, "validate")
                .process(exchange -> {
                    PaymentDTO payment = exchange.getIn().getBody(PaymentDTO.class);
                    STATUS_STORE.put(payment.getTransactionId(), "PROCESSING"); // Mark as queued
                })
                .marshal().json(JsonLibrary.Jackson)
                .to("jms:queue:ppsToBS") // Send to background queue
                .process(exchange -> {
                    // Instantly return 202 Accepted to the caller
                    exchange.getMessage().getHeaders().clear();
                    exchange.getMessage().setBody("{\"status\":\"ACCEPTED_FOR_PROCESSING\",\"message\":\"Queued\"}");
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
                    exchange.getMessage().setHeader("Connection", "close");
                });

        /* ==============================================================================
         * 6. BROKER SYSTEM (BS): SYNCHRONOUS
         * DEFENSE EXPLANATION: Acts as a middleware translator. Receives JSON from PPS,
         * converts it to XML required by the Fraud System, and triggers InOut pattern.
         * ============================================================================== */
        from("direct:bs-sync")
                .routeId("bs-sync")
                .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
                .marshal(jaxb) // Data Transformation: JSON -> XML
                .to("jms:queue:bsToFCS?exchangePattern=InOut") // Wait for Fraud response
                .to("direct:sync-response");

        /* ==============================================================================
         * 7. BROKER SYSTEM (BS): SYNCHRONOUS RESPONSE HANDLER
         * DEFENSE EXPLANATION: Translates the Fraud System's XML response back into
         * standard JSON for the API client to consume.
         * ============================================================================== */
        from("direct:sync-response")
                .routeId("sync-response")
                .convertBodyTo(String.class)
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    exchange.getMessage().getHeaders().clear();

                    if (body.contains("<status>REJECTED</status>")) {
                        exchange.getMessage().setBody("{\"status\":\"REJECTED\",\"message\":\"Fraud Policy Violation\"}");
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 403);
                    } else {
                        exchange.getMessage().setBody("{\"status\":\"APPROVED\",\"message\":\"Nothing found, all okay\"}");
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                    }

                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                    exchange.getMessage().setHeader("Connection", "close");
                });

        /* ==============================================================================
         * 8. BROKER SYSTEM (BS): ASYNCHRONOUS WORKER
         * DEFENSE EXPLANATION: Listens to the ActiveMQ queue, converts queued JSON
         * payloads to XML, and passes them to the Fraud engine.
         * ============================================================================== */
        from("jms:queue:ppsToBS")
                .routeId("bs-async")
                .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
                .marshal(jaxb)
                .to("jms:queue:bsToFCS");

        /* ==============================================================================
         * 9. FRAUD CHECK SYSTEM (FCS)
         * DEFENSE EXPLANATION: A completely isolated component that strictly evaluates XML.
         * We use Camel Choice and Simple languages to apply regex against blacklisted entities.
         * ============================================================================== */
        from("jms:queue:bsToFCS")
                .routeId("fraud-engine")
                .unmarshal(jaxb) // Parse the XML
                .choice()
                .when(simple(
                        "${body.payerName} regex '(?i)Mark Imaginary|Govind Real|Shakil Maybe|Chang Imagine' || " +
                                "${body.payeeName} regex '(?i)Mark Imaginary|Govind Real|Shakil Maybe|Chang Imagine' || " +
                                "${body.payerCountryCode} in 'CUB,IRQ,IRN,PRK,SDN,SYR' || " +
                                "${body.payeeCountryCode} in 'CUB,IRQ,IRN,PRK,SDN,SYR' || " +
                                "${body.payerBank} regex '(?i).*BANK OF KUNLUN.*|.*KARAMAY CITY COMMERCIAL BANK.*' || " +
                                "${body.payeeBank} regex '(?i).*BANK OF KUNLUN.*|.*KARAMAY CITY COMMERCIAL BANK.*' || " +
                                "${body.paymentInstruction} regex '(?i).*Artillery Procurement.*|.*Lethal Chemicals payment.*'"
                ))
                .setBody(simple(
                        "<payment>" +
                                "<transactionId>${body.transactionId}</transactionId>" +
                                "<status>REJECTED</status>" +
                                "<message>Suspicious payment</message>" +
                                "</payment>"
                ))
                .otherwise() // Happy path
                .setBody(simple(
                        "<payment>" +
                                "<transactionId>${body.transactionId}</transactionId>" +
                                "<status>APPROVED</status>" +
                                "<message>Nothing found, all okay</message>" +
                                "</payment>"
                ))
                .end()
                .to("jms:queue:fcsToBS");

        /* ==============================================================================
         * 10. RETURN ROUTING & ASYNC STATUS UPDATES
         * DEFENSE EXPLANATION: Takes the final XML from the Fraud system, audits it,
         * and updates the internal database so the client polling route can see the final status.
         * ============================================================================== */
        from("jms:queue:fcsToBS")
                .routeId("bs-result")
                .wireTap("jms:queue:auditQueue")
                .to("jms:queue:bsToPPS");

        from("jms:queue:bsToPPS")
                .routeId("pps-final")
                .convertBodyTo(String.class)
                .process(exchange -> {
                    String body = exchange.getIn().getBody(String.class);
                    String txId = extractTag(body, "transactionId");

                    // Update internal datastore with final result
                    if (body.contains("<status>APPROVED</status>")) {
                        STATUS_STORE.put(txId, "APPROVED");
                    } else if (body.contains("<status>REJECTED</status>")) {
                        STATUS_STORE.put(txId, "REJECTED");
                    }
                });

        /* ==============================================================================
         * 11. AUDIT QUEUE
         * DEFENSE EXPLANATION: A simple sink to log transactions without blocking flow.
         * ============================================================================== */
        from("jms:queue:auditQueue")
                .routeId("audit")
                .log("AUDIT EVENT: ${body}");
    }

    // Helper method to parse the raw XML response without needing full Unmarshalling overhead
    private static String extractTag(String xml, String tag) {
        String start = "<" + tag + ">";
        String end = "</" + tag + ">";

        int s = xml.indexOf(start);
        int e = xml.indexOf(end);

        if (s == -1 || e == -1) {
            return "UNKNOWN";
        }

        return xml.substring(s + start.length(), e);
    }
}