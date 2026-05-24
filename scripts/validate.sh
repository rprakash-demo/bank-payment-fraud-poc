#!/bin/bash
# Color codes
GREEN='\033[1;32m'
RED='\033[1;31m'
NC='\033[0m'

URL="http://localhost:8080/api/payment"
PASS=0; FAIL=0

# Generalized test runner
run_test() {
  local desc="$1" content_type="$2" payload="$3" expected="$4"
  
  actual=$(curl -s -X POST "$URL" -H "Content-Type: $content_type" -d "$payload")
  
  if echo "$actual" | grep -q "$expected"; then
    echo -e "✅ ${GREEN}PASS:${NC} $desc ($content_type)"
    ((PASS++))
  else
    echo -e "❌ ${RED}FAIL:${NC} $desc ($content_type) — expected '$expected', got: '$actual'"
    ((FAIL++))
  fi
}

echo "--- Starting Cross-Format Validation Suite ---"

# --- XML Tests ---
run_test "Suspicious (XML)" "application/xml" '<payment><transactionId>X1</transactionId><payerName>Mark Imaginary</payerName></payment>' "REJECTED"
run_test "Standard (XML)" "application/xml" '<payment><transactionId>X2</transactionId><payerName>John Doe</payerName></payment>' "APPROVED"

# --- JSON Tests ---
# Note: Ensure your app handles JSON now if you plan to use this
run_test "Suspicious (JSON)" "application/json" '{"transactionId":"J1", "payerName":"Mark Imaginary"}' "REJECTED"
run_test "Standard (JSON)" "application/json" '{"transactionId":"J2", "payerName":"John Doe"}' "APPROVED"

echo ""
echo "Results: $PASS passed, $FAIL failed"
if [ $FAIL -eq 0 ]; then exit 0; else exit 1; fi