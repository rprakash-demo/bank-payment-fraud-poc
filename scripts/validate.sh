#!/bin/bash
# Color codes
GREEN='\033[1;32m'
RED='\033[1;31m'
NC='\033[0m' # No Color

# Base URL - Note: using /api/v1/payment-fast for validation
URL="http://localhost:8080/api/v1/payment-fast"
PASS=0; FAIL=0

run_test() {
  local desc="$1" payload="$2" expected="$3"
  
  # Using application/json as per your working app
  actual=$(curl -s -X POST "$URL" -H "Content-Type: application/json" -d "$payload")
  
  if echo "$actual" | grep -q "$expected"; then
    echo -e "✅ ${GREEN}PASS:${NC} $desc"
    ((PASS++))
  else
    echo -e "❌ ${RED}FAIL:${NC} $desc — expected '$expected', got: $actual"
    ((FAIL++))
  fi
}

echo "--- Starting FCS Validation Tests ---"

# 1. Suspicious: Blacklisted Name
run_test "Suspicious (Name)" '{"payerName":"Mark Imaginary", "payeeName":"John Doe", "amount":50, "payerCountryCode":"DEU"}' "REJECTED"

# 2. Suspicious: Blacklisted Country
run_test "Suspicious (Country)" '{"payerName":"John Doe", "payeeName":"Jane Smith", "amount":50, "payerCountryCode":"CUB"}' "REJECTED"

# 3. High-risk Review (External Engine)
run_test "High-risk threshold" '{"payerName":"Alice Smith", "payeeName":"John Doe", "amount":500, "payerCountryCode":"DEU"}' "APPROVED"

# 4. Approved: Standard Payment (Internal Check)
run_test "Approved (Standard)" '{"payerName":"Alice Smith", "payeeName":"John Doe", "amount":50, "payerCountryCode":"DEU"}' "APPROVED"

echo ""
echo "Results: $PASS passed, $FAIL failed"

# Exit with code for the refresh_and_test.sh script to catch
if [ $FAIL -eq 0 ]; then exit 0; else exit 1; fi