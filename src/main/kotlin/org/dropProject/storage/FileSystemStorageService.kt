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
package org.dropProject.storage

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.model.UnzipParameters
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption


fun unzip(file: Path, originalFilename: String?): File {
    val destinationFileFile = file.toFile()
    val destinationFolder = File(destinationFileFile.parent, destinationFileFile.nameWithoutExtension)
    try {
        val zipFile = ZipFile(destinationFileFile)
        zipFile.extractAll(destinationFolder.absolutePath)
    } catch (e: ZipException) {
        throw StorageException("Failed to unzip ${originalFilename}", e)
    }
    return destinationFolder
}

@Service
class FileSystemStorageService : StorageService  {

    @Value("\${storage.rootLocation}")
    val rootLocation : String = "submissions"

    override fun store(file: MultipartFile) : File? {
        val filename = StringUtils.cleanPath(file.originalFilename)
        try {
            if (file.isEmpty) {
                throw StorageException("Failed to store empty file ${filename}")
            }
            if (filename.contains("..")) {
                // This is a security check
                throw StorageException("Cannot store file with relative path outside current directory ${filename}")
            }

            val destinationFile = Paths.get("$rootLocation/upload").resolve("${System.currentTimeMillis()}-${filename}")
            Files.copy(file.inputStream,
                    destinationFile,
                    StandardCopyOption.REPLACE_EXISTING)

            if (filename.endsWith(".zip", ignoreCase = true)) {
                val destinationFolder = unzip(destinationFile, filename)

                return destinationFolder
            }

        } catch (e: IOException) {
            throw StorageException("Failed to store file " + filename, e)
        }

        return null
    }



    override fun init() {
        try {
            Files.createDirectories(Paths.get("$rootLocation/upload"))
        } catch (e: IOException) {
            throw StorageException("Could not initialize storage", e)
        }

    }

    override fun retrieveProjectFolder(submissionId: String?): File? {
        return File(File(rootLocation,"upload"), submissionId)
    }
}
