package org.dropProject.sampleAssignments.testProj;

import org.junit.jupiter.api.Test;

// qdox resolves an annotation brought in by a wildcard import only if it finds it in the classpath.
// junit is not in Drop Project's own runtime classpath, so on a real assignment '@Timeout' arrives
// here with its simple name. This unresolvable import reproduces that, which the junit one can't:
// junit is in the classpath of these tests.
import org.dropProject.notInTheClasspath.*;

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
}
