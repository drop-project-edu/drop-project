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

import org.dropProject.dao.Submission
import org.dropProject.services.ZipService
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.util.StringUtils
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*

@Service
class FileSystemStorageService (val zipService : ZipService) : StorageService {

    @Value("\${storage.rootLocation}/upload")
    val uploadRootLocation : String = "submissions/upload"

    override fun rootFolder(): File {
        return File(uploadRootLocation)
    }

    override fun store(file: MultipartFile, assignmentId: String) : File? {
        val originalFilename = file.originalFilename ?: throw IllegalArgumentException("Missing original filename")
        val filename = StringUtils.cleanPath(originalFilename)
        try {
            if (file.isEmpty) {
                throw StorageException("Failed to store empty file ${filename}")
            }
            if (filename.contains("..")) {
                // This is a security check
                throw StorageException("Cannot store file with relative path outside current directory ${filename}")
            }

            val destinationPartialFolder = File(uploadRootLocation, Submission.relativeUploadFolder(assignmentId, Date()))
            destinationPartialFolder.mkdirs()

            val destinationFile = File(destinationPartialFolder, "${System.currentTimeMillis()}-${filename}")
            Files.copy(file.inputStream,
                    destinationFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING)

            if (filename.endsWith(".zip", ignoreCase = true)) {
                val destinationFolder = zipService.unzip(destinationFile.toPath(), filename)
                return destinationFolder
            } else {
                throw Exception("$filename doesn't end with .zip! This shouldn't happen.")
            }

        } catch (e: IOException) {
            throw StorageException("Failed to store file $filename", e)
        }
    }



    override fun init() {
        try {
            Files.createDirectories(Paths.get("$uploadRootLocation/upload"))
        } catch (e: IOException) {
            throw StorageException("Could not initialize storage", e)
        }

    }

    override fun retrieveProjectFolder(submission: Submission): File? {
        return File(File(uploadRootLocation), submission.submissionFolder)
    }
}
