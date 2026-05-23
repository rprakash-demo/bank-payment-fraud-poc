# Bank Payment Fraud Detection PoC

> Enterprise-grade payment fraud screening Proof of Concept built with **Apache Camel**, **Java 17**, **JMS Messaging**, and deployable to **Google Cloud Run / Docker**.

This project demonstrates two integration architectures for secure payment screening:

- **Synchronous REST Processing (Fast Decision Flow)**
- **Asynchronous JMS Messaging Processing (Secure Broker Flow)**

---

# Project Overview

This PoC simulates a banking payment processing and fraud screening engine.

The application validates payment transactions against compliance and fraud screening rules before approving or rejecting requests.

Core capabilities:

- REST API payment intake
- JMS-based asynchronous messaging
- Mandatory compliance/security screening
- Audit logging via WireTap pattern
- Correlation ID tracking
- High-value fraud routing
- XML/JSON message transformation
- Fire-and-forget broker integration

---

# Architecture

## Integration Models

### Solution 1 — Secure Messaging Flow

Asynchronous broker-based architecture:

```text
Client
   ↓
POST /api/v1/payment-secure
   ↓
Generate Correlation ID
   ↓
WireTap → auditQueue
   ↓
Security Gate
   ↓
Convert JSON → XML
   ↓
JMS Queue → brokerSystemQueue
   ↓
Accepted for processing
```

Response:

```json
{
  "status": "ACCEPTED_FOR_PROCESSING",
  "correlationId": "uuid"
}
```

---

### Solution 2 — Fast REST Flow

Synchronous real-time architecture:

```text
Client
   ↓
POST /api/v1/payment-fast
   ↓
Generate Correlation ID
   ↓
WireTap → auditQueue
   ↓
Security Gate
   ↓
Amount > 100 ?
      YES → External Fraud Engine
      NO  → Local Approval
   ↓
Return response
```

Response:

```json
{
  "status": "APPROVED",
  "engine": "Mock-External-REST",
  "correlationId": "uuid"
}
```

---

# Repository Structure

```text
bank-payment-fraud-poc/
├── pom.xml
├── Dockerfile
├── service.yaml
├── README.md
└── src/
    └── main/
        └── java/
            └── com/
                └── bank/
                    └── PaymentApp.java
```

---

# Technology Stack

| Technology | Version |
|----------|---------|
| Java | 17 |
| Apache Camel | 4.x |
| Maven | 3.8+ |
| Netty HTTP | Camel Component |
| JMS | Queue Messaging |
| Docker | Latest |
| Google Cloud Run | Optional Deployment |

---

# Payment API Endpoints

## 1. Fast Payment API

Synchronous fraud decision.

### Endpoint

```http
POST /api/v1/payment-fast
```

### Example Request

```json
{
  "payerName": "John Doe",
  "payeeName": "Alice Smith",
  "payerCountryCode": "DEU",
  "payeeCountryCode": "IND",
  "payerBank": "Deutsche Bank",
  "payeeBank": "HDFC Bank",
  "amount": 200
}
```

### Example Response

```json
{
  "status": "APPROVED",
  "engine": "Mock-External-REST",
  "correlationId": "f47ac10b"
}
```

---

## 2. Secure Payment API

Asynchronous processing.

### Endpoint

```http
POST /api/v1/payment-secure
```

### Example Request

```json
{
  "payerName": "John Doe",
  "payeeName": "Alice Smith",
  "payerCountryCode": "DEU",
  "payeeCountryCode": "IND",
  "payerBank": "Deutsche Bank",
  "payeeBank": "HDFC Bank",
  "amount": 500
}
```

### Example Response

```json
{
  "status": "ACCEPTED_FOR_PROCESSING",
  "correlationId": "f47ac10b"
}
```

---

# Fraud & Compliance Rules

Any single match causes immediate rejection.

## Blacklisted Names

- Mark Imaginary
- Govind Real
- Shakil Maybe
- Chang Imagine

---

## Restricted Countries

- CUB
- IRQ
- IRN
- PRK
- SDN
- SYR

---

## Blocked Banks

- Bank of Kunlun

---

# Decision Logic

## Security Gate

Checks:

