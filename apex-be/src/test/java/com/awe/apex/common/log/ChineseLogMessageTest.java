package com.awe.apex.common.log;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChineseLogMessageTest {

    private static final Pattern LOG_CALL_PATTERN = Pattern.compile(
            "\\b(?:log|logger|LOGGER)\\.(?:trace|debug|info|warn|error)\\s*\\(");
    private static final Pattern LOG_LITERAL_PATTERN = Pattern.compile(
            "\\b(?:log|logger|LOGGER)\\.(?:trace|debug|info|warn|error)\\s*\\(\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern CHINESE_PATTERN = Pattern.compile("[\\u4e00-\\u9fff]");
    private static final Pattern ENGLISH_FIELD_PATTERN = Pattern.compile("[A-Za-z][A-Za-z0-9_.]*=");

    @Test
    void allJavaLogsStartWithChineseContext() throws IOException {
        Path sourceRoot = Path.of("src/main/java");
        List<String> dynamicMessages = new ArrayList<>();
        List<String> nonChineseMessages = new ArrayList<>();
        List<String> englishFieldMessages = new ArrayList<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            Iterator<Path> iterator = paths.iterator();
            while (iterator.hasNext()) {
                Path sourceFile = iterator.next();
                if (!sourceFile.toString().endsWith(".java")) {
                    continue;
                }
                String source = Files.readString(sourceFile);
                Matcher callMatcher = LOG_CALL_PATTERN.matcher(source);
                Matcher literalMatcher = LOG_LITERAL_PATTERN.matcher(source);
                int callCount = 0;
                int literalCount = 0;
                while (callMatcher.find()) {
                    callCount++;
                }
                while (literalMatcher.find()) {
                    literalCount++;
                    String message = literalMatcher.group(1);
                    if (!CHINESE_PATTERN.matcher(message).find()) {
                        nonChineseMessages.add(sourceFile + " -> " + message);
                    }
                    if (ENGLISH_FIELD_PATTERN.matcher(message).find()) {
                        englishFieldMessages.add(sourceFile + " -> " + message);
                    }
                }
                if (callCount != literalCount) {
                    dynamicMessages.add(sourceFile + " -> 日志调用数 " + callCount + "，中文模板数 " + literalCount);
                }
            }
        }

        assertEquals(List.of(), dynamicMessages, "日志必须以固定中文模板开头");
        assertTrue(nonChineseMessages.isEmpty(), "发现非中文日志模板：" + nonChineseMessages);
        assertTrue(englishFieldMessages.isEmpty(), "发现英文日志字段：" + englishFieldMessages);
    }
}
