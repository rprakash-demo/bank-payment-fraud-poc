#!/bin/bash

# Fraud Check System: Build, Refresh, and Orchestrate
# Path: scripts/refresh_and_test.sh

# 1. Navigate to project root
cd "$(dirname "$0")/.."

echo -e "\033[1;36m--- Orchestrating System Refresh ---\033[0m"

# 2. Cleanup existing container
echo "Stopping and removing existing container..."
docker stop brave_driscoll 2>/dev/null; docker rm brave_driscoll 2>/dev/null

# 3. Clean and Build
echo "Building project with Maven..."
mvn clean package -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed. Please check your code."
    exit 1
fi

# 4. Build and Run Docker Container
echo "Rebuilding Docker image..."
docker build -t bank-fraud-poc:latest . > /dev/null

echo "Starting container: brave_driscoll..."
docker run -d --name brave_driscoll -p 8080:8080 bank-fraud-poc:latest

# 5. Initialize Background Audit Log Engine
echo "Initializing Audit Log Engine..."
> transaction_history.log
docker logs -f brave_driscoll | grep -E "AUDIT_TRACE|REJECTED|APPROVED" >> transaction_history.log &

echo -e "\n\033[1;92m==========================================="
echo -e "SYSTEM REFRESHED AND READY FOR DEMO"
echo -e "===========================================\033[0m"