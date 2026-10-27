# SFE4J Project Guide

This file records the current facts of the source tree so AI tools and maintainers can locate
work; `AGENTS.md` in the repository root holds the collaboration rules. The observations below
come from reading the code, they are not a complete security audit or a runtime verification.

## Call chains and locations

| Concern | Source location (relative to the repository root) | Notes |
| --- | --- | --- |
| File access facade | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/access/` | Path resolution, protection rules, shared authorization, file operations |
| File system logic | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/service/FileExplorerService.java` | Page metadata and directory trees; reads are delegated to the access facade |
| Text reading | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/text/` | `TextReadService` for bounded head/tail and incremental reads, encodings, BOM, binary heuristic; `TextFileReader` is the bounded head preview on top of it |
| File reading utility | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/util/FileUtils.java` | Read helper, only used by the bounded preview read path |
| Data model | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/vo/` | Files, directory trees, file content |
| Configuration model | `sfe4j-core/src/main/java/com/amoylabs/sfe4j/core/configuration/Sfe4jConfiguration.java` | Framework independent configuration |
| Access and session adapters | `sfe4j-spring-boot/src/main/java/com/amoylabs/sfe4j/spring/boot/access/` | Session grant store, password rate limiting, CSRF, directory authorization |
| Log following | `sfe4j-spring-boot/src/main/java/com/amoylabs/sfe4j/spring/boot/logs/` | `LogFollowManager` (shared scheduler, quotas, queue bound, re-authorization) and the signed resume cursor |
| Web infrastructure | `sfe4j-spring-boot/src/main/java/com/amoylabs/sfe4j/spring/boot/web/` | Error handling, download concurrency limit, URL codec, path labels |
| Web entry points | `sfe4j-spring-boot/src/main/java/com/amoylabs/sfe4j/spring/boot/controller/` | Listing, preview and download, all of them go through the access facade; `TextApiController` adds the JSON text read API and the follow stream |
| Service beans | `sfe4j-spring-boot/src/main/java/com/amoylabs/sfe4j/spring/boot/config/BeanConfig.java` | Injects configuration into the file service and the access facade |
| Pages | `sfe4j-spring-boot/src/main/resources/templates/` | `file-explorer.ftlh`, `file-viewer.ftlh`, `file-error.ftlh` |
| Page script | `sfe4j-spring-boot/src/main/resources/static/js/sfe4j-text-viewer.js` | Line numbers, encoding and mode switching, follow, search and copy; inserts file text only as text nodes |
| Auto configuration | `sfe4j-spring-boot-starter/src/main/java/com/amoylabs/sfe4j/spring/boot/starter/` | Property binding, mapping and validation, component scanning |
| Runnable sample | `sfe4j-spring-boot-sample/src/main/` | Application class, ping controller, application YAML and profiles |

At startup the starter registers the auto configuration and enables `Sfe4jProperties`.
`Sfe4jProperties.toConfiguration()` maps and validates them into `Sfe4jConfiguration`;
`BeanConfig` creates `FileAccessService` and `FileExplorerService`, and the controllers render a
view or stream a download response through the access facade.

## HTTP and configuration contract

- `GET /file-explorer?dir=...`: directory listing; the base directory is used when no directory
  is given.
- `POST /file-explorer`: validates the directory password from the `dir` and `pwd` parameters and
  answers with Post/Redirect/Get. Grants live in the HTTP session only, 30 minutes by default.
- `POST /file-explorer/logout`: drops SFE4J directory grants only, it does not invalidate the
  host session.
- `GET /file-viewer?file=...`: reads text within the preview limit and hands it to an
  auto escaped template.
- `GET /file-downloader?file=...`: streams an attachment with a fixed buffer, answers 429 when
  the concurrency limit is reached.
- `GET /sfe4j/api/text?file=...&mode=head|tail&lines=...&charset=...`: bounded JSON text read,
  registered only when `sfe4j.logs.enabled=true`.
- `GET /sfe4j/api/follow?file=...&charset=...&lines=...&cursor=...`: server sent event stream with
  `snapshot`, `append`, `reset`, `gap` and `error` events; the target is authorized before the
  stream is opened and again before every read round.
- Properties are prefixed with `sfe4j` and cover `title`, `description`, `quick-links`,
  `base-dir-path`, `restrict-to-base-dir`, `folder-passwords`, `access.*`, `preview.*`,
  `download.*` and `logs.*`. `logs.enabled` defaults to `false`, which leaves the API, the follow
  stream and the background scheduler unregistered.
- The auto configuration defaults an empty base directory to `/` and an empty restriction flag
  to `false`; title and description fall back to `GlobalConstants` in the service. An out of
  range numeric value fails startup instead of degrading silently.
- Map keys that hold a path use the bracket notation in YAML, for example `"[/data/secret]"`.
  The passwords in the sample files are for demonstration only and must not be used as
  deployment credentials.

## Build and runtime facts

- The four modules declare their version separately, currently `1.2.0`; the inter-module version
  properties are spread across the POMs, so a release has to update every place.
- `core` depends on Commons Lang; `web` brings in Spring MVC and FreeMarker; `starter` wires the
  integration; `sample` inherits the Spring Boot parent and adds the Boot Admin client.
- `.github/workflows/ci.yaml` uses JDK 25 and runs `mvn test` and a separate packaging step with
  tests skipped. The `build_image` input controls image build and push. Publishing a release also
  runs `scripts/publish-mvn-repo.sh` when `MVN_REPO_TOKEN` is configured.
- The Dockerfile is `.cicd/Dockerfile`, it copies the executable sample JAR and uses the Temurin
  25 JRE; the Jenkinsfile depends on a shared library outside this repository.
- The sample artifact is `sfe4j-spring-boot-sample/target/sfe4j-spring-boot-sample.jar` and it
  listens on port 8080.
- core, web and starter each configure JUnit 5 and Surefire with tests that really run: core
  covers paths, policies and text reading; web covers controllers, sessions, CSRF and template
  rendering; starter covers property binding and startup validation. The sample still only has
  `@SpringBootTest contextLoads()`.
- Integration tests (real HTTP downloads, interruption and handle release) and Windows CI are not
  in place yet, see the open items of the development plan.

## Observed improvement points

Kept for upcoming tasks. The first five were fixed by P0 and remain as regression anchors:

1. ~~**File access boundary**: `restrict-to-base-dir` was only used by `buildFileTree()`.~~ Fixed
   by the shared access facade: listing, preview and download all use `FileAccessService`,
   ownership is compared path segment by path segment and the real path is resolved.
2. ~~**Directory password scope**: password checks existed only in the listing controller.~~
   Fixed: rules take the longest match on the lexical path and on the real path, preview and
   download require the grant as well.
3. ~~**Preview output**: `<#noautoesc>` could render HTML.~~ Fixed: replaced by an auto escaped
   `<pre>`, file names are escaped too.
