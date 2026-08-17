# ============================================================================
# Multi-stage Dockerfile for Content Transformation Service
# Stage 1: Build with Maven + JDK 17
# Stage 2: Run with JRE 17 (minimal image)
# ============================================================================

# --- Stage 1: Build ---
FROM eclipse-temurin:17-jdk AS builder

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

WORKDIR /build

# Copy all project files
COPY pom.xml .
COPY task1-core task1-core
COPY task2-batch task2-batch
COPY task3-deployment task3-deployment
COPY samples samples

# Build the application (tests run during build to guarantee correctness)
RUN mvn clean package -B

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
