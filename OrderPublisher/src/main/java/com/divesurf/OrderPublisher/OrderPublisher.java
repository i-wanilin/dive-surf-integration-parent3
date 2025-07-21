package com.divesurf.OrderPublisher;

import org.apache.camel.main.Main;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.*;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;

import java.util.concurrent.atomic.AtomicLong;

import javax.jms.ConnectionFactory;
import java.util.Scanner;

public class OrderPublisher {

    private static AtomicLong orderIdGenerator = new AtomicLong(1);

    public static void main(String[] args) throws Exception {
        CamelContext context = new DefaultCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        //Implement Route from Order taking systems to message translator to content enricher to publisher
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:queue:orders")
                .routeId("order-processing-route")
                .log("Received raw order: ${body}")
                .process(new MessageTranslator())
                .log("Translated order: ${body}")
                .process(new OrderEnricher())
                .log("Enriched order: ${body}")
                .to("jms:topic:ordersForProcessing");
            }
        });

        context.start();
        System.out.println("OrderPublisher started...");
        System.out.println("Type 'exit' to quit.");

        try (Scanner scanner = new Scanner(System.in)) {
            ProducerTemplate producer = context.createProducerTemplate();
            while (true) {
                String input = scanner.nextLine();
                if ("exit".equalsIgnoreCase(input.trim())) {
                    break;
                }
            }
        }
        context.stop();
    }



    //Implementation of Message Translator to take WebOrders and Callcenter Orders and translate into unified format
    static class MessageTranslator implements Processor {
        @Override
        public void process(Exchange exchange) {
            String body = exchange.getIn().getBody(String.class);
            String[] parts = body.split(",");

            String customerId, firstName, lastName;
            int divingSuits, surfboards;

            if (parts[0].matches("\\d+")) {
                //Format WebOrder: <Customer-ID,First Name,Last  Name,Diving Suits,Surfboards>
                customerId = parts[0].trim();
                firstName = parts[1].trim();
                lastName = parts[2].trim();
                divingSuits = Integer.parseInt(parts[3].trim());
                surfboards = Integer.parseInt(parts[4].trim());
            } else {
                //Format CallCenterOrder: <Full Name,Surfboards,Diving Suits,Customer-ID>
                String[] nameParts = parts[0].trim().split(" ");
                firstName = nameParts[0].trim();
                lastName = nameParts[1].trim();
                surfboards = Integer.parseInt(parts[1].trim());
                divingSuits = Integer.parseInt(parts[2].trim());
                customerId = parts[3].trim();
            }

            //Unify Format
            UnifiedOrder order = new UnifiedOrder(customerId, firstName, lastName, divingSuits, surfboards);
            exchange.getIn().setBody(order);
        }
    }

    //Content Enricher to add OrderID, OverallItems, valid flag, validationResult
    static class OrderEnricher implements Processor {
        @Override
        public void process(Exchange exchange) {
            UnifiedOrder order = exchange.getIn().getBody(UnifiedOrder.class);

            int totalItems = order.getDivingSuits() + order.getSurfboards();
            long orderId = orderIdGenerator.getAndIncrement();
            boolean valid = true;

            String validationResult = "";

            EnrichedOrder enriched = new EnrichedOrder(
                order.getCustomerId(),
                order.getFirstName(),
                order.getLastName(),
                totalItems,
                order.getDivingSuits(),
                order.getSurfboards(),
                orderId,
                valid,
                validationResult
            );

            exchange.getIn().setBody(enriched.toCsv());
        }
    }

    //Unified Format Order Object
    static class UnifiedOrder {
        private final String customerId;
        private final String firstName;
        private final String lastName;
        private final int divingSuits;
        private final int surfboards;

        public UnifiedOrder(String customerId, String firstName, String lastName, int divingSuits, int surfboards) {
            this.customerId = customerId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.divingSuits = divingSuits;
            this.surfboards = surfboards;
        }

        public String getCustomerId() { return customerId; }
        public String getFirstName() { return firstName; }
        public String getLastName() { return lastName; }
        public int getDivingSuits() { return divingSuits; }
        public int getSurfboards() { return surfboards; }
    }

    //Enriched Order Object
    static class EnrichedOrder {
        private final String customerId;
        private final String firstName;
        private final String lastName;
        private final int overallItems;
        private final int divingSuits;
        private final int surfboards;
        private final long orderId;
        private final boolean valid;
        private final String validationResult;

        public EnrichedOrder(String customerId, String firstName, String lastName,
                             int overallItems, int divingSuits, int surfboards,
                             long orderId, boolean valid, String validationResult) {
            this.customerId = customerId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.overallItems = overallItems;
            this.divingSuits = divingSuits;
            this.surfboards = surfboards;
            this.orderId = orderId;
            this.valid = valid;
            this.validationResult = validationResult;
        }

        public String toCsv() {
            return String.join(",",
                customerId,
                firstName,
                lastName,
                String.valueOf(overallItems),
                String.valueOf(divingSuits),
                String.valueOf(surfboards),
                String.valueOf(orderId),
                String.valueOf(valid),
                validationResult
            );
        }
    }
}
