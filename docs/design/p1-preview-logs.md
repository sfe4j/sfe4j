# P1: Text Viewing and Log Troubleshooting

Status: design to be implemented. Prerequisite: the P0 shared file access facade and its security
regressions are in place. Goal: read large files within limits and follow logs under controlled
resource usage.

## 1. User flow and scope

The user opens a file from the listing and sees bounded text, and can switch encoding, newlines
and line numbers and search the loaded content; log mode reads the last 200 lines by default and
can follow, pause and resume. The page distinguishes explicitly between "search the loaded
content" and "search the whole file"; full text search over an entire file is not part of this
stage.

Plain text configuration files and logs are supported, HTML is not executed, there is no PDF or
Office conversion, and authorization never depends on the file extension. JSON, XML and YAML are
shown as text initially; syntax highlighting is a separate enhancement task and does not block the
release.

## 2. core read design

Add `TextReadService`, `TextReadRequest` and `TextReadResult`; every read goes through a target
that P0 already authorized. The result carries the text, the encoding used, the truncation state
and the next read position; offsets are byte offsets, never character counts.

- UTF-8 is the default, UTF-8, UTF-16LE, UTF-16BE and GB18030 are allowed; other encodings are
  refused for now.
- Detect a BOM and report the effective encoding; when an explicit selection conflicts with the
  BOM, return a clear error instead of silently overriding it.
- Default to the last 200 lines with a cap of 2000 lines; scanning is bounded to 1 MiB, and a very
  long single line is bounded by the byte limit too, with a hint that the line is not shown
  completely.
- For UTF-8 and GB18030, decode forward from a candidate boundary found by scanning backwards, and
  handle newlines across blocks, CRLF and multi byte characters.
- For UTF-16, scan aligned to two bytes and detect newlines in the matching encoding; test
  surrogate pairs, the BOM and block boundaries.
- The default binary detection is a heuristic over the decoded printable content and the ratio of
  decoding errors; it must not rely only on a NUL in the raw bytes, because UTF-16 text contains
  zero bytes as well.
- While a file grows, read up to the length captured in this pass; when a file shrinks, emit a
  reset event instead of using a negative offset.

The first release does not expose a physical file offset cursor supplied by the client. A resume
token must be short lived server side state or an integrity checked token bound to the root, the
relative path, the file generation, the encoding and the subject; it never replaces authorization
on each request.

## 3. HTTP contract

The default API base is `/sfe4j/api`, P2 makes the prefix configurable. The P1 `file` parameter
stays compatible with the old absolute path and is handled by the same resolver.

| Endpoint | Parameters | Returns |
| --- | --- | --- |
| `GET /text` | `file`, `mode=head/tail`, `lines`, `charset` | JSON: text, charset, truncated, cursor |
| `GET /follow` | `file`, `charset`, optional cursor | SSE, first read and later increments |

Authorization comes from the same origin session, the password is never put into a URL. Permission
is checked before an SSE connection is established; the browser side handles 403 and 429
explicitly and must not reconnect forever. Error responses follow the P0 contract.

SSE events:

- `snapshot`: the initial bounded tail text and the cursor.
- `append`: new text, an increasing event id and the cursor.
- `reset`: rotation, truncation or a changed file generation, with a reason, discarding the old
  cursor.
- `gap`: back pressure or an invalid resume cursor, states that data is missing and asks for a new
  snapshot.
- `error`: a stable error code, after which the connection is closed; heartbeats are SSE comments.

Payloads are JSON encoded before being sent over SSE, log lines are never concatenated into SSE
protocol fields directly. The front end only inserts text through safe APIs such as `textContent`.

## 4. Follow lifecycle

The web layer owns `LogFollowManager`, core provides bounded incremental reads. Use a shared
bounded scheduler that checks once per second by default, and never create an unbounded thread per
connection. Every subscription keeps the file identity, the byte position, decoder leftovers, the
permission expiry and an output queue.

- 20 connections per instance and 2 per session by default; overload answers 429.
- At most 256 KiB queued per connection and 64 KiB per increment; when the accumulated overflow is
  reached, send a gap and disconnect instead of silently dropping lines while still claiming the
  output is complete.
- Heartbeat every 15 seconds by default, a connection lives at most 30 minutes; it is closed
  proactively on expiry and may reconnect after a new grant.
- Before every read round, check whether the grant expired, the rule changed and the target still
  satisfies the path policy; an invalidated session must end the subscription.
- Migrate or terminate old connections when the session id rotates, so no unmanageable
  subscription is left behind.
- Unregister the task, close handles and release quotas when the client disconnects, times out,
  the write fails or the application shuts down.
- Prefer `fileKey` to detect rotation of the same path; when the provider has no `fileKey`, combine
  file attributes and send a conservative reset, without promising lossless recovery on every file
  system.
- A copytruncate scenario is detected by a shrinking length; an overwrite with the same length or a
  truncate followed by very fast growth cannot be detected reliably from the length alone, which is
  a documented limitation.

Resuming re-authorizes. When an old cursor falls outside the retention window or the file was
replaced, send gap or reset and read a new snapshot; exactly-once is not claimed.

## 5. Page behaviour

- The text page offers line numbers, newlines, encoding, copying the loaded text, a download button
  and file information.
- "Pause" closes the network subscription and keeps the last cursor; "resume" reconnects and shows a
  gap when the content cannot be completed, a UI pause must never turn into unlimited background
  caching.
- The DOM keeps 5000 lines and at most 2 MiB of text by default; reaching either cap evicts old
  content and notifies the user.
- Auto scrolling can be turned off; the user is not forced back to the bottom after scrolling up.
- Search works on plain text, arbitrary regular expressions are not exposed in the first version so
  a complex expression cannot block the browser.
- Error hints and action buttons are keyboard accessible. New scripts live in their own files,
  third party libraries are not modified.

## 6. Initial configuration

All keys carry the `sfe4j.` prefix: `logs.enabled=false`, `logs.default-lines=200`,
`logs.max-lines=2000`, `logs.max-scan-bytes=1048576`, `logs.poll-interval-ms=1000`,
`logs.max-connections=20`, `logs.max-connections-per-session=2`, `logs.max-connection-seconds=1800`,
`logs.max-queue-bytes=262144`, `logs.max-chunk-bytes=65536`.

Every number needs an upper bound check; the per session limit may not exceed the instance limit
and a chunk may not exceed the queue. When log following is disabled, its endpoints and background
tasks are not registered.

## 7. Acceptance

- Verify with a log larger than 1 GiB that the tail duration depends on the scan limit and not on
  the full file size.
- Cover a missing trailing newline, CRLF, very long lines, multi byte characters across blocks,
  UTF-16, invalid encodings, empty files and binary content.
- Verify append, rename rotation, copytruncate, delete and recreate, grant expiry, reconnect after
  a disconnect and a forged cursor.
- Run 20 connections for 30 minutes and record heap, threads, handles and queue peaks; a slow
  client must not push the queue out of bounds, and an append reaches the screen within 2 seconds
  in a normal local environment.
- After 100 connect/disconnect cycles the quota returns to zero with no lasting resource growth;
  shutting the application down leaves no read task behind.
- Content carrying scripts, SSE separators and hostile file names is only ever shown as text.
