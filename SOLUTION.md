# SOLUTION.md -- Architecture, Design Decisions & Cloud Evolution Plan

## Overview

This service ingests French legal XML documents (court judgments), validates them against an XSD schema, transforms them to normalized JSON using XSLT 3.0 (Saxon-HE), and publishes artifacts suitable for downstream search and AI/RAG pipelines.

The design prioritizes correctness, modularity, and operability in a cloud environment.

---

## Architecture

### Module Structure

```
content-transformation-service/
├── task1-core/          Core pipeline (ingest, validate, transform, publish)
├── task2-batch/         Batch processing, concurrency, health & metrics
├── task3-deployment/    Runnable application, Docker, externalized config
```

Dependency flow: `task3-deployment -> task2-batch -> task1-core`

- **task1-core** is a library module containing all business logic, domain models, services, and the REST API controller.
- **task2-batch** adds operational capabilities: batch endpoints, configurable concurrency, Actuator health/readiness, and Micrometer metrics.
- **task3-deployment** is the Spring Boot application entry point. It provides externalized configuration, the Dockerfile, and runtime assembly.

### Pipeline Flow

```
                         +------------------+
                         |   REST Request   |
                         | (XML document)   |
                         +--------+---------+
                                  |
                                  v
                    +-------------+-------------+
                    |  Extract content_id (StAX)|  <-- Memory efficient, streams
                    +-------------+-------------+      until <content_id> found
                                  |
                                  v
                    +-------------+-------------+
                    |  Compute SHA-256 hash     |  <-- For idempotency
                    +-------------+-------------+
                                  |
                                  v
                    +-------------+-------------+
                    |  Duplicate check          |  <-- ConcurrentHashMap.putIfAbsent
                    |  (content_id + hash)      |      (atomic, no race condition)
                    +---+------------------+----+
                        |                  |
                   [duplicate]        [new/updated]
                        |                  |
                        v                  v
              +-------------------+  +-----+------+
              | DUPLICATE_SKIPPED |  | Validate   |  <-- JAXP against XSD
              | (return 200 OK)   |  | against XSD|
              +-------------------+  +-----+------+
                                           |
                              +------------+------------+
                              |                         |
                         [valid]                   [invalid]
                              |                         |
                              v                         v
                    +---------+---------+    +----------+-----------+
                    | Transform via     |    | VALIDATION_FAILED    |
                    | XSLT 3.0         |    | (return 422 +        |
                    | (Saxon-HE)       |    |  diagnostics)        |
                    +---------+---------+    +----------------------+
                              |
                              v
                    +---------+---------+
                    | Publish artifacts |
                    | - normalized.json |
                    | - plain_text.txt  |
                    | - result.json     |
                    +---------+---------+
                              |
                              v
                    +---------+---------+
                    |   PUBLISHED       |
                    |   (return 201)    |
                    +-------------------+
```

### Batch Processing Flow

```
+---------------------+       +--------------------------+
| POST /batch         |       |   ThreadPoolTaskExecutor |
| (multipart files)   |       |   (fixed size, config-   |
+----------+----------+       |    driven concurrency)   |
           |                  +-----------+--------------+
           v                              |
+----------+----------+                   |
| Read all file bytes |                   |
| upfront             |                   |
+----------+----------+                   |
           |                              |
           v                              v
+----------+----------------------------+----+
| CompletableFuture.supplyAsync per document |
| (fault-isolated: one failure != batch fail)|
+----------+----------------------------+----+
           |                              |
           v                              v
+----------+----------+    +--------------+-----------+
| Process doc 1       |    | Process doc 2 ... N      |
| (full pipeline)     |    | (concurrent, bounded)    |
+----------+----------+    +--------------+-----------+
           |                              |
           +--------- join all -----------+
           |
           v
+----------+----------+
| Record metrics      |  <-- Micrometer counters + timers
| Return BatchResponse|      (per-doc + per-batch)
+---------------------+
```

---

## Key Design Decisions

### 1. XSLT for Transformation (not Java code)

**Decision:** Use XSLT 3.0 (executed by Saxon-HE) to produce normalized JSON directly from XML.

