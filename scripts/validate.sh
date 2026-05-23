#!/bin/bash
# Color codes
GREEN='\033[1;32m'
RED='\033[1;31m'
NC='\033[0m' # No Color

URL="http://localhost:8080/api/payment"
PASS=0; FAIL=0

run_test() {
  local desc="$1" payload="$2" expected="$3"
  
  # Simplified curl command to ensure compatibility
  actual=$(curl -s -X POST "$URL" -H "Content-Type: application/xml" -d "$payload")
  
  if echo "$actual" | grep -q "$expected"; then
    echo -e "✅ ${GREEN}PASS:${NC} $desc"
    ((PASS++))
  else
    echo -e "❌ ${RED}FAIL:${NC} $desc — expected '$expected', got: $actual"
    ((FAIL++))
  fi
}

echo "--- Starting FCS Validation Tests ---"

# 1. Invalid: Missing mandatory fields
run_test "Invalid (Missing fields)" "<payment><amount>1000</amount></payment>" "INVALID_REQUEST"

# 2. Suspicious: Blacklisted Name
run_test "Suspicious (Name)" "<payment><amount>100</amount><payerName>Mark Imaginary</payerName><payerCountry>DE</payerCountry></payment>" "SUSPICIOUS_PAYMENT"

# 3. Suspicious: Blacklisted Country
run_test "Suspicious (Country)" "<payment><amount>100</amount><payerName>John Doe</payerName><payerCountry>CUB</payerCountry></payment>" "SUSPICIOUS_PAYMENT"

# 4. Suspicious: Blacklisted Bank
run_test "Suspicious (Bank)" "<payment><amount>100</amount><payerName>John Doe</payerName><payerCountry>DE</payerCountry><payeeBank>BANK OF KUNLUN</payeeBank></payment>" "SUSPICIOUS_PAYMENT"

# 5. Suspicious: Blacklisted Instruction
run_test "Suspicious (Instruction)" "<payment><amount>100</amount><payerName>John Doe</payerName><payerCountry>DE</payerCountry><instruction>Artillery Procurement</instruction></payment>" "SUSPICIOUS_PAYMENT"

# 6. High-risk Review
run_test "High-risk threshold" "<payment><amount>6000</amount><payerName>John Doe</payerName><payerCountry>DE</payerCountry></payment>" "REVIEW_REQUIRED"

# 7. Approved: Standard Payment
run_test "Approved (Standard)" "<payment><amount>1000</amount><payerName>John Doe</payerName><payerCountry>DE</payerCountry></payment>" "APPROVED"

echo ""
echo "Results: $PASS passed, $FAIL failed"