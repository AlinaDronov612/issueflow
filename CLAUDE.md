# CLAUDE.md — IssueFlow Project Instructions

This file tells Claude Code how to work in this repository. Read it fully before
writing or modifying code. Follow it on every change. If a request conflicts with
these rules, flag the conflict instead of silently breaking a rule.

---

## 1. What we are building

IssueFlow is a RESTful backend for a lightweight project/issue tracking platform,
built as the AT&T TDP 2026 home assignment. It manages users, projects, tickets
(issues), comments, plus a set of extended features (audit log, dependencies,
attachments, CSV import/export, soft delete, @mentions, auto-escalation,
workload-based auto-assignment).

Two sources define the work, and they serve different roles:

- **`README.md` defines the API contract** — endpoint paths, HTTP methods, request/
  response field names, and status codes. Match it exactly. Do not invent endpoints
  or fields that are not in that table.
- **The requirements PDF defines business behavior** — rules that the API tables do
  not fully express (escalation logic, auto-assignment tie-breaking, mention
  re-evaluation, soft-delete/restore semantics, etc.). The PDF is the source of
  truth for *how* an endpoint must behave.

On a genuine conflict between the two, do not silently pick one — flag it and ask.

---

## 2. Tech stack (already fixed — do not change versions)

- **Java 21**, **Spring Boot 3.4.2** (parent POM)
- **Maven** via the bundled `./mvnw` wrapper — never assume a global `mvn`
- **Spring Data JPA / Hibernate** for persistence
- **PostgreSQL** at runtime (via `compose.yml`), **H2** in-memory for tests
- **Lombok** is available — use it to cut boilerplate (`@Getter`, `@Setter`,
  `@RequiredArgsConstructor`, `@Builder`)
- **spring-boot-starter-validation** is present — use Jakarta Bean Validation
- **Apache Commons CSV** (`commons-csv` 1.10.0) is already on the classpath — use it
  for export/import; do not hand-roll CSV parsing
- Do not add new dependencies without asking first. If one is truly needed, propose
  it and explain why before editing `pom.xml`.

---

## 3. Package structure

Root package: `com.att.tdp.issueflow`

Organize **by feature**, then by layer inside each feature:

```
com.att.tdp.issueflow
├── user/         (controller, service, repository, entity, dto)
├── project/
├── ticket/
├── comment/
├── auth/
├── audit/
├── dependency/
├── attachment/
├── common/       (shared: exceptions, error handling, base entity, enums, config)
└── IssueFlowApplication.java
```

Each feature package contains its own `Controller`, `Service`, `Repository`,
entity, and DTOs. Cross-cutting concerns (global exception handler, security
config, base auditing entity) live in `common`.

---

## 4. Architecture rules

> The conventions in sections 3–5 (feature packages, `BaseEntity`, layering, DTO
> boundaries) are deliberate engineering choices, not verbatim assignment
> requirements. They exist to make the codebase clean and reviewable. Treat the
> business rules in section 6 and the API contract from `README.md` as
> non-negotiable; treat these structural conventions as the house style to follow
> consistently unless I say otherwise.

- **Strict layering:** Controller → Service → Repository. Controllers never touch
  repositories directly. Business logic lives in services, not controllers.
- **Controllers** are thin: validate input, delegate to a service, map to a
  response. No business rules in controllers.
- **DTOs at the boundary:** never expose JPA entities directly in request or
  response bodies. Use request DTOs (with validation annotations) and response
  DTOs. Map explicitly (a small mapper method or component); do not leak entities.
- **Dependency injection:** constructor injection only (via Lombok
  `@RequiredArgsConstructor` on `final` fields). Never field injection
  (`@Autowired` on a field).
- **Transactions:** annotate service methods that change state with
  `@Transactional`. Read-only queries use `@Transactional(readOnly = true)`.
- Keep methods small and single-purpose. If a service method is doing three things,
  split it.

---

## 5. Persistence conventions

- The skeleton ships a placeholder `task` table in `schema.sql` and matching rows
  in `data.sql`. **Replace these** with the real IssueFlow schema/seed data — do
  not leave the `task` table behind.
- `ddl-auto` is `update` and Hibernate manages the schema from entities. Keep
  `schema.sql`/`data.sql` consistent with the entities, or drive everything from
  entities and trim the SQL files to only what is needed (e.g. seed data). Flag
  which approach you take.
- Every persistent entity extends a shared `BaseEntity` in `common` that carries
  `id`, `createdAt`, `updatedAt` (JPA auditing via `@CreatedDate`/`@LastModifiedDate`,
  enabled with `@EnableJpaAuditing`).
