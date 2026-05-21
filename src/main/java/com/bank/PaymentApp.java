package com.bank;

import org.apache.camel.main.Main;
import org.apache.camel.builder.RouteBuilder;

public class PaymentApp {
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                // We use a direct endpoint to avoid all REST configuration/parsing
                from("undertow:http://0.0.0.0:8080/api/payment")
                    .log("Payment received")
                    .to("direct:validatePayment");

                from("direct:validatePayment")
                    .to("seda:payment.incoming");

                from("seda:payment.incoming")
                    .marshal().jacksonXml()
                    .to("seda:fraud.check.queue");

                from("seda:fraud.check.queue")
                    .setBody(constant("<response><status>APPROVED</status></response>"))
                    .to("seda:payment.reply.queue");
            }
        });
        main.run(args);
    }
}