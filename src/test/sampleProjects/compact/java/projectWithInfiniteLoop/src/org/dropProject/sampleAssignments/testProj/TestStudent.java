package org.dropProject.sampleAssignments.testProj;

import org.junit.Test;

public class TestStudent {

    @Test
    public void testInfiniteLoop() {
        long counter = 0;
        while (true) {
            counter++;
        }
    }
}