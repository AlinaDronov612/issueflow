# prompts.md — AI Interaction Log (IssueFlow)

This document records how AI agents were used to build IssueFlow, the main prompts and
decision points, and the human review applied throughout. The guiding principle was that the
AI generates and I direct, review, and remain accountable for every change.

## Models & tools used

- **Primary coding agent:** Claude Code (Anthropic), running **Claude Opus 4.8**.
- **Review & planning assistant:** Claude (Opus 4.8) in a chat interface — helped with planning
  the build, reviewing each phase, discussing design questions, and validating decisions that I
  ultimately reviewed and approved.
- **Dedicated code-review agent:** I created a custom Claude Code **sub-agent**
  (`.claude/agents/code-reviewer.md`, invoked via `.claude/commands/review.md`) and ran it in a
  separate session to review the finished codebase with fresh eyes.

## Instruction & skill files in this submission

- **`CLAUDE.md`** — the project instruction file Claude Code reads on every session. It defines
  the tech stack, package/layering conventions, the business rules, the API-contract-vs-PDF
  precedence, the per-feature definition of done, and (Section 13) the binding decisions made
  during implementation so a fresh session stays consistent.
- **`.claude/agents/code-reviewer.md`** and **`.claude/commands/review.md`** — the custom
  review sub-agent and command used for the final code review.
- **Claude Code skills used:** Plan Mode (read-only design/planning passes before writing code)
  and the product/documentation skills for verifying tool and library details. The core
  workflow leaned on `CLAUDE.md` as the always-on instruction file plus Plan Mode for
  structural changes.

## Workflow

1. Authored `CLAUDE.md` up front, grounded in the actual project skeleton.
2. Used **Plan Mode** for the initial design pass — entity model, build order, open questions —
   before any code was written.
3. Built one feature/phase at a time: plan → review → approve → implement → run tests →
   verify behavior against the live API → review → next.
4. Verified business rules against the running app (live HTTP calls), not just unit tests.
5. Ran a final independent code review using a separate sub-agent session, reviewed and triaged
   every finding myself, and fixed the issues that I confirmed were real before submission.

---

## Representative prompts

**Initial planning (Plan Mode, read-only):**
> Read CLAUDE.md, then read README.md and the requirements PDF. Don't write any code yet. Give
> me an implementation plan: the full entity model with fields and enums, the order you'd build
> features in, and any ambiguities or conflicts between the README (API contract) and the PDF
> (business behavior) that you want me to clarify. Tell me which model you are.

*This surfaced 10 ambiguities, including two real blockers (missing JWT dependency; the
undefined "developer in the project" concept for auto-assignment), resolved before building.*

**A feature prompt (the hardest rules):**
> Implement the Tickets business rules: forward-only status transitions (reject backward with
> 409), a DONE ticket cannot be updated, and optimistic locking returning 409. For the
> optimistic-lock test, write a REAL two-transaction race (load same version twice, commit one,
> commit the second against the stale version, assert 409) — do not fake it with a unit-level
> exception assertion.

**A correction prompt (bug found by live testing):**
> Bug found in live testing: CSV import's per-row error handling can be defeated by a DB-flush
> failure — an over-length title passes bean-validation but fails at flush, marking the
> transaction rollback-only and 500-ing the whole import. Add a length cap so it's caught as a
> clean per-row error, and persist each row in its own REQUIRES_NEW boundary so one bad row
> can't poison the batch.

---

## When the AI got it wrong (failed / adjusted outputs)

AI output was not accepted on faith. Several outputs were wrong, incomplete, or needed
correction, and catching these was a core part of the workflow:

**A. A test that silently never ran.** Claude Code generated a test class named `*IT`. Surefire
(what `./mvnw test` runs) silently skips `*IT` classes — they belong to Failsafe — so the build
reported green while that test never executed. I caught it by checking the *test count* against
what I expected, not just the pass/fail status. Fix: renamed to `*Test` and made it a standing
convention. Takeaway: a green build can hide tests that never ran.

**B. "Live 500s" that were environment artifacts, not the code.** On more than one occasion an
endpoint returned 500 in live testing while all unit tests passed (the audit endpoint, then
dependency creation, then a suspected routing collision). Each time, rather than accept the
AI's or my own first explanation, I reproduced on a clean rebuild — and the real cause was a
**stale JVM still holding port 8080**, serving old code that predated the feature. Durable fixes
that came out of this: the global exception handler now logs stack traces (a swallowed 500 had
been undiagnosable), and I adopted a habit of checking the port and rebuilding before
live-testing. Takeaway: "green tests + a live 500" can mean the running server isn't the code
you think it is.

**C. Identified a flawed debugging instruction and validated it via test.** While investigating one of the 500 errors above, I identified that a suggested debugging hypothesis (route-precedence collision / “MockMvc doesn’t test real routing”) was likely incorrect and not supported by the actual behavior of the system.
Instead of proceeding with a code change based on that assumption, I asked Claude Code to write a targeted test directly against the committed code to validate whether the routing issue actually existed. The test showed that routing behavior was already correct, disproving the original hypothesis.
Fix: no code change was made to application logic; a regression test and minor defensive checks produced during the investigation were retained.
Takeaway: validating assumptions with a concrete test is more reliable than acting on plausible but unverified debugging hypotheses.

