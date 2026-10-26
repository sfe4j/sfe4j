package com.amoylabs.sfe4j.core.access;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;

/**
 * Result of a resolution and authorization step: the lexical path used for links and display, the
 * real path used for filesystem access, and the protection rules that have been enforced.
 */
public final class ResolvedPath {

    private final Path lexicalPath;

    private final Path realPath;

    private final List<DirectoryProtectionRule> matchedRules;

    public ResolvedPath(Path lexicalPath, Path realPath, List<DirectoryProtectionRule> matchedRules) {
        this.lexicalPath = lexicalPath;
        this.realPath = realPath;
        this.matchedRules = matchedRules == null ? Collections.emptyList() : List.copyOf(matchedRules);
    }

    public Path getLexicalPath() {
        return lexicalPath;
    }

    public Path getRealPath() {
        return realPath;
    }

    public List<DirectoryProtectionRule> getMatchedRules() {
        return matchedRules;
    }
}
