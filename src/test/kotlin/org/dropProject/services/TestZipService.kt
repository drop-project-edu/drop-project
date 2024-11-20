/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2021 Pedro Alves
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =========================LICENSE_END==================================
 */
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

        // second, test the unzipping process
        val fileToUnzip = File(temDirectory, zippedFile.name)
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
