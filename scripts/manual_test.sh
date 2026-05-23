#!/bin/bash

# Fraud Check System: Manual Test Suite
# Path: scripts/manual_test.sh
# Purpose: Use this for quick, verbose debugging to see headers and raw responses.

# Improved ANSI Color Codes for higher visibility
CYAN='\033[0;36m'
YELLOW='\033[1;33m'
MAGENTA='\033[0;35m'
GREEN='\033[0;32m'
BOLD='\033[1m'
# High intensity green for better contrast
BOLD_GREEN='\033[1;92m'
NC='\033[0m' # No Color

echo -e "\n${BOLD}${CYAN}===========================================${NC}"
echo -e "${BOLD}${CYAN}===   FCS Manual Debugging Suite       ===${NC}"
echo -e "${BOLD}${CYAN}===========================================${NC}\n"

echo -e "${BOLD}${YELLOW}>>> Testing: Suspicious Name (Blacklist) >>>${NC}"
curl -v -X POST http://localhost:8080/api/payment \
  --header "Content-Type: application/xml" \
  --data "<payment><amount>100</amount><payerName>Mark Imaginary</payerName><payerCountry>DE</payerCountry></payment>"

echo -e "\n\n${BOLD}${YELLOW}>>> Testing: Invalid Request (Missing mandatory field) >>>${NC}"
curl -v -X POST http://localhost:8080/api/payment \
  --header "Content-Type: application/xml" \
  --data "<payment><amount>1000</amount></payment>"

echo -e "\n\n${BOLD}${BOLD_GREEN}>>> Testing: Approved Payment (Expect: <response><status>APPROVED</status></response>) >>>${NC}"
curl -v -X POST http://localhost:8080/api/payment \
  --header "Content-Type: application/xml" \
  --data "<payment><amount>1000</amount><payerName>John Doe</payerName><payerCountry>DE</payerCountry></payment>"

echo -e "\n\n${BOLD}${MAGENTA}=== Debugging Complete ===${NC}\n"