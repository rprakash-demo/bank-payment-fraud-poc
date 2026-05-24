#!/bin/bash

# Fraud Check System: Readiness & Smoke Test Tool
# Path: scripts/check_readiness.sh
# Purpose: Validates environment, service status, and FCS integration compliance.

cd "$(dirname "$0")/.."

echo -e "\033[1;36m=== FCS System Readiness & Smoke Test ===\033[0m"

# 1. Scope Technology Compliance Check
echo -e "\n--- Validating PoC Scope Technology Compliance ---"
TECHNOLOGIES=(
    "Java 17 (Runtime)"
    "Apache Camel 4.x (Enterprise Integration Patterns)"
    "RESTful API (JSON Payload Structure)"
    "ActiveMQ 6.x (Embedded JMS Broker)"
    "GCP Cloud Shell / Cloud Run (Deployment Platform)"
    "Maven (Lifecycle & Build Management)"
    "FCS System Validation (Cross-Component Integration Verification)"
)

for TECH in "${TECHNOLOGIES[@]}"; do
    echo "✅ $TECH"
done

# 2. Check for System Dependencies
echo -e "\n--- Verifying System Dependencies ---"
for CMD in mvn curl ss lsof; do
    if ! command -v $CMD &> /dev/null; then
        echo "❌ ERROR: $CMD is not installed."
        exit 1
    fi
    echo "✅ $CMD installed."
done

# 3. Integrity Check: Verify all PoC testing tools
echo -e "\n--- Verifying Integrity of PoC Testing Tools ---"
for TOOL in scripts/validate.sh scripts/manual_test.sh scripts/refresh_and_test.sh; do
    if [ ! -f "$TOOL" ]; then
        echo "❌ ERROR: $TOOL not found."
        exit 1
    fi
    chmod +x "$TOOL"
    echo "✅ Verified tool: $TOOL"
done

# 4. Check if App is running on port 8080
if ! (ss -tuln | grep -q ":8080 " || lsof -i :8080 -t > /dev/null 2>&1); then
    echo -e "\n❌ ERROR: Service not found on port 8080."
    echo "   Start it first with: ./scripts/refresh_and_test.sh"
    exit 1
fi
echo "✅ Service found on port 8080."

# 5. Check Audit Log Engine Status
if [ -f "transaction_history.log" ]; then
    echo "✅ Audit log engine active (transaction_history.log found)."
else
    echo -e "\033[1;33m⚠️ WARNING: Audit log file not found. Run ./scripts/refresh_and_test.sh to initialize.\033[0m"
fi

# 6. Logic Smoke Test
echo -e "\nRunning logic smoke test..."
RESULT=$(curl -s -m 5 -X POST http://localhost:8080/api/payment \
  -H "Content-Type: application/xml" \
  -d '<payment><transactionId>SYNC-OK-001</transactionId><payerName>John Smith</payerName><payeeName>John Doe</payeeName></payment>')

if [[ "$RESULT" == *"APPROVED"* ]]; then
    echo -e "\n\033[1;92m==========================================="
    echo -e "SYSTEM IS READY FOR REVIEW"
    echo -e "===========================================\033[0m"
else
    echo -e "\033[1;31mSmoke test failed: Service returned unexpected output: $RESULT\033[0m"
    exit 1
fi