package pt.ulusofona.deisi.aedProj2020;

import org.junit.*;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.IOException;
import java.util.stream.Stream;

import java.util.List;
import java.util.Arrays;

import static org.junit.Assert.*;
import static pt.ulusofona.deisi.aedProj2020.TestTeacherWithLargeFiles.runFor;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestTeacherHiddenPart2 {

    @BeforeClass  // this method only runs once, before all the tests
    public static void setup() throws IOException {

    }

    @AfterClass  // this method only runs once, after all the tests
    public static void cleanup() {

    }

    @Ignore
    @Test(timeout = 60000)
    public void testXXGetMoviesActorYearWithQuotesInActorName() throws IOException {

    }

    @Test(timeout = 60000)
    public void test01ImparInsertDuplicateActor() throws IOException {

    }

    @Test(timeout = 60000)
    public void test02ParInsertDuplicateDirector() throws IOException {

    }

    @Test(timeout=1000)
    public void test03BogusQueryCodes() throws IOException {

    }

    /*
    @Test(timeout=1000)
    public void test04ParDirectorsWith3Names() throws IOException {

	
    }
     */

    @Test(timeout=30000)
    public void test04ParDirectorsWith3Names() throws IOException {

    }
    
    @Test(timeout=1000)
    public void test04ImparActorsWith3Names() throws IOException {

	
    }
}
