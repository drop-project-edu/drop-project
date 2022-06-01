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
import java.util.*

enum class JUnitMethodResultType(val value: String) {
    SUCCESS("Success"),
    ERROR("Error"),
    FAILURE("Failure"),
    IGNORED("Ignored"),
    EMPTY("Empty")
}

/**
 * Represents the result of executing a certain JUnit Test.
 *
 * @property methodName is a String with the name of the test function
 * @property fullMethodName is a String with the full name of the test function (i.e. includes the package name)
 * @property type is the type of the result. Possible values are "Success", "Error" or "Failure"
 * @property failureType is a a String
 * @property failureErrorLine is a String
 * @property failureDetail is a String
 */
data class JUnitMethodResult(val methodName: String,
                             val fullMethodName: String,
                             val type: JUnitMethodResultType,
                             val failureType: String?,
                             val failureErrorLine: String?,
                             val failureDetail: String?) {

    companion object {
        fun empty(): JUnitMethodResult {
            return JUnitMethodResult("", "", JUnitMethodResultType.EMPTY, null, null, null)
        }
    }

    internal val failureDetailLines = failureDetail?.lines()?.filter{ it.trim().isNotEmpty() }?.toMutableList()

    fun filterStacktrace(packageName: String) {
        failureDetailLines?.removeIf { it.trimStart().startsWith("at") && !it.contains(packageName) }
    }

    fun getClassName(): String {
        return fullMethodName.removeSuffix(".${methodName}").split(".").last()
    }

    override fun toString(): String {
        return "${type.value.uppercase(Locale.getDefault())}: ${fullMethodName}\n${failureDetailLines?.joinToString("\n")}\n\n"
    }
}

/**
 * Represents the JUnit Test results for a certain Test class.
 *
 * @property testClassNam is a String, identifying the Test class (e.g. TestTeacher-01.java)
 * @property fullTestClassName is a String, with the Test class name prefixed by the name of the respective package
 * @property numTests is an Int with the number of unit tests that were executed
 * @property numErrors is an Int with the number of unit tests that resulting in an error
 * @property numFailures is an Int with the number of unit tests that failed
 * @property timeEllapsed is a Float, representing the time that it took to execute the Test class
 * @property junitMethodResults is a List of [JUnitMethodResult] with the result each unit test
 */
data class JUnitResults(val testClassName: String,
                        val fullTestClassName: String,
                        val numTests: Int,
                        val numErrors: Int,
                        val numFailures: Int,
                        val numSkipped: Int,
                        val timeEllapsed: Float,
                        val junitMethodResults: List<JUnitMethodResult>) {

    /**
     * Determines if the Test class contains *public* tests implemented by the teacher. Public tests are tests that are
     * designed for their results to be fully and always visible to the students that performed the [Submission].
     *
     * @param assignment is an [Assignment]
     * @return a Boolean with the value true iif the test class represents public teacher tests; false otherwise
     */
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

    /**
     * Determines if the Test class contains *private* tests implemented by the teacher. Private tests are tests that
     * are designed for their results to be invisible, or only partially visible, to the students that performed the
     * [Submission].
     *
     * @return a Boolean with the value true iif the test class represents private teacher tests; false otherwise
     */
    fun isTeacherHidden() : Boolean {
        return testClassName.startsWith(Constants.TEACHER_HIDDEN_TEST_NAME_PREFIX)
    }

    /**
     * Determines if the Test class contains tests implemented by the students performing the [Submission].
     *
     * @return a Boolean with the value true iif the test class represents student tests; false otherwise
     */
    fun isStudent(assignment: Assignment) : Boolean {
        if (assignment.acceptsStudentTests) {
            return !testClassName.startsWith(Constants.TEACHER_TEST_NAME_PREFIX) &&
                    !testClassName.startsWith(Constants.TEACHER_HIDDEN_TEST_NAME_PREFIX)
        } else {
            return false
        }
    }
}

/**
 * Utility for parsing JUnit test results a String.
 */
@Service
class JunitResultsParser {

    /**
     * Parses from a String the test results of testing a single Test class.
     *
     * @param content is a String containing the contents of an XML file with a JUnit report.
     *
     * @return a [JUnitResults]
     */
    fun parseXml(content: String) : JUnitResults {
        val parser = TestSuiteXmlParser(PrintStreamLogger(System.out))
        val byteArrayIs = ByteArrayInputStream(content.toByteArray())
        val parseResults: List<ReportTestSuite> = parser.parse(InputStreamReader(byteArrayIs, Charsets.UTF_8))

        assert(parseResults.size == 1)

        val parseResult = parseResults[0]
        val testCases : List<ReportTestCase> = parseResult.testCases

        val junitMethodResults = testCases.map { JUnitMethodResult(it.name, it.fullName,
                if (it.hasError()) JUnitMethodResultType.ERROR
                else if (it.hasFailure()) JUnitMethodResultType.FAILURE
                else if (it.hasSkipped()) JUnitMethodResultType.IGNORED else JUnitMethodResultType.SUCCESS,
                it.failureType, it.failureErrorLine,
                it.failureDetail) }.toList()

        return JUnitResults(parseResult.name, parseResult.fullClassName,
                parseResult.numberOfTests - parseResult.numberOfSkipped,
                parseResult.numberOfErrors, parseResult.numberOfFailures, parseResult.numberOfSkipped,
                parseResult.timeElapsed, junitMethodResults)
    }
}
