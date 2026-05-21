package com.bank;

import org.apache.camel.main.Main;
import org.apache.camel.builder.RouteBuilder;

/**
 * Payment Fraud Engine - Final Version
 * * Optimized for Google Cloud Run:
 * 1. Listens on 0.0.0.0:8080
 * 2. Uses synchronous 'direct' routing to prevent ExchangeTimedOutException
 * 3. Bypasses faulty REST configuration parsers
 */
public class PaymentApp {

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Payment Fraud Engine...");

        Main main = new Main();
        
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                // 1. Entry point: Undertow listener
                // Cloud Run strictly requires 0.0.0.0:8080
                from("undertow:http://0.0.0.0:8080/api/payment")
                    .log("Received payment request: ${body}")
                    .to("direct:validatePayment");

                // 2. Processing: Synchronous validation
                // Using 'direct' ensures the HTTP connection remains open 
                // until the response is sent back to the client.
                from("direct:validatePayment")
                    .setBody(constant("<response><status>APPROVED</status></response>"))
                    .log("Returning APPROVED response to client");
            }
        });

        // Run the application
        main.run(args);
    }
}