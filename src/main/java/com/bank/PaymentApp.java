package com.bank;

import com.bank.model.PaymentDTO;
import com.bank.service.PaymentValidator;
import jakarta.xml.bind.JAXBContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;

/**
 * Bank Payment Fraud Detection PoC — Apache Camel Route Definitions
 *
 * Solution 1: POST /api/v1/payments/secure  — Async JMS  (PPS ↔ BS via JSON/JMS)
 * Solution 2: POST /api/v1/payments             — Sync REST  (PPS ↔ BS via JSON/REST)
 *
 * Both solutions: BS ↔ FCS via XML/JMS
 *
 * Flow:
 * Client → PPS (validate) → BS (JSON→XML) → FCS (blacklist check)
 * → BS (XML→JSON)  → PPS (process result) → Client
 */
public class PaymentApp extends RouteBuilder {

    private final PaymentValidator validator = new PaymentValidator();
    private JaxbDataFormat jaxbFormat;

    @Override
    public void configure() throws Exception {

        // Shared JAXB context for BS: JSON→XML (request) and XML→JSON (response)
        JAXBContext context = JAXBContext.newInstance(PaymentDTO.class);
        this.jaxbFormat = new JaxbDataFormat(context);

        // ── Global Exception Handler ───────────────────────────────────────────
        // Catches validation and runtime errors — returns standard JSON error body
        onException(Exception.class)
                .handled(true)
                .process(exchange -> {
                    Exception cause = exchange.getProperty(Exchange.EXCEPTION_CAUGHT, Exception.class);
                    String txId = exchange.getProperty("txId", String.class);
                    String msg = (cause != null && cause.getMessage() != null)
                            ? cause.getMessage() : "Invalid request";
                    exchange.getMessage().setBody(
                            "{\"transactionId\":\"" + (txId != null ? txId : "UNKNOWN") + "\"," +
                            "\"status\":\"REJECTED\"," +
                            "\"message\":\"" + msg + "\"}"
                    );
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
                    exchange.getMessage().setHeader("Connection", "close");
                });

        // ── REST Configuration ─────────────────────────────────────────────────
        restConfiguration()
                .component("netty-http")
                .host("0.0.0.0")
                .port(8080);

        rest("/api/v1/payments")
                .consumes("application/json")
                .produces("application/json")
                .post()
                    .to("direct:pps-sync")      // Solution 2 — Sync REST
                .post("/secure")
                    .to("direct:pps-async");    // Solution 1 — Async JMS

        // ══════════════════════════════════════════════════════════════════════
        // PPS — PAYMENT PROCESSING SYSTEM
        // ══════════════════════════════════════════════════════════════════════

        // ── PPS: Solution 2 — Synchronous REST ────────────────────────────────
        // Receives JSON → validates → sends to BS → returns fraud result to client
        from("direct:pps-sync")
                .routeId("pps-sync")
                .wireTap("jms:queue:auditQueue")                            // Audit: payment received
                .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
                .process(exchange -> {
                    PaymentDTO dto = exchange.getIn().getBody(PaymentDTO.class);
                    if (dto != null) exchange.setProperty("txId", dto.getTransactionId());
                })
                .bean(validator, "validate")                                // Validate 13 mandatory fields
                .marshal().json(JsonLibrary.Jackson)
                .to("direct:bs-sync");                                      // Hand off to BS

        // ── PPS: Solution 1 — Asynchronous JMS ────────────────────────────────
        // Receives JSON → validates → publishes to JMS queue → returns 202 immediately
        from("direct:pps-async")
                .routeId("pps-async")
                .wireTap("jms:queue:auditQueue")                            // Audit: payment received
                .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
                .process(exchange -> {
                    PaymentDTO dto = exchange.getIn().getBody(PaymentDTO.class);
                    if (dto != null) exchange.setProperty("txId", dto.getTransactionId());
                })
                .bean(validator, "validate")                                // Validate 13 mandatory fields
                .marshal().json(JsonLibrary.Jackson)
                .to("jms:queue:ppsToBS")                                    // Publish JSON to BS via JMS
                .process(exchange -> {
                    String txId = exchange.getProperty("txId", String.class);
                    exchange.getMessage().getHeaders().clear();
                    exchange.getMessage().setBody(
                            "{\"transactionId\":\"" + txId + "\"," +
                            "\"status\":\"ACCEPTED_FOR_PROCESSING\"," +
                            "\"message\":\"Payment submitted for fraud screening via JMS\"}"
                    );
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
                    exchange.getMessage().setHeader("Connection", "close");
                });

        // ── PPS: Async Result Consumer ─────────────────────────────────────────
        // Receives final processing results from BS via JSON/JMS
        from("jms:queue:bsToPPS")
                .routeId("pps-result")
                .wireTap("jms:queue:auditQueue")                            // Audit: final result received
                .choice()
                    .when().jsonpath("$.status == 'APPROVED'")
                        .log("[${jsonpath($.transactionId)}] APPROVED — processing payment downstream")
                    .otherwise()
                        .log("[${jsonpath($.transactionId)}] REJECTED — halting payment processing")
                .end();

        // ══════════════════════════════════════════════════════════════════════
        // BS — BROKER SYSTEM
        // ══════════════════════════════════════════════════════════════════════

        // ── BS: Solution 2 — Sync JSON→XML → FCS → XML→JSON ───────────────────
        // Translates JSON to XML, sends to FCS via JMS (InOut), converts result back to JSON
        from("direct:bs-sync")
                .routeId("bs-sync")
                .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
                .marshal(jaxbFormat)                                        // JSON → XML
                .setHeader("CorrelationTxId", exchangeProperty("txId"))
                .to("jms:queue:bsToFCS?exchangePattern=InOut&requestTimeout=10000") 
                .convertBodyTo(String.class)
                .choice()
                    // Fixed: Replaced raw string inspection with a highly reliable standard XPath block
                    .when().xpath("/payment/status/text() = 'APPROVED'")
                        .process(exchange -> {
                            String txId = exchange.getIn().getHeader("CorrelationTxId", String.class);
                            exchange.getMessage().getHeaders().clear();
                            exchange.getMessage().setBody("{\"transactionId\":\"" + txId + "\",\"status\":\"APPROVED\",\"message\":\"Nothing found, all okay\"}");
                            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                            exchange.getMessage().setHeader("Connection", "close");
                        })
                    .otherwise()
                        .process(exchange -> {
                            String txId = exchange.getIn().getHeader("CorrelationTxId", String.class);
                            exchange.getMessage().getHeaders().clear();
                            exchange.getMessage().setBody("{\"transactionId\":\"" + txId + "\",\"status\":\"REJECTED\",\"message\":\"Suspicious payment\"}");
                            exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 403);
                            exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                            exchange.getMessage().setHeader("Connection", "close");
                        })
                .end();

