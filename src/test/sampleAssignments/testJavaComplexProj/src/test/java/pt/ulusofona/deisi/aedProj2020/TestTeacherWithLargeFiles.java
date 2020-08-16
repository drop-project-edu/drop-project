package pt.ulusofona.deisi.aedProj2020;

import org.junit.*;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.hamcrest.Matchers.contains;
import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestTeacherWithLargeFiles {

    @BeforeClass  // this method only runs once, before all the tests
    public static void setup() throws IOException {

    }

    @AfterClass  // this method only runs once, after all the tests
    public static void cleanup() {

    }

    @Test(timeout = 60000)
    public void test00GetMovies() throws IOException {


    }

    @Test(timeout = 30000)
    public void test01ParInvalidQueries() throws IOException {

    }

    @Test(timeout = 30000)
    public void test01ImparInvalidQueries() throws IOException {

    }

    @Test(timeout = 30000)
    public void test02ParCountMoviesMonthYearOBG() throws IOException {

    }



    @Test(timeout = 30000)
    public void test02ImparGetMoviesActorYearOBG() throws IOException {

    }

    @Test(timeout = 30000)
    public void test03ImparCountMoviesDirectorOBG() throws IOException {

    }

    @Test(timeout = 30000)
    public void test03ParTopMonthMovieCountOBG() throws IOException {

    }

    @Test(timeout = 30000)
    public void test04ImparGetMoviesWithMoreGenderOBG() throws IOException {

    }

    @Test(timeout = 30000)
    public void test04ParGetActorsByDirectorOBG() throws IOException {



    }

    @Test(timeout = 30000)
    public void test05ParGetTopMoviesWithGenderBias() throws IOException {

    }

    @Test(timeout = 30000)
    public void test05ImparCountActorsIn2Years() throws IOException {

    }

    @Test(timeout = 30000)
    public void test06ParGetMoviesWithActorContaining() throws IOException {


    }

    @Test(timeout = 30000)
    public void test06ImparGetYearsWithMoviesContaining() throws IOException {

    }

    @Test(timeout = 60000)
    public void test07ParTop6DirectorsWithinFamily() throws IOException {

    }

    @Test(timeout=50000)
    public void test07ImparDistanceBetweenActors() throws IOException {


    }

    @Test(timeout = 30000)
    public void test08ImparInsertActor() throws IOException {


    }

    @Test(timeout = 30000)
    public void test08ParInsertDirector() throws IOException {

    }

    @Test(timeout = 30000)
    public void test09ParCountMoviesBetweenYearsWithNActorsOBG() throws IOException {

    }

    @Test(timeout = 30000)
    public void test09ImparCountDirectorsYearGenreOBG() throws IOException {

    }

    @Test(timeout = 30000)
    public void test10ParGetGenresByDirectorOBG() throws IOException {

    }

    @Test(timeout = 30000)
    public void test10ImparTopBudgetByDirectorOBG() throws IOException {


    }

    private static boolean filesAlreadyParsed = false;

    private static void parseFiles() throws IOException {
        if (!filesAlreadyParsed) {
            Main.parseFiles();
            filesAlreadyParsed = true;
        }
    }

    public static boolean runFor(String group) {
        String userId = System.getProperty("dropProject.currentUserId");
        System.out.println("userId = " + userId);
        if (userId != null) {
            int lastDigit = Integer.parseInt(userId.substring(userId.length() - 1));
            return (group.equals("par") && lastDigit % 2 == 0) ||
                    (group.equals("impar") && lastDigit % 2 != 0);
        }

        return true;
    }


}