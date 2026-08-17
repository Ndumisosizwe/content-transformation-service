# ============================================================================
# Multi-stage Dockerfile for Content Transformation Service
# Stage 1: Build with Maven + JDK 17
# Stage 2: Run with JRE 17 (minimal image)
# ============================================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:17-jdk AS builder

WORKDIR /build

# Copy Maven wrapper and POMs first for dependency caching
COPY pom.xml .
COPY task1-core/pom.xml task1-core/
COPY task2-batch/pom.xml task2-batch/
COPY task3-deployment/pom.xml task3-deployment/

# Copy Maven wrapper if present, otherwise use system Maven
COPY .mvn .mvn
COPY mvnw mvnw
RUN chmod +x mvnw || true

# Download dependencies (cached unless POMs change)
RUN ./mvnw dependency:go-offline -B 2>/dev/null || mvn dependency:go-offline -B

# Copy source code
COPY task1-core/src task1-core/src
COPY task2-batch/src task2-batch/src
COPY task3-deployment/src task3-deployment/src
COPY samples samples

# Build the application (tests run during build to guarantee correctness)
RUN ./mvnw clean package -B 2>/dev/null || mvn clean package -B

# --- Stage 2: Runtime ---
FROM eclipse-temurin:17-jre

WORKDIR /app

# Create non-root user for security
RUN groupadd -r cts && useradd -r -g cts -d /app cts

# Create output directory
RUN mkdir -p /app/output && chown -R cts:cts /app

# Copy the executable JAR from the build stage
COPY --from=builder /build/task3-deployment/target/task3-deployment-1.0.0-SNAPSHOT.jar app.jar

# Copy sample files for demo/testing
COPY --from=builder /build/samples /app/samples

# Set ownership
RUN chown -R cts:cts /app

# Switch to non-root user
USER cts

# Expose the application port
EXPOSE 8080

# Health check using Actuator endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=15s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM tuning for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
