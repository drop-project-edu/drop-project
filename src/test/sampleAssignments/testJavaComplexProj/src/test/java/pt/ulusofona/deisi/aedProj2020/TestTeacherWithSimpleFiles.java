package pt.ulusofona.deisi.aedProj2020;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.Assert.*;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class TestTeacherWithSimpleFiles {

    @Before
    @After
    public void deleteAllFiles() {
        new File("deisi_movies.txt").delete();
        new File("deisi_movie_votes.txt").delete();
        new File("deisi_actors.txt").delete();
        new File("deisi_directors.txt").delete();
        new File("deisi_genres.txt").delete();
        new File("deisi_genres_movies.txt").delete();
    }

    @Test(timeout = 1000)
    public void test01ParseSimpleFile() throws IOException {



    }

    @Test(timeout = 1000)
    public void test02ParseFileWithSpaces() throws IOException {

    }

    @Test(timeout = 1000)
    public void test03ParseFileWithInvalidLines() throws IOException {


    }

    @Test(timeout = 1000)
    public void test04CountIgnoredLines() throws IOException {


    }

    private void generateInvalidFiles() throws IOException {

    }

}
