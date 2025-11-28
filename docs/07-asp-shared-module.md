# asp-shared module overview

## Purpose
`asp-shared` is a plain Java 17 library that hosts artifacts reused by both `asp-core` (the API) and `asp-worker` (the SQS worker). The first shared asset is `IngestionQueueMessage`, the DTO that defines the payload exchanged over SQS.

## Key characteristics
- **Build setup**: Uses the `java-library` plugin with a Java 17 toolchain. Dependencies are limited to Jackson (core + JSR-310) and optional Lombok.
- **Gradle linkage**: Both `asp-core` and `asp-worker` declare `implementation(project(":asp-shared"))`, so any class placed here is immediately available to the other modules without additional wiring.
- **DTO parity**: Moving common payloads (like `IngestionQueueMessage`) into this module ensures the API and worker serialize/deserialize the exact same schema, reducing drift and test duplication.

## How to extend
1. Add new DTOs or lightweight utilities under `asp-shared/src/main/java/com/asp/shared/...`.
2. Keep the module Spring-free; it should remain a minimal dependency so it can load in any JVM context.
3. Document notable additions with small changelog entries in `backend/changelog/`.

