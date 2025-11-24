# Project Setup & Initialization

## Overview

The ASP Platform is a production-grade Spring Boot 3.3 application designed as a multi-tenant fintech platform (Plaid-for-Morocco). It provides a foundation for bank statement ingestion, connection management, and data processing.

## Technology Stack

- **Java 17** (JDK 17+)
- **Spring Boot 3.3.0**
- **Spring Data JPA** - Database persistence
- **Spring Security** - Authentication & authorization
- **PostgreSQL 15+** - Database
- **Flyway** - Database migrations
- **Gradle 8.7+** (Kotlin DSL) - Build system
- **Lombok** - Boilerplate reduction
- **Jackson** - JSON serialization
- **Jakarta Validation** - Request validation

## Project Structure

```
com.asp
├── AspApplication.java       # Main application entry point
├── config/                   # Configuration classes
├── security/                 # Security & authentication
├── tenant/                   # Multi-tenancy context
├── domain/                   # JPA entities & repositories
├── api/                      # REST controllers
├── service/                  # Business logic services
├── dto/                      # Data transfer objects
├── storage/                  # Storage abstraction layer
└── util/                     # Utility classes
```

## Key Configuration Files

### `application.yml`

- **Database**: PostgreSQL connection with HikariCP pool
- **JPA**: Validation mode, batch processing enabled
- **Flyway**: Automatic migrations from `classpath:db/migration`
- **Jackson**: CamelCase JSON, ISO date formatting
- **Multipart**: File upload configuration (10MB max)
- **Logging**: DEBUG level for `com.asp` package

### `build.gradle.kts`

- All Spring Boot starters (Web, JPA, Security, Validation, Actuator, AOP)
- PostgreSQL driver and Flyway
- Lombok for annotation processing
- Testing dependencies (JUnit 5, MockMvc, Spring Security Test)

## Docker Support

Multi-stage Dockerfile included:
- **Build stage**: Uses `eclipse-temurin:17-jdk` with Gradle
- **Runtime stage**: Uses `eclipse-temurin:17-jre` with non-root user
- Health check endpoint: `/actuator/health`

## Database Setup

PostgreSQL database required:
- Database name: `asp_platform`
- User: `asp` (configurable via environment variables)
- Port: `5432` (default)
- Migrations: Automatically applied on startup via Flyway

## Environment Variables

```bash
DATASOURCE_URL=jdbc:postgresql://localhost:5432/asp_platform
DATASOURCE_USERNAME=asp
DATASOURCE_PASSWORD=asp
SERVER_PORT=8080
ASP_PUBLIC_BASE_URL=http://localhost:8080
ASP_LOCAL_STORAGE_BASE_PATH=./storage
```

## Quick Start

See `RUN.md` in project root for detailed setup and running instructions.

