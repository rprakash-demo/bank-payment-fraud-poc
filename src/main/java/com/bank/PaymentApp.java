package com.bank;

import org.apache.camel.main.Main;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.Exchange;
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
                        InputStream is = exchange.getIn().getBody(InputStream.class);
                        String body = (is != null) ? new String(is.readAllBytes(), StandardCharsets.UTF_8) : "";
                        
                        String status;

                        // 1. Mandatory Field Validation
                        if (!body.contains("<amount>") || !body.contains("<payerName>") || !body.contains("<payerCountry>")) {
                            status = "INVALID_REQUEST";
                        }
                        // 2. Blacklist Fraud Check
                        else if (body.contains("Mark Imaginary") || body.contains("Govind Real") || 
                                 body.contains("Shakil Maybe") || body.contains("Chang Imagine") ||
                                 body.contains(">CUB<") || body.contains(">IRQ<") || body.contains(">IRN<") || 
                                 body.contains(">PRK<") || body.contains(">SDN<") || body.contains(">SYR<") ||
                                 body.contains("BANK OF KUNLUN") || body.contains("KARAMAY CITY COMMERCIAL BANK") ||
                                 body.contains("Artillery Procurement") || body.contains("Lethal Chemicals payment")) {
                            status = "Suspicious payment";
                        }
                        // 3. Threshold Check
                        else {
                            try {
                                String amountStr = body.split("<amount>")[1].split("</amount>")[0].trim();
                                if (Double.parseDouble(amountStr) > 5000) {
                                    status = "REVIEW_REQUIRED";
                                } else {
                                    status = "Nothing found, all okay.";
                                }
                            } catch (Exception e) {
                                status = "INVALID_REQUEST";
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