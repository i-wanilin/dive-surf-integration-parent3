package com.divesurf.results;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.AggregationStrategies;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;

import javax.jms.ConnectionFactory;

import static org.apache.camel.builder.Builder.header;

public class ResultSystem {

    public static void main(String[] args) throws Exception {

        CamelContext context = new DefaultCamelContext();

        // Configure the JMS component with ActiveMQ
        ConnectionFactory connectionFactory =
                new ActiveMQConnectionFactory("tcp://localhost:61616");
        context.addComponent("jms",
                JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {

                /* ---------- Large orders ---------- */
                from("jms:queue:largeOrders")
                    .process(e -> {
                        String[] parts = e.getIn().getBody(String.class).split(",");
                        e.getIn().setHeader("orderId", parts[6].trim());
                    })
                    // concatenate all message bodies for the same orderId
                    .aggregate(header("orderId"),
                               AggregationStrategies.string("\n"))
                    .completionSize(2)          // wait for billing + inventory
                    .process(e -> System.out.println(
                        "\n=== Aggregated Large Order ===\n"
                        + e.getIn().getBody(String.class)
                        + "\n============================\n"));

                /* ---------- Small orders ---------- */
                from("jms:queue:smallOrders")
                    .process(e -> {
                        String[] parts = e.getIn().getBody(String.class).split(",");
                        e.getIn().setHeader("orderId", parts[6].trim());
                    })
                    .aggregate(header("orderId"),
                               AggregationStrategies.string("\n"))
                    .completionSize(2)
                    .process(e -> System.out.println(
                        "\n=== Aggregated Small Order ===\n"
                        + e.getIn().getBody(String.class)
                        + "\n============================\n"));
            }
        });

        context.start();
        System.out.println("ResultSystem started. Waiting for orders…");
        Thread.sleep(Long.MAX_VALUE);
        context.stop();
    }
}
