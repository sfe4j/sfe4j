# P2: Host Integration, Multiple Directories and Browsing at Scale

Status: design to be implemented. Depends on P0; the integration acceptance for live logs also
depends on P1. The tasks inside P2 ship in batches as described in the development plan.

## 1. Auto configuration and routing

Add `sfe4j.enabled=true`. When it is off, no controllers, default file service beans or background
executors are created; property binding and validation must not raise unrelated startup errors for
a feature that is disabled.

Default beans are registered conditionally, so the host can replace the authorization policy, the
root provider, the file access facade and the audit sink. core interfaces do not expose Spring
types; the starter owns the default implementations and their composition, and broad component
scanning that registers a component although its conditional bean was skipped is forbidden.

Add `sfe4j.web.base-path=/sfe4j` as the shared prefix for pages, APIs and static application
resources. The new API base is `${base-path}/api`. The old `/file-explorer`, `/file-viewer` and
`/file-downloader` routes stay as aliases of the same implementation through
`web.legacy-routes-enabled=true` and are removed in a later major release.

The base path must be a normalized absolute path inside the application, without a protocol, a
query string, `..` or a reserved root path. Check the Servlet context path and the base path
combination separately so the prefix is not duplicated.

## 2. Multiple root directory model

Proposed configuration:

```yaml
sfe4j:
  access:
    symbolic-link-policy: deny
  roots:
    - id: logs
      label: Application logs
      path: /srv/app/logs
      operations: [LIST, PREVIEW, DOWNLOAD, FOLLOW]
    - id: exports
      label: Exported files
      path: /srv/app/exports
      operations: [LIST, DOWNLOAD]
```

The sample is only a proposal, it is not implemented. A non-empty `roots` list switches to multi
root mode, where every root always restricts access to its own scope and cannot be relaxed by the
old `restrict-to-base-dir=false`. Root ids are unique and limited to `[a-zA-Z0-9_-]{1,64}`; an
invalid operation name, a duplicate root or an unreadable path fails startup.

The new API takes `root` and a relative `path`, an empty path means the root directory;
`/roots` only returns the id, label and operations visible to the current identity and never a
physical path. Directory entries only return the logical path and the file name.

When `roots` is not configured, the old base directory configuration is mapped to a `default` root
while the old restriction flag keeps its meaning; the old mode that allows access outside the root
is only reachable through the old routes and cannot escape the `default` root through the new
relative path API. The page states the scope of that deployment mode.

In multi root mode an old absolute path must map to an explicitly allowed root; overlapping roots
use the root named in the request, an old request maps to the most specific root and every
applicable protection rule is checked, so a root with wider permissions cannot be picked to bypass
them. Where rules become complex, overlapping roots should be forbidden, and the first version
fails startup on overlapping roots.

The old `folder-passwords` stay as global protection rules. New root level authorization and the
old password condition combine with AND, they do not replace each other.

## 3. Identity, authorization and hashed passwords

Provide a core `FileAuthorizationProvider` that takes the subject, the root, the relative path and
the operation and returns allow or deny with an internal reason code. The default policy denies any
action the root operation set does not allow, then combines directory passwords and the host
policy; any policy denying means deny.

The Spring Security adapter lives in an optional integration layer and is only assembled when the
dependency is present and explicitly enabled, it never affects hosts that do not use Security. The
adapter extracts identity and roles from the authenticated principal, does not build its own login
table and does not parse arbitrary client identity headers. When host authentication is enabled but
no authentication adapter is found, startup fails.

The host owns login, session, CSRF and logout; the component offers extension points and does not
install a default `SecurityFilterChain` that would cover every host route. The final API and tests
follow what the repository dependencies actually compile against.

Directory passwords gain a hash format with an algorithm marker and a replaceable verifier; the
old plain text value stays a compatibility format and is documented with a migration note. A failed
hash check must not fall back to plain text, and the submitted password never reaches an audit
event.

## 4. Audit events and persistence boundaries

