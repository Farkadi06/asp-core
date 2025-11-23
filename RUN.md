# ASP Platform - Application Run Guide

This guide explains how to set up and run the ASP Platform backend application.

## Prerequisites

- **Java 17** (JDK 17 or higher)
- **PostgreSQL 15+** (database)
- **Gradle 8.7+** (or use Gradle Wrapper)
- **Docker** (optional, for PostgreSQL)

---

## 1. Database Setup

### Option A: PostgreSQL via Docker (Recommended)

```bash
docker run --name asp-postgres \
  -e POSTGRES_USER=asp \
  -e POSTGRES_PASSWORD=asp \
  -e POSTGRES_DB=asp_platform \
  -p 5432:5432 \
  -d postgres:15
```

### Option B: Local PostgreSQL Installation

1. Install PostgreSQL 15+ on your system
2. Create database:
   ```sql
   CREATE DATABASE asp_platform;
   CREATE USER asp WITH PASSWORD 'asp';
   GRANT ALL PRIVILEGES ON DATABASE asp_platform TO asp;
   ```

### Verify Database Connection

```bash
psql -h localhost -U asp -d asp_platform
```

---

## 2. Configuration

### Environment Variables

Set the following environment variables or use default values in `application.yml`:

```bash
# Database Configuration
export DATASOURCE_URL=jdbc:postgresql://localhost:5432/asp_platform
export DATASOURCE_USERNAME=asp
export DATASOURCE_PASSWORD=asp

# Server Configuration
export SERVER_PORT=8080

# Platform Configuration
export ASP_PUBLIC_BASE_URL=http://localhost:8080

# Storage Configuration
export ASP_LOCAL_STORAGE_BASE_PATH=./storage
```

**Windows PowerShell:**
```powershell
$env:DATASOURCE_URL="jdbc:postgresql://localhost:5432/asp_platform"
$env:DATASOURCE_USERNAME="asp"
$env:DATASOURCE_PASSWORD="asp"
$env:SERVER_PORT="8080"
$env:ASP_PUBLIC_BASE_URL="http://localhost:8080"
$env:ASP_LOCAL_STORAGE_BASE_PATH=".\storage"
```

**Windows CMD:**
```cmd
set DATASOURCE_URL=jdbc:postgresql://localhost:5432/asp_platform
set DATASOURCE_USERNAME=asp
set DATASOURCE_PASSWORD=asp
set SERVER_PORT=8080
set ASP_PUBLIC_BASE_URL=http://localhost:8080
set ASP_LOCAL_STORAGE_BASE_PATH=.\storage
```

---

## 3. Building the Application

### Using Gradle Wrapper (Recommended)

```bash
# Make gradlew executable (Linux/Mac)
chmod +x gradlew

# Build the application
./gradlew clean build

# Or on Windows
gradlew.bat clean build
```

### Skip Tests (Faster Build)

```bash
./gradlew clean build -x test
```

---

## 4. Running the Application

### Option A: Gradle Run

```bash
./gradlew bootRun
```

### Option B: Run JAR File

After building:

```bash
java -jar build/libs/asp-platform-1.0.0-SNAPSHOT.jar
```

### Option C: IDE (IntelliJ IDEA / Eclipse)

1. Import project as Gradle project
2. Locate `AspApplication.java` in `src/main/java/com/asp/`
3. Run as Spring Boot Application

---

## 5. Verify Application is Running

### Health Check

```bash
curl http://localhost:8080/actuator/health
```

**Expected Response:**
```json
{
  "status": "UP"
}
```

### Ping Endpoint (No Auth Required)

```bash
curl http://localhost:8080/v1/ping
```

**Expected Response:**
```json
{
  "status": "ok",
  "tenantId": null
}
```

---

## 6. Database Migrations

Flyway migrations run automatically on application startup. To verify:

1. Check application logs for:
   ```
   Flyway: Successfully applied X migrations
   ```

2. Connect to database and check:
   ```sql
   SELECT * FROM flyway_schema_history;
   ```