**Rationale:**
- The assignment explicitly requires Saxon-HE and XSLT.
- XSLT keeps transformation logic declarative and separate from application code.
- The stylesheet can be modified/reloaded without recompiling Java.
- Saxon-HE's compiled `Templates` object is thread-safe and cached at startup -- new `Transformer` instances are created per invocation (cheap).

**Trade-off:** XSLT is less familiar to most Java developers than manual DOM/Jackson mapping. Debugging XSLT errors requires XSLT expertise.

### 2. Idempotency via SHA-256 Content Hashing

**Decision:** Compute a SHA-256 hash of the raw XML content. Use `content_id + hash` as the dedup key.

**Rationale:**
- Same `content_id` + same hash = identical document already processed -- skip (DUPLICATE_SKIPPED).
- Same `content_id` + different hash = updated document -- overwrite.
- This handles the requirement to "avoid duplicate outputs on repeated submissions of the same content."

**Trade-off:** SHA-256 is computed on every submission. For typical legal documents (tens of KB), this is negligible (<1ms). For very large documents, this is still O(n) but bounded.

### 3. Filesystem Artifact Store with In-Memory Hash Index

**Decision:** Store artifacts as files on disk, keyed by sanitized `content_id`. Maintain a `ConcurrentHashMap` of `content_id -> hash` for fast duplicate detection.

**Rationale:**
- Simple to demo, inspect, and debug -- artifacts are plain JSON/text files.
- The in-memory index allows O(1) duplicate checks without hitting the filesystem.
- The index is rebuilt on startup by scanning existing artifacts (crash recovery).
- The `ArtifactStore` interface makes it trivial to swap in S3, Blob Storage, or a database later.

**Trade-off:** In-memory index doesn't survive horizontal scaling (multiple instances). In production, this would be backed by a shared data store (DynamoDB, Redis, or PostgreSQL).

### 4. StAX for Content ID Extraction

**Decision:** Use StAX streaming to extract `content_id` before validation.

**Rationale:**
- We need the `content_id` early in the pipeline for dedup checks and error reporting.
- StAX is memory-efficient -- it doesn't load the entire DOM.
- It reads only until `<content_id>` is found, then stops.

### 5. Validation Before Transformation

**Decision:** Validate against XSD first. Only transform valid documents.

**Rationale:**
- Invalid documents should never produce published artifacts.
- Validation failures are recorded with full diagnostics (line, column, severity, message).
- This prevents wasting CPU on XSLT transformation of garbage input.

### 6. Structured Error Model

**Decision:** Every processing outcome (success, validation failure, transformation failure, duplicate) produces a `ProcessingResult` record stored in the artifact store.

**Rationale:**
- Operators and clients can always retrieve the status of any submitted document.
- Diagnostics are preserved for debugging.
- The REST API returns semantically meaningful HTTP status codes (201, 200, 422, 500).

### 7. Fixed-Size Thread Pool for Batch Concurrency

**Decision:** Use a `ThreadPoolTaskExecutor` with core and max pool size both set to `cts.processing.concurrency` (default 4).

**Rationale:**
- Fixed pool gives predictable resource usage -- no unbounded thread creation under load.
- Bounded queue (capacity 100) prevents memory exhaustion from large batch submissions.
- The pool is configurable via environment variable for tuning per deployment.
- `CompletableFuture.supplyAsync` + the executor gives clean concurrent processing with fault isolation per document.

**Trade-off:** Queue capacity is fixed at 100. Extremely large batches beyond pool + queue would block. In production, this would be handled by an external queue (SQS).

### 8. Micrometer Metrics for Observability

**Decision:** Instrument all processing paths with Micrometer counters and timers.

**Rationale:**
- `cts.documents.processed` (counter, tagged by status) gives instant visibility into success/failure rates.
- `cts.documents.processing.duration` (timer) tracks latency per document.
- `cts.batch.processed` and `cts.batch.duration` give batch-level throughput metrics.
- Prometheus registry enables scraping by standard monitoring infrastructure.

### 9. Multi-Stage Docker Build

**Decision:** Two-stage Dockerfile -- JDK 17 for build, JRE 17 for runtime.

