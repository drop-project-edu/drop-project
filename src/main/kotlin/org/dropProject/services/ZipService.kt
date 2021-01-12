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

import net.lingala.zip4j.core.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.util.Zip4jConstants
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.dropProject.dao.Assignment
import org.dropProject.dao.GitSubmission
import org.dropProject.dao.Submission
import org.dropProject.data.SubmissionInfo
import org.dropProject.data.TestType
import org.dropProject.repository.*
import java.io.File
import java.util.ArrayList

/**
 * Utility to create ZIP files based on folder contents.
 */
@Service
class ZipService {

    fun createZipFromFolder(zipFilename: String, projectFolder: File): ZipFile {
        val zFile = File.createTempFile(zipFilename, ".zip")
        if (zFile.exists()) {
            zFile.delete();
        }
        val zipFile = ZipFile(zFile)
        val zipParameters = ZipParameters()
        zipParameters.isIncludeRootFolder = false
        zipParameters.compressionLevel = Zip4jConstants.DEFLATE_LEVEL_ULTRA
        zipFile.createZipFileFromFolder(projectFolder, zipParameters, false, -1)
        return zipFile
    }

}
