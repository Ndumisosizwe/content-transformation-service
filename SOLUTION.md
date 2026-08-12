# SOLUTION.md — Architecture, Design Decisions & Cloud Evolution Plan

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

Dependency flow: `task3-deployment → task2-batch → task1-core`

- **task1-core** is a library module containing all business logic, domain models, services, and the REST API controller.
- **task2-batch** adds operational capabilities: batch endpoints, configurable concurrency, Actuator health/readiness, and Micrometer metrics.
- **task3-deployment** is the Spring Boot application entry point. It provides externalized configuration, the Dockerfile, and runtime assembly.

### Pipeline Flow

```
XML Input → Extract content_id (StAX)
          → Compute SHA-256 hash
          → Check duplicate (hash index)
          → Validate against XSD (JAXP)
          → Transform via XSLT 3.0 (Saxon-HE)
          → Publish artifacts (filesystem)
          → Return ProcessingResult
```

---

## Key Design Decisions

### 1. XSLT for Transformation (not Java code)

**Decision:** Use XSLT 3.0 (executed by Saxon-HE) to produce normalized JSON directly from XML.

**Rationale:**
- The assignment explicitly requires Saxon-HE and XSLT.
- XSLT keeps transformation logic declarative and separate from application code.
- The stylesheet can be modified/reloaded without recompiling Java.
- Saxon-HE's compiled `Templates` object is thread-safe and cached at startup — new `Transformer` instances are created per invocation (cheap).

**Trade-off:** XSLT is less familiar to most Java developers than manual DOM/Jackson mapping. Debugging XSLT errors requires XSLT expertise.

### 2. Idempotency via SHA-256 Content Hashing

**Decision:** Compute a SHA-256 hash of the raw XML content. Use `content_id + hash` as the dedup key.

**Rationale:**
- Same `content_id` + same hash = identical document already processed → skip (DUPLICATE_SKIPPED).
- Same `content_id` + different hash = updated document → overwrite.
- This handles the requirement to "avoid duplicate outputs on repeated submissions of the same content."

**Trade-off:** SHA-256 is computed on every submission. For typical legal documents (tens of KB), this is negligible (<1ms). For very large documents, this is still O(n) but bounded.

### 3. Filesystem Artifact Store with In-Memory Hash Index

**Decision:** Store artifacts as files on disk, keyed by sanitized `content_id`. Maintain a `ConcurrentHashMap` of `content_id → hash` for fast duplicate detection.

**Rationale:**
- Simple to demo, inspect, and debug — artifacts are plain JSON/text files.
- The in-memory index allows O(1) duplicate checks without hitting the filesystem.
- The index is rebuilt on startup by scanning existing artifacts (crash recovery).
- The `ArtifactStore` interface makes it trivial to swap in S3, Blob Storage, or a database later.

**Trade-off:** In-memory index doesn't survive horizontal scaling (multiple instances). In production, this would be backed by a shared data store (DynamoDB, Redis, or PostgreSQL).

### 4. StAX for Content ID Extraction

**Decision:** Use StAX streaming to extract `content_id` before validation.

**Rationale:**
- We need the `content_id` early in the pipeline for dedup checks and error reporting.
- StAX is memory-efficient — it doesn't load the entire DOM.
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

1. **S3 Event Notifications** → SQS queue when new XML lands in the input bucket.
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
2. **S3 object naming:** Key output objects as `{content_id}/{hash}/normalized.json` — naturally deduplicates identical content.
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

- **54 unit/integration tests** covering all pipeline paths.
- Test classes per component: `XmlValidationServiceTest`, `XsltTransformationServiceTest`, `ContentIdExtractorTest`, `FileSystemArtifactStoreTest`, `DocumentProcessingServiceTest`, `DocumentControllerTest`.
- Coverage targets: all branches (valid, invalid, malformed, duplicate, empty, wrong namespace, special characters, error handling).
- Integration test uses `@SpringBootTest` with `MockMvc` for full REST endpoint verification.

---

## Trade-offs and Limitations

| Limitation | Why | Mitigation in Production |
|------------|-----|--------------------------|
| In-memory hash index | Single-instance only | Replace with DynamoDB/Redis |
| Filesystem artifact store | Not horizontally scalable | Replace with S3 |
| Synchronous processing | Simpler to demo and reason about | Add SQS-driven async processing for volume |
| No authentication | Demo scope | Add Spring Security + API keys or OAuth2 |
| No rate limiting | Demo scope | Add Spring Cloud Gateway or API Gateway throttling |
| XSLT JSON output not pretty-printed | Keeps XSLT simple | Post-process with Jackson if needed |
