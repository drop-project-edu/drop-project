package org.dropProject.sampleAssignments.testProj;

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

public class TestStudent {

    Integer i = null;

    @BeforeEach
    public void setup() {
        i = 0;
    }

    @Test
    public void testFuncaoParaEuTestar() {
        assertEquals(4 + i, Main.funcaoParaEuTestar());
    }

    @Test
    public void testFuncaoParaEuTestar2() {
        assertEquals(4 + i, Main.funcaoParaEuTestar());
    }
}
