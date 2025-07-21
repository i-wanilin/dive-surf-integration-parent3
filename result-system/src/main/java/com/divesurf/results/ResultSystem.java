package com.divesurf.results;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.aggregate.AggregationStrategy;
import static org.apache.camel.builder.Builder.header;
import javax.jms.ConnectionFactory;
import org.apache.activemq.ActiveMQConnectionFactory;

public class ResultSystem {

    public static void main(String[] args) throws Exception {
        @SuppressWarnings("resource")
        CamelContext context = new DefaultCamelContext();

        // Configure the JMS component with ActiveMQ
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        // Define a route that listens to the validationResults queue
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // Aggregate large orders by orderId
                from("jms:queue:largeOrders")
                    .process(exchange -> {
                        String body = exchange.getIn().getBody(String.class);
                        String[] parts = body.split(",");
                        exchange.getIn().setHeader("orderId", parts[6].trim());
                    })
                    .aggregate(header("orderId"), (oldExchange, newExchange) -> {
                        if (oldExchange == null) {
                            return newExchange;
                        }
                        String combined = oldExchange.getIn().getBody(String.class)
                            + "\n" + newExchange.getIn().getBody(String.class);
                        oldExchange.getIn().setBody(combined);
                        return oldExchange;
                    })
                    .completionSize(2)
                    .process(exchange -> {
                        System.out.println("\n=== Aggregated Large Order ===\n" + exchange.getIn().getBody(String.class) + "\n============================\n");
                    });

                // Aggregate small orders by orderId
                from("jms:queue:smallOrders")
                    .process(exchange -> {
                        String body = exchange.getIn().getBody(String.class);
                        String[] parts = body.split(",");
                        exchange.getIn().setHeader("orderId", parts[6].trim());
                    })
                    .aggregate(header("orderId"), (oldExchange, newExchange) -> {
                        if (oldExchange == null) {
                            return newExchange;
                        }
                        String combined = oldExchange.getIn().getBody(String.class)
                            + "\n" + newExchange.getIn().getBody(String.class);
                        oldExchange.getIn().setBody(combined);
                        return oldExchange;
                    })
                    .completionSize(2)
                    .process(exchange -> {
                        System.out.println("\n=== Aggregated Small Order ===\n" + exchange.getIn().getBody(String.class) + "\n============================\n");
                    });
            }
        });

        context.start();
        System.out.println("ResultSystem started. Waiting for orders...");
        // Keep the application running
        Thread.sleep(Long.MAX_VALUE);
        context.stop();
    }
}