- payer name
- payee name
- payer country
- payee country
- payer bank
- payee bank

If matched:

```json
{
  "status": "REJECTED",
  "reason": "BLACKLISTED_NAME"
}
```

or

```json
{
  "status": "REJECTED",
  "reason": "RESTRICTED_COUNTRY"
}
```

or

```json
{
  "status": "REJECTED",
  "reason": "BLOCKED_BANK"
}
```

HTTP Status:

```http
403 Forbidden
```

---

## High Value Fraud Check

Condition:

```text
amount > 100
```

Then route to:

```text
direct:mock-external-fraud-engine
```

Response:

```json
{
  "status": "APPROVED",
  "engine": "Mock-External-REST"
}
```

Otherwise:

```json
{
  "status": "APPROVED",
  "engine": "Local-Internal-Check"
}
```

---

# Apache Camel Design Patterns Used

## Wire Tap

Used for audit logging:

```java
.wireTap("jms:queue:auditQueue")
```

---

## Content-Based Router

Decision routing:

```java
.choice()
.when(...)
.otherwise()
```

---

## Message Translator

Format transformations:

```java
.marshal().json()
.marshal().jacksonXml()
```

---

## Fire-and-Forget Messaging

Asynchronous JMS:

```java
.setExchangePattern(ExchangePattern.InOnly)
```

---

## Correlation Identifier

Tracking every transaction:

```java
.setHeader("correlationId", simple("${uuid}"))
```

---

# Build Instructions

## Prerequisites

Install:

- Java 17
- Maven 3.8+
- Docker

---

## Maven Build

```bash
mvn clean package
```

Generated artifact:

```text
target/payment-fraud-poc-1.0-SNAPSHOT.jar
```

---

## Run Locally

```bash
java -jar target/payment-fraud-poc-1.0-SNAPSHOT.jar
```

Application starts on:

```text
http://localhost:8080
```

---

# Docker

## Build Image

```bash
docker build -t payment-fraud-poc .
```

## Run Container

```bash
docker run -p 8080:8080 payment-fraud-poc
```

---

# Testing

## Fast API Approved

```bash
curl -X POST http://localhost:8080/api/v1/payment-fast \
-H "Content-Type: application/json" \
-d '{
  "payerName":"John Doe",
  "payeeName":"Alice",
  "payerCountryCode":"DEU",
  "payeeCountryCode":"IND",
  "payerBank":"DB",
  "amount":50
}'
```

---

## Fast API High Value

```bash
curl -X POST http://localhost:8080/api/v1/payment-fast \
-H "Content-Type: application/json" \
-d '{
  "payerName":"John Doe",
  "payeeName":"Alice",
  "payerCountryCode":"DEU",
  "payeeCountryCode":"IND",
  "payerBank":"DB",
  "amount":500
}'
```

---

## Rejected Request

```bash
curl -X POST http://localhost:8080/api/v1/payment-fast \
-H "Content-Type: application/json" \
-d '{
  "payerName":"Mark Imaginary",
  "amount":100
}'
```

Expected:

```json
{
  "status":"REJECTED",
  "reason":"BLACKLISTED_NAME"
}
```

---

## Secure Async API

```bash
curl -X POST http://localhost:8080/api/v1/payment-secure \
-H "Content-Type: application/json" \
-d '{
  "payerName":"John Doe",
  "amount":1000
}'
```

---

# Audit Flow

Audit messages are asynchronously copied to:

```text
jms:queue:auditQueue
```

Consumer route:

```java
from("jms:queue:auditQueue")
```

Logs:

```text
AUDIT SYSTEM: Persisting transaction: {body}
```

---

# Deployment

Google Cloud Run deployment supported.

Artifacts:

```text
Dockerfile
service.yaml
```

Deploy example:

```bash
gcloud run deploy payment-fraud-poc \
  --source .
```

---

# Demo Talking Points

During demo highlight:

- Apache Camel integration routing
- Fraud screening logic
- Synchronous vs asynchronous processing
- Audit logging
- JMS messaging
- Correlation tracking
- Security compliance checks
- REST + broker hybrid architecture

---

# Author

Bank Payment Fraud Detection PoC  
Apache Camel + Java 17 + Enterprise Integration Patterns