### Manual Migration Check

```sql
-- Check tables exist
\dt

-- Expected tables:
-- - tenant
-- - api_key
-- - api_key_scope
-- - bank
-- - bank_connection
-- - ingestion
```

---

## 7. Creating Test Data

### 1. Create a Tenant

```sql
INSERT INTO tenant (id, name, slug, status, plan, created_at, updated_at)
VALUES (
  gen_random_uuid(),
  'Test Tenant',
  'test-tenant',
  'ACTIVE',
  'FREE',
  CURRENT_TIMESTAMP,
  CURRENT_TIMESTAMP
);
```

Note the `id` from the insert result.

### 2. Create an API Key

Use the API key management endpoint (see Testing section below) or manually:

```sql
-- Get tenant_id from previous insert
-- Generate a prefix (8 characters)
-- Hash the secret using BCrypt

-- Example (you'll need to generate these values):
INSERT INTO api_key (id, tenant_id, prefix, key_hash, display_name, sandbox, created_at)
VALUES (
  gen_random_uuid(),
  '<TENANT_ID>',
  'abc12345',
  '$2a$10$...', -- BCrypt hash of secret
  'Test API Key',
  false,
  CURRENT_TIMESTAMP
);

INSERT INTO api_key_scope (api_key_id, scope)
VALUES ('<API_KEY_ID>', 'read'), ('<API_KEY_ID>', 'write');
```

**Note:** It's recommended to use the API key creation endpoint instead of manual SQL.

---

## 8. Testing API Endpoints

### Setup

1. Get your API key from `/v1/api-keys` (see API Key Management below)
2. Format: `asp_live_<prefix>_<secret>` or `asp_sandbox_<prefix>_<secret>`

### Test with curl

**Set API Key variable:**
```bash
export API_KEY="asp_live_abc12345_yoursecrethere"
```

**Or in PowerShell:**
```powershell
$env:API_KEY="asp_live_abc12345_yoursecrethere"
```

### API Key Management

**Create API Key:**
```bash
curl -X POST http://localhost:8080/v1/api-keys \
  -H "X-Api-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "<TENANT_UUID>",
    "displayName": "Test Key",
    "scopes": ["read", "write"],
    "sandbox": false
  }'
```

**List API Keys:**
```bash
curl -X GET http://localhost:8080/v1/api-keys \
  -H "X-Api-Key: $API_KEY"
```

**Delete API Key:**
```bash
curl -X DELETE http://localhost:8080/v1/api-keys/<API_KEY_ID> \
  -H "X-Api-Key: $API_KEY"
```

### Bank Directory

**List Banks:**
```bash
curl -X GET http://localhost:8080/v1/banks \
  -H "X-Api-Key: $API_KEY"
```

**Note:** Banks must be inserted into the database first.

### Bank Connections

**Create Connection:**
```bash
curl -X POST http://localhost:8080/v1/bank-connections/<BANK_ID>/connect \
  -H "X-Api-Key: $API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "userRef": "user_sara_001"
  }'
```

**List Connections:**
```bash
curl -X GET http://localhost:8080/v1/bank-connections \
  -H "X-Api-Key: $API_KEY"
```

**Get Connection:**
```bash
curl -X GET http://localhost:8080/v1/bank-connections/<CONNECTION_ID> \
  -H "X-Api-Key: $API_KEY"
```

### PDF Ingestion

**Upload PDF Statement:**
```bash
curl -X POST http://localhost:8080/v1/ingestions \
  -H "X-Api-Key: $API_KEY" \
  -F "file=@/path/to/statement.pdf" \
  -F "bankConnectionId=<BANK_CONNECTION_UUID>"
```

**List Ingestions:**
```bash
curl -X GET http://localhost:8080/v1/ingestions \
  -H "X-Api-Key: $API_KEY"
```

**Get Ingestion:**
```bash
curl -X GET http://localhost:8080/v1/ingestions/<INGESTION_ID> \
  -H "X-Api-Key: $API_KEY"
```

