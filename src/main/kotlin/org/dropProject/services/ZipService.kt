/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 Pedro Alves
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

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream
import org.dropProject.repository.*
import org.dropProject.storage.StorageException
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path


/**
 * Utility to create ZIP files based on folder contents.
 */
@Service
class ZipService {

    /**
     * Creates a ZIP File with the contents of [projectFolder].
     *
     * @param zipFilename is a String with the desired name for the ZIP file
     * @param projectFolder is a File containing the directory that shall be zipped
     *
     * @return a [ZipFile]
     */
    fun createZipFromFolder(zipFilename: String, projectFolder: File): File {
        val zFile = File.createTempFile(zipFilename, ".zip")
        if (zFile.exists()) {
            zFile.delete();
        }
//        val zipFile = ZipFile(zFile)
//        val zipParameters = ZipParameters()
//        zipParameters.isIncludeRootFolder = false
//        zipParameters.compressionLevel = CompressionLevel.ULTRA
//        zipFile.addFolder(projectFolder, zipParameters)
//        return zipFile

        ZipArchiveOutputStream(FileOutputStream(zFile)).use { zipOut ->
            zipOut.setLevel(9) // Set maximum compression level

            // Add the folder contents to the ZIP
            addFolderToZip(projectFolder, projectFolder, zipOut)
        }

        return zFile
    }

    private fun addFolderToZip(baseFolder: File, currentFolder: File, zipOut: ZipArchiveOutputStream) {
        val files = currentFolder.listFiles() ?: return
        for (file in files) {
            val entryName = baseFolder.toPath().relativize(file.toPath()).toString().replace("\\", "/") // Normalize path for ZIP format

            if (file.isDirectory) {
                // Add directory entry (required to preserve folder structure)
                val dirEntry = ZipArchiveEntry(file, "$entryName/")
                zipOut.putArchiveEntry(dirEntry)
                zipOut.closeArchiveEntry()

                // Recursively add subdirectories and files
                addFolderToZip(baseFolder, file, zipOut)
            } else {
                // Add file entry
                val fileEntry = ZipArchiveEntry(file, entryName)
                zipOut.putArchiveEntry(fileEntry)
                Files.copy(file.toPath(), zipOut)
                zipOut.closeArchiveEntry()
            }
        }
    }

    /**
     * Decompresses a ZIP file.
     *
     * @param file is a Path representing the .ZIP file to decompress
     * @param originalFilename is a String
     *
     * @return a File containing a directory with the unzipped files
     */
    fun unzip(file: Path, originalFilename: String?): File {
        val destinationFileFile = file.toFile()
        val destinationFolder = File(destinationFileFile.parent, destinationFileFile.nameWithoutExtension)

        org.apache.commons.compress.archivers.zip.ZipFile(destinationFileFile).use { zipFile ->
            zipFile.entries.iterator().forEachRemaining { entry ->
                try {
                    val outFile = File(destinationFolder, entry.getName())
                    if (entry.isDirectory()) {
                        outFile.mkdirs()
                        outFile.setWritable(true) // Set directory writable
                    } else {
                        // Ensure parent directories exist
                        File(outFile.parent).mkdirs()
                        zipFile.getInputStream(entry).use { inputStream ->
                            Files.newOutputStream(outFile.toPath()).use { outputStream ->
                                val buffer = ByteArray(1024)
                                var len: Int
                                while ((inputStream.read(buffer).also { len = it }) > 0) {
                                    outputStream.write(buffer, 0, len)
                                }
                            }
                        }
                    }
                } catch (e: IOException) {
                    throw StorageException("Failed to unzip ${originalFilename}", e)
                }
            }
        }

        return destinationFolder
    }

}
