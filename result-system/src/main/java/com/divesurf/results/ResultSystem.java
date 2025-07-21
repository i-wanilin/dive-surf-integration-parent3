package com.divesurf.results;

import org.apache.activemq.ActiveMQConnectionFactory;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
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

                // Publish-Subscribe Channel: Receives billing results from topic
                from("jms:topic:billingResults?clientId=resultBilling&durableSubscriptionName=resultBilling")
                    .process(e -> {
                        String[] parts = e.getIn().getBody(String.class).split(",");
                        e.getIn().setHeader("orderId", parts[6].trim());
                        e.getIn().setHeader("source", "billing");
                        // Capture credit score for aggregation
                        if (parts.length > 9) {
                            e.getIn().setHeader("creditScoreHeader", parts[9].trim());
                        }
                    })
                    .to("jms:queue:aggregationInput");
                    
                // Point-to-Point Channel: Receives large orders from inventory
                from("jms:queue:largeOrders")
                    .process(e -> {
                        String[] parts = e.getIn().getBody(String.class).split(",");
                        e.getIn().setHeader("orderId", parts[6].trim());
                        e.getIn().setHeader("source", "inventory");
                        e.getIn().setHeader("orderSize", "large");
                    })
                    .to("jms:queue:aggregationInput");
                    
                // Point-to-Point Channel: Receives small orders from inventory
                from("jms:queue:smallOrders")
                    .process(e -> {
                        String[] parts = e.getIn().getBody(String.class).split(",");
                        e.getIn().setHeader("orderId", parts[6].trim());
                        e.getIn().setHeader("source", "inventory");
                        e.getIn().setHeader("orderSize", "small");
                    })
                    .to("jms:queue:aggregationInput");

                // Aggregator: Combines billing and inventory results for the same orderId
                // Content-Based Router: Routes aggregated orders by order size
                from("jms:queue:aggregationInput")
                    .aggregate(header("orderId"), (oldEx, newEx) -> {
                        // Handle first message case
                        if (oldEx == null) {
                            return newEx;
                        }
                        
                        /* -- decide which message is which -- */
                        Exchange billingEx   = "billing".equals(oldEx.getIn().getHeader("source"))   ? oldEx
                                             : "billing".equals(newEx.getIn().getHeader("source"))   ? newEx : null;
                        Exchange inventEx    = "inventory".equals(oldEx.getIn().getHeader("source")) ? oldEx
                                             : "inventory".equals(newEx.getIn().getHeader("source")) ? newEx : null;

                        if (billingEx == null || inventEx == null) {
                            /* only one side arrived – just keep collecting */
                            return oldEx != null ? oldEx : newEx;
                        }

                        String[] b = billingEx.getIn().getBody(String.class).split(",", -1);
                        String[] i = inventEx.getIn().getBody(String.class).split(",", -1);

                        /*  -------- build unified CSV --------  */
                        String[] out = new String[13];
                        /* static fields straight from billing */
                        System.arraycopy(b, 0, out, 0, 7);   // 0‑6

                        /* 7: valid flag – comes from inventory */
                        out[7] = i.length > 7 ? i[7].trim() : "";

                        /* 8: validation result = billing + inventory */
                        String vrBilling = b.length > 8 ? b[8].trim() : "";
                        String vrInv     = i.length > 8 ? i[8].trim() : "";
                        out[8] = vrBilling.isEmpty() ? vrInv
                                : vrInv.isEmpty()   ? vrBilling
                                : vrBilling + " && " + vrInv;

                        /* 9: credit‑score – from billing (header copy is safer) */
                        out[9] = billingEx.getIn().getHeader("creditScoreHeader", String.class);

                        /* 10‑12: stock numbers – from inventory */
                        out[10] = i.length > 10 ? i[9].trim() : "";
                        out[11] = i.length > 11 ? i[10].trim() : "";
                        out[12] = i.length >= 11 ? i[11].trim() : ""; // because it won't work otherwise

                        /* set body + propagate orderSize header */
                        billingEx.getIn().setBody(String.join(",", out));
                        billingEx.getIn().setHeader("orderSize",
                              inventEx.getIn().getHeader("orderSize"));
                        return billingEx;         // always return the enriched envelope
                    })
                    .completionSize(2)
                    .completionTimeout(5000)   // 5‑second timeout
                    // Content-Based Router: route by order size from header or fallback to parsing
                    .choice()
                        .when(header("orderSize").isEqualTo("large"))
                            .to("jms:queue:finalLargeOrders")
                        .when(header("orderSize").isEqualTo("small"))
                            .to("jms:queue:finalSmallOrders")
                        .otherwise()
                            // Fallback: parse message if header is missing
                            .choice()
                                .when(e -> {
                                    String[] parts = e.getIn().getBody(String.class).split(",");
                                    return Integer.parseInt(parts[3].trim()) > 10;
                                })
                                    .to("jms:queue:finalLargeOrders")
                                .otherwise()
                                    .to("jms:queue:finalSmallOrders");

                /* ---------- Large orders ---------- */
                from("jms:queue:finalLargeOrders")
                    .process(e -> {
                        String[] fields = {"Customer ID", "First Name", "Last Name", "Overall Items", "Diving Suits", "Surfboards", "Order ID", "Valid", "Validation Result", "Credit Score", "Current Surfboards", "Current Suits", "Total Stock"};
                        String[] values = e.getIn().getBody(String.class).split(",");
                        StringBuilder sb = new StringBuilder();
                        sb.append("\n=== Aggregated Large Order ===\n");
                        for (int i = 0; i < Math.min(fields.length, values.length); i++) {
                            sb.append(String.format("%-18s : %s\n", fields[i], values[i].trim()));
                        }
                        sb.append("============================\n");
                        System.out.print(sb.toString());
                    });

                /* ---------- Small orders ---------- */
                from("jms:queue:finalSmallOrders")
                    .process(e -> {
                        String[] fields = {"Customer ID", "First Name", "Last Name", "Overall Items", "Diving Suits", "Surfboards", "Order ID", "Valid", "Validation Result", "Credit Score", "Current Surfboards", "Current Suits", "Total Stock"};
                        String[] values = e.getIn().getBody(String.class).split(",");
                        StringBuilder sb = new StringBuilder();
                        sb.append("\n=== Aggregated Small Order ===\n");
                        for (int i = 0; i < Math.min(fields.length, values.length); i++) {
                            sb.append(String.format("%-18s : %s\n", fields[i], values[i].trim()));
                        }
                        sb.append("============================\n");
                        System.out.print(sb.toString());
                    });
            }
        });

        context.start();
        System.out.println("ResultSystem started. Waiting for orders…");
        Thread.sleep(Long.MAX_VALUE);
        context.stop();
    }
}
