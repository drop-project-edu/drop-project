package org.dropProject.sampleAssignments.testProj;

// wildcard import: qdox reports these annotations with their simple name
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestTeacherProject {

    @Test
    @Timeout(1)
    public void testFuncaoParaTestar() {
        assertEquals(3, Main.funcaoParaTestar());
    }

    @Test
    @Timeout(2)
    public void testFuncaoLentaParaTestar() {
        assertEquals(3, Main.funcaoLentaParaTestar());
    }

    @Test
    @Disabled
    public void testFuncaoDesativada() {
        assertEquals(3, Main.funcaoQueRebenta());
    }
}
