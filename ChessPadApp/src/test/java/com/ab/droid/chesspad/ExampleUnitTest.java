package com.ab.droid.chesspad;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * To work on unit tests, switch the Sample Artifact in the Build Variants view.
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() {
        assertEquals(4, 2 + 2);
    }

    @Test
    public void testPV() {
     String str = "f7f5 e2e3 c7c6 f1e2 e8g8 e1g1 b8d7 c4d5 c6d5 c3b5 d7f6 d2e4 f6e4 f2f3 e4f6 a2a3 b4e7 b5c7 a8b8 b2b4";
     String[] parts = str.split("\\s+");
     System.out.printf("%d moves", parts.length);
    }
}