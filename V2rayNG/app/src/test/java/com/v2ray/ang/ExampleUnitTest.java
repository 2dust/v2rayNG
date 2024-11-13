package com.v2ray.ang;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * ExampleUnitTest contains unit tests for basic arithmetic operations.
 */
public class ExampleUnitTest {

    @Test
    public void testAddition() {
        // Given two numbers
        int number1 = 2;
        int number2 = 2;
        
        // When we add them
        int result = number1 + number2;
        
        // Then the result should be equal to 4
        assertEquals("Addition result should be 4", 4, result);
    }
}
