# Content Transformation Service

An XML-to-JSON content transformation service for legal documents. Ingests French legal XML judgments, validates them against an XSD schema, transforms them to normalized JSON using XSLT 3.0 (Saxon-HE), and publishes artifacts suitable for downstream search and AI/RAG pipelines.

## Tech Stack

- **Java 17**
- **Spring Boot 3.5.4**
- **Saxon-HE 12.4** (XSLT 3.0 transformation)
- **Maven** (multi-module build)
- **Spring Boot Actuator + Micrometer** (health, metrics)
- **Docker** (containerization)

## Project Structure

```
content-transformation-service/
├── task1-core/          Core pipeline: ingest, validate, transform, publish
├── task2-batch/         Batch processing, concurrency, health & metrics
├── task3-deployment/    Runnable Spring Boot application, Docker, configuration
├── SOLUTION.md          Architecture, cloud design, and trade-offs
└── README.md            This file
```

### Module Overview

| Module | Responsibility |
|--------|---------------|
| **task1-core** | Accepts legal XML documents via REST API, validates against XSD, transforms to normalized JSON via XSLT (Saxon-HE), produces plain text for RAG, publishes artifacts keyed by `content_id` with idempotent handling. |
| **task2-batch** | Adds batch submission of multiple XML files, configurable concurrent processing, health/readiness endpoints, and runtime metrics (processing counts, durations). |
| **task3-deployment** | The runnable Spring Boot application. Packages everything into an executable JAR, provides externalized configuration via environment variables, and includes a Dockerfile for containerized deployment. |

## Prerequisites

- Java 17+ (JDK)
- Maven 3.8+

## Build

```bash
mvn clean install
```

## Run

```bash
java -jar task3-deployment/target/task3-deployment-1.0.0-SNAPSHOT.jar
```

Or using Maven:

```bash
mvn spring-boot:run -pl task3-deployment
```

The service starts on port **8080** by default.

## API Endpoints

### Submit a Single Document

```bash
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/xml" \
  -d @sample-judgment.xml
```

### Submit a Batch of Documents

```bash
curl -X POST http://localhost:8080/api/v1/documents/batch \
  -H "Content-Type: multipart/form-data" \
  -F "files=@doc1.xml" \
  -F "files=@doc2.xml"
```

### Retrieve Document Status and Outputs

```bash
curl http://localhost:8080/api/v1/documents/{contentId}
```

### Health and Metrics

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/prometheus
```

## Configuration

Key properties (configurable via `application.yml` or environment variables):

| Property | Description | Default |
|----------|-------------|---------|
| `server.port` | Server port | `8080` |
| `cts.output.path` | Filesystem path for published artifacts | `./output` |
| `cts.processing.concurrency` | Thread pool size for batch processing | `4` |
| `cts.processing.max-file-size` | Maximum allowed XML file size | `10MB` |

## Cloud Target

This service is designed for deployment on **AWS**:
- **S3** for artifact storage (inputs and outputs)
- **SQS** for event-driven processing triggers
- **ECS/Fargate** for container orchestration
- **CloudWatch** for monitoring and alerting

See [SOLUTION.md](SOLUTION.md) for the full architecture and evolution plan.

## License

Private — technical assessment submission.
