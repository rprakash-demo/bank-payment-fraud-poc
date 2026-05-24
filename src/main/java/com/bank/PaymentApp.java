package com.bank;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jacksonxml.JacksonXMLDataFormat;
import org.apache.camel.model.rest.RestBindingMode;

public class PaymentApp extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        // Configure REST component
        restConfiguration()
            .component("netty-http")
            .host("0.0.0.0").port(8080)
            .bindingMode(RestBindingMode.auto);

        // 1. SYNCHRONOUS FCS GATEWAY (XML Interface)
        rest("/api/payment")
            .post()
            .consumes("application/xml")
            .produces("application/xml")
            .to("direct:fcs-xml-process");

        from("direct:fcs-xml-process")
            .routeId("fcs-xml-gateway")
            .unmarshal(new JacksonXMLDataFormat())
            .setHeader("correlationId", simple("${body[transactionId]}"))
            .wireTap("jms:queue:auditQueue")
            .choice()
                .when(simple("${body[payerName]} regex 'Mark Imaginary|Govind Real|Shakil Maybe|Chang Imagine' || " +
                               "${body[payeeName]} regex 'Mark Imaginary|Govind Real|Shakil Maybe|Chang Imagine' || " +
                               "${body[payerCountryCode]} regex 'CUB|IRQ|IRN|PRK|SDN|SYR' || " +
                               "${body[payeeCountryCode]} regex 'CUB|IRQ|IRN|PRK|SDN|SYR' || " +
                               "${body[payerBank]} regex '(?i)BANK OF KUNLUN|KARAMAY CITY COMMERCIAL BANK' || " +
                               "${body[payeeBank]} regex '(?i)BANK OF KUNLUN|KARAMAY CITY COMMERCIAL BANK' || " +
                               "${body[paymentInstruction]} regex 'Artillery Procurement|Lethal Chemicals payment'"))
                    .to("direct:xml-reject")
                .otherwise()
                    .to("direct:xml-approve")
            .end();

        from("direct:xml-approve")
            .setBody(simple("<response><status>APPROVED</status><message>Nothing found, all okay</message></response>"));

        from("direct:xml-reject")
            .setBody(simple("<response><status>REJECTED</status><message>Suspicious payment</message></response>"));

        // 2. ASYNCHRONOUS SECURE PROCESSING
        rest("/api/payment-secure")
            .post()
            .to("direct:async-entry");

        from("direct:async-entry")
            .setHeader("correlationId", simple("${body[transactionId]}"))
            .wireTap("jms:queue:auditQueue")
            .to("jms:queue:paymentProcessingQueue")
            .setBody(constant("{\"status\":\"ACCEPTED_FOR_PROCESSING\"}"));

        // 3. ASYNC BACKGROUND WORKER (Fraud Validation)
        from("jms:queue:paymentProcessingQueue")
            .routeId("async-transaction-validator")
            .choice()
                .when(simple("${body[payerName]} regex 'Mark Imaginary|Govind Real|Shakil Maybe|Chang Imagine' || " +
                               "${body[payerCountryCode]} regex 'CUB|IRQ|IRN|PRK|SDN|SYR'"))
                    .log("ASYNC_REJECTED: Transaction ${header.correlationId} flagged for fraud.")
                .otherwise()
                    .log("ASYNC_APPROVED: Transaction ${header.correlationId} processed successfully.")
            .end();

        // 4. AUDIT LOGGING
        from("jms:queue:auditQueue")
            .routeId("audit-logger")
            .log("AUDIT_TRACE [ID: ${header.correlationId}]: ${body}");
    }
}