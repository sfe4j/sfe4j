# SFE4J Development and Acceptance Plan

Status: P0 is implemented (see section 2 and the [P0 acceptance record](reports/p0.md)), P1 and P2
are still to be developed. Task dependencies are merged before the feature entry points they
guard, and a stage is only rolled out after its acceptance.

Design entry point: [design index](../design/README.md).

## 1. Development baseline

- JDK 25, using the version declared in the actual POM; framework upgrades are not part of a
  feature task.
- The root POM only aggregates; the test dependencies, JUnit 5 and the Surefire version of
  core/web/starter have to be configured per module or by a dedicated build task. "No tests
  found" never counts as a pass.
- Build test files in a temporary directory by default. Windows path tests run on Windows CI;
  when symbolic links are unavailable, state explicitly why a test is skipped.
- Keep the behaviour baseline tests first, then add the security regressions; do not write an
  expected unsafe old behaviour as an assertion that should hold forever.
- Without a global Maven, run from the root with
  `(cd sfe4j-core && sh mvnw -f ../pom.xml <goal>)`; a wrapper that prints its version does not
  mean dependency downloads or tests already succeeded.

## 2. P0 work packages

| ID | Content and main landing points | Depends on | Status | Completion criteria |
| --- | --- | --- | --- | --- |
| P0-01 | Test foundation: module POMs, JUnit 5, temporary file fixtures, Windows/POSIX CI | none | done (Windows CI not included) | every module really executes the new tests, report counts can be checked |
| P0-02 | core: configuration defaults and validation, PathResolver, unified exceptions | P0-01 | done | path boundary, link, relative path and invalid configuration tests pass |
| P0-03 | core/web: shared operation authorization, safe delegation of the old service methods, three controllers wired in | P0-02 | done | a direct preview/download request cannot bypass the root restriction or a password |
| P0-04 | web: session grant TTL, rule version, rate limiting, logout, CSRF | P0-03 | done | grant expiry, rule change, session invalidation and rate limiting tests pass |
| P0-05 | core/web: bounded text, resource closing, streaming download and bounded executor | P0-03 | done (integration benchmark not executed) | the heap does not grow linearly with the file, resources are released after an interrupt |
| P0-06 | web: plain text template, unified error page, URL/header encoding, breadcrumbs | P0-04, P0-05 | done | XSS, special characters, repeated directory names and context path tests pass |
| P0-07 | starter/sample/README: configuration mapping, production sample, change notes | P0-06 | done | default and mapping tests are complete, root verify passes |

One to a few pull requests per item are expected; when P0-03 is merged the three entry points must
become effective at the same time, no temporary unprotected old read method may remain. Inside
P0-05, commit the executor, lifecycle and tests first, then switch the controller response.

The executed results, the deviations from the design and the open items are recorded in
[reports/p0.md](reports/p0.md).

## 3. P1 work packages

| ID | Content | Depends on | Completion criteria |
| --- | --- | --- | --- |
| P1-01 | core bounded head/tail, encoding and BOM, decoding across block boundaries | P0-07 | multi encoding, very long line, truncation and binary detection tests pass |
| P1-02 | web text API, safe viewer, line numbers/newlines/partial search | P1-01 | plain text rendering, explicit truncation, keyboard operable |
| P1-03 | follow manager: shared scheduling, quotas, queues, lifecycle | P1-01 | slow client and connection leak tests pass |
| P1-04 | SSE API, cursor, reconnect, rotation, authorization invalidation | P1-03 | reset/gap/append contract and cursor forgery tests pass |
| P1-05 | follow page, pause and resume, DOM cap, configuration and load test report | P1-02, P1-04 | 20 connections / 30 minutes benchmark and interrupt recovery accepted |

Syntax highlighting can follow as P1-06 and does not block P1-05. Pick local resources and a safe
plain text insertion first, then evaluate a highlighting library; do not pull in an external CDN.

## 4. P2 work packages

