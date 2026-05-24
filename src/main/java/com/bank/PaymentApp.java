package com.bank;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultHeaderFilterStrategy;
import com.bank.model.PaymentDTO;
import com.bank.service.PaymentValidator;
import org.apache.camel.converter.jaxb.JaxbDataFormat;
import jakarta.xml.bind.JAXBContext;

/**
 * Enterprise Fraud Engine
 * Structure: [Infrastructure] -> [Security Gates] -> [Business Logic] -> [Audit/Response]
 * * WORKFLOW INDICATOR:
 * 1. [Infrastructure] Layer: REST/Netty initialization and header sanitation.
 * 2. [Security Gates] Layer: Ingestion validation via PaymentValidator.
 * 3. [Business Logic] Layer: Asynchronous pattern matching and blacklist verification.
 * 4. [Audit/Response] Layer: Egress cleanup and connection termination.
 */
public class PaymentApp extends RouteBuilder {

    @Override
    public void configure() throws Exception {
        JAXBContext context = JAXBContext.newInstance(PaymentDTO.class);
        JaxbDataFormat jaxb = new JaxbDataFormat(context);
        PaymentValidator validator = new PaymentValidator();

        /*
         * [INFRASTRUCTURE]
         * Configures the transport layer and enforces strict header hygiene.
         */
        DefaultHeaderFilterStrategy filter = new DefaultHeaderFilterStrategy();
        filter.setOutFilterPattern(".*"); 
        getContext().getRegistry().bind("myFilter", filter);

        restConfiguration().component("netty-http")
            .host("0.0.0.0").port(8080)
            .endpointProperty("headerFilterStrategy", "#myFilter");

        rest("/api/payment").post().to("direct:receive-payment");

        /*
         * [SECURITY GATES]
         * Validates input integrity before the message enters the internal bus.
         */
        from("direct:receive-payment")
            .unmarshal().json(PaymentDTO.class)
            .bean(validator, "validate")
            .marshal(jaxb)
            .to("jms:queue:fcs-input");

        /*
         * [BUSINESS LOGIC]
         * Processes asynchronously. Rules are partitioned for granular reporting.
         */
        from("jms:queue:fcs-input")
            .unmarshal(jaxb)
            .choice()
                .when(simple("${body.payerName} regex '(?i)Bad Actor|Mark Imaginary|Govind Real|Shakil Maybe' || " +
                               "${body.payeeName} regex '(?i)Bad Actor|Mark Imaginary|Govind Real|Shakil Maybe'"))
                    .setBody(constant("{\"status\":\"REJECTED\", \"message\":\"Blacklisted Entity Detected\"}"))
                
                .when(simple("${body.payerCountryCode} in 'CUB,SYR,IRQ,IRN,PRK,SDN,SY,CU,IQ,IR,KP,SD' || " +
                               "${body.payeeCountryCode} in 'CUB,SYR,IRQ,IRN,PRK,SDN,SY,CU,IQ,IR,KP,SD'"))
                    .setBody(constant("{\"status\":\"REJECTED\", \"message\":\"Restricted Jurisdiction Violation\"}"))
                
                .when(simple("${body.payerBank} regex '(?i).*Bank of Kunlun.*|.*Karamay City Commercial Bank.*' || " +
                               "${body.payeeBank} regex '(?i).*Bank of Kunlun.*|.*Karamay City Commercial Bank.*'"))
                    .setBody(constant("{\"status\":\"REJECTED\", \"message\":\"Sanctioned Bank Violation\"}"))
                
                .when(simple("${body.paymentInstruction} regex '(?i).*Artillery Procurement.*|.*Lethal Chemicals payment.*'"))
                    .setBody(constant("{\"status\":\"REJECTED\", \"message\":\"Suspicious Activity Detected\"}"))
                
                .otherwise()
                    .setBody(constant("{\"status\":\"APPROVED\", \"message\":\"All okay\"}"))
            .end()
            .to("jms:queue:fcs-output");

        /*
         * [AUDIT/RESPONSE]
         * Sanitizes the egress pipe to ensure only required headers reach the client.
         */
        from("jms:queue:fcs-output")
            .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
            .setHeader("Connection", constant("close"));
    }
}