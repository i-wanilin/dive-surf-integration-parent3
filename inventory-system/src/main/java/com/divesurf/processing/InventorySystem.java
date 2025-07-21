package com.divesurf.InventorySystem;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.activemq.ActiveMQConnectionFactory;
import com.divesurf.common.Order;

import javax.jms.ConnectionFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class InventorySystem {

    public static void main(String[] args) throws Exception {
        Properties stockProps = new Properties();
        File stockFile = new File("stock.properties");

        if (!stockFile.exists()) {
            stockProps.setProperty("surfboards", "100");
            stockProps.setProperty("divingSuits", "50");
            try (FileOutputStream out = new FileOutputStream(stockFile)) {
                stockProps.store(out, "Inventory Stock");
            }
        }

        try (FileInputStream in = new FileInputStream(stockFile)) {
            stockProps.load(in);
        }

        // Use a wrapper to allow updating stock values and file from the processor
        StockManager stockManager = new StockManager(stockProps, stockFile);

        CamelContext context = new DefaultCamelContext();
        ConnectionFactory connectionFactory = new ActiveMQConnectionFactory("tcp://localhost:61616");
        context.addComponent("jms", JmsComponent.jmsComponentAutoAcknowledge(connectionFactory));

        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                // Inventory subscribes to the ordersForProcessing topic, validates, enriches, updates stock, and routes to big/small queues and inventoryEnrichedOrders
                from("jms:topic:ordersForProcessing?clientId=inventory&durableSubscriptionName=inventory")
                    .process(new StockValidator(stockManager));
            }
        });

        context.start();
        System.out.println("InventorySystem started");
        System.out.println("Initial stock - Surfboards: " + stockManager.getSurfboardStock() +
                          ", Diving Suits: " + stockManager.getDivingSuitStock());
        Thread.sleep(Long.MAX_VALUE);
    }

    // Helper class to manage stock and file updates
    private static class StockManager {
        private final Properties stockProps;
        private final File stockFile;

        public StockManager(Properties stockProps, File stockFile) {
            this.stockProps = stockProps;
            this.stockFile = stockFile;
        }

        public synchronized int getSurfboardStock() {
            return Integer.parseInt(stockProps.getProperty("surfboards"));
        }

        public synchronized int getDivingSuitStock() {
            return Integer.parseInt(stockProps.getProperty("divingSuits"));
        }

        public synchronized void updateStock(int surfboards, int divingSuits) {
            stockProps.setProperty("surfboards", String.valueOf(surfboards));
            stockProps.setProperty("divingSuits", String.valueOf(divingSuits));
            try (FileOutputStream out = new FileOutputStream(stockFile)) {
                stockProps.store(out, "Inventory Stock");
            } catch (Exception e) {
                System.err.println("Failed to update stock.properties: " + e.getMessage());
            }
        }
    }

    private static class StockValidator implements Processor {
        private final StockManager stockManager;

        public StockValidator(StockManager stockManager) {
            this.stockManager = stockManager;
        }

        @Override
        public void process(Exchange exchange) throws Exception {
            String message = exchange.getIn().getBody(String.class);
            String[] parts = message.split(",", 9);
            if (parts.length < 9) {
                throw new IllegalArgumentException("Invalid message format: " + message);
            }
            Order order = new Order();
            order.setCustomerID(parts[0].trim());
            order.setFirstName(parts[1].trim());
            order.setLastName(parts[2].trim());
            order.setOverallItems(parts[3].trim());
            order.setNumberOfDivingSuits(parts[4].trim());
            order.setNumberOfSurfboards(parts[5].trim());
            order.setOrderID(parts[6].trim());
            order.setValid(parts[7].trim());
            order.setValidationResult(parts.length > 8 ? parts[8].trim() : "");
            int surfboards = 0;
            int divingSuits = 0;
            try {
                surfboards = Integer.parseInt(order.getNumberOfSurfboards());
                divingSuits = Integer.parseInt(order.getNumberOfDivingSuits());
            } catch (NumberFormatException e) {
                order.setValid("false");
                order.setValidationResult("Invalid quantity format");
                exchange.getIn().setBody(order.toString());
                exchange.getIn().setHeader("validationType", "inventory");
                System.out.println("Inventory validation error: Invalid quantities");
                return;
            }
            boolean isValid = true;
            StringBuilder validationResult = new StringBuilder();
            int currentSurfboards = stockManager.getSurfboardStock();
            int currentDivingSuits = stockManager.getDivingSuitStock();
            int currentTotalStock = currentSurfboards + currentDivingSuits;
            if (surfboards > currentSurfboards) {
                isValid = false;
                validationResult.append("Insufficient surfboards. ");
            }
            if (divingSuits > currentDivingSuits) {
                isValid = false;
                validationResult.append("Insufficient diving suits. ");
            }
            // If valid, update the stock and save to file
            if (isValid) {
                stockManager.updateStock(currentSurfboards - surfboards, currentDivingSuits - divingSuits);
            } else {
                order.setValid("false");
                if (!order.getValidationResult().isEmpty()) {
                    order.setValidationResult(order.getValidationResult() + " | ");
                }
                order.setValidationResult(order.getValidationResult() + validationResult.toString());
            }
            int overallItems = Integer.parseInt(order.getOverallItems());
            exchange.getIn().setHeader("overallItems", overallItems);
            exchange.getIn().setHeader("validationType", "inventory");
            // Enrich with current stock
            EnrichedByInventorySystemOrder enriched = new EnrichedByInventorySystemOrder(
                order.getCustomerID(),
                order.getFirstName(),
                order.getLastName(),
                order.getOverallItems(),
                order.getNumberOfDivingSuits(),
                order.getNumberOfSurfboards(),
                order.getOrderID(),
                isValid,
                order.getValidationResult(),
                currentSurfboards,
                currentDivingSuits,
                currentTotalStock
            );
            // Send enriched order to largeOrders or smallOrders queue
            String enrichedCsv = enriched.toCsv();
            if (overallItems > 10) {
                exchange.getContext().createProducerTemplate().sendBody("jms:queue:largeOrders", enrichedCsv);
            } else {
                exchange.getContext().createProducerTemplate().sendBody("jms:queue:smallOrders", enrichedCsv);
            }
            System.out.println("Inventory validation: " + order.getOrderID() +
                    " - " + (isValid ? "IN STOCK" : "OUT OF STOCK") +
                    " | Surfboards: " + stockManager.getSurfboardStock() +
                    " | Suits: " + stockManager.getDivingSuitStock() +
                    " | CurrentTotalStock: " + currentTotalStock);
        }
    }

    // Enriched order with current stock fields
    public static class EnrichedByInventorySystemOrder {
        private final String customerId;
        private final String firstName;
        private final String lastName;
        private final String overallItems;
        private final String divingSuits;
        private final String surfboards;
        private final String orderId;
        private final boolean valid;
        private final String validationResult;
        private final int currentSurfboardStock;
        private final int currentDivingSuitStock;
        private final int currentTotalStock;

        public EnrichedByInventorySystemOrder(String customerId, String firstName, String lastName, String overallItems, String divingSuits, String surfboards, String orderId, boolean valid, String validationResult, int currentSurfboardStock, int currentDivingSuitStock, int currentTotalStock) {
            this.customerId = customerId;
            this.firstName = firstName;
            this.lastName = lastName;
            this.overallItems = overallItems;
            this.divingSuits = divingSuits;
            this.surfboards = surfboards;
            this.orderId = orderId;
            this.valid = valid;
            this.validationResult = validationResult;
            this.currentSurfboardStock = currentSurfboardStock;
            this.currentDivingSuitStock = currentDivingSuitStock;
            this.currentTotalStock = currentTotalStock;
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
                String.valueOf(currentSurfboardStock),
                String.valueOf(currentDivingSuitStock),
                String.valueOf(currentTotalStock)
            );
        }
    }
    }
