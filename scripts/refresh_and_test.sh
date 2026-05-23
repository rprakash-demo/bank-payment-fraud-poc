#!/bin/bash

# Fraud Check System: Build, Refresh, and Test Automation
# Path: scripts/refresh_and_test.sh

# 1. Navigate to project root
cd "$(dirname "$0")/.."

echo "--- 1. Cleaning and Compiling Project ---"
mvn clean compile

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please check your code for syntax errors."
    exit 1
fi

echo "--- 2. Checking if App is running on port 8080 ---"
# Check if 8080 is occupied
if ! netstat -tuln | grep -q ":8080 "; then
    echo "❌ ERROR: App is not running on 8080. Start it in another tab with:"
    echo '   mvn exec:java -Dexec.mainClass="com.bank.Main"'
    exit 1
fi

echo "--- 3. Running Validation Suite ---"
# Ensure validate.sh is executable
chmod +x ./scripts/validate.sh

# Capture the current timestamp
TIMESTAMP=$(date "+%Y-%m-%d %H:%M:%S")

# Execute validation and log the results
./scripts/validate.sh > build_report.log 2>&1

# Provide feedback and update history
if [ $? -eq 0 ]; then
    echo "✅ Validation successful. See build_report.log for details."
    echo "[$TIMESTAMP] BUILD & TEST SUCCESS" >> build_history.log
else
    echo "❌ Validation failed. Check build_report.log for details."
    echo "[$TIMESTAMP] BUILD & TEST FAILED" >> build_history.log
fi

# Optional: Display the results in the terminal
cat build_report.log