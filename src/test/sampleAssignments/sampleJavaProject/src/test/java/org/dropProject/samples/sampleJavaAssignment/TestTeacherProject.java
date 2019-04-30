package org.dropProject.samples.sampleJavaAssignment;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

// in Drop Project, all test classes must begin with "Test"
public class TestTeacherProject {

    @Test
    public void testFindMax() {
        assertEquals(7, Main.findMax(new int[] { 1, 2, 7, 4 }));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFindMaxWithNull() {
        Main.findMax(null);
    }
}