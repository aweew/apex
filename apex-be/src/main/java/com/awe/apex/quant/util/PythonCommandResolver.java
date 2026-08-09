package com.awe.apex.quant.util;

import com.awe.apex.common.util.StringUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

/**
 * Resolves the configured Python executable across macOS/Linux and Windows.
 */
public final class PythonCommandResolver {

    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    private PythonCommandResolver() {
    }

    public static String resolve(String configuredCommand) {
        String cacheKey = StringUtils.isNotBlank(configuredCommand) ? configuredCommand.trim() : "python3";
        return CACHE.computeIfAbsent(cacheKey, key -> resolve(key, PythonCommandResolver::locate));
    }

    static String resolve(String configuredCommand, Function<String, String> locator) {
        String command = StringUtils.isNotBlank(configuredCommand) ? configuredCommand.trim() : "python3";
        String resolved = locator.apply(command);
        if (StringUtils.isNotBlank(resolved)) {
            return resolved;
        }
        if ("python".equalsIgnoreCase(command)) {
            resolved = locator.apply("python3");
            return StringUtils.isNotBlank(resolved) ? resolved : command;
        }
        if ("python3".equalsIgnoreCase(command)) {
            resolved = locator.apply("python");
            return StringUtils.isNotBlank(resolved) ? resolved : command;
        }
        return command;
    }

    private static String locate(String command) {
        Path direct = Paths.get(command);
        if (direct.isAbsolute() || command.contains(File.separator)) {
            return isExecutable(direct) ? command : null;
        }
        List<Path> candidates = new ArrayList<>();
        String pathValue = System.getenv("PATH");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        String[] suffixes = windows ? new String[]{"", ".exe", ".cmd", ".bat"} : new String[]{""};
        if (StringUtils.isNotBlank(pathValue)) {
            for (String directory : pathValue.split(File.pathSeparator)) {
                String normalized = directory.replace("\"", "").trim();
                if (normalized.isEmpty()) {
                    continue;
                }
                for (String suffix : suffixes) {
                    candidates.add(Paths.get(normalized, command + suffix));
                }
            }
        }
        for (String candidate : commonPaths(command)) {
            candidates.add(Paths.get(candidate));
        }
        Set<Path> executables = new LinkedHashSet<>();
        for (Path candidate : candidates) {
            if (isExecutable(candidate)) {
                executables.add(candidate.toAbsolutePath().normalize());
            }
        }
        for (Path executable : executables) {
            if (supportsSyncRuntime(executable)) {
                return executable.toString();
            }
        }
        return executables.stream().findFirst().map(Path::toString).orElse(null);
    }

    private static List<String> commonPaths(String command) {
        if ("python3".equalsIgnoreCase(command)) {
            return List.of("/opt/homebrew/bin/python3", "/usr/local/bin/python3", "/usr/bin/python3");
        }
        if ("python".equalsIgnoreCase(command)) {
            return List.of("/usr/local/bin/python", "/usr/bin/python");
        }
        return List.of();
    }

    private static boolean isExecutable(Path path) {
        return Files.isRegularFile(path) && Files.isExecutable(path);
    }

    private static boolean supportsSyncRuntime(Path executable) {
        Process process = null;
        try {
            process = new ProcessBuilder(executable.toString(), "-c", "import pymysql, akshare")
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }
}