| ID | Content | Depends on | Completion criteria |
| --- | --- | --- | --- |
| P2-01 | Conditional auto configuration, feature switches, replaceable beans, route prefix and old route aliases | P0-07 | conditional assembly, override and prefix matrix pass |
| P2-02 | Root directory model, relative path API, old configuration adapter, per root operation set | P2-01 | cross root, overlapping root and absolute path leak regressions pass |
| P2-03 | Host authorization SPI, optional Security adapter, password hash extension | P2-02 | runs without the dependency, missing identity or policy errors deny access |
| P2-04 | Audit events, structured log sink, optional fail-closed | P2-03 | transfer completion semantics are exact, the queue is bounded, sensitive values never reach the log |
| P2-05 | Bounded directory scan, server side filter/sort/paging, page switching | P2-02 | 50000 entry cap, stable sorting and the weak consistency contract pass |
| P2-06 | Localization, removal statistics script, deployment samples and optional monitoring | P2-01 | browsable without internet access, sample configuration verified by startup |
| P2-07 | Cross feature integration: FOLLOW combined with roots, identity and audit | P1-05, P2-04 | an invalidated grant stops the subscription, audit aggregation and quota release are correct |

## 5. Test layers and commands

| Layer | What it verifies | Implementation suggestion |
| --- | --- | --- |
| core unit tests | paths, policies, text reading, error classification | JUnit 5, temporary directories, deterministic clock and controllable file fixtures |
| MVC tests | parameters, session, CSRF, error page, headers, encoding, text output | MVC test configuration matching the actual Boot version |
| starter tests | default assembly, property binding, disabling, overrides, optional classpath | conditional context tests, not depending on a full sample startup |
| integration tests | real HTTP download, SSE, cancellation, proxy buffering behaviour | random port allowed, explicit timeouts and cleanup; an interrupted download must not be replaced by MockMvc only |
| browser acceptance | navigation, escaping, encoding, DOM cap, keyboard operation | automated or reproducible steps, keep failure information |
| performance/resources | large files, large directories, follow concurrency, handle release | separate benchmark, never assert absolute timings that depend on the machine in normal unit tests |

Common commands (from the repository root):

```sh
mvn -pl sfe4j-core -am test
mvn -pl sfe4j-spring-boot -am test
mvn -pl sfe4j-spring-boot-starter -am test
mvn verify
```

When integration tests are added later, configure the Failsafe `integration-test`/`verify`
executions explicitly first and agree on the `*IT` naming; until then do not claim that
`mvn verify` already runs every integration test. CI can be split into three groups: full POSIX,
Windows path and configuration tests, and a separate benchmark group; long benchmarks run on
demand or on a schedule and do not block every small documentation pull request.

## 6. Stage acceptance checks

### P0 release gate

- [x] The three original entry points go through the shared authorization facade, no old public
      method offers an unauthorized read path.
- [x] Links, traversal, directory passwords, session expiry, output escaping and download headers
      have regression cases.
- [ ] The memory/resource behaviour of download and preview matches P0, with the real environment
      and measurement written down. (Unit tests cover bounded reads and concurrency 429; the 1 GiB
      heap benchmark and handle release after an interrupt were not executed.)
- [x] The original routes, configuration and a non-root context path still work; intentional
      behaviour changes are in the release notes.
- [x] No environment problem, skipped test or zero test execution was reported as a pass.

### P1 release gate

- [ ] Quotas, back pressure, disconnects, rotation, grant expiry and logout are covered.
- [ ] A cursor cannot be reused across identities; gaps and truncation are visible in the UI.
- [ ] DOM, queues, threads and handles are observable and bounded.

### P2 release gate

- [ ] Default assembly, host overrides, disabling and the optional authentication integration
      combinations pass.
- [ ] Multiple roots, old routes and the old configuration adapter do not form an authorization
      bypass.
- [ ] Audit completion/cancellation semantics, error handling and redaction match the design.
- [ ] Paging documents the real scan cost; exceeding the cap never returns a partial result that
      pretends to be complete.
- [ ] The read-only Docker data mount and the writable local log directory sample were tried.

## 7. Evidence record template

Record the following in the pull request description or in
`docs/development/reports/<task-id>.md`:

```text
Task ID / design section:
Scope and intentional compatibility changes:
Environment (OS / JDK / Maven):
Test command, executed count and result:
Performance scenario, sample size, metrics and measurement:
Not executed, failure reasons and remaining risks:
Where configuration and documentation were updated:
```

Task status follows the code and the acceptance evidence. Do not tick implementation checklists
when only the design is finished, and never trigger a release or deployment automatically.

## 8. Decisions taken during implementation

These choices get a decision record after a small verification inside the relevant task, they do
not block starting P0: support of secure directory handles on the target file system, the resource
lifecycle of streaming responses in the current Boot version, the way host CSRF is adapted,
rotation detection without a fileKey, and whether the optional Security integration becomes its
own module. Conclusions carry the verification result; if a security or compatibility contract
changes, update the design and the related acceptance cases first.
