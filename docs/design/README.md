# SFE4J Feature Evolution Design Index

Date: 2026-10-26. Status: P0 and P1 are implemented (see
[section 10 of the P0 design](p0-security-reliability.md#10-implementation-record) and the
[P1 acceptance record](../development/reports/p1.md)); P2 is still a design to be implemented, so
the class names, interfaces and configuration in that file are proposals and, unless a file
explicitly describes the current state, are not implemented.

Goal: turn the existing file explorer into a safe, lightweight, read-only file and log tool that
fits into a Spring Boot application or runs as a sidecar. The current analysis is based on the
source, no complete runtime or security verification was done.

## Reading order

1. [P0: Access Security and Baseline Reliability](p0-security-reliability.md)
2. [P1: Text Preview and Log Troubleshooting](p1-preview-logs.md)
3. [P2: Integration Capability and Browsing at Scale](p2-integration-scale.md)
4. [Development Task, Test and Delivery Plan](../development/implementation-plan.md)

For the current state see the [project guide](../ai/project-context.md), development follows
[AGENTS.md](../../AGENTS.md) in the repository root.

## Priority and scope

| Stage | User benefit | Release gate |
| --- | --- | --- |
| P0 | Listing, preview and download obey the same permissions; exceptions and large files are handled predictably | Implemented: every entry point bypass test passes, stream closing, escaping and error behaviour have regression coverage; integration tests and performance benchmarks were not executed |
| P1 | Read configuration and large logs quickly, follow log changes | Implemented: bounded head/tail reads with four encodings, a follow stream with quotas, reset/gap handling and a signed cursor; the 20 connections / 30 minutes benchmark and the 1 GiB tail measurement were not executed |
| P2 | Plug into host authentication, serve several directories, audit access, page through large directories | Auto configuration is replaceable, authorization fails closed, paging cost is documented |

This round only covers design and development documents. The implementation stage does not
introduce a database, an account system, recursive full text indexing, upload, edit, delete or
command execution by default.

## Cross stage conventions

- `core` does not depend on Spring, Servlet or the HTTP session; the web layer turns the request
  identity into a framework independent access context.
- Every entry point goes through the same file access facade; metadata, opening a file and
  registering a follow task only happen after authorization succeeded.
- A configuration change must cover property binding, the core configuration, the mapping, the
  documentation, the samples and the default value tests.
- Every new numeric option is a positive number with an explicit lower and upper bound;
  unsupported or conflicting configuration fails startup instead of degrading silently.
- The three existing routes and their parameters are kept; new JSON APIs live under the default
  `/sfe4j/api`. Route prefix migration is described in the P2 design.
- New interfaces return stable error codes and a requestId, and never leak absolute server paths,
  stack traces, passwords or file content.
- Atomic authorization on a locally writable file system is not promised; environments with a
  malicious local writer need read-only mounts or isolation, see P0.
- The numbers below are initial engineering defaults and acceptance targets, they have to be
  confirmed by a benchmark in the target environment and are not measured performance.

## Compatibility strategy

P0 keeps the default values of `base-dir-path` and `restrict-to-base-dir` so existing host
configuration is not changed silently, while production is explicitly advised to enable the
restriction. New standalone deployment samples use a dedicated read-only directory together with
the restriction flag; `/` is not allowed as a production sample.

The following are intentional security behaviour changes: the preview is plain text by default,
out of range access no longer falls back to the base directory, missing parameters and invalid
paths return an explicit error, and preview and download require the directory grant as well.
Release notes must list these changes.

P1 and P2 prefer adding configuration and APIs. Changes to path representation, default routes
and old configuration go through a compatibility adapter, no controller implements two
authorization paths.

## Deliverables and document maintenance

Every development task ships the implementation, the necessary tests, the configuration
documentation and the acceptance evidence. Split pull requests along the dependency order and
never expose a new entry point before its authorization exists. After finishing a stage, update
the status in this file, the task status in the development plan and the project guide, and state
the version that carries the change together with the remaining issues.
