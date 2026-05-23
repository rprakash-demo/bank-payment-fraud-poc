#!/bin/bash
# Color codes
BOLD='\033[1m'
GREEN='\033[1;32m'
RED='\033[1;31m'
NC='\033[0m' # No Color

URL="http://localhost:8080/api/payment"
PASS=0; FAIL=0

run_test() {
  local desc="$1" payload="$2" expected="$3"
  
  # Fetch response from server
  actual=$(curl -s -X POST "$URL" \
    --header "Content-Type: application/xml" \
    --data "$payload")
  
  if echo "$actual" | grep -q "$expected"; then
    # Visual logic: Green/Bold for success (Approved), Red/Bold for others
    if [[ "$expected" == "Nothing found, all okay." ]]; then
       echo -e "✅ ${GREEN}PASS:${NC} $desc - Status: ${GREEN}${BOLD}$expected${NC}"
    else
       echo -e "✅ ${GREEN}PASS:${NC} $desc - Status: ${RED}${BOLD}$expected${NC}"
    fi
    ((PASS++))
  else
    echo -e "❌ ${RED}FAIL:${NC} $desc — expected '$expected', got: $actual"
    ((FAIL++))
  fi
}

echo "--- Starting FCS Validation Tests ---"

# 1. Suspicious: Blacklisted Name
run_test "Suspicious (Name)" "<payment><amount>100</amount><payerName>Mark Imaginary</payerName><payerCountry>DE</payerCountry></payment>" "Suspicious payment"

# 2. Suspicious: Blacklisted Country
run_test "Suspicious (Country)" "<payment><amount>100</amount><payerName>John Doe</payerName><payerCountry>CUB</payerCountry></payment>" "Suspicious payment"

# 3. Suspicious: Blacklisted Bank
run_test "Suspicious (Bank)" "<payment><amount>100</amount><payerName>John Doe</payerName><payerCountry>DE</payerCountry><payeeBank>BANK OF KUNLUN</payeeBank></payment>" "Suspicious payment"

# 4. Suspicious: Blacklisted Instruction
run_test "Suspicious (Instruction)" "<payment><amount>100</amount><payerName>John Doe</payerName><payerCountry>DE</payerCountry><instruction>Artillery Procurement</instruction></payment>" "Suspicious payment"

# 5. High-risk Review
run_test "High-risk threshold" "<payment><amount>6000</amount><payerName>John Doe</payerName><payerCountry>DE</payerCountry></payment>" "REVIEW_REQUIRED"

# 6. Approved: Standard Payment
run_test "Approved (Standard)" "<payment><amount>1000</amount><payerName>John Doe</payerName><payerCountry>DE</payerCountry></payment>" "Nothing found, all okay."

# 7. Invalid: Missing mandatory fields
run_test "Invalid (Missing fields)" "<payment><amount>1000</amount></payment>" "INVALID_REQUEST"

echo ""
echo "Results: $PASS passed, $FAIL failed"