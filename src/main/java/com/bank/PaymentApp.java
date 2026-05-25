package com.bank;

import com.bank.model.PaymentDTO;
import com.bank.service.PaymentValidator;
import jakarta.xml.bind.JAXBContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;

import java.util.concurrent.ConcurrentHashMap;

public class PaymentApp extends RouteBuilder {

    private static final ConcurrentHashMap<String, String> STATUS_STORE = new ConcurrentHashMap<>();

    @Override
    public void configure() throws Exception {

        JAXBContext context = JAXBContext.newInstance(PaymentDTO.class);
        JaxbDataFormat jaxb = new JaxbDataFormat(context);
        PaymentValidator validator = new PaymentValidator();

        /*
         * GLOBAL ERROR HANDLER
         */
        onException(Exception.class)
            .handled(true)
            .process(exchange -> {
                String contentType = exchange.getProperty("originalContentType", String.class);

                exchange.getMessage().getHeaders().clear();

                if (contentType != null && contentType.contains("xml")) {
                    exchange.getMessage().setBody(
                        "<paymentResponse>" +
                        "<status>REJECTED</status>" +
                        "<message>Invalid Request</message>" +
                        "</paymentResponse>"
                    );
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/xml");
                } else {
                    exchange.getMessage().setBody(
                        "{\"status\":\"REJECTED\",\"message\":\"Invalid Request\"}"
                    );
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                }

                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 400);
                exchange.getMessage().setHeader("Connection", "close");
            });

        /*
         * REST CONFIG
         */
        restConfiguration()
            .component("netty-http")
            .host("0.0.0.0")
            .port(8080);

        /*
         * REST APIs
         */
        rest("/api/v1/payments")
            .post()
                .to("seda:pps-sync")
            .post("/secure")
                .to("seda:pps-async")
            .get("/{transactionId}/status")
                .to("direct:get-status");

