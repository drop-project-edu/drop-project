package pt.ulusofona.deisi.aedProj2020;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestTeacherHiddenPart1 {

    @BeforeClass  // this method only runs once, before all the tests
    public static void setup() throws IOException {

    }

    @AfterClass  // this method only runs once, after all the tests
    public static void cleanup() {

    }

    @Test(timeout = 60000)
    public void test01CountIgnored() throws IOException {

    }

    @Test(timeout = 5000)
    public void test02CountIgnored2() throws IOException {

    }

    @Test(timeout = 5000)
    public void test03CountIgnoredWithQuotes() throws IOException {

    }

    @Test(timeout = 5000)
    public void test04GetMoviesActorYearWithQuotes() throws IOException {

    }

    // this must be the last test
    @Test(timeout = 5000)
    public void test99EmptyFile() throws IOException {

    }

    private static boolean filesAlreadyParsed = false;

    private static void parseFiles() throws IOException {
        if (!filesAlreadyParsed) {
            Main.parseFiles();
            filesAlreadyParsed = true;
        }
    }
}