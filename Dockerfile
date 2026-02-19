# Multi-stage Dockerfile for building and running the Spring Boot application

# Build stage: use Maven to build the fat JAR
FROM maven:3.10.1-eclipse-temurin-21 AS build
WORKDIR /app

# Copy Maven wrapper and pom
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Copy source
COPY src ./src

# Build the application (skip tests to keep image build fast by default)
RUN mvn -B -DskipTests package

# Runtime stage: use a small JRE image
FROM eclipse-temurin:21-jre-jammy

# Default jar path created by the maven build
ARG JAR_FILE=target/demo-0.0.1-SNAPSHOT.jar

# Copy the jar from the build stage
COPY --from=build /app/${JAR_FILE} /app/app.jar

# Expose default port — override at runtime if needed
EXPOSE 8080

# Run the application. Pass any Spring properties via environment variables or command line.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