        /*
         * STATUS LOOKUP
         */
        from("direct:get-status")
            .routeId("status-check")
            .process(exchange -> {
                String txId = exchange.getIn().getHeader("transactionId", String.class);
                String status = STATUS_STORE.getOrDefault(txId, "NOT_FOUND");

                exchange.getMessage().getHeaders().clear();

                if ("NOT_FOUND".equals(status)) {
                    exchange.getMessage().setBody(
                        "{\"transactionId\":\"" + txId + "\",\"status\":\"NOT_FOUND\"}"
                    );
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 404);
                } else {
                    exchange.getMessage().setBody(
                        "{\"transactionId\":\"" + txId + "\",\"status\":\"" + status + "\"}"
                    );
                    exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                }

                exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                exchange.getMessage().setHeader("Connection", "close");
            });

        /*
         * PPS SYNC
         */
        from("seda:pps-sync")
            .routeId("pps-sync")
            .wireTap("jms:queue:auditQueue")
            .process(exchange -> {
                exchange.setProperty(
                    "originalContentType",
                    exchange.getIn().getHeader("Content-Type", String.class)
                );
            })
            .choice()
                .when(header("Content-Type").contains("xml"))
                    .unmarshal(jaxb)
                .otherwise()
                    .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
            .end()
            .bean(validator, "validate")
            .marshal().json(JsonLibrary.Jackson)
            .to("direct:bs-sync");

        /*
         * PPS ASYNC
         */
        from("seda:pps-async")
            .routeId("pps-async")
            .wireTap("jms:queue:auditQueue")
            .process(exchange -> {
                exchange.setProperty(
                    "originalContentType",
                    exchange.getIn().getHeader("Content-Type", String.class)
                );
            })
            .choice()
                .when(header("Content-Type").contains("xml"))
                    .unmarshal(jaxb)
                .otherwise()
                    .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
            .end()
            .bean(validator, "validate")
            .process(exchange -> {
                PaymentDTO payment = exchange.getIn().getBody(PaymentDTO.class);
                STATUS_STORE.put(payment.getTransactionId(), "PROCESSING");
            })
            .marshal().json(JsonLibrary.Jackson)
            .to("jms:queue:ppsToBS")
            .process(exchange -> {
                String contentType = exchange.getProperty("originalContentType", String.class);

                exchange.getMessage().getHeaders().clear();

                if (contentType != null && contentType.contains("xml")) {
                    exchange.getMessage().setBody(
                        "<paymentResponse>" +
                        "<status>ACCEPTED_FOR_PROCESSING</status>" +
                        "<message>Queued</message>" +
                        "</paymentResponse>"
                    );
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/xml");
                } else {
                    exchange.getMessage().setBody(
                        "{\"status\":\"ACCEPTED_FOR_PROCESSING\",\"message\":\"Queued\"}"
                    );
                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                }

                exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 202);
                exchange.getMessage().setHeader("Connection", "close");
            });

        /*
         * BS SYNC
         */
        from("direct:bs-sync")
            .routeId("bs-sync")
            .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
            .marshal(jaxb)
            .to("jms:queue:bsToFCS?exchangePattern=InOut")
            .to("direct:sync-response");

        /*
         * SYNC RESPONSE
         */
        from("direct:sync-response")
            .routeId("sync-response")
            .convertBodyTo(String.class)
            .process(exchange -> {
                String body = exchange.getIn().getBody(String.class);
                String contentType = exchange.getProperty("originalContentType", String.class);

                exchange.getMessage().getHeaders().clear();

                boolean rejected = body.contains("<status>REJECTED</status>");

                if (contentType != null && contentType.contains("xml")) {

                    if (rejected) {
                        exchange.getMessage().setBody(
                            "<paymentResponse>" +
                            "<status>REJECTED</status>" +
                            "<message>Fraud Policy Violation</message>" +
                            "</paymentResponse>"
                        );
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 403);
                    } else {
                        exchange.getMessage().setBody(
                            "<paymentResponse>" +
                            "<status>APPROVED</status>" +
                            "<message>Nothing found, all okay</message>" +
                            "</paymentResponse>"
                        );
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                    }

                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/xml");

                } else {

                    if (rejected) {
                        exchange.getMessage().setBody(
                            "{\"status\":\"REJECTED\",\"message\":\"Fraud Policy Violation\"}"
                        );
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 403);
                    } else {
                        exchange.getMessage().setBody(
                            "{\"status\":\"APPROVED\",\"message\":\"Nothing found, all okay\"}"
                        );
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                    }

                    exchange.getMessage().setHeader(Exchange.CONTENT_TYPE, "application/json");
                }

                exchange.getMessage().setHeader("Connection", "close");
            });

        /*
         * BS ASYNC
         */
        from("jms:queue:ppsToBS")
            .routeId("bs-async")
            .unmarshal().json(JsonLibrary.Jackson, PaymentDTO.class)
            .marshal(jaxb)
            .to("jms:queue:bsToFCS");

        /*
         * FCS
         */
        from("jms:queue:bsToFCS")
            .routeId("fraud-engine")
            .unmarshal(jaxb)
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
                .otherwise()
                    .setBody(simple(
                        "<payment>" +
                        "<transactionId>${body.transactionId}</transactionId>" +
                        "<status>APPROVED</status>" +
                        "<message>Nothing found, all okay</message>" +
                        "</payment>"
                    ))
            .end()
            .to("jms:queue:fcsToBS");

        /*
         * BS RESULT
         */
        from("jms:queue:fcsToBS")
            .routeId("bs-result")
            .wireTap("jms:queue:auditQueue")
            .to("jms:queue:bsToPPS");

        /*
         * PPS FINAL ASYNC STATUS
         */
        from("jms:queue:bsToPPS")
            .routeId("pps-final")
            .convertBodyTo(String.class)
            .process(exchange -> {
                String body = exchange.getIn().getBody(String.class);
                String txId = extractTag(body, "transactionId");

                if (body.contains("<status>APPROVED</status>")) {
                    STATUS_STORE.put(txId, "APPROVED");
                } else if (body.contains("<status>REJECTED</status>")) {
                    STATUS_STORE.put(txId, "REJECTED");
                }
            });

        /*
         * AUDIT
         */
        from("jms:queue:auditQueue")
            .routeId("audit")
            .log("AUDIT EVENT: ${body}");
    }

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