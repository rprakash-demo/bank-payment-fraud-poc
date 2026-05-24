# Stage 1: Build the application
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies first to cache this layer
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn clean package -DskipTests

# Stage 2: Create the runtime image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Copy the built JAR from the build stage
COPY --from=build /app/target/payment-fraud-poc-1.0-SNAPSHOT.jar app.jar
# Expose the port your application uses
EXPOSE 8080
# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]