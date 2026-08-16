# AI Usage Disclosure

## Purpose

This solution was developed with the support of OpenAI Codex through the
Codex desktop application.

The process was candidate-led. Each area started from a technical hypothesis
proposed by the candidate. AI was used to pressure-test those hypotheses,
identify missing edge cases, compare trade-offs, and assist with
implementation, testing, debugging, documentation, and presentation design.

The original case document remained the primary source of truth. The objective
was to satisfy its explicit and implicit requirements while introducing as few
assumptions as possible. When an assumption was necessary, it was bounded,
documented, and connected to a condition under which the decision should be
revisited.

This document focuses on engineering reasoning, AI-assisted use cases, and
resulting decisions. Conversation transcripts, verbatim prompts, and
screenshots are intentionally excluded.

## 1. Solution scope and architecture

### Candidate hypothesis

The current problem can be solved reliably through one Spring Boot deployable
with hexagonal boundaries and PostgreSQL as the durable state store and
worker-coordination mechanism.

Introducing a message broker, separate worker service, or additional
distributed infrastructure would increase operational complexity without being
required by the current use case. These components should be considered only
after the existing approach demonstrates a measurable limitation.

### Trade-offs and edge cases explored

AI supported the evaluation of:

- PostgreSQL versus an in-memory database.
- Database-backed coordination versus a message broker.
- One deployable versus independently scalable API and worker services.
- JDBC versus a higher-level persistence abstraction.
- Sequential processing inside a claimed batch.
- Database contention under higher throughput.
- Multiple application instances claiming work concurrently.
- The conditions that would justify architectural evolution.

### Final decision

The implemented V1 uses:

- Java 21 and Spring Boot 3.5.
- Gradle with Groovy.
- PostgreSQL and Flyway.
- Spring JDBC with named parameters.
- Hexagonal package boundaries.
- One deployable containing the API, importer, and configurable worker.
- PostgreSQL row locking and leases for multi-instance coordination.

The trade-off is that API and worker share deployment and scaling boundaries,
while polling and claims consume database capacity. The design should evolve
when throughput, contention, or independent scaling requirements make those
constraints measurable.

### Validation

The architecture was validated through PostgreSQL integration tests,
multi-worker claim behavior, lease recovery tests, code inspection, and
comparison against the case requirements.

## 2. Reliable delivery and concurrency

### Candidate hypothesis

Reliable webhook delivery requires explicit and durable state transitions.
Non-final outcomes should be retried through a configurable policy, while
definitive failures should remain available for controlled replay.

Because an HTTP timeout cannot prove whether the client processed the request,
the appropriate guarantee is at-least-once delivery rather than exactly-once
delivery.

### Trade-offs and edge cases explored

AI helped pressure-test:

- A timeout after the client processes the webhook.
- A worker crash after sending but before persisting the result.
- Retry exhaustion.
- Concurrent workers claiming due events.
- Expired leases and late worker results.
- Concurrent lease recovery.
- Concurrent replay requests.
- Missing or ambiguous subscription configuration.
- TLS validation failures.
- Client-side deduplication.

### Final decision

The delivery lifecycle uses the following states:

- `PENDING`
- `PROCESSING`
- `RETRY_SCHEDULED`
- `COMPLETED`
- `FAILED`

Timeouts, connection failures, `408`, `429`, and `5xx` responses are
retryable. TLS validation failures and other permanent outcomes move the
delivery to `FAILED`.

Each attempt is stored durably with its delivery cycle, attempt number,
timestamps, result, latency, HTTP status, or bounded failure category. Replay
is accepted only from `FAILED` and creates a new delivery cycle.

The trade-off is that a timeout or worker crash may produce a duplicate
delivery. Clients are expected to deduplicate using the notification event
identifier.

### Validation

The lifecycle was validated through unit tests, PostgreSQL integration tests,
mutation testing, and explicit scenarios for retry exhaustion, lease recovery,
concurrent claims, and replay conflicts.

## 3. Self-service API and tenant security

### Candidate hypothesis

Tenant ownership must be derived from the authenticated principal and enforced
in every persistence operation. A client-provided identifier must never be
trusted to establish the tenant boundary.

Cross-tenant resources should return `404` rather than `403` to avoid
confirming whether another client's event exists.

### Trade-offs and edge cases explored

AI assisted with evaluating:

- Unknown events versus events owned by another client.
- Cross-tenant reads and replay attempts.
- Replay of a non-failed event.
- Concurrent replay requests.
- Invalid creation-date ranges.
- Oversized page requests.
- Offset pagination at increasing depth.
- A future administrator role.
- Static credentials in a publicly exposed API.
- SQL injection and dependency supply-chain risks.

### Final decision

The API exposes:

- `GET /notification_events`
- `GET /notification_events/{notification_event_id}`
- `POST /notification_events/{notification_event_id}/replay`

Client identity comes from a role-scoped opaque bearer token. Repository
queries always include the principal-derived `client_id`.

The list endpoint supports bounded filtering by creation date and delivery
status. Offset pagination is limited to a maximum page size of 100.

