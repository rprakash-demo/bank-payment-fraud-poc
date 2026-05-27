# Bank Payment Fraud Detection PoC

> **Java 17 · Apache Camel 4.8 · ActiveMQ 6 · JMS + REST · JSON + XML**

A Proof of Concept demonstrating two payment fraud detection integration solutions using Apache Camel, ActiveMQ, REST APIs, and JMS messaging — designed to showcase architectural design, protocol mediation, and synchronous vs asynchronous communication patterns.

---

## 1. PoC Objective

This PoC demonstrates a secure, decoupled payment fraud screening solution across three systems.

The goal is to validate two integration approaches — synchronous REST and asynchronous JMS — while keeping the fraud logic and protocol mediation fully decoupled from the client-facing payment API.

### Key Capabilities

- Dual integration models: synchronous REST and asynchronous JMS
- JSON ↔ XML protocol mediation
- Fraud blacklist screening
- Request validation
- Wire Tap audit logging
- Transaction correlation tracking

### Core Design Rules

| Rule | Detail |
|---|---|
| PPS communicates in | **JSON only** |
| FCS communicates in | **XML only** |
| BS is responsible for | **all JSON ↔ XML translation** |
| BS ↔ FCS always uses | **JMS messaging** |
| PPS ↔ BS varies by solution | **JMS (Sol 1) or REST (Sol 2)** |

---

## 2. Systems Overview

| System | Full Name | Role |
|---|---|---|
| **PPS** | Payment Processing System | Receives JSON payment requests · validates all fields · manages client interaction · triggers fraud screening |
| **BS** | Broker System | Protocol mediator · converts JSON ↔ XML · routes between PPS and FCS · decouples the two systems |
| **FCS** | Fraud Check System | Receives XML from BS · applies blacklist rules · returns APPROVED or REJECTED in XML |

---

## 3. Integration Solutions

| | PPS to BS | Format | BS to FCS | Format | Client response |
|---|---|---|---|---|---|
| **Solution 1** | JMS Messaging | JSON | JMS Messaging | XML | 202 immediately — client polls for result |
| **Solution 2** | REST API | JSON | JMS Messaging | XML | 200 or 403 synchronously — client waits |

> BS to FCS is **always XML over JMS** in both solutions.
> Only the transport between PPS and BS differs.

---

## 4. End-to-End Workflow

```mermaid
%%{init: {"flowchart": {"htmlLabels": true, "curve": "linear", "nodeSpacing": 60, "rankSpacing": 80}} }%%
flowchart LR
    Client([Client])

    subgraph PPS[Payment Processing System]
        PPSAPI["Apache Camel
REST API + Validation
JSON Processing"]
    end

    subgraph BS[Broker System]
        BSRouting["Apache Camel
Routing + Protocol Mediation
JSON ↔ XML Transformation"]
    end

    subgraph FCS[Fraud Check System]
        Fraud["Fraud Rules Engine
Blacklist Screening
XML Processing"]
    end

    Client -->|"JSON Request"| PPSAPI
    PPSAPI -->|"Sol 1 — JSON / JMS (Async)"| BSRouting
    PPSAPI -->|"Sol 2 — JSON / REST (Sync)"| BSRouting
    BSRouting -->|"XML / JMS"| Fraud
    Fraud -->|"XML Result / JMS"| BSRouting
    BSRouting -->|"JSON Response"| PPSAPI
    PPSAPI -->|"HTTP Response / Async Status"| Client
```

---

## 5. UML Component Diagram

