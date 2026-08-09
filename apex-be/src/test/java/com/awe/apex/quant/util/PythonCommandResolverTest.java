package com.awe.apex.quant.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PythonCommandResolverTest {

    @Test
    void fallsBackToPython3WhenPythonIsUnavailable() {
        assertEquals("python3", PythonCommandResolver.resolve("python", this::locatePython3));
    }

    @Test
    void keepsConfiguredCommandWhenAvailable() {
        assertEquals("python", PythonCommandResolver.resolve("python", this::locatePython));
        assertEquals("/opt/venv/bin/python", PythonCommandResolver.resolve(
                "/opt/venv/bin/python", this::locateConfiguredVenv));
    }

    @Test
    void fallsBackToPythonWhenPython3IsUnavailable() {
        assertEquals("python", PythonCommandResolver.resolve("python3", this::locatePython));
    }

    @Test
    void usesAbsoluteHomebrewPathWhenIdePathDoesNotContainPython3() {
        assertEquals("/opt/homebrew/bin/python3", PythonCommandResolver.resolve(
                "python", command -> "python3".equals(command) ? "/opt/homebrew/bin/python3" : null));
    }

    private String locatePython(String command) {
        return "python".equals(command) ? command : null;
    }

    private String locatePython3(String command) {
        return "python3".equals(command) ? command : null;
    }

    private String locateConfiguredVenv(String command) {
        return "/opt/venv/bin/python".equals(command) ? command : null;
    }
}
