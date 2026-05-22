# Use a multi-stage build
FROM maven:3.8-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
# Build the JAR
RUN mvn clean package -DskipTests

# Use a lightweight JRE for the final image
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Copy the built JAR from the build stage
COPY --from=build /app/target/payment-fraud-poc-1.0-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]