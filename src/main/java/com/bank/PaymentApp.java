package com.bank;

import org.apache.camel.main.Main;
import org.apache.camel.builder.RouteBuilder;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.model.rest.RestBindingMode;

public class PaymentApp {
    public static void main(String[] args) throws Exception {
        // Use environment variable or default to 8080
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        
        Main main = new Main();
        ActiveMQConnectionFactory cf = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        main.bind("jms", JmsComponent.jmsComponentAutoAcknowledge(cf));

        main.configure().addRoutesBuilder(new RouteBuilder() {
            @Override
            public void configure() {
                // Configure Undertow web server
                restConfiguration()
                    .component("undertow")
                    .host("0.0.0.0")
                    .port(port)
                    .bindingMode(RestBindingMode.off);

                // 1. REST Entry point: Calls 'direct:processPayment'
                rest("/api").post("/payment")
                    .to("direct:processPayment");

                // 2. Bridge REST to JMS: Sends to queue and returns response immediately
                from("direct:processPayment")
                    .to("jms:queue:payment.incoming")
                    .setBody(constant("{\"status\": \"ACCEPTED\"}"))
                    .setHeader("Content-Type", constant("application/json"));

                // 3. Background Logic: Asynchronous processing
                from("jms:queue:payment.incoming")
                    .log("Payment received in queue")
                    .to("jms:queue:fraud.check.queue");

                from("jms:queue:fraud.check.queue")
                    .log("Fraud check processed for message");
            }
        });
        
        System.out.println("Payment Engine starting on port " + port);
        main.run(args);
    }
}