**Rationale:**
- Build stage uses full JDK + Maven for compilation and testing.
- Runtime stage uses slim JRE -- smaller image (~300MB vs ~700MB), reduced attack surface.
- Dependency caching layer (POMs copied first) speeds up rebuilds when only source changes.
- Non-root user (`cts`) for container security.
- JVM container-aware flags (`UseContainerSupport`, `MaxRAMPercentage=75%`) for proper memory behavior.

### 10. OpenAPI/Swagger with Rich Examples

**Decision:** Use springdoc-openapi with `@Schema` and `@ExampleObject` annotations on all endpoints.

**Rationale:**
- Interactive API documentation at `/swagger-ui.html` enables exploration without curl.
- Response examples show the exact JSON shape for each status (published, validation failed, batch mixed).
- `@Schema` on record fields provides field-level descriptions and example values.
- OpenAPI spec exportable at `/v3/api-docs` for client code generation.

### 11. Atomic Duplicate Detection (putIfAbsent)

**Decision:** Use `ConcurrentHashMap.putIfAbsent()` instead of separate `exists()` + `put()` for dedup in `FileSystemArtifactStore`.

**Rationale:**
- Eliminates TOCTOU (time-of-check-time-of-use) race condition under concurrent batch processing.
- If two threads submit the same content_id simultaneously, only one wins the `putIfAbsent` -- the other sees the existing hash and short-circuits.
- Index entry is rolled back on write failure (IOException) to prevent stale state.

---

## Containerization Strategy

### Dockerfile Highlights

| Aspect | Choice | Rationale |
|--------|--------|-----------|
| Base image | `eclipse-temurin:17-jdk` (build), `eclipse-temurin:17-jre` (runtime) | Official, well-maintained, small |
| Build tool | Maven (in-container) | Reproducible builds, no host dependency |
| Layer caching | POMs copied before source | Dependency download cached unless POMs change |
| Security | Non-root user `cts` | Principle of least privilege |
| Health check | `curl` to `/actuator/health` | Native Docker health monitoring |
| JVM tuning | `UseContainerSupport`, `MaxRAMPercentage=75%`, `G1GC` | Container-aware memory, low-pause GC |
| Resource limits | 512MB RAM, 2 CPUs (compose) | Predictable resource usage |

### Running Locally

```bash
# Option 1: Docker Compose (recommended)
docker-compose up -d

# Option 2: Plain Docker
docker build -t content-transformation-service .
docker run -d -p 8080:8080 --name cts content-transformation-service

# Option 3: JAR directly
mvn clean package
java -jar task3-deployment/target/task3-deployment-1.0.0-SNAPSHOT.jar
```

### Externalized Configuration

All configuration is injectable via environment variables:

| Variable | Purpose | Default |
|----------|---------|---------|
| `SERVER_PORT` | HTTP port | `8080` |
| `CTS_OUTPUT_PATH` | Artifact storage path | `./output` |
| `CTS_PROCESSING_CONCURRENCY` | Batch thread pool size | `4` |
| `CTS_PROCESSING_MAX_FILE_SIZE` | Max single file size | `10MB` |
| `CTS_PROCESSING_MAX_REQUEST_SIZE` | Max batch request size | `50MB` |
| `JAVA_OPTS` | JVM flags | Container-optimized defaults |

---

## Cloud Deployment Plan (AWS)

### Where to Store Inputs and Outputs

| Artifact | Storage | Rationale |
|----------|---------|-----------|
| Input XML documents | S3 bucket (`cts-inputs`) | Durable, versioned, event-driven triggers |
| Published JSON artifacts | S3 bucket (`cts-outputs`) | Scalable, CDN-compatible, queryable via Athena |
| Plain text for RAG | S3 bucket (`cts-outputs`) alongside JSON | Co-located for single-fetch by downstream consumers |
| Processing metadata | DynamoDB table (keyed by `content_id`) | Fast lookup, TTL support, conditional writes for dedup |

### How to Trigger Processing at Volume

1. **S3 Event Notifications** -- SQS queue when new XML lands in the input bucket.
2. **ECS/Fargate tasks** poll SQS, process documents, write to output bucket.
3. **Auto-scaling** based on SQS queue depth (ApproximateNumberOfMessagesVisible).
4. For initial bulk loads: Step Functions orchestrating parallel Fargate tasks with configurable concurrency.

### How to Monitor the Service

