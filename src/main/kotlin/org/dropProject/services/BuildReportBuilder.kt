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
package org.dropproject.services

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.dropproject.dao.Assignment
import org.dropproject.dao.JUnitReport
import org.dropproject.dao.JacocoReport
import org.dropproject.dao.Submission
import org.dropproject.data.BuildReport
import org.dropproject.repository.AssignmentTestMethodRepository
import org.dropproject.repository.JUnitReportRepository
import org.dropproject.repository.JacocoReportRepository
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileNotFoundException
import java.util.logging.Logger

/**
 * This class contains functions that perform the creation of [BuildReport]s for both [Assignment]s and [Submission]s.
 */
@Service
class BuildReportBuilder(
    val junitResultsParser: JunitResultsParser,
    val jacocoResultsParser: JacocoResultsParser,
    val jUnitReportRepository: JUnitReportRepository,
    val jacocoReportRepository: JacocoReportRepository
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    /**
     * Builds a BuildReport
     *
     * @param mavenOutputLines is a List of String with the output of a Maven build process
     * @param mavenizedProjectFolder is a String
     * @param assignment is an [Assignment]
     * @param submission is a [Submission]
     *
     * @return a [BuildReport]
     */
    fun build(mavenOutputLines: List<String>,
              mavenizedProjectFolder: String,
              assignment: Assignment,
              submission: Submission? = null) : BuildReport {

        val junitReportFromDB : List<JUnitReport>? =
                if (submission != null) jUnitReportRepository.findBySubmissionId(submission.id)
                else null

        val jUnitResults =
                if (junitReportFromDB != null && !junitReportFromDB.isEmpty()) {
                    // LOG.info("Got jUnit Report from DB")
                    junitReportFromDB
                            .map { it -> junitResultsParser.parseXml(it.xmlReport) }
                            .toList()
                } else {
                    try {
                        // LOG.info("Got jUnit Report from File System")
                        File("${mavenizedProjectFolder}/target/surefire-reports")
                                .walkTopDown()
                                .filter { it -> it.name.endsWith(".xml") }
                                .map { it -> junitResultsParser.parseXml(it.readText()) }
                                .toList()
                    } catch (e: FileNotFoundException) {
                        LOG.info("Not found ${mavenizedProjectFolder}/target/surefire-reports. Probably this assignment doesn't produce test results")
                        emptyList<JUnitResults>()
                    }
                }

        val jacocoReportFromDB : List<JacocoReport>? =
                if (submission != null) jacocoReportRepository.findBySubmissionId(submission.id)
                else null

        val jacocoResults =
            if (jacocoReportFromDB != null && !jacocoReportFromDB.isEmpty()) {
                // LOG.info("Got Jacoco Report from DB")
                jacocoReportFromDB
                        .map { it -> jacocoResultsParser.parseCsv(it.csvReport) }
                        .toList()

            } else {
                emptyList<JacocoResults>()
            }

        return BuildReport(mavenOutputLines, mavenizedProjectFolder, assignment, jUnitResults, jacocoResults)
    }
}
