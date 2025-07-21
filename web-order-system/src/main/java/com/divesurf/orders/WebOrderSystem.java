package com.divesurf.WebOrderSystem;  // was com.divesurf.orders, won't run on my machine, changed to com.divesurf.WebOrderSystem

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import javax.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;

import java.util.Scanner;

public class WebOrderSystem {

    public static void main(String[] args) throws Exception {
        CamelContext context = new DefaultCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        //Implement Message Endpoint
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                //Route: from direct:start (input endpoint) to JMS queue (output endpoint)
                from("direct:start")
                    .routeId("webOrderRoute")
                    .log("Received raw order: ${body}")
                    .process(new WebOrderProcessor())
                    .to("jms:queue:orders")
                    .log("Sent to JMS queue: ${body}");
            }
        });

        context.start();

        try (Scanner scanner = new Scanner(System.in)) {
            ProducerTemplate producer = context.createProducerTemplate();

            System.out.println("Enter orders in format: <Customer-ID,First Name,Last Name,Diving suits,Surfboards>");
            System.out.println("Type 'exit' to quit.");
            while (true) {
                System.out.print("Order: ");
                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input.trim())) {
                    break;
                }
                producer.sendBody("direct:start", input);
            }
        }

        context.stop();
    }

    static class WebOrderProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            String body = exchange.getIn().getBody(String.class);
            try {
                String[] parts = body.split(",");
                if (parts.length != 5) throw new IllegalArgumentException();

                String customerId = parts[0];
                String firstName = parts[1];
                String lastName = parts[2];
                int divingSuits = Integer.parseInt(parts[3]);
                int surfboards = Integer.parseInt(parts[4]);

                String orderLine = String.format("%s,%s,%s,%d,%d", customerId, firstName, lastName, divingSuits, surfboards);
                exchange.getIn().setBody(orderLine);
            } catch (Exception e) {
                System.out.println("Error: Invalid input. Please use the format: Customer-ID,First Name,Last Name,Diving suits,Surfboards");
            }
        }
    }
}