---

## 9. Logging

### Log Locations

- **Console:** Standard output
- **File:** `logs/` directory (if configured)
- **API Key Actions:** `logs/api-key-actions.log`

### Log Levels

Default log levels in `application.yml`:
- `com.asp`: DEBUG
- `org.springframework.security`: DEBUG
- `org.hibernate.SQL`: DEBUG

---

## 10. Storage Directory

### Local Storage

Files are stored in:
```
./storage/tenants/{tenantId}/ingestions/{ingestionId}/original/{filename}
```

This directory is automatically created on first upload.

### Verify Storage

```bash
# Linux/Mac
ls -la storage/tenants/

# Windows
dir storage\tenants\
```

---

## 11. Troubleshooting

### Port Already in Use

**Error:** `Port 8080 is already in use`

**Solution:**
- Change port: `export SERVER_PORT=8081`
- Or kill process using port 8080

### Database Connection Failed

**Error:** `Connection refused` or `Authentication failed`

**Solutions:**
1. Verify PostgreSQL is running:
   ```bash
   docker ps | grep asp-postgres
   # or
   pg_isready -h localhost -p 5432
   ```

2. Check credentials in `application.yml` or environment variables

3. Verify database exists:
   ```sql
   \l  -- List databases
   ```

### Flyway Migration Errors

**Error:** `Migration checksum mismatch` or `Migration failed`

**Solutions:**
1. Check database schema:
   ```sql
   SELECT * FROM flyway_schema_history ORDER BY installed_rank DESC;
   ```

2. If needed, repair Flyway:
   ```sql
   -- Check for failed migrations
   DELETE FROM flyway_schema_history WHERE success = false;
   ```

3. Re-run application

### Storage Directory Permission Errors

**Error:** `Permission denied` when creating storage directory

**Solutions:**
1. Ensure write permissions on project directory
2. Check `ASP_LOCAL_STORAGE_BASE_PATH` environment variable
3. Create directory manually: `mkdir -p storage`

---

## 12. Docker Deployment

### Build Docker Image

```bash
docker build -t asp-platform:1.0.0 .
```

### Run Docker Container

```bash
docker run -d \
  --name asp-platform \
  -p 8080:8080 \
  -e DATASOURCE_URL=jdbc:postgresql://host.docker.internal:5432/asp_platform \
  -e DATASOURCE_USERNAME=asp \
  -e DATASOURCE_PASSWORD=asp \
  asp-platform:1.0.0
```

**Note:** For database connection from Docker, use `host.docker.internal` (Windows/Mac) or host IP (Linux).

---

## 13. Development Tips

### Hot Reload

With Spring Boot DevTools enabled:
1. Make code changes
2. Save files
3. Application auto-restarts (if configured)

### Enable SQL Logging

Already enabled in `application.yml`:
```yaml
logging:
  level:
    org.hibernate.SQL: DEBUG
```

### Debug Mode

Run with debug port:
```bash
./gradlew bootRun --debug-jvm
```

Then attach debugger to port 5005.

---

## 14. Quick Start Checklist

- [ ] PostgreSQL running on port 5432
- [ ] Database `asp_platform` created
- [ ] Environment variables set (or using defaults)
- [ ] Application built (`./gradlew build`)
- [ ] Application started (`./gradlew bootRun`)
- [ ] Health check passes (`curl http://localhost:8080/actuator/health`)
- [ ] Created test tenant in database
- [ ] Created API key (via endpoint or manually)
- [ ] Tested API with API key header

---

## 15. Additional Resources

- **Spring Boot Documentation:** https://spring.io/projects/spring-boot
- **PostgreSQL Documentation:** https://www.postgresql.org/docs/
- **Flyway Documentation:** https://flywaydb.org/documentation/

---

## Support

For issues or questions:
1. Check application logs
2. Verify database connectivity
3. Check environment variables
4. Review this guide's troubleshooting section

---

**Last Updated:** 2025-11-22

