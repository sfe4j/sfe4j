# P0: Access Security and Baseline Reliability

Status: implemented (P0-01 to P0-07 with 111 unit tests passing). Dependencies: none. Goal: fix
the access boundaries, output handling, file I/O and error behaviour of the existing features.

Deviations and open items are listed in [section 10](#10-implementation-record).

## 1. Current state and scope

`FileExplorerService.buildFileTree()` performs the base directory check, `readFile()` does not;
directory passwords are only evaluated in `FileExplorerController`. Preview and download can
therefore bypass the listing entry point. The preview template uses `noautoesc`, so file content
enters the page as markup; files are loaded into memory completely and the read helper does not
close its input stream.

This stage adds no user database, no write operations and no raw HTML rendering. The existing
directory passwords stay as a simple protection mechanism.

## 2. Component design

The types below live in the `access`, `service` and `exception` packages of `core`; the names are
proposals:

| Type | Responsibility |
| --- | --- |
| `FileOperation` | `LIST`, `PREVIEW`, `DOWNLOAD`, extended with `FOLLOW` in P1 |
| `FileAccessContext` | Subject identifier and the granted protection rule ids; carries neither the HTTP session nor a plain text password |
| `PathResolver` | Resolves the requested path, the base directory, the real path and the symbolic link policy |
| `FileAccessPolicy` | Returns allow/deny for a path, an operation and an identity |
| `FileAccessService` | The facade that resolves, checks and accesses files; callers must not read around the policy |
| `FileAccessException` | Stable error code and a mappable reason, raw exception messages never reach the user |

The web layer contributes a session adapter, a password submission handler and a shared exception
handler. The public methods of `FileExplorerService` are kept where possible and delegate to the
secure facade; the old `readFile(String)` has no identity parameter, so it runs with an anonymous
context and must refuse protected files. New methods taking a context may be added and the old ones
deprecated, but an unauthorized path must never be kept for compatibility.

The framework independent configuration needs explicit defaults and a validation entry point;
constructing the core services directly must not bypass a check or throw a null pointer because a
Boolean is null.

## 3. Request processing order

1. The web layer validates that parameters exist, their length and their format, and builds the
   access context.
2. Resolve the lexical path and check the protection rules and the lexical base directory
   boundary.
3. Resolve the real path and check the real boundary, the symbolic link policy and the protection
   rules matching the real path.
4. Check this operation with the shared policy and confirm the target type and readability.
5. Return the directory data, bounded text, or open the download stream; a failure while opening
   is mapped to a stable error.
6. Close resources on every exit path; a controller must never rebuild a file object from the raw
   parameters.

Unauthorized requests are rejected before existence is checked where possible, to limit existence
leaks. Where the real path has to be resolved first, the external error still carries no concrete
host path.

## 4. Paths and symbolic links

- The old interface keeps absolute paths; relative paths are resolved against the configured base
  directory, never against the process working directory.
- Compare `Path` segments for directory ownership, a string `startsWith` is not allowed: `/data2`
  is not inside `/data`.
- Normalize `.` and `..`, use `toRealPath()` for an existing target; the configured base directory
  must exist, be a directory and be resolvable, otherwise startup fails.
- New `sfe4j.access.symbolic-link-policy`, initially defaulting to `deny`; `within-root` allows a
  link whose final real target is still inside the base directory. With the restriction enabled,
  neither policy may reach a real target outside the root.
- `deny` checks the intermediate links from the effective root to the target as well as the target
  link itself, not just the final file. The base directory itself must satisfy the selected policy.
- Even without the base directory restriction, directory password rules must be normalized and
  checked against both the lexical path and the real target, so an alias path cannot bypass them.
- Several protection rules each take the longest match on the lexical path and on the real path;
  when they differ, both must be granted. Taking only the longer string of one side is not
  allowed.
- On Windows use the path semantics of the file system provider; never convert backslashes to
  slashes and reuse the result for Linux file system access, because a backslash can be a file
  name character on Linux.
- An invalid configured path or an unresolvable protected directory fails startup; the
  documentation asks for the protected directory to be created first.

### Concurrent replacement risk

A TOCTOU window remains between the path check and opening the file. Prefer
`SecureDirectoryStream` and relative handle opening where the provider supports it; when it is
unavailable, re-check right before opening and document the limitation. `NOFOLLOW_LINKS` only
solves part of the link problem and must not be presented as complete protection against
replacement of an intermediate directory. The deployment model requires that untrusted users
cannot modify the directory structure, or read-only mounts. Tests cover a reproducible race, but a
bounded number of race tests is never proof of absolute safety.

## 5. Directory passwords and session

- Keep the existing POST `dir` and `pwd`, and answer with Post/Redirect/Get after a successful
  check; the redirect target is generated by the server and limited to paths inside the project.
- The session stores the rule id, the rule version and the expiry time, never the password; a grant
  is valid for 30 minutes by default and is dropped when it expires. A configuration change
  invalidates the old rule version.
- Add a POST operation that drops SFE4J directory grants only, without invalidating the whole host
  session.
- Protection covers the directory itself, every child and their preview and download; overlapping
  rules follow the longest match rule above.
- A wrong password returns one shared message and is rate limited per session combined with a
  trusted client address, initially 5 attempts per minute; `X-Forwarded-For` is not trusted. A
  shared rate limit backend for distributed deployments is left to P2.
- Use a bounded rate limit cache with periodic eviction so exhausting memory by creating new
  sessions is not possible. The single instance default must be documented.
- Forms use a CSRF token; the host CSRF mechanism is preferred when present, a session token is
  used in standalone mode. A session that does not exist before granting must not let the check be
  bypassed; change the session id when verification succeeds.
- The first stage stays compatible with the configured plain text password but never logs or echoes
  it; hash based verification is a P2 extension and Spring Security is not introduced into `core`.

## 6. Text preview and download

### Text preview

Drop `noautoesc` and render the content in an auto escaped `<pre>`. The response carries
`X-Content-Type-Options: nosniff` and file content is never interpreted as script, style or
template. Binary detection, encoding selection and line numbers are completed in P1; P0 at least
refuses obviously binary content and states that this detection is heuristic.

New `sfe4j.preview.max-bytes=1048576` (1 MiB): read at most the limit plus one byte to detect
truncation and tell the user explicitly that the content was truncated. Avoid showing broken
multi byte UTF-8 characters without explanation; handle decoder boundaries and report decoding
errors. UTF-8 is the default, other encodings become selectable in P1.

### Download

Use a streaming response or a `Resource` transfer supported by Spring with a fixed size buffer,
never `readFile()` to obtain a complete byte array. The resource is closed by the lifecycle that
actually writes the response; it must not be closed before the response is returned.

Build the attachment header with the framework `ContentDisposition` and handle UTF-8 file names
correctly; CR/LF must never reach a response header. Only regular files are supported; named
pipes, devices and directories are refused. Close the file stream promptly after a client
interrupts; a failure after the response was committed can only be logged as a failed transfer, it
cannot answer with a JSON error any more.

Range is not implemented in this stage. Very large transfers are bounded by the concurrency limit,
initially `sfe4j.download.max-concurrent=8`; the thread pool and the waiting queue must be bounded
and answer 429 when exhausted instead of queueing without limit in the default Servlet thread pool.

## 7. Error contract and page behaviour

| Condition | HTTP | error code |
| --- | --- | --- |
| Missing parameter, invalid path or encoding | 400 | `INVALID_REQUEST` |
| Directory grant missing | 403 | `DIRECTORY_AUTH_REQUIRED` |
| Out of range, denied by policy, not readable | 403 | `ACCESS_DENIED` |
| Target does not exist although access was granted | 404 | `FILE_NOT_FOUND` |
| Preview type not supported | 415 | `UNSUPPORTED_PREVIEW` |
| Request or transfer concurrency exhausted | 429 | `RATE_LIMITED` |
| Unexpected I/O error | 500 | `FILE_IO_ERROR` |

The old HTML routes serve an error page with the right status; the password challenge of the
directory listing keeps showing a form, while an unauthorized preview or download is refused with a
safe link back to the authorization page. JSON endpoints use `{code, message, requestId}` and never
expose `exception.toString` as the message. A missing target only answers 404 after the
authorization preconditions were actually evaluated.

Path parameters go through a shared URL builder and encoder, templates must not concatenate raw
paths. Breadcrumbs accumulate segment by segment, which fixes the repeated directory name bug; all
links keep the host context path.

## 8. Configuration list

| New property | Default | Validation |
| --- | --- | --- |
| `access.symbolic-link-policy` | `deny` | `deny` or `within-root` |
| `access.grant-ttl-seconds` | 1800 | 60-86400 |
| `access.password-attempts-per-minute` | 5 | 1-60 |
| `preview.max-bytes` | 1048576 | 1024-16777216 |
| `download.max-concurrent` | 8 | 1-128 |

The `sfe4j.` prefix is omitted in the table. Transfer timeouts and the bounded executor
configuration are decided in the download task and documented together with the behaviour towards
slow clients.

## 9. Acceptance and rollback

Coverage must include: out of range access on the three entry points, password bypass, nested
rules, path aliases, the root directory, directories with a shared prefix, links, missing or
unreadable targets, non ASCII and URL special characters, CR/LF file names, HTML scripts, preview
truncation, download interruption and resource release after repeated requests. Windows and POSIX
are verified separately, a drive letter string simulated on Linux is not a Windows test.

Benchmark: download a file of at least 1 GiB, a single transfer must not allocate a byte array of
the file size; compare heap usage when idle and while transferring and record the buffer and
framework overhead. File descriptors must not keep growing after 100 interrupted downloads.
Permission tests run under a real restricted identity, an assertion about unreadable files under an
administrator identity is skipped explicitly with a note.

Rollback happens by releasing a version; there is no switch to turn the shared authorization off or
to bring back raw HTML output. When a compatibility problem appears, record the failing case first
and fix the adapter layer.

## 10. Implementation record

The three old routes and their parameter names were kept, the work landed in this order:

1. core gained an `access` package: `FileOperation`, `FileAccessContext`, `PathResolver`,
   `FileAccessPolicy`, `FileAccessService`, `DirectoryProtectionRegistry`,
   `DirectoryProtectionRule`, `ResolvedPath`, plus `TextFileReader` and
   `FileAccessErrorCode`/`FileAccessException`.
2. `FileExplorerService` delegates through `FileAccessService`; `readFile(String)` keeps its
   signature and runs with an anonymous context, protected targets must be refused.
3. The web layer gained `access` and `web` packages: session grant store, password rate limiting,
   CSRF, directory authorization service, download concurrency limit, shared exception handler and
   URL codec.
4. The preview template dropped `noautoesc` in favour of an auto escaped `<pre>`; `file-error.ftlh`
   was added as the shared error page; listing links are encoded in one place and breadcrumbs
   accumulate per segment.

### Deviations from the design

- **Base directory existence**: existence, directory type and resolvability are validated at
  startup regardless of `restrict-to-base-dir`; a violation fails startup. The design said a
  configuration error fails startup when the restriction is enabled, the implementation is stricter
  because relative path resolution always depends on the base directory.
- **Blank base directory**: a blank `base-dir-path` counts as unset and falls back to `/`, it is
  not treated as a configuration error; range and existence errors still fail startup.
- **Concurrent downloads**: concurrency uses a bounded semaphore that answers 429 when exhausted,
  no dedicated thread pool was introduced; queue waiting still happens in the container Servlet
  thread pool, so slow client behaviour has to be re-evaluated in the integration test stage.
- **Download response**: uses an `InputStreamResource` instead of opening a `Resource` handle; a
  failure to open is mapped to `FILE_IO_ERROR`.
- **Secure directory handles**: `SecureDirectoryStream` is not used, the TOCTOU window between the
  path check and opening remains, and the deployment prerequisites (untrusted users cannot modify
  the directory structure, or read-only mounts) are unchanged.

### Not executed

- Integration tests: real HTTP download, file descriptor and handle release after a client
  interrupt, and the 1 GiB file heap benchmark.
- Windows CI path and symbolic link tests; the tests running on POSIX all live in temporary
  directories and cover neither drive letters nor backslash file names.
- Permission tests were not run under a real restricted identity.
- The waiting queue and transfer timeout configuration of the bounded executor are not in place.
