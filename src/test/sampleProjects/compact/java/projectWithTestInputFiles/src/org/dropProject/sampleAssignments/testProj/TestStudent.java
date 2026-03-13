package org.dropProject.sampleAssignments.testProj;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.junit.Assert.*;

public class TestStudent {

    @Test
    public void testFuncaoParaEuTestar() {
        try {
            List<String> lines = Files.readAllLines(new File("test-files/test.txt").toPath());
            assertEquals(Integer.parseInt(lines.get(0)), Main.funcaoParaTestar());
        } catch (IOException e) {
            e.printStackTrace();
            fail("Should not have thrown exception");
        }
    }
}
