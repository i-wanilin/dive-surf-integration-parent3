
# VS 2025 – Distributed Systems: Practical Assignment 4

## JMS Multi-System Order Processing with ActiveMQ

This repository contains the implementation of a **distributed order processing system** using **Java Message Service (JMS)** and **ActiveMQ**, developed for the *Distributed Systems* course (VS 2025) at Technische Universität Berlin. The system simulates a real-world order workflow, integrating multiple subsystems for order validation, billing, inventory management, and result aggregation.

---

## Assignment Objectives

This project addresses the following key goals:

- Implement a **multi-stage distributed order processing pipeline** using **JMS (Java Message Service)**.
- Integrate **multiple subsystems** (web/callcenter order entry, billing, inventory, result aggregation).
- Ensure **robust field mapping and data consistency** across all subsystems.
- Support **real-time order validation** and **stock management**.
- Use **ActiveMQ** as the message broker for reliable, decoupled communication.

---

## Protocol & Functional Overview

### System Components

- **Web Order System**: Accepts orders from web clients.
- **Call Center Order System**: Accepts orders from call center agents.
- **Order Publisher**: Publishes orders to the processing pipeline.
- **Billing System**: Validates customer credit and enriches orders with credit score.
- **Inventory System**: Validates and updates stock, enriches orders with current stock.
- **Result System**: Aggregates results from billing and inventory, produces final order status.

### Message Flow

1. **Order Entry**: Orders are submitted via web or call center systems.
2. **Publishing**: Orders are published to a JMS topic for processing.
3. **Billing Validation**: Billing system checks credit score and validity.
4. **Inventory Validation**: Inventory system checks and updates stock.
5. **Result Aggregation**: Result system merges billing and inventory results, producing the final order outcome.

### Data Consistency

- **Explicit field mapping** is enforced at each stage to prevent data misalignment.
- **Credit score** is always sourced from the billing system.
- **Stock values** are always sourced from the inventory system and reflect the post-order state.

---

## Project Structure

```
dive-surf-integration-parent/
├── pom.xml                        # Parent Maven configuration
├── README TASK 4.md               # Project documentation (this file)
├── billing-system/                # Billing validation subsystem
│   ├── pom.xml
│   └── src/main/java/com/divesurf/processing/BillingSystem.java
├── inventory-system/              # Inventory validation subsystem
│   ├── pom.xml
│   └── src/main/java/com/divesurf/processing/InventorySystem.java
├── result-system/                 # Result aggregation subsystem
│   ├── pom.xml
│   └── src/main/java/com/divesurf/processing/ResultSystem.java
├── web-order-system/              # Web order entry
│   ├── pom.xml
│   └── src/main/java/com/divesurf/WebOrderSystem/WebOrderSystem.java
├── callcenter-order-system/       # Call center order entry
│   ├── pom.xml
│   └── src/main/java/com/divesurf/CallCenterOrderSystem/CallCenterOrderSystem.java
├── OrderPublisher/                # Order publisher
│   ├── pom.xml
│   └── src/main/java/com/divesurf/OrderPublisher/OrderPublisher.java
├── common/                        # (Optional) Shared classes and message types
│   ├── pom.xml
│   └── src/main/java/com/divesurf/common/Order.java
├── lib/                           # Third-party libraries (ActiveMQ, Camel, etc.)
│   └── ...
├── stock.properties               # Inventory stock file (auto-generated/updated)
└── orders/                        # Order logs
    └── callcenter_orders_log.txt
```

---

## Build & Execution

### Requirements

- **Java 8+**
- **Maven 3.6+**
- **ActiveMQ 5.13.3+** (see installation instructions below)

### ActiveMQ Installation

#### Download and Install ActiveMQ

1. Download ActiveMQ from Apache:
```bash
wget http://archive.apache.org/dist/activemq/5.16.3/apache-activemq-5.16.3-bin.tar.gz
```

2. Extract the downloaded file:
```bash
sudo tar -xvzf apache-activemq-5.16.3-bin.tar.gz
```

3. Create directory and move files:
```bash
sudo mkdir /opt/activemq
sudo mv apache-activemq-5.16.3/* /opt/activemq
```

#### Create ActiveMQ User and Group

4. Create group and user for ActiveMQ:
```bash
sudo addgroup --quiet --system activemq
sudo adduser --quiet --system --ingroup activemq --no-create-home --disabled-password activemq
```

5. Set permissions:
```bash
sudo chown -R activemq:activemq /opt/activemq
```

#### Configure as System Service

6. Create systemd service file:
```bash
sudo nano /etc/systemd/system/activemq.service
```

7. Add the following content to the service file:
```ini
[Unit]
Description=Apache ActiveMQ
After=network.target

[Service]
Type=forking
User=activemq
Group=activemq

ExecStart=/opt/activemq/bin/activemq start
ExecStop=/opt/activemq/bin/activemq stop

[Install]
WantedBy=multi-user.target
```

8. Enable and start the service:
```bash
sudo systemctl daemon-reload
sudo systemctl start activemq
```

### Setup

```bash
git clone <repository-url>
cd dive-surf-integration-parent
mvn clean package -DskipTests
```

### Running the System

Start each subsystem in a separate terminal (order does not matter):

```bash
# Web Order System
mvn exec:java -pl web-order-system -Dexec.mainClass="com.divesurf.WebOrderSystem.WebOrderSystem"

# Call Center Order System
mvn exec:java -pl callcenter-order-system -Dexec.mainClass="com.divesurf.CallCenterOrderSystem.CallCenterOrderSystem"

# Order Publisher
mvn exec:java -pl OrderPublisher -Dexec.mainClass="com.divesurf.OrderPublisher.OrderPublisher"

# Billing System
mvn exec:java -pl billing-system -Dexec.mainClass="com.divesurf.BillingSystem.BillingSystem"

# Inventory System
mvn exec:java -pl inventory-system -Dexec.mainClass="com.divesurf.InventorySystem.InventorySystem"

# Result System
mvn exec:java -pl result-system -Dexec.mainClass="com.divesurf.ResultSystem.ResultSystem"
```

---

## Usage

- Submit orders via the web or call center systems.
- Orders are processed and validated in real-time.
- The `stock.properties` file is updated after each order, always reflecting the current stock.
- Each time `stock.properties` is updated, a comment is added warning about file refresh and manual edits.

### Initial Stock

- Diving suits: 50
- Surfboards: 100

---

## Implementation Details

### Architecture

- **Order Entry**: Web and call center systems accept and forward orders.
- **Order Publisher**: Publishes orders to the processing pipeline.
- **Billing System**: Validates credit, enriches orders with credit score.
- **Inventory System**: Validates and updates stock, outputs current stock.
- **Result System**: Aggregates results, produces final order status.
- **ActiveMQ Integration**: All communication is via JMS queues and topics.

### Key Features

- **Explicit Field Mapping**: All fields are mapped by name, not index, to prevent data misalignment.
- **Robust Aggregation**: Result system merges billing and inventory results with null checks and field preference.
- **Real-Time Stock Updates**: Inventory system always outputs the up-to-date stock after each order.
- **User Guidance**: `stock.properties` includes a comment about file refresh and manual edits.
- **Modular Design**: Each subsystem can be started, stopped, or scaled independently.

### Error Handling

- Invalid orders are rejected with clear error messages.
- All subsystems log their actions and errors to the console for easy debugging.

---

## Notes

- Ensure ActiveMQ is running before starting any subsystem.
- If you edit `stock.properties` manually, save the file and restart the inventory system for changes to take effect.
- For troubleshooting, check the console output of each subsystem.

---