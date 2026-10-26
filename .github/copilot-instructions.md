# SFE4J repository notes

Read and follow `AGENTS.md` in the repository root before starting a task; the project guide is
`docs/ai/project-context.md`. All collaboration rules live in `AGENTS.md` as the single source of
truth and are not duplicated here.

This is a Java 25 Maven project with four modules, dependency direction
sample -> starter -> spring-boot -> core. The root POM only aggregates. Configuration changes
must check property binding, the core configuration, the auto configuration mapping, the samples
and the README; file access changes must check listing, preview and download together. See
`AGENTS.md` for the commands and the verification expectations.