---

## Key decisions & interaction moments

*Each: what happened → the decision → why it matters.*

**1. Project-specific `CLAUDE.md` before any code.** Tailored to the real skeleton (verified
Java 21 / Spring Boot 3.4.2, Lombok / validation / Commons-CSV already present); codified
conventions and business rules up front so generated code stayed consistent.

**2. Fixed the source-precedence rule.** After carefully reviewing `CLAUDE.md`, I noticed that
the assumption that "README wins over everything" was incorrect. The PDF contains
business-behavior requirements that are not expressed in the API tables. I therefore changed the
rule to: README = API contract, PDF = business behavior, and any true conflict should be
clarified.

**3. Plan Mode for design.** The first task was read-only: entity model, build order, and an
open-questions list — no code. Caught design problems while they were free to fix.

**4. Identified two blockers during plan review.** After reviewing the implementation plan
generated by Claude, I noticed two issues that needed to be resolved before development could
begin. First, JWT/Spring Security dependencies were missing from `pom.xml`, making the
authentication requirements impossible to implement as specified. Second, the concept of a
"DEVELOPER in the project" was undefined because the system had no project-membership model. I
analyzed the requirements and decided that all users with the DEVELOPER role would be eligible
for automatic assignment, with workload calculated per project. Resolving these ambiguities up
front prevented incorrect assumptions from being built into the system.

**5. Verified the plan's spec claims against the source.** I manually verified the agent's
interpretation against the original requirements, confirming enum values, the `/auth/me`
response shape, and that ticket `type` is immutable rather than relying solely on the AI's
reading.

**6. The silent test-skip (`*IT` vs `*Test`).** During review of the test execution results, I
noticed that a test class named `*IT` was not being executed by Surefire despite the build
succeeding. Renamed and adopted `*Test` as a convention. Lesson: a green build can hide tests
that never executed — check the test count, not just pass/fail.

**7. Security-conscious choices, reviewed and kept.** No user enumeration (same 401 for
unknown-user and bad-password); JWT secret via env var with a dev default; password hash never
returned. Understood and retained, not rubber-stamped.

**8. Verified auth on live traffic.** I verified the authentication flow against the running
application by testing protected endpoints with and without valid JWTs.

**9. Bootstrap gap found by running the app.** While testing the running application, I
discovered a bootstrap problem: every endpoint except login required authentication, but no
initial user existed to obtain a token. Decided to seed a single BCrypt-hashed ADMIN, keep all
endpoints protected, and document the credentials.

**10. Pushed back on an over-general instruction.** A prompt said to "set caller identity from
the principal" across features; for Tickets that was wrong (no caller field in the contract).
While reviewing the generated implementation approach, I determined that applying this
instruction to the Tickets feature would violate the API contract and chose not to implement it.

**11. Comment edit permissions — settled by the spec.** Open question: author-only edits? The
requirement "two users can't edit simultaneously (Admin/Developer)" is a concurrency guard and
implies cross-role edit access. Decided to leave editing unrestricted beyond optimistic locking
rather than invent a rule.

**12. The optimistic-lock test as a real race.** Required (and verified) a true two-transaction
version conflict via `TransactionTemplate`, not a faked exception — deterministic rather than
thread-timing-flaky, but a genuine stale-version commit.

**13. CSV import transaction safety.** The naive per-row `createTicket` call would let one bad
row mark the whole transaction rollback-only, discarding rows already reported as created. The
fix extracted a non-proxied persist method and validated before save, later hardened with
per-row `REQUIRES_NEW` isolation plus a title length cap — so "bad row reported, not fatal"
actually holds.

**14. Validated a suspected bug before fixing it.** While investigating a reported routing
issue, I reviewed the implementation and asked Claude to generate a focused test against the
committed code. The test showed the routing behaved correctly, allowing me to rule out the
suspected cause and avoid an unnecessary code change. I then added a regression test and
defensive guard to strengthen the implementation.

**15. Final independent review, triaged.** After running my review sub-agent, I examined each
finding individually, confirmed which issues were legitimate, and fixed the confirmed defects
while rejecting findings inconsistent with the requirements. The real issues fixed were comments
leaking soft-deleted tickets and the CSV flush vulnerability; I confirmed a flagged
authorization concern against the PDF (user management is not spec-restricted to ADMIN) and
deliberately left it rather than gold-plate, and cleaned up dead imports and stray files.

---

## Environment issues resolved
- Local machine had JDK 11 (PATH and `JAVA_HOME`); installed Temurin 21 and set `JAVA_HOME`.
- Two local PostgreSQL services occupied port 5432 and shadowed the Docker DB; stopped them.
- Recurring stale JVM on port 8080 after `Ctrl+C`; adopted a port-check + clean-rebuild habit.