```mermaid
%%{init: {"flowchart": {"htmlLabels": true, "curve": "linear", "nodeSpacing": 55, "rankSpacing": 90}} }%%
flowchart LR
    Client([Client])

    subgraph PPS[PPS — Payment Processing System]
        direction TB
        SYNC["POST /api/v1/payments
Sync REST entry
Solution 2"]
        ASYNC["POST /api/v1/payments/secure
Async JMS entry
Solution 1"]
        STATUS["GET /api/v1/payments/{id}/status
Status poll"]
        VALIDATOR["Payment Validator
UUID · ISO country · ISO currency
amount · date · 13 mandatory fields
paymentInstruction optional"]
        STORE["Status Store
transactionId to APPROVED or REJECTED"]
    end

    subgraph BS[BS — Broker System]
        direction TB
        JX["JSON to XML Translator"]
        XJ["XML to JSON Translator"]
        AUDIT_BS["Wire Tap
Audit Logger"]
    end

    subgraph FCS[FCS — Fraud Check System]
        direction TB
        ENGINE["Blacklist Fraud Engine
Name · Country · Bank · Instruction"]
        DECISION["Fraud Decision
APPROVED / REJECTED"]
    end

    subgraph MQ[ActiveMQ — Embedded JMS Broker]
        direction TB
        Q1[["ppsToBS   JSON   Sol 1"]]
        Q2[["bsToFCS   XML"]]
        Q3[["fcsToBS   XML"]]
        Q4[["bsToPPS   JSON   Sol 1"]]
        Q5[["auditQueue"]]
    end

    Client -->|"JSON"| SYNC
    Client -->|"JSON"| ASYNC
    Client --> STATUS
    STATUS --> STORE

    SYNC --> VALIDATOR
    ASYNC --> VALIDATOR

    SYNC -.->|"Wire Tap"| Q5
    ASYNC -.->|"Wire Tap"| Q5
    VALIDATOR -->|"Sol 2: JSON via direct route"| JX
    VALIDATOR -->|"Sol 1: JSON"| Q1

    Q1 -->|"JSON"| JX
    JX -->|"XML"| Q2
    Q2 -->|"XML"| ENGINE
    ENGINE --> DECISION
    DECISION -->|"XML"| Q3
    Q3 -->|"XML"| XJ
    XJ -->|"Sol 2: JSON to PPS"| SYNC
    XJ -->|"Sol 1: JSON"| Q4
    XJ -.->|"Wire Tap"| AUDIT_BS
    AUDIT_BS -.-> Q5
    Q4 -->|"JSON"| STORE
```

---

## 6. UML Sequence Diagram — Solution 1 Asynchronous

> Client receives **202 immediately**. Fraud check runs in the background. Client polls for result.

```mermaid
%%{init: {"sequence": {"width": 200, "actorFontSize": 14, "noteFontSize": 13, "messageFontSize": 13, "mirrorActors": false}} }%%
sequenceDiagram
    autonumber
    participant Client
    participant PPS
    participant BS
    participant FCS
    participant AuditQueue

    Client ->>+ PPS : Submit Payment (JSON)
    PPS    ->>  PPS : Validate Payment
    PPS    -->> AuditQueue : Wire Tap — audit copy (non-blocking)
    PPS    -->>- Client : 202 ACCEPTED_FOR_PROCESSING

    note over PPS,BS: JSON over JMS Messaging (Async)

    PPS    ->>  BS  : Fraud Request (JSON)

    note over BS,FCS: XML over JMS Messaging

    BS     ->>  BS  : Convert JSON → XML
    BS     ->>  FCS : Fraud Check Request (XML)

    FCS    ->>  FCS : Fraud Evaluation
    FCS    ->>  BS  : Fraud Result (XML)

    BS     ->>  BS  : Convert XML → JSON
    BS     -->> AuditQueue : Wire Tap — audit copy (non-blocking)
    BS     ->>  PPS : Fraud Decision (JSON)

    PPS    ->>  PPS : Store Final Status

    note over Client: Client polls when ready.

    Client ->>+ PPS : GET /api/v1/payments/{transactionId}/status
    PPS    -->>- Client : 200 OK — APPROVED or REJECTED
```

---

## 7. UML Sequence Diagram — Solution 2 Synchronous

> Client **waits** on the same HTTP connection and receives a direct response.

