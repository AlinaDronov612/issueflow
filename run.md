# IssueFlow — Setup, Build & Run

Backend for a lightweight project/issue tracker (Java 21, Spring Boot 3.4.2).

## Prerequisites

- **JDK 21** (required — the build targets Java 21).
  - Verify: `java -version` should report 21.
  - `JAVA_HOME` must point at a JDK 21 install, e.g.
    `C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`.
- **Docker** (for the PostgreSQL runtime DB via `compose.yml`).
- No global Maven needed — use the bundled wrapper (`./mvnw` / `mvnw.cmd`).

## 1. Start the database

```bash
docker compose up -d
```

This starts PostgreSQL on `localhost:5432` (db `issueflow`, user/password `issueflow`/`issueflow`),
matching `src/main/resources/application.yaml`.

## 2. Build

```bash
./mvnw clean package        # macOS/Linux
mvnw.cmd clean package       # Windows
```

## 3. Run the application

```bash
./mvnw spring-boot:run
# or run the packaged jar:
java -jar target/issueflow-0.0.1-SNAPSHOT.jar
```

App starts on http://localhost:8080.

## 4. Run the tests

Tests use an in-memory H2 database — **Docker is not required** for tests.

```bash
./mvnw test
```

---

## Authentication & the seeded admin

Every endpoint is protected by JWT auth **except** `POST /auth/login`. To avoid a
bootstrap deadlock (you need a token to create users, but creating a user needs a
token), the app seeds a single bootstrap ADMIN on first startup via
`src/main/resources/data.sql` (loaded after Hibernate creates the schema).

**Seeded admin credentials:**

| Field    | Value      |
|----------|------------|
| username | `admin`    |
| password | `admin123` |
| role     | `ADMIN`    |

> The seed is idempotent (`INSERT ... WHERE NOT EXISTS`), so restarts won't fail.
> Change these credentials before any real deployment.

### Demo flow

1. **Log in** to get a token:

   ```bash
   curl -s -X POST http://localhost:8080/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin123"}'
   # => { "accessToken": "<jwt>", "tokenType": "Bearer", "expiresIn": 3600 }
   ```

2. **Use the token** on protected endpoints:

   ```bash
   TOKEN=<paste accessToken>
   curl -s http://localhost:8080/auth/me      -H "Authorization: Bearer $TOKEN"
   curl -s http://localhost:8080/users        -H "Authorization: Bearer $TOKEN"
   ```

3. **Create more users** (still protected — no public registration):

   ```bash
   curl -s -X POST http://localhost:8080/users \
     -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" \
     -d '{"username":"jdoe","email":"jdoe@example.com","fullName":"John Doe","role":"DEVELOPER","password":"secret123"}'
   ```

4. **Log out** (revokes the current token via an in-memory deny-list):

   ```bash
   curl -s -X POST http://localhost:8080/auth/logout -H "Authorization: Bearer $TOKEN"
   ```

## Configuration notes

- **JWT secret**: set via the `JWT_SECRET` environment variable in any real
  environment. The default in `application.yaml` is a dev-only fallback.
- **Token lifetime**: `app.jwt.expiration-seconds` (default `3600`).
- **Logout** uses an in-memory token deny-list; it is per-instance and cleared on
  restart (tokens also expire on their own).
