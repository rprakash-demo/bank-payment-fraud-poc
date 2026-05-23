package com.bank;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.Exchange;
import java.util.Map;

public class PaymentApp extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // REST Configuration
        restConfiguration()
            .component("netty-http")
            .host("0.0.0.0").port(8080)
            .bindingMode(RestBindingMode.json)
            .enableCORS(true);

        // --- AUDIT QUEUE ---
        from("jms:queue:auditQueue")
            .routeId("audit-logger")
            .log("AUDIT SYSTEM: Persisting transaction: ${body}");

        // --- FAST TRACK (Synchronous) ---
        rest("/api/v1/payment-fast").post().to("direct:rest-flow");
        
        from("direct:rest-flow")
            .routeId("solution-2-rest")
            .setHeader("correlationId", simple("${uuid}"))
            .wireTap("jms:queue:auditQueue")
            
            // MANDATORY SECURITY GATE
            .choice()
                .when(simple("${body[payerName]} == 'Bad Actor' || ${body[payeeName]} == 'Bad Actor'"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                    .setBody(simple("{\"status\":\"REJECTED\", \"reason\":\"BLACKLISTED_NAME\", \"correlationId\":\"${header.correlationId}\"}"))
                    .stop()
                .when(simple("${body[amount]} > 10000"))
                    .setHeader(Exchange.HTTP_RESPONSE_CODE, constant(403))
                    .setBody(simple("{\"status\":\"REJECTED\", \"reason\":\"LIMIT_EXCEEDED\", \"correlationId\":\"${header.correlationId}\"}"))
                    .stop()
            .end()
            
            // BUSINESS LOGIC
            .choice()
                .when(simple("${body[amount]} > 100"))
                    .to("direct:mock-external-fraud-engine")
                .otherwise()
                    .setBody(simple("{\"status\":\"APPROVED\", \"engine\":\"Local-Internal-Check\", \"correlationId\":\"${header.correlationId}\"}"))
            .end();

        // --- MOCK FRAUD ENGINE ---
        from("direct:mock-external-fraud-engine")
            .setBody(simple("{\"status\":\"APPROVED\", \"engine\":\"Mock-External-REST\", \"correlationId\":\"${header.correlationId}\"}"));

        // --- SECURE FLOW (Asynchronous) ---
        rest("/api/v1/payment-secure").post().to("direct:messaging-flow");
        
        from("direct:messaging-flow")
            .routeId("solution-1-messaging")
            .setHeader("correlationId", simple("${uuid}")) // Ensuring ID is present
            .wireTap("jms:queue:auditQueue")
            .setBody(simple("{\"status\":\"ACCEPTED_FOR_PROCESSING\", \"correlationId\":\"${header.correlationId}\"}"));
    }
}