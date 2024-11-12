package com.v2ray.ang;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * Unit test class for verifying basic arithmetic operations.
 */
public class ExampleUnitTest {

    /**
     * Test to verify that the addition of two numbers is correct.
     */
    @Test
    public void testAddition() {
        // Given two numbers
        int number1 = 2;
        int number2 = 2;

        // When adding the two numbers
        int result = add(number1, number2);

        // Then the result should equal 4
        assertEquals(4, result);
    }

    /**
     * Adds two integers and returns the sum.
     *
     * @param a First integer
     * @param b Second integer
     * @return The sum of a and b
     */
    private int add(int a, int b) {
        return a + b;
    }
}
