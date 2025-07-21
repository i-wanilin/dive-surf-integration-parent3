package com.divesurf.InventorySystem;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.activemq.ActiveMQConnectionFactory;
//import com.divesurf.common.Order;

import javax.jms.ConnectionFactory;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;

public class InventorySystem {

    public static void main(String[] args) throws Exception {
        Properties stockProps = new Properties();
        // Ensure we create/read stock.properties in the inventory-system module folder
        String baseDir = System.getProperty("user.dir");
        File stockFile = new File(baseDir + File.separator + "inventory-system" + File.separator + "stock.properties");

        if (!stockFile.exists()) {
            stockProps.setProperty("surfboards", "100");
            stockProps.setProperty("divingSuits", "50");
            try (FileOutputStream out = new FileOutputStream(stockFile)) {
                String header = "Inventory Stock\n"
                        + "Important notice: you may need to close and open the file if you've made some orders if you want to see this file changed\n"
                        + "if you change the file manually, you may want to also save it CTRL+S";
                stockProps.store(out, header);
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
                // Inventory subscribes to enriched orders from billing system
                from("jms:queue:billingToInventory")
                    .process(new StockValidator(stockManager))
                    // Content-Based Router: route to large/small based on quantity
                    .choice()
                        .when(header("overallItems").isGreaterThan(10))
                            .to("jms:queue:largeOrders")
                        .otherwise()
                            .to("jms:queue:smallOrders")
                    .end();
            }
        });

        context.start();
        System.out.println("InventorySystem started");
        // Display suits first, then surfboards
        System.out.println("Initial stock - Diving Suits: " + stockManager.getDivingSuitStock() +
                          ", Surfboards: " + stockManager.getSurfboardStock());
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
                String header = "Inventory Stock\n"
                        + "Important notice: you may need to close and open the file if you've made some orders if you want to see this file changed\n"
                        + "if you change the file manually, you may want to also save it CTRL+S";
                stockProps.store(out, header);
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
            // Split incoming full enriched CSV
            String[] parts = message.split(",", -1);
            String customerID = parts[0].trim();
            String firstName = parts[1].trim();
            String lastName = parts[2].trim();
            String overallItemsStr = parts[3].trim();
            String divingSuitsStr = parts[4].trim();
            String surfboardsStr = parts[5].trim();
            String orderID = parts[6].trim();
            String validStr = parts[7].trim();
            String validationResultStr = parts.length > 8 ? parts[8].trim() : "";
            // Parse quantities from enriched CSV: parts[5]=surfboards, parts[4]=divingSuits
            int surfboards;
            int divingSuits;
            try {
                divingSuits = Integer.parseInt(parts[4].trim());
                surfboards = Integer.parseInt(parts[5].trim());
            } catch (NumberFormatException e) {
                String errorCsv = String.join(",",
                    customerID,
                    firstName,
                    lastName,
                    overallItemsStr,
                    divingSuitsStr,
                    surfboardsStr,
                    orderID,
                    "false",
                    "Invalid quantity format",
                    String.valueOf(stockManager.getSurfboardStock()),
                    String.valueOf(stockManager.getDivingSuitStock()),
                    String.valueOf(stockManager.getSurfboardStock() + stockManager.getDivingSuitStock())
                );
                exchange.getIn().setBody(errorCsv);
                exchange.getIn().setHeader("validationType", "inventory");
                System.out.println("Inventory validation error: Invalid quantities");
                return;
            }
            boolean isBillingValid = Boolean.parseBoolean(validStr);
            int currentSurfboards = stockManager.getSurfboardStock();
            int currentDivingSuits = stockManager.getDivingSuitStock();
            int currentTotalStock = currentSurfboards + currentDivingSuits;
            int overallItems = Integer.parseInt(overallItemsStr);
            exchange.getIn().setHeader("overallItems", overallItems);
            exchange.getIn().setHeader("validationType", "inventory");

            /* ---------- evaluate stock regardless of billing result ---------- */
            boolean stockOk = surfboards <= currentSurfboards
                           && divingSuits <= currentDivingSuits;
            String stockMsg = stockOk ? "Stock sufficient"
                                      : "Insufficient stock";

            /* overall validity = billing OK **and** stock OK */
            boolean finalValid = isBillingValid && stockOk;

            String combinedValidation = stockMsg;

            if (stockOk && isBillingValid) {
                // update physical stock only when order will ship
                int newSurfboards = currentSurfboards - surfboards;
                int newDivingSuits = currentDivingSuits - divingSuits;
                stockManager.updateStock(newSurfboards, newDivingSuits);
                currentSurfboards = newSurfboards;
                currentDivingSuits = newDivingSuits;
                currentTotalStock = newSurfboards + newDivingSuits;
            }

            EnrichedByInventorySystemOrder enriched = new EnrichedByInventorySystemOrder(
                customerID.isEmpty() ? "-" : customerID,
                firstName.isEmpty()  ? "-" : firstName,
                lastName.isEmpty()   ? "-" : lastName,
                overallItemsStr,
                divingSuitsStr,
                surfboardsStr,
                orderID,
                finalValid,
                combinedValidation,
                currentSurfboards,
                currentDivingSuits,
                currentTotalStock
            );

            exchange.getIn().setBody(enriched.toCsv());
            exchange.getIn().setHeader("overallItems", overallItems);

            // Print updated stock counts and recalculate total after any update
            // Display suits first, then surfboards in validation log
            String status;
            if (!isBillingValid) {
                status = "BILLING REJECTED";
            } else if (finalValid) {
                status = "IN STOCK";
            } else {
                status = "OUT OF STOCK";
            }
            System.out.println("Inventory validation: " + orderID +
                    " - " + status +
                    " | Suits: " + stockManager.getDivingSuitStock() +
                    " | Surfboards: " + stockManager.getSurfboardStock() +
                    " | CurrentTotalStock: " + (stockManager.getSurfboardStock() + stockManager.getDivingSuitStock()));
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
