/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2026 Pedro Alves
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
package org.dropproject.services

import org.apache.commons.io.FileUtils
import org.dropproject.config.DropProjectProperties
import org.dropproject.dao.Assignment
import org.dropproject.dao.Language
import org.dropproject.dao.SubmissionStructure
import org.dropproject.controllers.ResourceNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap

/**
 * Builds the zip with the reference solution of an assignment, i.e. the code under "src/main" of the teacher's
 * repository, laid out as a student submission. It is what the students of a continuation start from when they have
 * no submission of their own to the assignment it continues.
 *
 * Each zip is only built the first time it is asked for, and kept in a cache folder, where it is identified by the
 * commit of the teacher's repository it was built from. So a refresh of that repository makes the next download build
 * a new one, and nobody is ever served an outdated solution.
 */
@Service
class ReferenceSolutionService(
    val dropProjectProperties: DropProjectProperties,
    val zipService: ZipService
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    // one lock per assignment, so that the students who ask for the same zip at the same time (e.g. at the start of a
    // class) wait for a single build, instead of each building their own
    private val locks = ConcurrentHashMap<String, Any>()

    /**
     * The zip with the reference solution of [baseAssignment], in the [SubmissionStructure] that [assignment] (the
     * continuation of [baseAssignment]) expects its submissions in.
     *
     * @throws ResourceNotFoundException if the repository of [baseAssignment] was never cloned
     */
    fun referenceSolutionZip(assignment: Assignment, baseAssignment: Assignment): File {
        val cacheFolder = cacheFolderOf(baseAssignment.id)
        val zipFile = File(cacheFolder,
            "${baseAssignment.gitCurrentHash ?: "unknown"}-${assignment.submissionStructure.name.lowercase()}.zip")

        if (zipFile.exists()) {
            return zipFile
        }

        synchronized(locks.computeIfAbsent(baseAssignment.id) { Any() }) {
            // another request may have built it while this one was waiting
            if (!zipFile.exists()) {
                build(baseAssignment, assignment.submissionStructure, cacheFolder, zipFile)
            }
        }

        return zipFile
    }

    /**
     * Deletes the zips that were built from the assignment identified by [assignmentId], e.g. because it was deleted.
     */
    fun deleteCached(assignmentId: String) {
        cacheFolderOf(assignmentId).deleteRecursively()
    }

    private fun cacheFolderOf(assignmentId: String) =
        File(dropProjectProperties.storage.rootLocation, "reference-solutions/${assignmentId}")

    private fun build(baseAssignment: Assignment, structure: SubmissionStructure, cacheFolder: File, zipFile: File) {
        val sourceFolder = File(dropProjectProperties.assignments.rootLocation,
            "${baseAssignment.gitRepositoryFolder}/src/main")
        if (!sourceFolder.exists()) {
            throw ResourceNotFoundException()
        }

        val stagingFolder = Files.createTempDirectory("reference-solution-${baseAssignment.id}").toFile()
        try {
            when (structure) {
                // the compact structure only has the source files, directly under "src"
                SubmissionStructure.COMPACT -> {
                    val languageFolder = if (baseAssignment.language == Language.JAVA) "java" else "kotlin"
                    FileUtils.copyDirectory(File(sourceFolder, languageFolder), File(stagingFolder, "src"))
                }
                SubmissionStructure.MAVEN -> FileUtils.copyDirectory(sourceFolder, File(stagingFolder, "src/main"))
            }

            val builtZip = zipService.createZipFromFolder("reference-solution-${baseAssignment.id}", stagingFolder)
            cacheFolder.mkdirs()

            // the zip only appears under its final name once it is complete, so that no one downloads half of it
            val partialZip = File(cacheFolder, "${zipFile.name}.partial")
            Files.move(builtZip.toPath(), partialZip.toPath(), StandardCopyOption.REPLACE_EXISTING)
            Files.move(partialZip.toPath(), zipFile.toPath(), StandardCopyOption.ATOMIC_MOVE)
            LOG.info("Built the reference solution of ${baseAssignment.id} into ${zipFile}")

            // the zips built from older commits are never served again
            cacheFolder.listFiles()
                ?.filter { it.name != zipFile.name && !it.name.startsWith(baseAssignment.gitCurrentHash ?: "unknown") }
                ?.forEach { it.delete() }
        } finally {
            stagingFolder.deleteRecursively()
        }
    }
}