```mermaid
%%{init: {"sequence": {"width": 200, "actorFontSize": 14, "noteFontSize": 13, "messageFontSize": 13, "mirrorActors": false}} }%%
sequenceDiagram
    autonumber
    participant Client
    participant PPS
    participant BS
    participant FCS
    participant AuditQueue

    Client ->> PPS : Submit Payment (JSON)
    PPS    ->> PPS : Validate Payment
    PPS    -->> AuditQueue : Wire Tap — audit copy (non-blocking)

    alt Validation fails
        PPS -->> Client : 400 Bad Request — validation error
    else Validation passes
        note over PPS,BS: JSON over REST API (Sync)

        PPS ->>  BS  : Fraud Request (JSON)

        note over BS,FCS: XML over JMS Messaging

        BS  ->>  BS  : Convert JSON → XML
        BS  ->>  FCS : Fraud Check Request (XML)

        FCS ->>  FCS : Fraud Evaluation
        FCS ->>  BS  : Fraud Result (XML)

        BS  ->>  BS  : Convert XML → JSON
        BS  -->> AuditQueue : Wire Tap — audit copy (non-blocking)
        BS  -->> PPS : Fraud Decision (JSON)

        PPS -->> Client : 200 APPROVED or 403 REJECTED
    end
```

---

## 8. UML Audit Flow — Wire Tap Pattern

> The Wire Tap pattern captures a copy of every message without interrupting the main flow. Both PPS and BS tap into the shared `auditQueue`.

```mermaid
%%{init: {"sequence": {"width": 200, "actorFontSize": 14, "noteFontSize": 13, "messageFontSize": 13, "mirrorActors": false}} }%%
sequenceDiagram
    participant PPS
    participant BS
    participant AuditQueue
    participant Logger

    PPS ->> AuditQueue : Wire Tap — copy incoming payment event
    BS  ->> AuditQueue : Wire Tap — copy transformation / result event
    AuditQueue ->> Logger : Structured audit log entry (correlationId · timestamp · payload)
```

---

## 9. Payment Payload

### JSON Request — Client to PPS

```json
{
  "transactionId":      "550e8400-e29b-41d4-a716-446655440000",
  "payerName":          "John Smith",
  "payerBank":          "Bank of America",
  "payerCountryCode":   "GBR",
  "payerAccount":       "123456789",
  "payeeName":          "Jane Doe",
  "payeeBank":          "BNP Paribas",
  "payeeCountryCode":   "DEU",
  "payeeAccount":       "987654321",
  "paymentInstruction": "Salary",
  "executionDate":      "2025-05-22",
  "amount":             "1500.00",
  "currency":           "EUR",
  "creationTimestamp":  "2025-05-22T09:00:00Z"
}
```

### XML Format — BS to FCS (internal only)

```xml
<payment>
  <transactionId>550e8400-e29b-41d4-a716-446655440000</transactionId>
  <payerName>John Smith</payerName>
  <payerBank>Bank of America</payerBank>
  <payerCountryCode>GBR</payerCountryCode>
  <payerAccount>123456789</payerAccount>
  <payeeName>Jane Doe</payeeName>
  <payeeBank>BNP Paribas</payeeBank>
  <payeeCountryCode>DEU</payeeCountryCode>
  <payeeAccount>987654321</payeeAccount>
  <paymentInstruction>Salary</paymentInstruction>
  <executionDate>2025-05-22</executionDate>
  <amount>1500.00</amount>
  <currency>EUR</currency>
  <creationTimestamp>2025-05-22T09:00:00Z</creationTimestamp>
</payment>
```

### Validation

Validation includes:

- UUID format
- ISO 3166-1 alpha-3 country codes
- ISO 4217 currency
- amount precision
- ISO 8601 date/timestamp
- mandatory field checks

`paymentInstruction` is optional.

---

## 10. Fraud Rules

Any single match on payer **or** payee triggers rejection.

| Outcome | Message | HTTP |
|---|---|---|
| No match | `Nothing found, all okay` | 200 |
| Any match | `Suspicious payment` | 403 |

Fraud checks cover: blocked payer/payee names · restricted countries (sanctioned states) · sanctioned banks · prohibited payment instructions.

---

## 11. UML Class Diagram — Code Structure

