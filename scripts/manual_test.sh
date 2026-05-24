#!/bin/bash

# Fraud Check System: Manual Test Suite
# Purpose: Verbose debugging with JSON payloads to match PaymentApp.java
# Note: Ensure these endpoints match your @RequestMapping in the Controller.

# Navigate to project root
cd "$(dirname "$0")/.."

# ANSI Color Codes
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
MAGENTA='\033[0;35m'
BOLD_GREEN='\033[1;92m'
BOLD='\033[1m'
NC='\033[0m'

echo -e "\n${BOLD}${CYAN}===========================================${NC}"
echo -e "${BOLD}${CYAN}===   FCS Manual Debugging Suite (JSON) ===${NC}"
echo -e "${BOLD}${CYAN}===========================================${NC}\n"

# 1. Test Blacklist
echo -e "${BOLD}${YELLOW}>>> Testing: Suspicious Name (Blacklist) >>>${NC}"
curl -v -X POST http://localhost:8080/api/v1/payment-fast \
  -H "Content-Type: application/json" \
  -d '{"payerName":"Mark Imaginary", "payeeName":"John Doe", "amount":50, "payerCountryCode":"DEU"}'
sleep 1

# 2. Test High Value (Fraud Engine)
echo -e "\n\n${BOLD}${YELLOW}>>> Testing: High-Value Payment (Expect: Mock-External) >>>${NC}"
curl -v -X POST http://localhost:8080/api/v1/payment-fast \
  -H "Content-Type: application/json" \
  -d '{"payerName":"Alice", "payeeName":"Bob", "amount":500, "payerCountryCode":"DEU"}'
sleep 1

# 3. Test Approved Payment
echo -e "\n\n${BOLD}${BOLD_GREEN}>>> Testing: Approved Payment (Expect: Local-Internal) >>>${NC}"
curl -v -X POST http://localhost:8080/api/v1/payment-fast \
  -H "Content-Type: application/json" \
  -d '{"payerName":"John Doe", "payeeName":"Jane Smith", "amount":50, "payerCountryCode":"DEU"}'
sleep 1

echo -e "\n\n${BOLD}${MAGENTA}=== Debugging Complete: Check Audit Log for results ===${NC}\n"