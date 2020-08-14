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

import org.apache.maven.plugin.surefire.log.api.PrintStreamLogger
import org.apache.maven.plugins.surefire.report.ReportTestCase
import org.apache.maven.plugins.surefire.report.TestSuiteXmlParser
import org.springframework.stereotype.Service
import java.io.InputStreamReader
import org.apache.maven.plugins.surefire.report.ReportTestSuite
import org.dropProject.Constants
import org.dropProject.dao.Assignment
import java.io.ByteArrayInputStream

data class JUnitMethodResult(val methodName: String,
                             val fullMethodName: String,
                             val type: String,  // Success, Error or Failure
                             val failureType: String?,
                             val failureErrorLine: String?,
                             val failureDetail: String?) {

    companion object {
        fun empty(): JUnitMethodResult {
            return JUnitMethodResult("", "", "Empty", null, null, null)
        }
    }

    internal val failureDetailLines = failureDetail?.lines()?.toMutableList()

    fun filterStacktrace(packageName: String) {
        failureDetailLines?.removeIf { it.startsWith("\tat") && !it.contains(packageName) }
    }

    fun getClassName(): String {
        return fullMethodName.removeSuffix(".${methodName}").split(".").last()
    }

    override fun toString(): String {
        return "${type.toUpperCase()}: ${fullMethodName}\n${failureDetailLines?.joinToString("\n")}\n\n"
    }
}

data class JUnitResults(val testClassName: String,
                        val fullTestClassName: String,
                        val numTests: Int,
                        val numErrors: Int,
                        val numFailures: Int,
                        val numSkipped: Int,
                        val timeEllapsed: Float,
                        val junitMethodResults: List<JUnitMethodResult>) {

    fun isTeacherPublic(assignment: Assignment) : Boolean {
        if (testClassName.startsWith(Constants.TEACHER_HIDDEN_TEST_NAME_PREFIX)) {
            return false
        }

        if (assignment.acceptsStudentTests) {
            return testClassName.startsWith(Constants.TEACHER_TEST_NAME_PREFIX)
        } else {
            return true
        }
    }

    fun isTeacherHidden() : Boolean {
        return testClassName.startsWith(Constants.TEACHER_HIDDEN_TEST_NAME_PREFIX)
    }

    fun isStudent(assignment: Assignment) : Boolean {
        if (assignment.acceptsStudentTests) {
            return !testClassName.startsWith(Constants.TEACHER_TEST_NAME_PREFIX) &&
                    !testClassName.startsWith(Constants.TEACHER_HIDDEN_TEST_NAME_PREFIX)
        } else {
            return false
        }
    }
}

@Service
class JunitResultsParser {

    fun parseXml(content: String) : JUnitResults {
        val parser = TestSuiteXmlParser(PrintStreamLogger(System.out))
        val byteArrayIs = ByteArrayInputStream(content.toByteArray())
        val parseResults: List<ReportTestSuite> = parser.parse(InputStreamReader(byteArrayIs, Charsets.UTF_8))

        assert(parseResults.size == 1)

        val parseResult = parseResults[0]
        val testCases : List<ReportTestCase> = parseResult.testCases

        val junitMethodResults = testCases.map { JUnitMethodResult(it.name, it.fullName,
                if (it.hasError()) "Error" else if (it.hasFailure()) "Failure" else "Success",
                it.failureType, it.failureErrorLine,
                it.failureDetail) }.toList()

        return JUnitResults(parseResult.name, parseResult.fullClassName,
                parseResult.numberOfTests - parseResult.numberOfSkipped,
                parseResult.numberOfErrors, parseResult.numberOfFailures, parseResult.numberOfSkipped,
                parseResult.timeElapsed, junitMethodResults)
    }
}
