# VS 2025 – Distributed Systems: Practical Assignment 3

## JMS Stock Trading System with ActiveMQ

This repository contains the implementation of a **distributed stock trading system** using **Java Message Service (JMS)** and **ActiveMQ**, developed for the *Distributed Systems* course (VS 2025) at Technische Universität Berlin. The system consists of a broker server and multiple clients that can trade stocks in real-time.
---

##  Assignment Objectives

This project addresses the following key goals:

- Implement a **distributed stock trading system** using **JMS (Java Message Service)**.
- Support **multiple concurrent clients** trading stocks simultaneously.
- Enable **real-time stock updates** via publish-subscribe messaging.
- Implement **portfolio management** with budget constraints.
- Provide **stock watching** functionality for real-time price monitoring.
- Use **ActiveMQ** as the message broker for reliable message delivery.

---

##  Protocol & Functional Overview

### Supported Commands
| Command     | Description                                  | Usage Example |
|-------------|----------------------------------------------|----------------|
| `quit`      | Disconnects from the broker and exits       | `quit`         |
| `list`      | Lists all available stocks                   | `list`         |
| `buy`       | Purchase stocks                              | `buy SAP 150`  |
| `sell`      | Sell owned stocks                            | `sell SAP 50`  |
| `watch`     | Subscribe to real-time updates for a stock  | `watch SAP`    |
| `unwatch`   | Unsubscribe from stock updates              | `unwatch SAP`  |
| `info`      | Get detailed information about a stock       | `info SAP`     |

### Available Stocks
The system includes the following stocks:

- **ALDI** - €2.00 (200 shares available)
- **LIDL** - €1.00 (300 shares available)
- **BOSCH** - €3.50 (350 shares available)
- **BMW** - €2.50 (220 shares available)
- **SONY** - €4.00 (400 shares available)
- **AMD** - €4.20 (575 shares available)
- **TSMC** - €6.00 (250 shares available)
- **LVMH** - €5.50 (620 shares available)
- **SAP** - €3.30 (150 shares available)
- **NVDA** - €8.00 (1200 shares available)

---


## Project Structure

```
vs2025_ha3_group_16/
├── pom.xml                    # Parent Maven configuration
├── README.md                  # Project documentation
├── start_clients.sh          # Shell script to start multiple test clients
├── common/                   # Shared classes and message types
│   ├── pom.xml
│   └── src/main/java/de/tu_berlin/cit/vs/jms/common/
│       ├── BrokerMessage.java         # Base message class
│       ├── Stock.java                 # Stock data model
│       ├── RegisterMessage.java       # Client registration
│       ├── UnregisterMessage.java     # Client unregistration
│       ├── ListMessage.java           # Stock list response
│       ├── RequestListMessage.java    # Stock list request
│       ├── BuyMessage.java            # Buy stock request
│       ├── SellMessage.java           # Sell stock request
│       ├── BuySellResponseMessage.java # Buy/sell response
│       ├── WatchMessage.java          # Watch stock request
│       ├── UnwatchMessage.java        # Unwatch stock request
│       ├── StockUpdateMessage.java    # Real-time stock updates
│       ├── InfoMessage.java           # Stock info request
│       └── StockInfoMessage.java      # Stock info response
├── broker/                   # Stock broker server
│   ├── pom.xml
│   ├── target/
│   │   └── broker-1.0-SNAPSHOT.jar   # Executable broker JAR
│   └── src/main/java/de/tu_berlin/cit/vs/jms/broker/
│       ├── JmsBrokerServer.java       # Main server application
│       └── SimpleBroker.java          # Broker implementation
└── client/                   # Trading client application
    ├── pom.xml
    ├── target/
    │   └── client-1.0-SNAPSHOT.jar   # Executable client JAR
    └── src/main/java/de/tu_berlin/cit/vs/jms/client/
        └── JmsBrokerClient.java       # Client implementation
```



##  Build & Execution

### Requirements
- **Java 14+**
- **Maven 3.6+**
- **ActiveMQ 5.16.3+** (see installation instructions below)

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
cd vs2025_ha3_group_16
mvn clean package
```

### Running the System

#### 1. Start the Broker Server

```bash
java -jar broker/target/broker-1.0-SNAPSHOT.jar
```

The broker will start and wait for client connections. Press Enter to stop the broker.

#### 2. Start Clients

```bash
java -jar client/target/client-1.0-SNAPSHOT.jar
```

For automated testing with multiple clients:
(install dependency and make script executable)
```bash
sudo apt install tmux
chmod +x start_clients.sh
```

```bash
./start_clients.sh
```

---

##  Usage

### Client Registration
When starting a client, you'll be prompted to:
1. Enter a unique client name
2. Enter your starting budget (e.g., 100000)

### Trading Commands
After registration, you can use the following commands:

#### Example Trading Session

```
Enter the client name:
trader1
Enter client budget:
100000
sending registration msg
Enter command:
list
ALDI -- price: 2.0 -- available: 200 -- sum: 200
LIDL -- price: 1.0 -- available: 300 -- sum: 300
...
Enter command:
buy SAP 50
Bought 50 shares
Enter command:
watch SAP
Enter command:
info SAP
Name: SAP max Amount: 150 currently available: 100
Enter command:
sell SAP 25
Sold 25 shares
Stock update: SAP -- price: 3.3 -- available: 125 -- sum: 150
Enter command:
quit
Bye bye
```

---

##  Implementation Details

### Architecture
- **Broker Server**: Manages stock inventory, client portfolios, and message routing
- **Client Applications**: Interactive trading terminals with real-time updates
- **Common Library**: Shared message types and data models
- **ActiveMQ Integration**: Reliable message delivery using JMS queues and topics

### Message Flow
1. **Client Registration**: Clients register with name and budget via registration queue
2. **Command Processing**: Buy/sell/list commands sent to individual client queues
3. **Real-time Updates**: Stock price changes broadcast via topic subscriptions
4. **Portfolio Management**: Server tracks individual client portfolios and budgets

### Key Features
- **Thread-safe Operations**: Synchronized stock transactions prevent race conditions
- **Real-time Notifications**: Publish-subscribe pattern for stock updates
- **Budget Validation**: Prevents clients from spending beyond their means
- **Inventory Management**: Tracks available stock quantities in real-time
- **Graceful Shutdown**: Proper cleanup of JMS resources on client disconnect
