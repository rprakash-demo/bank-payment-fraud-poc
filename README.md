# Bank Payment Fraud Check PoC

This Proof of Concept (PoC) provides a professional, decoupled, and modular fraud detection engine for banking systems. It supports enterprise-grade integration patterns, including REST-based real-time processing and JMS-based asynchronous messaging.

## 1. Project Overview
The system validates payment requests, performs automated fraud screening based on defined business thresholds, and ensures full auditability of all transactions.

### Integration Patterns
* **Solution 1 (Messaging):** PPS → JSON/JMS → BS → XML/JMS → FCS → XML/JMS → BS → JSON/JMS → PPS
* **Solution 2 (REST):** PPS → REST/JSON → BS → XML/JMS → FCS → XML/JMS → BS → JSON/REST → PPS

## 2. Architecture & Design
Our design prioritizes modularity. The fraud engine is decoupled from the transport layer, allowing seamless switching between protocols.

```mermaid
flowchart TD
    subgraph Client [Client Layer]
        User[Client: JSON/XML]
    end

    subgraph App [Bank Payment Fraud POC]
        PR[PaymentRoute - REST API]
        FR[FraudCheckRoute - Logic Flow]
        BR[BrokerRoute - JMS Export]
        Val[PaymentValidationProcessor]
        Dec[FraudDecisionProcessor]
    end

    subgraph Infra [Infrastructure]
        JMS[ActiveMQ Broker]
        Logs[Audit Logs / SLF4J]
    end

    User -- "POST /api/payment" --> PR
    PR --> FR
    FR --> Val
    Val --> Dec
    Dec --> BR
    BR --> JMS
    BR -.-> Logs
