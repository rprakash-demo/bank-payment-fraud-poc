package com.bank;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.main.Main;

public class BankApplication {
    public static void main(String[] args) throws Exception {
        Main main = new Main();
        ActiveMQConnectionFactory connectionFactory = new ActiveMQConnectionFactory("vm://localhost?broker.persistent=false");
        main.bind("jms", connectionFactory);
        main.configure().addRoutesBuilder(new PaymentApp());
        main.run(args);
    }
}