package com.divesurf.BillingSystem;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.activemq.ActiveMQConnectionFactory;
import com.divesurf.common.Order;

import javax.jms.ConnectionFactory;
import java.util.stream.Collectors;

public class BillingSystem {

    public static void main(String[] args) throws Exception {
        CamelContext context = new DefaultCamelContext();
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("jms:topic:ordersForProcessing?clientId=billing&durableSubscriptionName=billing")
                    .process(new CreditValidator())
                    .choice()
                        .when(header("overallItems").isGreaterThan(10))
                            .to("jms:queue:largeOrders")
                        .otherwise()
                            .to("jms:queue:smallOrders")
                    .end()
                    .to("jms:queue:billingToInventory");
            }
        });

        context.start();
        System.out.println("BillingSystem started");
        Thread.sleep(Long.MAX_VALUE);
    }

    private static class CreditValidator implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            String message = exchange.getIn().getBody(String.class);
            String[] parts = message.split(",", 9);
            if (parts.length < 9) {
                throw new IllegalArgumentException("Invalid message format: " + message);
            }
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim();
            }
            Order order = new Order();
            order.setCustomerID(parts[0]);
            order.setFirstName(parts[1]);
            order.setLastName(parts[2]);
            order.setOverallItems(parts[3]);
            order.setNumberOfDivingSuits(parts[4]);
            order.setNumberOfSurfboards(parts[5]);
            order.setOrderID(parts[6]);
            String digitString = order.getCustomerID().chars()
                .filter(Character::isDigit)
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());
            int digitSum = digitString.chars()
                .map(Character::getNumericValue)
                .sum();
            int threshold = 3;
            boolean isValid = (digitSum % 10) >= threshold;
            order.setValid(String.valueOf(isValid));
            if (!isValid) {
                order.setValidationResult("Credit check failed");
            }
            int overallItems = Integer.parseInt(order.getOverallItems());
            exchange.getIn().setHeader("overallItems", overallItems);
            // Enrich with creditScore and send to new queue
            EnrichedByBillingSystemOrder enriched = new EnrichedByBillingSystemOrder(
                order.getCustomerID(),
                order.getFirstName(),
                order.getLastName(),
                order.getOverallItems(),
                order.getNumberOfDivingSuits(),
                order.getNumberOfSurfboards(),
                order.getOrderID(),
                isValid,
                order.getValidationResult(),
                digitSum // creditScore
            );
            exchange.getIn().setBody(enriched.toCsv());
            exchange.getIn().setHeader("validationType", "billing");
            // The route will handle sending to largeOrders/smallOrders and to inventory
            System.out.println("Billing validation: " + order.getOrderID() +
                " - " + (isValid ? "APPROVED" : "REJECTED") + " | CreditScore: " + digitSum);
        }
    }

    // Enriched order with creditScore
    public static class EnrichedByBillingSystemOrder {
        private final String customerId;
        private final String firstName;
        private final String lastName;
        private final String overallItems;
        private final String divingSuits;
        private final String surfboards;
        private final String orderId;
        private final boolean valid;
        private final String validationResult;
        private final int creditScore;

        public EnrichedByBillingSystemOrder(String customerId, String firstName, String lastName, String overallItems, String divingSuits, String surfboards, String orderId, boolean valid, String validationResult, int creditScore) {
            this.customerId = customerId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.overallItems = overallItems;
            this.divingSuits = divingSuits;
            this.surfboards = surfboards;
            this.orderId = orderId;
            this.valid = valid;
            this.validationResult = validationResult;
            this.creditScore = creditScore;
        }
        public String toCsv() {
            return String.join(",",
                customerId,
                firstName,
                lastName,
                overallItems,
                divingSuits,
                surfboards,
                orderId,
                String.valueOf(valid),
                validationResult,
                String.valueOf(creditScore)
            );
        }
    }
    }
