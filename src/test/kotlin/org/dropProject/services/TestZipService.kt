package org.dropProject.services

import org.junit.Test
import java.io.File

class TestZipService {

    val zipCreationTime = System.currentTimeMillis()
    val temDirectory = File(System.getProperty("java.io.tmpdir"))
    val subDirectory = File(temDirectory, "to-zip-" + zipCreationTime)
    val zippedFilename = "zipped-file-" + zipCreationTime + ".zip"

    @Test
    fun testZip()  {
        val zipper = ZipService()
        subDirectory.mkdir();
        val fileToZip = File(subDirectory, "file-to-zip-" + zipCreationTime + ".txt")
        fileToZip.createNewFile()
        fileToZip.writeText("This is a unit test of the Drop Project... project.")
        val zippedFile = zipper.createZipFromFolder(zippedFilename, subDirectory)
        assert(zippedFile.isValidZipFile())

        // TODO: improve asserts, try to unzip and get the file, check the unzipped file contents
    }

}