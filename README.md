# Content Transformation Service

An XML-to-JSON content transformation service for French legal documents. Ingests court judgments in XML, validates them against an XSD schema, transforms them to normalized JSON using XSLT 3.0 (Saxon-HE), and publishes artifacts suitable for downstream search and AI/RAG pipelines.

## Tech Stack

- **Java 17**
- **Spring Boot 3.5.4**
- **Saxon-HE 12.4** (XSLT 3.0 transformation)
- **Maven** (multi-module build)
- **Spring Boot Actuator + Micrometer** (health, metrics, Prometheus)
- **JUnit 5 + AssertJ + MockMvc** (testing)
- **Docker** (containerization)

## Project Structure

```
content-transformation-service/
├── task1-core/          Core pipeline: ingest, validate, transform, publish
├── task2-batch/         Batch processing, concurrency, health & metrics
├── task3-deployment/    Runnable Spring Boot application, Docker, configuration
├── samples/             Example XML documents for testing and demo
├── Dockerfile           Multi-stage build (JDK 17 build → JRE 17 runtime)
├── docker-compose.yml   Local container orchestration
├── SOLUTION.md          Architecture, cloud design, and trade-offs
└── README.md            This file
```

### Module Overview

| Module | Responsibility |
|--------|---------------|
| **task1-core** | REST API for document submission and retrieval. Validates XML against XSD, transforms to normalized JSON via XSLT (Saxon-HE), produces plain text for RAG, publishes artifacts keyed by `content_id` with idempotent duplicate handling. |
| **task2-batch** | Batch submission of multiple XML files, configurable concurrent processing (thread pool), health/readiness endpoints, Micrometer metrics (processing counts, durations, Prometheus export). |
| **task3-deployment** | The runnable Spring Boot application. Packages everything into an executable JAR, provides externalized configuration via environment variables, and includes a Dockerfile for containerized deployment. |

## Prerequisites

- Java 17+ (JDK)
- Maven 3.8+
- Docker (optional, for containerized run)

## Build

```bash
mvn clean install
```

This compiles all modules and runs all **81 unit/integration tests**. Tests are never skipped — they run on every build to guarantee correctness.

## Run Locally

```bash
java -jar task3-deployment/target/task3-deployment-1.0.0-SNAPSHOT.jar
```

Or using Maven:

```bash
mvn spring-boot:run -pl task3-deployment
```

The service starts on port **8080** by default.

## Run with Docker

### Build the image:

```bash
docker build -t content-transformation-service .
```

### Run the container:

```bash
docker run -d \
  --name cts \
  -p 8080:8080 \
  -e CTS_OUTPUT_PATH=/app/output \
  -e CTS_PROCESSING_CONCURRENCY=4 \
  content-transformation-service
```

### Or use Docker Compose (recommended):

```bash
docker-compose up -d
```

This builds the image and starts the service with a named volume for output persistence, health checks, and resource limits.

### Stop:

```bash
docker-compose down
```

## Run Tests

```bash
mvn test
```

Tests run automatically as part of `mvn clean install` — they are never skipped. You can also run tests for a specific module:

```bash
mvn test -pl task1-core
mvn test -pl task2-batch
```

**81 tests** across 2 modules:
- **task1-core** (54 tests): validation, transformation, duplicate detection, artifact storage, REST endpoints, error handling
- **task2-batch** (27 tests): batch processing, concurrency, multipart upload, metrics, health indicator

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

### Submit a Batch of Documents

```bash
curl -X POST http://localhost:8080/api/v1/documents/batch \
  -F "files=@samples/valid-judgment.xml" \
  -F "files=@samples/valid-judgment-minimal.xml" \
  -F "files=@samples/invalid-bad-date.xml"
```

**Response (200 OK):**
```json
{
  "total": 3,
  "successful": 2,
  "failed": 1,
  "results": [
    {"content_id": "FR-2024-CA-000123", "status": "PUBLISHED", ...},
    {"content_id": "FR-2024-MIN-001", "status": "PUBLISHED", ...},
    {"content_id": "FR-2024-BAD-DATE", "status": "VALIDATION_FAILED", "diagnostics": [...]}
  ]
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
# Health check (includes pipeline readiness details)
curl http://localhost:8080/actuator/health

# All metrics
curl http://localhost:8080/actuator/metrics

# Prometheus scrape endpoint
curl http://localhost:8080/actuator/prometheus

# Specific metric
curl http://localhost:8080/actuator/metrics/cts.documents.processed
```

## API Documentation (Swagger)

Once the service is running, interactive API docs are available at:

- **Swagger UI:** [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)
- **OpenAPI JSON:** [http://localhost:8080/v3/api-docs](http://localhost:8080/v3/api-docs)
- **OpenAPI YAML:** [http://localhost:8080/v3/api-docs.yaml](http://localhost:8080/v3/api-docs.yaml)

## Configuration

Key properties (configurable via `application.yml` or environment variables):

| Property | Env Variable | Description | Default |
|----------|-------------|-------------|---------|
| `server.port` | `SERVER_PORT` | Server port | `8080` |
| `cts.output.path` | `CTS_OUTPUT_PATH` | Filesystem path for published artifacts | `./output` |
| `cts.processing.concurrency` | `CTS_PROCESSING_CONCURRENCY` | Thread pool size for batch processing | `4` |
| `cts.processing.max-file-size` | `CTS_PROCESSING_MAX_FILE_SIZE` | Maximum allowed XML file size | `10MB` |
| `spring.servlet.multipart.max-request-size` | `CTS_PROCESSING_MAX_REQUEST_SIZE` | Maximum total batch request size | `50MB` |

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
| 200 OK | Duplicate detected — same content already published (single); batch response (batch) |
| 400 Bad Request | Empty body, unreadable request, or no files in batch |
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
