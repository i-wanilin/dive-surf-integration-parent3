package com.divesurf.CallCenterOrderSystem;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.*;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.aggregate.GroupedBodyAggregationStrategy;

import javax.jms.ConnectionFactory;
import java.util.Scanner;

public class CallCenterOrderSystem {

    public static void main(String[] args) throws Exception {
        CamelContext context = new DefaultCamelContext();

        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        // Channel Adapter: Integrates external CLI input into Camel routes
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // Point-to-Point Channel: Sending orders to a JMS queue (orders)
                from("direct:cli-orders")
                    .routeId("bufferedOrderRoute")
                    .log("Received order: ${body}")
                    .multicast().parallelProcessing()
                        .to("jms:queue:orders", "direct:collect-orders");

                // Aggregator pattern: Collects orders for 2 minutes before writing to file
                from("direct:collect-orders")
                    .aggregate(constant(true), new GroupedBodyAggregationStrategy())
                    .completionInterval(120000) //2 minutes
                    .log("Writing ${body.size()} orders to file")
                    // Message Translator: Converts list of orders to string for file output
                    .process(exchange -> {
                        @SuppressWarnings("unchecked")
                        java.util.List<String> orders = (java.util.List<String>) exchange.getIn().getBody();
                        StringBuilder builder = new StringBuilder();
                        for (String order : orders) {
                            builder.append(order).append("\n");
                        }
                        exchange.getIn().setBody(builder.toString());
                    })
                    .to("file:orders?fileName=callcenter_orders_log.txt&fileExist=Append");
            }
        });

        context.start();
        ProducerTemplate template = context.createProducerTemplate();

        //Get orders via CLI
        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter orders in format: <Full Name,Surfboards,Diving Suits,Customer-ID>");
        System.out.println("Type 'exit' to quit.");

        while (true) {
            System.out.print("Order: ");
            String input = scanner.nextLine();
            if ("exit".equalsIgnoreCase(input.trim())) break;

            try {
                String[] parts = input.split(",");
                if (parts.length != 4) throw new IllegalArgumentException();

                String fullName = parts[0].trim();
                int surfboards = Integer.parseInt(parts[1].trim());
                int divingSuits = Integer.parseInt(parts[2].trim());
                int customerId = Integer.parseInt(parts[3].trim());

                // Message Translator: Formats CLI input into a CSV order line
                String orderLine = String.format("%s,%d,%d,%d", fullName, surfboards, divingSuits, customerId);
                template.sendBody("direct:cli-orders", orderLine);
            } catch (Exception e) {
                System.out.println("Error: Invalid input. Please use the format: Full Name,Surfboards,Diving Suits,Customer-ID");
            }
        }

        context.stop();
        System.out.println("Application stopped.");
    }
}