        // ── BS: Solution 1 — Async JSON→XML → FCS ─────────────────────────────
        // Consumes JSON from ppsToBS queue → translates to XML → forwards to FCS via InOnly JMS
        from("jms:queue:ppsToBS")
                .routeId("bs-async")
                .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
                .marshal(jaxbFormat)                                        // JSON → XML
                .to("jms:queue:bsToFCS?exchangePattern=InOnly");

        // ── BS: Solution 1 — Async XML→JSON Result Transformer ──────────────────
        // Intercepts async XML response from FCS, marshals to JSON using clean declarative expressions
        from("jms:queue:fcsToBS")
                .routeId("bs-async-result")
                .wireTap("jms:queue:auditQueue")
                .choice()
                    // Fixed: Swapped literal math style validation for robust text node extraction checking
                    .when().xpath("/payment/status/text() = 'APPROVED'")
                        .setBody(simple("{\"transactionId\":\"${xpath(/payment/transactionId/text())}\",\"status\":\"APPROVED\",\"message\":\"Nothing found, all okay\"}"))
                    .otherwise()
                        .setBody(simple("{\"transactionId\":\"${xpath(/payment/transactionId/text())}\",\"status\":\"REJECTED\",\"message\":\"Suspicious payment\"}"))
                .end()
                .to("jms:queue:bsToPPS");

        // ══════════════════════════════════════════════════════════════════════
        // FCS — FRAUD CHECK SYSTEM
        // ══════════════════════════════════════════════════════════════════════

        // ── FCS: Blacklist Engine ──────────────────────────────────────────────
        // Receives XML → checks blacklists → replies directly back to caller or out to fcsToBS
        from("jms:queue:bsToFCS")
                .routeId("fcs-engine")
                .setProperty("capturedTxId", header("CorrelationTxId"))
                .unmarshal(jaxbFormat)                                      // XML → PaymentDTO
                .choice()
                    .when(simple(
                        // Name blacklist — payer and payee
                        "${body.payerName} regex '(?i)Mark Imaginary|Govind Real|Shakil Maybe|Chang Imagine' || " +
                        "${body.payeeName} regex '(?i)Mark Imaginary|Govind Real|Shakil Maybe|Chang Imagine' || " +
                        // Country blacklist — payer and payee
                        "${body.payerCountryCode} in 'CUB,IRQ,IRN,PRK,SDN,SYR' || " +
                        "${body.payeeCountryCode} in 'CUB,IRQ,IRN,PRK,SDN,SYR' || " +
                        // Bank blacklist — payer and payee
                        "${body.payerBank} regex '(?i).*BANK OF KUNLUN.*|.*KARAMAY CITY COMMERCIAL BANK.*' || " +
                        "${body.payeeBank} regex '(?i).*BANK OF KUNLUN.*|.*KARAMAY CITY COMMERCIAL BANK.*' || " +
                        // Payment instruction blacklist
                        "${body.paymentInstruction} regex '(?i).*Artillery Procurement.*|.*Lethal Chemicals payment.*'"
                    ))
                        .setBody(simple(
                            "<payment>" +
                                "<transactionId>${body.transactionId}</transactionId>" +
                                "<status>REJECTED</status>" +
                                "<message>Suspicious payment</message>" +
                            "</payment>"
                        ))
                    .otherwise()
                        .setBody(simple(
                            "<payment>" +
                                "<transactionId>${body.transactionId}</transactionId>" +
                                "<status>APPROVED</status>" +
                                "<message>Nothing found, all okay</message>" +
                            "</payment>"
                        ))
                .end()
                .setHeader("CorrelationTxId", exchangeProperty("capturedTxId"))
                .choice()
                    .when(header("CorrelationTxId").isNotNull())
                        // Solution 2 (Sync/InOut): Let Camel handle automatic JMSReplyTo return routing
                        .log("FCS processing synchronous InOut message.")
                    .otherwise()
                        // Solution 1 (Async/InOnly): Directly back to BS for conversion layer processing
                        .to("jms:queue:fcsToBS")
                .end();

        // ══════════════════════════════════════════════════════════════════════
        // AUDIT — WIRE TAP TARGET (all components)
        // ══════════════════════════════════════════════════════════════════════
        from("jms:queue:auditQueue")
                .routeId("audit")
                .log("AUDIT EVENT: ${body}");
    }
}