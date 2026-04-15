# ── Build stage ───────────────────────────────────────────────────────────────
# Uses the full Maven + JDK 21 image to compile and package the fat JAR.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy dependency descriptors first so Docker can cache the layer.
COPY pom.xml mvnw ./
COPY .mvn .mvn

# Pre-fetch all dependencies (cached unless pom.xml changes).
RUN mvn -B dependency:go-offline -q

# Copy application source and build.
COPY src ./src
RUN mvn -B -DskipTests package -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
# Minimal JRE-only image — no compiler or build tools in production.
FROM eclipse-temurin:21-jre-jammy

# Create a non-root user for security.
RUN groupadd --system assetiq && useradd --system --gid assetiq assetiq

WORKDIR /app

# Copy the fat JAR produced by the build stage.
# The artifactId is "assetIQ" and version "0.0.1-SNAPSHOT" (see pom.xml).
COPY --from=build /app/target/assetIQ-0.0.1-SNAPSHOT.jar app.jar

# Set ownership so the non-root user can read the JAR.
RUN chown assetiq:assetiq app.jar
USER assetiq

# Expose the default Spring Boot port (override via SERVER_PORT env var).
EXPOSE 8085

# JVM tuning for containers:
#   -XX:+UseContainerSupport  — respect cgroup memory/CPU limits
#   -XX:MaxRAMPercentage=75   — use up to 75 % of container RAM for the heap
#   -Djava.security.egd      — faster startup on Linux (avoids /dev/random blocking)
ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-jar", "app.jar"]
