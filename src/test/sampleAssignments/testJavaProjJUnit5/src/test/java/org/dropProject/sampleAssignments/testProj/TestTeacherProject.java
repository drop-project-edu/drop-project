package org.dropProject.sampleAssignments.testProj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(1)
public class TestTeacherProject {

    @Test
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