| Concern | Tool | Details |
|---------|------|---------|
| Application metrics | CloudWatch (via Micrometer) | Processing count, duration histograms, error rates |
| Health checks | ALB health endpoint (`/actuator/health`) | Readiness and liveness probes |
| Logs | CloudWatch Logs (structured JSON) | Correlation IDs per document |
| Alerts | CloudWatch Alarms | Error rate threshold, queue depth, latency P99 |
| Tracing | AWS X-Ray | End-to-end request tracing across services |

### How to Prevent Duplicate Publishing

1. **DynamoDB conditional write:** Before publishing, perform a `PutItem` with `ConditionExpression: attribute_not_exists(content_hash) OR content_hash <> :hash`.
2. **S3 object naming:** Key output objects as `{content_id}/{hash}/normalized.json` -- naturally deduplicates identical content.
3. **SQS deduplication:** Use FIFO queue with `MessageDeduplicationId = SHA-256(content)` for exactly-once processing within the 5-minute dedup window.

### How to Evolve for RAG Pipeline

To feed a RAG pipeline, the service would additionally produce:

1. **Chunked embeddings:** Split `full_text` into overlapping chunks (e.g., 512 tokens with 50-token overlap), generate vector embeddings (via Bedrock/Titan or OpenAI), and store in a vector database (OpenSearch, Pinecone, or pgvector).

2. **Rich metadata:** Add structured metadata per chunk:
   - Source document `content_id`, court, jurisdiction, decision date
   - Section type (facts, reasons, disposition)
   - Paragraph ID for citation back to source

3. **Document graph:** Extract citations between documents to build a legal knowledge graph (useful for multi-hop RAG retrieval).

4. **Change detection:** When a document is re-submitted with a different hash, re-index only the changed chunks (diff-based partial re-embedding).

The current architecture supports this evolution because:
- The `NormalizedDocument` already contains paragraph-level structure with section tags.
- The `full_text` field is ready for chunking.
- The `ArtifactStore` interface can be extended to write to additional sinks (vector DB, event stream).

---

## Testing Strategy

- **81 unit/integration tests** covering all pipeline paths across 2 modules.
- Tests are **never skipped** -- they run on every `mvn clean install` and inside the Docker build to guarantee correctness.

### task1-core (54 tests)

| Test Class | Coverage |
|------------|----------|
| `XmlValidationServiceTest` | Valid, invalid, malformed, wrong namespace, special chars |
| `XsltTransformationServiceTest` | Full transformation, minimal doc, output structure |
| `ContentIdExtractorTest` | Present, missing, malformed XML |
| `FileSystemArtifactStoreTest` | Store, retrieve, duplicate detection, concurrent access |
| `DocumentProcessingServiceTest` | Full pipeline: success, validation fail, transform fail, duplicate |
| `DocumentControllerTest` | REST endpoints: submit, retrieve, error responses |

### task2-batch (27 tests)

| Test Class | Coverage |
|------------|----------|
| `BatchProcessingServiceTest` | Concurrency, error isolation, empty/null input, metrics |
| `BatchControllerTest` | Multipart upload, mixed results, empty files, order preservation |
| `ProcessingMetricsServiceTest` | All counter types, timers, accumulation |
| `PipelineHealthIndicatorTest` | UP/DOWN for each component (XSD, XSLT, store) |

---

## Trade-offs and Limitations

| Limitation | Why | Mitigation in Production |
|------------|-----|--------------------------|
| In-memory hash index | Single-instance only | Replace with DynamoDB/Redis |
| Filesystem artifact store | Not horizontally scalable | Replace with S3 |
| Synchronous single-doc processing | Simpler to demo and reason about | Add SQS-driven async processing for volume |
| Batch held in memory | All files read upfront | Stream from multipart for very large batches |
| No authentication | Demo scope | Add Spring Security + API keys or OAuth2 |
| No rate limiting | Demo scope | Add Spring Cloud Gateway or API Gateway throttling |
| XSLT JSON output not pretty-printed | Keeps XSLT simple | Post-process with Jackson if needed |
| Fixed thread pool queue (100) | Bounded resource usage | External queue (SQS) for production scale |
| Docker HEALTHCHECK uses curl | Simple, universally available | Use Spring Boot's built-in container probes in K8s |
