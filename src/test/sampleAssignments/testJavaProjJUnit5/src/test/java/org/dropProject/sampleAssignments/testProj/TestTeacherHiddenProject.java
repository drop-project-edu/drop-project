package org.dropProject.sampleAssignments.testProj;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Timeout(1)
public class TestTeacherHiddenProject {

    @Test
    public void testFuncaoParaTestarQueNaoApareceAosAlunos() {
        assertEquals(3, Main.funcaoQueRebenta());
    }

}
