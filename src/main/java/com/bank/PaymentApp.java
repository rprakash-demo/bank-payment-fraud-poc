package com.bank;

import org.apache.camel.main.Main;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.converter.stream.CachedOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class PaymentApp {
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        
        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                getContext().setStreamCaching(true);

                from("netty-http:http://0.0.0.0:8080/api/payment")
                    .process(exchange -> {
                        // 1. Manually extract the input stream/buffer
                        InputStream is = exchange.getIn().getBody(InputStream.class);
                        String body = "";
                        
                        if (is != null) {
                            body = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                        }
                        
                        System.out.println("DEBUG_ACTUAL_BODY: " + body);

                        // 2. Logic
                        String status = "INVALID_REQUEST";
                        if (body.contains("<amount>")) {
                            try {
                                String amountStr = body.split("<amount>")[1].split("</amount>")[0].trim();
                                double amount = Double.parseDouble(amountStr);
                                status = (amount > 5000) ? "REVIEW_REQUIRED" : "APPROVED";
                            } catch (Exception e) {
                                status = "ERROR_PARSING";
                            }
                        }

                        exchange.getMessage().setBody("<response><status>" + status + "</status></response>");
                        exchange.getMessage().setHeader(Exchange.HTTP_RESPONSE_CODE, 200);
                        exchange.getMessage().setHeader("Content-Type", "application/xml");
                    });
            }
        });

        main.run(args);
    }
}