Add `FileAuditEvent` with eventId, requestId, time, subject id, rootId, logical path, operation,
outcome, reasonCode, durationMs and bytesTransferred. By default no content, password, session id,
token or server physical path is recorded; a logical file name can be sensitive as well, so sinks
should support redaction.

Directory listing and preview record the operation outcome; downloads distinguish grant approved,
transfer completed, client cancelled and server failure, and a returned `ResponseEntity` alone is
not a successful download. Log following only records start, end and a summary, never every line.

The default sink writes structured application logs and the deployment log system persists them;
no database is added. An audit sink interface lets the host plug in a database or a message system.
The queue of an asynchronous sink must be bounded, count and warn when full; by default an audit
failure does not block reading, deployments that need strong auditing can enable fail-closed, which
must confirm acceptance before the response starts, and the semantics are documented.

Directory grants still live in the session and log cursors are short lived state; file content and
passwords are never persisted. Multiple instances need shared sessions, one token key and a shared
rate limit implementation; configuring sticky sessions alone is not cross instance consistency.

## 5. Directory paging and search

`GET /api/directories?root=logs&path=&page=0&size=100&sort=name&direction=asc&q=error`.

Size defaults to 100 and is capped at 500; page is a non-negative integer, `q` is at most 128
characters and is a plain substring filter on the file names of the current directory. Sort is
limited to name, size and modified, directories come first, then a stable comparison on the chosen
field and the relative path; arbitrary property names or expressions are not accepted.

The response carries items, page, size, total, scannedAt and truncated. Permission bits, size and
modification time keep their raw values for sorting, formatting happens in the UI only. When a file
is not accessible, return the entry state or skip it according to the contract and report the
count, a single entry must not abort the whole directory scan.

### Cost and consistency

The first version scans at most 50000 entries and pages after one scan, filter and sort pass; this
shrinks the response body and the DOM but does not turn file system scanning into O(pageSize).
Exceeding the scan cap returns an explicit `DIRECTORY_LIMIT_EXCEEDED` asking for a smaller
directory, a partial result is never presented as a complete directory.

At most 4 directory scans run concurrently, unbounded requests must not pile up. Nothing is cached
by default; when caching is added later it is isolated by root, path, subject and policy version,
has a capacity and TTL and never reuses an authorization result across identities.

A directory may change between two pages, so the first version is weakly consistent: the response
carries scannedAt and the page suggests a refresh. A stable snapshot needs its own task with
bounded snapshot storage and a cursor, the first version does not pretend to be consistent paging.

Recursive search is not implemented. When it is added it needs a maximum depth, entry cap, time
budget, cancellation and authorization per target.

## 6. Operations and interface

- Stay read-only, add per root operation switches for preview, download and follow; a disabled
  feature is refused by the backend as well, not just hidden in the UI.
- Remove the hard coded analytics script; optional statistics are injected by the host and the
  intranet mode does not depend on a CDN.
- Ship localisation and keyboard accessibility first, then a dark theme. The theme does not affect
  the secure template constraints.
- Update the Docker documentation to the current Java 25 and show a non-root user, a read-only data
  volume and a separate writable log directory.
- The external Boot Admin address and the management exposure of the demo stay in the explicit demo
  configuration and are not general production defaults.
- Expose active transfer/follow connections, denial counts and queue overflow metrics without
  sensitive path labels. A missing optional monitoring dependency does not break startup.

## 7. Acceptance

Conditional assembly tests cover the switch being off, a missing optional dependency, host
overridden beans, a custom prefix and a context path. Authorization tests cover anonymous, logged
in without permission, allowed role, denied by the root operation set, unauthorized directory
password and policy exceptions; an exception must fail closed.

Multi root tests cover invalid ids, overlapping roots, cross root paths, old parameter
compatibility and physical path leaks. Audit tests check failure and interruption events, sensitive
data redaction, the bounded queue and fail-closed behaviour. Paging measures latency and memory for
directories with 100, 10000 and 50000 entries, confirms the request limits and returns the agreed
weakly consistent result while the directory changes.
