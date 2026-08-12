# Content Transformation Service

An XML-to-JSON content transformation service for French legal documents. Ingests court judgments in XML, validates them against an XSD schema, transforms them to normalized JSON using XSLT 3.0 (Saxon-HE), and publishes artifacts suitable for downstream search and AI/RAG pipelines.

## Tech Stack

- **Java 17**
- **Spring Boot 3.5.4**
- **Saxon-HE 12.4** (XSLT 3.0 transformation)
- **Maven** (multi-module build)
- **Spring Boot Actuator + Micrometer** (health, metrics)
- **JUnit 5 + AssertJ + MockMvc** (testing)
- **Docker** (containerization)

## Project Structure

```
content-transformation-service/
├── task1-core/          Core pipeline: ingest, validate, transform, publish
├── task2-batch/         Batch processing, concurrency, health & metrics
├── task3-deployment/    Runnable Spring Boot application, Docker, configuration
├── samples/             Example XML documents for testing and demo
├── SOLUTION.md          Architecture, cloud design, and trade-offs
└── README.md            This file
```

### Module Overview

| Module | Responsibility |
|--------|---------------|
| **task1-core** | REST API for document submission and retrieval. Validates XML against XSD, transforms to normalized JSON via XSLT (Saxon-HE), produces plain text for RAG, publishes artifacts keyed by `content_id` with idempotent duplicate handling. |
| **task2-batch** | Batch submission of multiple XML files, configurable concurrent processing (thread pool), health/readiness endpoints, and runtime metrics (processing counts, durations). |
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

## Run Tests

```bash
mvn test -pl task1-core
```

**54 tests** covering all pipeline paths: validation (valid, invalid, malformed), transformation, duplicate detection, artifact storage, REST endpoints, and error handling.

## API Endpoints

### Submit a Single Document

```bash
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/xml" \
  -d @samples/valid-judgment.xml
```

**Response (201 Created):**
```json
{
  "content_id": "FR-2024-CA-000123",
  "status": "PUBLISHED",
  "content_hash": "a1b2c3...",
  "processed_at": "2024-03-12T10:30:00Z",
  "normalized_json": { ... },
  "plain_text": "Le litige porte sur..."
}
```

### Submit Invalid Document

```bash
curl -X POST http://localhost:8080/api/v1/documents \
  -H "Content-Type: application/xml" \
  -d @samples/invalid-bad-date.xml
```

**Response (422 Unprocessable Entity):**
```json
{
  "content_id": "FR-2024-BAD-DATE",
  "status": "VALIDATION_FAILED",
  "diagnostics": [
    {"line": 7, "column": 45, "severity": "ERROR", "message": "..."}
  ]
}
```

### Retrieve Document Status and Outputs

```bash
curl http://localhost:8080/api/v1/documents/FR-2024-CA-000123
```

### Health and Metrics

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8080/actuator/metrics
curl http://localhost:8080/actuator/prometheus
```

## Configuration

Key properties (configurable via `application.yml` or environment variables):

| Property | Env Variable | Description | Default |
|----------|-------------|-------------|---------|
| `server.port` | `SERVER_PORT` | Server port | `8080` |
| `cts.output.path` | `CTS_OUTPUT_PATH` | Filesystem path for published artifacts | `./output` |
| `cts.processing.concurrency` | `CTS_PROCESSING_CONCURRENCY` | Thread pool size for batch processing | `4` |
| `cts.processing.max-file-size` | `CTS_PROCESSING_MAX_FILE_SIZE` | Maximum allowed XML file size | `10MB` |

## Sample Documents

The `samples/` directory contains example XML files:

| File | Purpose |
|------|---------|
| `valid-judgment.xml` | Full valid judgment with citations, parties, multiple sections |
| `valid-judgment-minimal.xml` | Minimal valid judgment (no citations/parties) |
| `invalid-bad-date.xml` | Invalid: `decision_date` is not a valid xs:date |
| `invalid-missing-content-id.xml` | Invalid: missing required `content_id` element |
| `invalid-malformed.xml` | Malformed XML (unclosed tag) |
| `invalid-wrong-namespace.xml` | Invalid: wrong XML namespace |

## HTTP Status Codes

| Status | Meaning |
|--------|---------|
| 201 Created | Document successfully processed and published |
| 200 OK | Duplicate detected — same content already published |
| 400 Bad Request | Empty body or unreadable request |
| 415 Unsupported Media Type | Content-Type is not `application/xml` |
| 422 Unprocessable Entity | XML validation failed (diagnostics included) |
| 404 Not Found | Requested content_id does not exist |
| 500 Internal Server Error | Transformation or storage failure |

## Cloud Target

This service is designed for deployment on **AWS**:
- **S3** for artifact storage (inputs and outputs)
- **SQS** for event-driven processing triggers
- **ECS/Fargate** for container orchestration
- **DynamoDB** for deduplication metadata
- **CloudWatch** for monitoring and alerting

See [SOLUTION.md](SOLUTION.md) for the full architecture, cloud evolution plan, and design trade-offs.

## License

Private — technical assessment submission.
