package org.dropProject.sampleAssignments.testProj;

import org.junit.*;
import static org.junit.Assert.assertEquals;

public class TestTeacherHiddenProject {

    @Test
    public void testFuncaoParaTestarQueNaoApareceAosAlunos() {
        assertEquals(3, Main.funcaoParaTestar());
    }

}