4. ~~**Resources and memory**: streams were not closed and files were loaded completely.~~ Fixed:
   the preview reads at most `preview.max-bytes`, downloads stream with a fixed buffer and are
   closed with the response.
5. ~~**Exceptions and encoding**: `readFile()` returned `null` on error and the file name was
   concatenated into a response header.~~ Fixed: shared error codes and an error page/JSON
   contract, download headers built with `ContentDisposition`, listing links URL encoded.

Remaining items (P1/P2 scope):

6. ~~**Following and log features**: head/tail of large logs, encoding selection, line numbers and
   server sent event following are not implemented.~~ Implemented by P1: bounded head/tail reads
   with four encodings, and a follow stream with quotas, reset/gap handling and a signed cursor.
   Syntax highlighting and the 20 connections / 30 minutes benchmark are still open.
7. **Scale and integration**: multiple roots, a host authentication SPI, auditing, server side
   paging, localization and the removal statistics script are not implemented.
8. **Concurrent replacement risk**: a TOCTOU window remains between the path check and opening
   the file, `SecureDirectoryStream` is not used; deployments still need read-only mounts or
   isolation.

## Scenarios worth verifying for future tasks

Pick them by task scope: base directory and subdirectories, sibling directories with a shared
prefix, path traversal, symbolic links, Windows paths, password protected directories and their
subdirectories, nested password priority, direct preview/download access, missing or unreadable
files, non ASCII and special character file names, HTML content and large files. Use a temporary
directory for file based tests and never depend on the host root directory or on paths from the
sample configuration.

## Evolution design and development plan

The prioritized designs are listed in the [design index](../design/README.md), the task
breakdown and acceptance criteria in the [development plan](../development/implementation-plan.md).
Those files describe target designs, not features that already exist.
