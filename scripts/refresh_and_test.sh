#!/bin/bash

# Fraud Check System: Build, Refresh, and Test Automation
# Path: scripts/refresh_and_test.sh

echo "--- 1. Cleaning and Compiling Project ---"
# Navigate to project root from the scripts directory
cd "$(dirname "$0")/.."

mvn clean compile

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please check your code for syntax errors."
    exit 1
fi

echo "--- 2. Checking if App is running on port 8080 ---"
if ! netstat -tuln | grep -q ":8080 "; then
    echo "❌ ERROR: App is not running on 8080. Start it in another tab with:"
    echo "   mvn exec:java -Dexec.mainClass=\"com.bank.PaymentApp\""
    exit 1
fi

echo "--- 3. Running Validation Suite ---"
# Ensure validate.sh is executable before running
chmod +x ./scripts/validate.sh

# Capture the current timestamp for logging
TIMESTAMP=$(date "+%Y-%m-%d %H:%M:%S")

# Execute validation and log the results to build_report.log
./scripts/validate.sh > build_report.log 2>&1

# Provide immediate feedback
if [ $? -eq 0 ]; then
    echo "✅ Validation successful. See build_report.log for details."
    echo "[$TIMESTAMP] BUILD & TEST SUCCESS" >> build_history.log
else
    echo "❌ Validation failed. Check build_report.log for details."
    echo "[$TIMESTAMP] BUILD & TEST FAILED" >> build_history.log
fi