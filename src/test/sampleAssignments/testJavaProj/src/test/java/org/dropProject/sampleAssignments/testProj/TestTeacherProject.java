package org.dropProject.sampleAssignments.testProj;

import org.junit.*;
import static org.junit.Assert.assertEquals;

public class TestTeacherProject {

    @Test(timeout=500)
    public void testFuncaoParaTestar() {
        assertEquals(3, Main.funcaoParaTestar());
    }

    @Test
    public void testFuncaoLentaParaTestar() {
        assertEquals(3, Main.funcaoLentaParaTestar());
    }

    // @Test
    public void testFuncaoIgnorada1() {

    }

    /*

     @Test
    public void testFuncaoIgnorada2() {

    }

     */
}
