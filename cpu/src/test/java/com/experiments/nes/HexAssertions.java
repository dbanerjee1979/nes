package com.experiments.nes;

import org.junit.jupiter.api.Assertions;

public class HexAssertions {
    public static void assertEquals(int expected, byte actual) {
        Assertions.assertEquals(String.format("%02x", expected), String.format("%02x", actual));
    }

    public static void assertEquals(int expected, byte actual, String description) {
        Assertions.assertEquals(String.format("%02x", expected), String.format("%02x", actual), description);
    }

    public static void assertEquals(int expected, short actual) {
        Assertions.assertEquals(String.format("%04x", expected), String.format("%04x", actual));
    }
}