```mermaid
classDiagram
    class BankApplication {
        +main(String[] args)
    }

    class PaymentApp {
        +configure()
        -PaymentValidator validator
        -JaxbDataFormat jaxbFormat
    }

    class PaymentDTO {
        +transactionId
        +payerName
        +payerBank
        +payerCountryCode
        +payerAccount
        +payeeName
        +payeeBank
        +payeeCountryCode
        +payeeAccount
        +paymentInstruction
        +executionDate
        +amount
        +currency
        +creationTimestamp
    }

    class PaymentValidator {
        +validate(PaymentDTO)
    }

    BankApplication --> PaymentApp
    PaymentApp --> PaymentValidator
    PaymentApp --> PaymentDTO
    PaymentValidator --> PaymentDTO
```

---

## 12. Technology Stack

| Technology | Purpose |
|---|---|
| Java 17 | Runtime |
| Apache Camel 4.8 | Integration framework — routing, EIP patterns, Wire Tap |
| ActiveMQ 6 (embedded) | JMS messaging |
| REST API (Netty HTTP) | Synchronous client communication — Solution 2 |
| JSON / XML | Data exchange formats — PPS uses JSON, FCS uses XML |
| Google Cloud Platform (GCP) | Cloud hosting / container runtime |

---

## 13. Build and Run

### Build

```bash
mvn clean package -DskipTests
```

### Run Locally

```bash
java -jar target/payment-fraud-poc-1.0-SNAPSHOT.jar
```

### Run with Docker

```bash
docker build -t bank-fraud-poc .
docker run -d -p 8080:8080 --name bank-fraud-poc-container bank-fraud-poc
docker logs -f bank-fraud-poc-container
```

---

## 14. API Endpoints

| Method | Endpoint | Solution | Pattern | Response |
|---|---|---|---|---|
| `POST` | `/api/v1/payments` | Solution 2 | Synchronous | 200 APPROVED · 403 REJECTED · 400 INVALID |
| `POST` | `/api/v1/payments/secure` | Solution 1 | Asynchronous | 202 ACCEPTED_FOR_PROCESSING |
| `GET` | `/api/v1/payments/{transactionId}/status` | Solution 1 | Status poll | 200 APPROVED or REJECTED · 404 NOT_FOUND |

### Example — Solution 2 Synchronous (Approved)

```bash
curl -i -X POST http://localhost:8080/api/v1/payments \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId":      "550e8400-e29b-41d4-a716-446655440001",
    "payerName":          "John Smith",
    "payerBank":          "Bank of America",
    "payerCountryCode":   "GBR",
    "payerAccount":       "123456789",
    "payeeName":          "Jane Doe",
    "payeeBank":          "BNP Paribas",
    "payeeCountryCode":   "DEU",
    "payeeAccount":       "987654321",
    "executionDate":      "2025-05-22",
    "amount":             "1500.00",
    "currency":           "EUR",
    "creationTimestamp":  "2025-05-22T09:00:00Z"
  }'
```

Response — HTTP 200:
```json
{"transactionId":"550e8400...","status":"APPROVED"}
```

### Example — Solution 1 Asynchronous (Submit + Poll)

```bash
# Submit
curl -i -X POST http://localhost:8080/api/v1/payments/secure \
  -H "Content-Type: application/json" \
  -d '{
    "transactionId":"660e8400-e29b-41d4-a716-446655440010",
    "payerName":"John Smith","payerBank":"Bank of America",
    "payerCountryCode":"GBR","payerAccount":"123456789",
    "payeeName":"Jane Doe","payeeBank":"BNP Paribas",
    "payeeCountryCode":"DEU","payeeAccount":"987654321",
    "executionDate":"2025-05-22","amount":"1500.00",
    "currency":"EUR","creationTimestamp":"2025-05-22T09:00:00Z"
  }'
```

Response — HTTP 202 immediate:
```json
{"transactionId":"660e8400...","status":"ACCEPTED_FOR_PROCESSING","message":"Payment submitted for fraud screening via JMS"}
```

```bash
# Poll for result
sleep 3 && curl -i http://localhost:8080/api/v1/payments/660e8400-e29b-41d4-a716-446655440010/status
```

Response — HTTP 200:
```json
{"transactionId":"660e8400...","status":"APPROVED"}
```

---

*Bank Payment Fraud Detection PoC — Java 17 · Apache Camel 4.8 · ActiveMQ 6 · REST + JMS*
