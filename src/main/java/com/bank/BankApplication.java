package com.bank;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.main.Main;

public class BankApplication {
    public static void main(String[] args) throws Exception {
        Main main = new Main();

        // Initialize the ActiveMQ ConnectionFactory (Jakarta native for Camel 4)
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory();
        // Use an in-memory broker for the POC
        connectionFactory.setBrokerURL("vm://localhost?broker.persistent=false");

        // Bind the connection factory to the registry so the 'jms' component finds it
        main.bind("connectionFactory", connectionFactory);

        main.configure().addRoutesBuilder(new PaymentApp());
        main.run(args);
    }
}
