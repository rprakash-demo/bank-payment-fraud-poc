package com.bank;

import org.apache.camel.main.Main;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;

public class PaymentApp {
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Broker System (BS) - Fraud Engine (Netty)...");

        Main main = new Main();
        
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                // Using netty-http for reliable proxy handling in Cloud Run
                from("netty-http:http://0.0.0.0:8080/api/payment")
                    .setExchangePattern(ExchangePattern.InOut)
                    .log("Broker System: Request received")
                    .process(exchange -> {
                        String responseBody = "<response><status>APPROVED</status></response>";
                        
                        // Set headers
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                        exchange.getMessage().setHeader("Content-Type", "application/xml");
                        
                        // Set body
                        exchange.getMessage().setBody(responseBody);
                    })
                    .log("Broker System: Response flushed");
            }
        });

        main.run(args);
    }
}