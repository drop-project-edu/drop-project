// Move this file to src/test/java/<your/package/path>/ and set the package below.
//
// The class name must start with TestTeacher. Name it TestTeacherHidden... instead for tests whose
// source the students never see - if you do, remember to set hiddenTestsVisibility on the
// assignment, otherwise validation fails with an error.
package REPLACE.WITH.YOUR.PACKAGE;

import org.junit.*;
import static org.junit.Assert.assertEquals;

public class TestTeacherAssignment {

    // Every test method needs a timeout, in milliseconds: without it, a student's infinite loop
    // blocks the evaluation queue. Numbering the methods fixes the order they are reported in,
    // which is the order the students will work through them.
    @Test(timeout = 500)
    public void test_001_simpleCase() {
        assertEquals(3, Main.functionToTest(1, 2));
    }

    @Test(timeout = 500)
    public void test_002_edgeCase() {
        assertEquals(0, Main.functionToTest(0, 0));
    }
}