- Enums (`Role`, `Status`, `Priority`, `Type`, audit `Action`/`Actor`) are real
  Java enums, persisted with `@Enumerated(EnumType.STRING)` — never ordinal.
- JSON field naming is **camelCase** exactly as in the README (`fullName`,
  `ownerId`, `assigneeId`, `isOverdue`, `mentionedUsers`). Match it precisely.

---

## 6. Business rules that must not be violated

These are the high-value correctness requirements. Implement them in the service
layer and cover each with a test.

- **Concurrent update protection:** a ticket (and a comment) cannot be updated by
  two users at once. Use JPA optimistic locking (`@Version`). On conflict, return a
  clear 409 Conflict, not a 500.
- **Ticket status is forward-only:** `TODO → IN_PROGRESS → IN_REVIEW → DONE`.
  Reject any backward transition.
- **A DONE ticket cannot be updated** at all. Reject with an informative error.
- **A ticket cannot move to DONE** while it has unresolved (non-DONE) blocking
  dependencies.
- **Dependencies** require both tickets to exist and belong to the **same project**.
- **Auto-escalation** (scheduler): only for tickets with a `dueDate`; promotes
  priority one level when overdue; idempotent at `CRITICAL` (sets `isOverdue=true`,
  never escalates past CRITICAL); a manual priority change via PATCH resets the
  escalation state; escalation never changes `status`.
- **Auto-assignment:** on ticket creation with no `assigneeId`, assign the
  least-loaded **DEVELOPER** in the project (workload = count of non-DONE tickets
  assigned to them in that project); ties broken by oldest registration first;
  ADMINs excluded; no developers → `assigneeId = null` without error; record in the
  audit log with `actor = SYSTEM`, `action = AUTO_ASSIGN`.
- **Audit log** is append-only — never update or delete entries. Record every
  state-changing action, whether user-triggered or system-triggered.
- **@Mentions** matching is case-insensitive; re-evaluated on comment update (add
  new, remove gone).
- **Attachments:** reject files over 10MB and any type outside
  `image/png, image/jpeg, application/pdf, text/plain`.
- **CSV import/export** must correctly handle commas and quotes inside field values
  (this is exactly why we use Commons CSV).
- **Soft delete:** tickets and projects are never hard-deleted; soft-deleted records
  are hidden from normal responses; only ADMIN can list/restore them.

---

## 7. Validation & error handling

- Validate all input with Jakarta Bean Validation annotations (`@NotNull`,
  `@NotBlank`, `@Email`, `@Size`, etc.) on request DTOs; trigger with `@Valid`.
- Reject invalid enum values (e.g. a bad `role` or `status`) with a 400, not a 500.
- Use a single `@RestControllerAdvice` global exception handler in `common`.
- Error responses use a consistent shape, e.g.:
  ```json
  { "timestamp": "...", "status": 400, "error": "Bad Request",
    "message": "role must be one of ADMIN, DEVELOPER", "path": "/users" }
  ```
- Map exceptions to correct status codes: not found → 404, validation → 400,
  optimistic-lock / illegal transition → 409, auth failure → 401, forbidden
  (e.g. non-ADMIN hitting an ADMIN endpoint) → 403.
- Error messages must be informative and name the offending field.

---

## 8. Security / auth

- All endpoints are protected by **JWT-based** auth except `POST /auth/login`.
- `POST /auth/login` returns a signed JWT; `GET /auth/me` returns the current user;
  `POST /auth/logout` invalidates the token (server-side deny-list or rely on
  stateless expiry — pick one and document the choice).
- ADMIN-only endpoints (soft-delete listing/restore) must enforce the role.
- Never log tokens, passwords, or secrets. Keep the JWT secret in configuration, not
  hard-coded in a committed file with a real value.

---

## 9. Testing (required, and graded)

- Use the existing test setup: H2 in-memory, profile config in
  `src/test/resources/application.yaml`.
- Prioritize **behavioral tests of the business rules in section 6** over trivial
  getter/setter coverage. Each rule in section 6 should have at least one test that
  proves it (happy path + the rejection case).
- Write a test that demonstrates the optimistic-locking conflict (two updates,
  second one fails with a conflict).
- Use `@SpringBootTest` + `MockMvc` for endpoint/integration tests; plain unit tests
  for pure service logic.
- After any change to a feature, run that feature's tests. Before declaring a task
  done, run the full suite: `./mvnw test`.

---

## 10. Workflow rules for Claude Code

- **Plan before large changes.** For any feature, briefly state the entities,
  endpoints, and tests you intend to add before writing them. Wait for confirmation
  on anything structural.
- **One feature at a time.** Implement → test → confirm green → move on. Do not
  scaffold all eight extended features at once.
- **Show diffs and explain non-obvious decisions.** I review every change. If you
  make a design tradeoff, say so in one sentence.
