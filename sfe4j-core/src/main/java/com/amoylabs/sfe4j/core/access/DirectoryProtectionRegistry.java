package com.amoylabs.sfe4j.core.access;

import com.amoylabs.sfe4j.core.exception.Sfe4jRuntimeException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Immutable set of {@link DirectoryProtectionRule} built from {@code sfe4j.folder-passwords}.
 *
 * <p>Invalid configuration fails at startup: an unprotected directory must never be the result of a
 * silently ignored rule.</p>
 */
public final class DirectoryProtectionRegistry {

    private final List<DirectoryProtectionRule> rules;

    private final Map<String, DirectoryProtectionRule> rulesById;

    private DirectoryProtectionRegistry(List<DirectoryProtectionRule> rules) {
        this.rules = Collections.unmodifiableList(new ArrayList<>(rules));
        Map<String, DirectoryProtectionRule> byId = new LinkedHashMap<>();
        for (DirectoryProtectionRule rule : rules) {
            byId.put(rule.getId(), rule);
        }
        this.rulesById = Collections.unmodifiableMap(byId);
    }

    public static DirectoryProtectionRegistry empty() {
        return new DirectoryProtectionRegistry(Collections.emptyList());
    }

    /**
     * Builds and validates the registry.
     *
     * @param folderPasswords protected path to password mapping, may be {@code null}
     * @param baseDir         normalized absolute base directory used to resolve relative keys
     */
    public static DirectoryProtectionRegistry build(Map<String, String> folderPasswords, Path baseDir) {
        if (folderPasswords == null || folderPasswords.isEmpty()) {
            return empty();
        }

        List<DirectoryProtectionRule> rules = new ArrayList<>(folderPasswords.size());
        Set<String> ids = new LinkedHashSet<>();

        for (Map.Entry<String, String> entry : folderPasswords.entrySet()) {
            String declaredPath = entry.getKey();
            if (declaredPath == null || declaredPath.trim().isEmpty()) {
                throw new Sfe4jRuntimeException(
                        "sfe4j.folder-passwords contains an empty path key; configure the absolute path of the protected directory.");
            }

            Path path;
            try {
                path = Path.of(declaredPath.trim());
            } catch (InvalidPathException e) {
                throw new Sfe4jRuntimeException(
                        "sfe4j.folder-passwords contains an illegal path: '" + declaredPath + "'.", e);
            }

            if (!path.isAbsolute()) {
                path = baseDir.resolve(path);
            }
            path = path.normalize();

            if (!Files.exists(path)) {
                throw new Sfe4jRuntimeException(
                        "Protected directory does not exist: '" + path + "'. Create the directory before starting the application.");
            }
            if (!Files.isDirectory(path)) {
                throw new Sfe4jRuntimeException("Protected path is not a directory: '" + path + "'.");
            }

            Path realPath;
            try {
                realPath = path.toRealPath();
            } catch (IOException e) {
                throw new Sfe4jRuntimeException("Protected directory cannot be resolved: '" + path + "'.", e);
            }

            if (!ids.add(path.toString())) {
                throw new Sfe4jRuntimeException("Duplicate protected directory after normalization: '" + path + "'.");
            }

            rules.add(DirectoryProtectionRule.create(path, realPath, entry.getValue()));
        }

        return new DirectoryProtectionRegistry(rules);
    }

    public boolean isEmpty() {
        return rules.isEmpty();
    }

    public List<DirectoryProtectionRule> getRules() {
        return rules;
    }

    public DirectoryProtectionRule findById(String id) {
        return id == null ? null : rulesById.get(id);
    }

    /**
     * Selects the longest matching rule for the given path.
     *
     * <p>Path comparison uses path segments, never string prefixes: {@code /data2} is not inside
     * {@code /data}.</p>
     *
     * @return the longest matching rule, or an empty list when the path is not protected
     */
    public List<DirectoryProtectionRule> longestMatches(Path candidate) {
        return longest(candidate, DirectoryProtectionRule::getPath);
    }

    /**
     * Selects the longest matching rule in the real path space.
     *
     * <p>Needed because the configured path and its real path differ when a parent directory is a symbolic
     * link; matching only the configured form would let an alias escape the rule.</p>
     */
    public List<DirectoryProtectionRule> longestRealMatches(Path candidate) {
        return longest(candidate, DirectoryProtectionRule::getRealPath);
    }

    private List<DirectoryProtectionRule> longest(Path candidate,
                                                  java.util.function.Function<DirectoryProtectionRule, Path> selector) {
        if (candidate == null || rules.isEmpty()) {
            return Collections.emptyList();
        }
        DirectoryProtectionRule best = null;
        for (DirectoryProtectionRule rule : rules) {
            Path rulePath = selector.apply(rule);
            if (best != null && rulePath.getNameCount() <= selector.apply(best).getNameCount()) {
                continue;
            }
            if (candidate.startsWith(rulePath)) {
                best = rule;
            }
        }
        return best == null ? Collections.emptyList() : List.of(best);
    }

    /**
     * Matches both the lexical path and the real path.
     *
     * <p>When the two lookups return different rules, both are returned and both grants are required;
     * a longer string on one side must not mask the rule selected on the other side.</p>
     */
    public List<DirectoryProtectionRule> match(Path lexicalPath, Path realPath) {
        List<DirectoryProtectionRule> lexicalMatches = longestMatches(lexicalPath);
        List<DirectoryProtectionRule> realMatches = longestRealMatches(realPath);
        if (realMatches.isEmpty()) {
            return lexicalMatches;
        }
        if (lexicalMatches.isEmpty()) {
            return realMatches;
        }

        Set<DirectoryProtectionRule> merged = new LinkedHashSet<>(lexicalMatches);
        merged.addAll(realMatches);
        return new ArrayList<>(merged);
    }

    /**
     * Rules that must be granted before accessing the given real path.
     */
    public List<DirectoryProtectionRule> matchReal(Collection<Path> paths) {
        Set<DirectoryProtectionRule> merged = new LinkedHashSet<>();
        if (paths != null) {
            for (Path path : paths) {
                merged.addAll(longestRealMatches(path));
            }
        }
        return new ArrayList<>(merged);
    }
}
