package com.divesurf.BillingSystem;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.activemq.ActiveMQConnectionFactory;
//import com.divesurf.common.Order;

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
                // Publish-Subscribe Channel: Consumes orders from topic
                from("jms:topic:ordersForProcessing?clientId=billing&durableSubscriptionName=billing")
                    .process(new CreditValidator());
        
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
            String customerID = parts[0];
            String firstName = parts[1];
            String lastName = parts[2];
            String overallItemsStr = parts[3];
            String divingSuits = parts[4];
            String surfboards = parts[5];
            String orderID = parts[6];
            String digitString = customerID.chars()
                .filter(Character::isDigit)
                .mapToObj(c -> String.valueOf((char) c))
                .collect(Collectors.joining());
            int digitSum = digitString.chars()
                .map(Character::getNumericValue)
                .sum();
            int creditScore = (digitSum % 10) + 1; // 1 to 10
            boolean isValid = creditScore >= 5; // 5-10 is good
            String validationResult = isValid ? "Credit score is good" : "Credit score too low";
            int overallItems = Integer.parseInt(overallItemsStr);
            exchange.getIn().setHeader("overallItems", overallItems);

            // Create basic order for inventory (no credit score)
            BasicValidatedOrder basicOrder = new BasicValidatedOrder(
                customerID,
                firstName,
                lastName,
                overallItemsStr,
                divingSuits,
                surfboards,
                orderID,
                isValid,
                validationResult
            );

            // Create enriched order for results (with credit score)
            EnrichedByBillingSystemOrder enriched = new EnrichedByBillingSystemOrder(
                customerID,
                firstName,
                lastName,
                overallItemsStr,
                divingSuits,
                surfboards,
                orderID,
                isValid,
                validationResult,
                creditScore
            );

            // Point-to-Point Channel: Send basic order (no credit score) to inventory queue for stock validation
            exchange.getContext().createProducerTemplate().sendBody("jms:queue:billingToInventory", basicOrder.toCsv());

            // Publish-Subscribe Channel: Send enriched order (with credit score) to results topic for aggregation
            exchange.getContext().createProducerTemplate().sendBody("jms:topic:billingResults", enriched.toCsv());
            // (Content-Based Router and Aggregator patterns are typically implemented in downstream systems)
            System.out.println("Billing validation: " + orderID +
                " - " + (isValid ? "APPROVED" : "REJECTED") + " | CreditScore: " + creditScore + " (" + validationResult + ")");
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

    // Basic validated order without credit score (for inventory)
    public static class BasicValidatedOrder {
        private final String customerId;
        private final String firstName;
        private final String lastName;
        private final String overallItems;
        private final String divingSuits;
        private final String surfboards;
        private final String orderId;
        private final boolean valid;
        private final String validationResult;

        public BasicValidatedOrder(String customerId, String firstName, String lastName, String overallItems, String divingSuits, String surfboards, String orderId, boolean valid, String validationResult) {
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
                overallItems,
                divingSuits,
                surfboards,
                orderId,
                String.valueOf(valid),
                validationResult
            );
        }
    }
}