- **Match the README contract exactly** — endpoint paths, HTTP methods, field
  names, status codes. If the README is ambiguous, ask rather than guess.
- **Do not gold-plate.** Build what the requirements ask for, correctly and tested.
  No extra endpoints, no speculative features, no premature optimization.
- **Keep the build clean:** no unused imports, no dead code, no commented-out
  blocks left behind, no `System.out.println` debugging in committed code.
- If you are unsure whether something is in scope, ask before building it.

---

## 11. Useful commands

```bash
# Start PostgreSQL (from issueflow-java/)
docker compose up -d

# Build
./mvnw clean compile

# Run the app (http://localhost:8080)
./mvnw spring-boot:run

# Run all tests (H2, no Docker needed)
./mvnw test

# Run a single test class
./mvnw test -Dtest=TicketServiceTest
```

---

## 12. Definition of done (per feature)

A feature is done when: endpoints match the README contract; input is validated;
errors return correct status codes with informative messages; the relevant business
rules from section 6 hold; tests cover the happy path and key rejections; and
`./mvnw test` is green.

---

## 13. Decisions made during implementation (binding rules)

These resolve ambiguities the original spec/contract left open. They are **binding** for
all remaining work — apply them consistently. (The code on disk is the record of *what* is
built; this section records *decisions* a fresh session must honor.)

**Open-question resolutions:**
- **Identity is from the JWT principal, always.** Audit `performedBy` and any actor identity
  come from the authenticated principal. Where a request body carries an identity field (e.g.
  comment `authorId`, project `ownerId`), validate it against the principal and reject a
  mismatch (403); never trust the body as authoritative.
- **Auto-assignment candidate pool = all DEVELOPERs globally** (no project-membership concept
  exists). Workload = count of that developer's non-DONE tickets in the project; ties broken by
  oldest registration first; ADMINs excluded; no developers → `assigneeId = null` (no error);
  audit as `actor = SYSTEM`, `action = AUTO_ASSIGN`.
- **`DELETE /users/:id` is a hard delete, guarded:** return 409 if the user is referenced by any
  ticket (`assigneeId`), comment (`authorId`), or audit log (`performedBy`).
- **A DONE ticket's immutability applies only to the ticket's own fields.** Comments,
  attachments, and dependencies may still be added/changed on a DONE ticket.
- **Comment editing is not author-restricted** — the "two users can't edit simultaneously
  (Admin/Developer)" requirement is a concurrency guard (optimistic locking) and implies
  cross-role edit access; do not add an author-only rule.
- **Soft-deleted tickets are excluded from dependency logic:** a soft-deleted blocker does not
  block and is excluded from blocker listings.
- **Passwords:** `POST /users` accepts a `password` (deliberate, documented deviation from the
  README user shape); stored only as a BCrypt hash; never returned in any response.
- **Logout:** in-memory deny-list of revoked token `jti`s. **Auth:** JWT secret via
  `${JWT_SECRET:<dev-default>}`. A seeded ADMIN (`admin`) is the only bootstrap path; all
  endpoints except `POST /auth/login` require authentication.
- **`performedBy` is a plain nullable `Long`, not a FK** — audit history must survive user
  deletion. **AuditLog is append-only** (no setters; columns `updatable=false`).
- **Soft delete uses explicit finders** (`findByDeletedFalse`, `findByIdAndDeletedFalse`), NOT a
  global `@SQLRestriction` (which would break restore and the ADMIN deleted-listing).
- **PATCH endpoints return `200 OK` with an empty body** (matches the README for ticket,
  project, and comment updates).
- **Status conflicts → 409** (illegal/backward transition, DONE-immutability, optimistic-lock,
  unresolved-blocker). ADMIN-only endpoints enforce `principal.role()` → 403 via
  `ForbiddenException`.

**Conventions that emerged:**
- **Test classes MUST use the `*Test` suffix** (not `*IT`) so Surefire runs them under
  `./mvnw test`. `*IT` is silently skipped (Failsafe's domain) and produces false-green builds.
- **For principal-based auth in MockMvc tests, use the `asUser(...)` helper**
  (`SecurityMockMvcRequestPostProcessors.authentication(...)` injecting a real `AuthPrincipal`),
  not `@WithMockUser` (which injects a `String` principal that `@AuthenticationPrincipal
  AuthPrincipal` can't bind).
- **Tests that must observe committed writes** (optimistic-lock races, audit persistence) use
  separate committed transactions via `TransactionTemplate` rather than `@Transactional`
  rollback, which would share one transaction and mask the behavior.
- **The global exception handler's catch-all logs the stack trace** (so unhandled 500s are
  diagnosable) while returning only a generic message to the client.
