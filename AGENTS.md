# AI Collaboration Guide

This file applies to the whole repository and is the single project guide for AI coding tools.
Read it before starting a task; read `docs/ai/project-context.md` and the relevant source when
you need implementation details. All documentation and communication in this repository are in
English, code identifiers stay English as well.

## Project and module boundaries

SFE4J is a file explorer, preview and download component for Spring Boot applications. The
current version is 1.2.1, the POMs and CI use Java 25, and the Spring Boot dependency version is
4.1.0; always trust the actual POM over this file.

Dependency direction: `sample -> starter -> spring-boot -> core`.

- `sfe4j-core`: file operations, configuration model, value objects. It stays independent of
  Spring and Servlet.
- `sfe4j-spring-boot`: MVC controllers, bean configuration, FreeMarker templates, static assets.
- `sfe4j-spring-boot-starter`: `sfe4j.*` property binding, defaults, auto configuration
  registration.
- `sfe4j-spring-boot-sample`: runnable example, environment configuration and the only
  application context test today.
- The root `pom.xml` only aggregates the modules, the submodules do not inherit from it. Never
  assume root properties, plugins or dependency management reach the submodules.

## Common commands

Run these from the repository root; they need JDK 25 and a working Maven:

```sh
mvn test
mvn verify
mvn -pl sfe4j-spring-boot -am test
mvn -pl sfe4j-spring-boot-sample -am package
java -jar sfe4j-spring-boot-sample/target/sfe4j-spring-boot-sample.jar --logging.file.name=./target/sfe4j.log
```

There is no Maven Wrapper at the root, each module has one but the script is not executable.
Without a global Maven, try `(cd sfe4j-core && sh mvnw -f ../pom.xml test)` from the root. The
wrapper pins Maven 3.8.1, downloads the distribution on first use, and must not be assumed
verified beforehand.

Before running `spring-boot:run` for the sample alone, run
`mvn -pl sfe4j-spring-boot-starter -am install -DskipTests` first, then
`mvn -f sfe4j-spring-boot-sample/pom.xml spring-boot:run -Dspring-boot.run.arguments=--logging.file.name=./target/sfe4j.log`.
Log paths are relative to the application working directory. The sample defaults to
`/data/logs/app.log`, override it with a writable directory when running locally.

## Change conventions

- Read the relevant classes, POMs, templates and configuration first and keep changes focused.
  Do not upgrade dependencies or refactor the whole project in passing.
- Follow the current Java style: four space indentation, braces on the same line, explicit
  getters and setters. Do not introduce Lombok, a front end build pipeline or repository wide
  reformatting for a local change.
- Keep the existing public API, `sfe4j.*` configuration keys, routes, template model fields and
  context path behaviour compatible; when a behaviour change is intended, describe its impact.
- When adding a configuration option, check `Sfe4jProperties`, `Sfe4jConfiguration`,
  `Sfe4jAutoConfiguration`, the example YAML and the README configuration table together.
- When touching auto configuration, check both `META-INF/spring.factories` and
  `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`. Never
  update only one of them and leave a stale class name behind.
- Templates and scripts live directly under `sfe4j-spring-boot/src/main/resources`; do not edit
  the vendored Bootstrap, jQuery and DataTables files to implement behaviour. New page scripts are
  plain JavaScript in their own file under `static/js` and insert file text only with `textContent`
  or text nodes.
- Handle file paths for Linux/macOS and Windows, including spaces, non ASCII and other special
  characters.
- Never commit real passwords, tokens, machine specific paths or build output; keep the license.
  Publishing and deployment are not part of the usual local verification.

## Security and testing

- Review the listing, preview, download, text read API and follow stream entry points together for
  any file access change; hiding a link in a page is not a fix. A follow connection is authorized
  again before every read round, so a change in the facade must keep that call in place.
- Base directory restrictions must consider normalized paths, `..`, sibling directories with a
  shared prefix and symbolic links; directory passwords must consider subdirectories,
  overlapping rules and session state. P0 moved all of these boundaries behind one facade with
  regression tests; the remaining risks (TOCTOU, integration and Windows verification) are
  written down in section 10 of the P0 design and in the project guide.
- Keep file text apart from trusted HTML, review template escaping, URL encoding and download
  response headers; never treat raw file content as trusted page input.
- Use reliable resource closing for file I/O, watch the memory footprint of large files and
  handle missing files and files without read permission.
- Add targeted regression tests for behaviour changes; build file samples in a temporary
  directory instead of depending on real data on a development machine. core, web and starter
  each declare JUnit 5 and Surefire 3.5.4, and the sample pins the same version in its
  `<build>` block (it inherits 3.5.6 from the Boot parent). Build a single module with `-am`,
  otherwise the starter resolves an old `sfe4j-core` jar from the repository.
- The base directory and every protected directory must exist, and an out of range numeric value
  fails startup. When adding or changing example configuration, the directories must really
  exist in the target environment, otherwise the sample context test fails.
- For documentation changes, reviewing the diff and the paths is enough. For code changes, run
  the tests of the affected module at least; for cross module or dependency changes prefer
  `mvn verify` at the root. A passing application context does not mean file access and security
  logic are tested.
- When a test fails, report the command you ran, the reason and what was not verified, and
  separate environment or dependency resolution failures from code failures. Never describe a
  build with skipped tests as tests passing.

## Delivery notes

When handing work over, summarise what changed, what was verified and which limitations remain.
Keep this file and the project guide in sync when the project structure, build commands or key
behaviour change. Tool specific entry files only reference this file, so rules are never copied
and drift apart.