Opaque tokens are appropriate for the local technical case but not the
intended production identity solution. Public exposure would additionally
require managed credential lifecycle, TLS at ingress, rate limiting, and
protected operational endpoints.

### Validation

Tenant isolation was validated through controller, security, and PostgreSQL
integration tests covering successful access, unknown events, cross-tenant
identifiers, and replay conflicts.

The public API risks were also evaluated against the OWASP Top 10, with
implemented controls and residual exposure documented separately.

## 4. Observability and operational response

### Candidate hypothesis

A global dashboard is insufficient for responding to client complaints.
Monitoring must help identify the affected client and event type while avoiding
unbounded metric cardinality.

Event identifiers, payloads, and destinations should not become metric labels.
Event-level diagnosis should use tenant-scoped persisted investigation data.

### Trade-offs and edge cases explored

AI supported the analysis of:

- One client failing while global success remains acceptable.
- A growing backlog while the application still appears healthy.
- A worker that is enabled but no longer progressing.
- High-cardinality metric labels.
- Database-pool and HTTP-thread saturation.
- Alert grouping and deduplication.
- Alert firing and recovery.
- Failure of the application versus failure of the monitoring stack.
- Moving from a complaint to a specific operational cause.

### Final decision

The observability model separates:

- Notification-delivery operations.
- Platform runtime health.
- Client and event-type behavior.
- Event-level persisted investigation.

Prometheus collects the application metrics. Alertmanager handles alert
grouping, routing, deduplication, and recovery. Grafana remains outside the
application and provides the operational dashboards.

The diagnostic flow allows an operator to move through:

1. Active alert.
2. Affected client.
3. Event type.
4. Specific event.
5. Attempt timeline.
6. Failure category.

The trade-off is that client and event-type labels remain safe only while their
cardinality is controlled.

### Validation

The dashboards were validated using temporary synthetic traffic. An alert was
triggered through the real application, Prometheus, and Alertmanager flow,
delivered as a native macOS notification, and followed by a recovery
notification when the condition cleared.

## 5. Engineering confidence and solution communication

### Candidate hypothesis

Quality tooling should verify distinct failure classes and execute
automatically in CI. It should provide meaningful confidence rather than exist
only as unused local configuration.

The final proposal should communicate the implemented V1 and its reasoning
within approximately ten minutes, without presenting speculative evolution as
current functionality.

### Trade-offs and edge cases explored

AI assisted with:

- Automatic formatting.
- Line and branch coverage.
- Mutation testing.
- Static analysis.
- Dependency vulnerability scanning.
- Unit versus PostgreSQL integration tests.
- CI execution time.
- Gradle and IDE compatibility.
- Presentation information density.
- Default interactive states.
- Desktop layout and accessibility feedback.
- Public repository and deployment accessibility.

### Final decision

The engineering quality gate includes:

- Spotless.
- Unit tests.
- PostgreSQL integration tests.
- JaCoCo.
- SpotBugs.
- PITest.
- OWASP Dependency-Check.
- GitHub Actions.

The current verified results are:

- 95.1% line coverage.
- 74.8% branch coverage.
- 73% mutation coverage.
- Zero active known runtime vulnerabilities.
- Successful build, integration, and security workflows.

The solution proposal was implemented as a static interactive site and
deployed through Vercel. It presents the current architecture, lifecycle,
failure behavior, tenant isolation, operational diagnosis, security assessment,
and engineering decisions.

The trade-off is that the presentation intentionally omits some implementation
detail and future evolution triggers to preserve a focused ten-minute
narrative. Supporting technical documents remain available through the
repository.

### Validation

The quality gate was executed both locally and through GitHub Actions. The
public proposal was tested through its production Vercel URL, including
navigation, interactive states, accessibility attributes, external links,
favicon, console health, and desktop layout.

## Human accountability

AI assisted with analysis, implementation, test creation, debugging,
documentation, and presentation development. The candidate retained
responsibility for:

- Requirements interpretation.
- Architecture and scope decisions.
- Trade-off evaluation.
- Acceptance or rejection of proposed alternatives.
- Assumption management.
- Code and behavior review.
- Validation strategy.
- Repository history.
- Final technical explanation.

Repository-changing operations followed an explicit review and authorization
workflow. Commits and pushes were performed after the corresponding increment
had been discussed or inspected.

The submitted implementation, documentation, and technical reasoning remain
the responsibility of the candidate.

## Limitations and controls

AI assistance can introduce incorrect assumptions, unnecessary complexity, or
implementation details that do not match the required contract.

These risks were controlled by:

- Returning continuously to the original case document.
- Treating external documentation as supporting context rather than
  contractual evidence.
- Minimizing and documenting assumptions.
- Discussing trade-offs before expanding scope.
- Comparing claims against the implemented code.
- Running automated and manual validation.
- Rejecting or simplifying suggestions that did not provide sufficient value.

AI accelerated the engineering process, but it did not replace candidate
ownership, technical judgment, or validation.
