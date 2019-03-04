package org.dropProject.sampleAssignments.testProj;

import org.junit.*;
import static org.junit.Assert.assertEquals;

public class TestProject2 {

    @Test
    public void testFuncaoParaTestar() {
        assertEquals(3, Main.funcaoParaTestar());
    }

    @Test
    public void testFuncaoLentaParaTestar() {
        assertEquals(3, Main.funcaoLentaParaTestar());
    }
}
