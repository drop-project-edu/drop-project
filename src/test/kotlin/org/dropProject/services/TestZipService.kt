package org.dropProject.services

import org.junit.Test
import java.io.File

class TestZipService {

    val zipper = ZipService()

    /**
     * Tested functions: ZipService.createZipFromFolder() and ZipService.unzip().
     *
     * Scenario: a ZIP file is first created by compressing a .text file. Afterwards, the resulting ZIP file
     * is unzipped and we check if the decompressed file has the same contents that the original file had.
     */
    @Test
    fun testZipAndUnzip()  {
        val zipCreationTime = System.currentTimeMillis()
        val temDirectory = File(System.getProperty("java.io.tmpdir"))
        val subDirectory = File(temDirectory, "to-zip-" + zipCreationTime)
        val zippedFilename = "zipped-file-" + zipCreationTime + "-"

        // first, test the zipping process
        subDirectory.mkdir();
        val fileToZip = File(subDirectory, "file-to-zip-" + zipCreationTime + ".txt")
        fileToZip.createNewFile()
        fileToZip.writeText("This is a unit test of the Drop Project... project.")
        val zippedFile = zipper.createZipFromFolder(zippedFilename, subDirectory)

        assert(zippedFile.isValidZipFile())

        // second, test the unzipping process
        val fileToUnzip = File(temDirectory, zippedFile.file.name)
        val unzippedFolder = zipper.unzip(fileToUnzip.toPath(), "file-to-zip-" + zipCreationTime + ".txt")
        val files = unzippedFolder.listFiles()

        val expectedFileName = "file-to-zip-" + zipCreationTime + ".txt"
        assert(expectedFileName == files.get(0).name);
        assert(1 == files.get(0).readLines().size)
        assert("This is a unit test of the Drop Project... project." == files.get(0).readLines().get(0))

        // clean-up
        fileToZip.delete()
        fileToUnzip.delete()
    }

}