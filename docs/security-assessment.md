# OWASP risk assessment

## Scope and assumptions

The technical case asks us to consider that the self-service API could be
exposed through the public internet. The current implementation is a local
demonstration, so HTTP is acceptable for local execution; a public deployment
would terminate TLS at a trusted ingress or API gateway.

The ratings below are qualitative and distinguish between controls already in
the repository and measures that would be required before a public deployment.

| OWASP Top 10:2025 risk | Impact | Inherent likelihood | Current residual risk | Status |
| --- | --- | --- | --- | --- |
| A01 Broken Access Control | High | High | Low | Mitigated in the application |
| A05 Injection | High | Medium | Very low | Mitigated in the application |
| A03 Software Supply Chain Failures | High | Medium | Low | Mitigated through build and process controls |
| A07 Authentication Failures | High | Medium | Medium | Appropriate for the local case; production controls proposed |
| A02 Security Misconfiguration | High | Medium | Low locally | Application hardened; production infrastructure controls proposed |

## A01: Broken Access Control

**Threat.** A client could try to read or replay another client's notification
by changing an event identifier. This could expose payment-related data or
cause an unauthorized delivery.

**Implemented controls.** The client identifier is derived from the
authenticated principal, never accepted as a self-service request parameter.
Every read and replay repository operation includes both the event identifier
and authenticated `client_id`. Client and monitoring identities have separate
roles, unknown routes are denied, and a cross-client lookup returns the same
`404` as a missing event to avoid confirming its existence.

Representative controller and repository code:

```java
NotificationEventDetailsQuery query =
        new NotificationEventDetailsQuery(client.clientId(), notificationEventId);
```

```sql
SELECT event_id, event_type, content, created_at, delivery_date, delivery_status
FROM notification_events
WHERE event_id = :notificationEventId
  AND client_id = :clientId
```

**Verification.** Integration tests cover missing authentication, role
separation and cross-client list, detail and replay attempts.

**Public-deployment measures.** Keep authorization server-side, add security
audit events for denied operations, and review tenant-isolation tests whenever
a new endpoint is introduced.

## A05: Injection

**Threat.** Query parameters, path variables or imported event values could be
crafted to alter a SQL statement, disclose data or damage persisted delivery
state.

**Implemented controls.** Persistence adapters use JDBC prepared statements or
`NamedParameterJdbcTemplate`. Values are always bound as parameters. The only
dynamic query fragments are fixed clauses selected by application code; no
request value is concatenated into SQL. Jakarta validation also bounds and
validates application inputs before they reach persistence.

Representative query construction:

```java
StringBuilder sql = new StringBuilder(BASE_QUERY);
MapSqlParameterSource parameters = new MapSqlParameterSource("clientId", query.clientId());

if (query.deliveryStatus() != null) {
    sql.append("AND delivery_status = :deliveryStatus\n");
    parameters.addValue("deliveryStatus", query.deliveryStatus().name());
}
```

**Verification.** Repository integration tests execute the real SQL against
PostgreSQL rather than relying on an in-memory SQL dialect.

**Public-deployment measures.** Preserve parameter binding in all future
adapters, keep database credentials least-privileged, and add request-size
limits at the ingress to reduce abusive payload processing.

## A03: Software Supply Chain Failures

**Threat.** A vulnerable or compromised transitive dependency could introduce
remote code execution, data exposure or denial of service without changing
application code.

**Implemented controls.** OWASP Dependency-Check scans the production runtime
classpath, fails the build for vulnerabilities with CVSS 7.0 or higher and
runs on every push to `main`. Dependency versions were updated after reviewing
the report. The only suppression is documented, limited to an inapplicable
Tomcat example application and has an expiration date so it cannot become a
permanent silent exception.

Representative build policy:

```groovy
dependencyCheck {
    failBuildOnCVSS = 7.0
    failOnError = true
    failBuildOnUnusedSuppressionRule = true
    scanConfigurations = ['runtimeClasspath']
    suppressionFile = 'config/dependency-check-suppressions.xml'
}
```

**Verification.** The GitHub Actions `Dependency security` job currently scans
68 runtime dependencies with no active known vulnerabilities.

**Public-deployment measures.** Review dependency updates and suppression
expiry regularly, protect the main branch, pin and review CI actions, and
produce an SBOM if the service enters a managed release process.

## Residual public-API risks

### A07: Authentication Failures

The local case deliberately uses role-scoped opaque bearer tokens supplied by
environment variables. Tokens are unique, compared with
`MessageDigest.isEqual`, and the API is stateless. This is intentionally
smaller than introducing an identity platform for a local exercise.

```java
return configuredTokens.stream()
        .filter(configured -> MessageDigest.isEqual(configured.token(), candidate))
        .map(ConfiguredToken::principal)
        .findFirst();
```

Before public exposure, TLS must be mandatory and tokens must have sufficient
entropy, managed storage, rotation and revocation. Rate limits should protect
authentication and replay operations. If multiple clients or external
integrations are onboarded dynamically, migrate authentication to an identity
provider using OAuth 2.0 client credentials and short-lived access tokens. An
OAuth access token may be opaque or a JWT; JWT is a token format, not a
replacement for OAuth.

### A02: Security Misconfiguration

The application disables unused form, basic and session-based authentication,
keeps health details hidden, protects metrics and internal investigation with
the monitoring role, and finishes authorization rules with `denyAll`.

```java
.requestMatchers("/internal/monitoring/**").hasRole("MONITORING")
.requestMatchers("/notification_events/**").hasRole("CLIENT")
.anyRequest().denyAll()
```

A public deployment should terminate TLS at a hardened ingress, keep metrics
and internal monitoring on a private route, manage secrets outside the
artifact, apply request and connection limits, and validate environment-specific
configuration during deployment.

## Risk intentionally not selected: SSRF

The public API does not accept or modify webhook URLs. Destinations come from
trusted bootstrap configuration, must be absolute HTTPS URLs without user
information or fragments, and redirects are disabled. SSRF is therefore not
currently reachable through the exposed contract. If subscription onboarding
becomes a public API, the design must add hostname allowlisting, DNS and IP
range validation, revalidation after DNS resolution, and outbound network
policy before accepting client-controlled destinations.
