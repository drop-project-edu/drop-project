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
package org.dropProject.data

import org.dropProject.dao.Assignment
import org.dropProject.dao.Language
import org.dropProject.services.JUnitResults
import org.dropProject.services.JacocoResults
import java.math.BigDecimal
import java.util.logging.Logger

enum class TestType {
    STUDENT, TEACHER, HIDDEN
}

data class BuildReport(val mavenOutputLines: List<String>,
                       val mavenizedProjectFolder: String,
                       val assignment: Assignment,
                       val junitResults: List<JUnitResults>,
                       val jacocoResults: List<JacocoResults>) {

    val LOG = Logger.getLogger(this.javaClass.name)

    fun mavenOutput() : String {
        return mavenOutputLines.joinToString(separator = "\n")
    }

    fun mavenExecutionFailed() : Boolean {

        // if it has a failed goal other than compiler or surefire (junit), it is a fatal error

        if (mavenOutputLines.
                        filter { it.startsWith("[ERROR] Failed to execute goal org.apache.maven.plugins:") }.isNotEmpty()) {
            return mavenOutputLines.filter {
                        it.startsWith("[ERROR] Failed to execute goal org.apache.maven.plugins:maven-surefire-plugin") ||
                        it.startsWith("[ERROR] Failed to execute goal org.apache.maven.plugins:maven-compiler-plugin")
            }.isEmpty()
        }

        return false;
    }

    fun compilationErrors() : List<String> {

        var errors = ArrayList<String>()

        val folder = if (assignment.language == Language.JAVA) "java" else "kotlin"

        // parse compilation errors
        run {
            val triggerStartOfCompilationOutput =
                    if (assignment.language == Language.JAVA)
                        "\\[ERROR\\] COMPILATION ERROR :.*".toRegex()
                    else
                        "\\[INFO\\] --- kotlin-maven-plugin:\\d+\\.\\d+\\.\\d+:compile.*".toRegex()

            var startIdx = -1;
            var endIdx = -1;
            for ((idx, mavenOutputLine) in mavenOutputLines.withIndex()) {
                if (triggerStartOfCompilationOutput.matches(mavenOutputLine)) {
                    startIdx = idx + 1
                    LOG.fine("Found start of compilation output (line $idx)")
                } else if (startIdx > 0) {
                    if (mavenOutputLine.startsWith("[INFO] BUILD FAILURE") ||
                            mavenOutputLine.startsWith("[INFO] --- ")) {    // no compilation errors on Kotlin
                        endIdx = idx
                        LOG.fine("Found end of compilation output (line $idx)")
                        break
                    }
                }
            }

            if (startIdx > 0 && endIdx > startIdx) {
                errors.addAll(
                        mavenOutputLines
                                .subList(startIdx, endIdx)
                                .filter { it -> it.startsWith("[ERROR] ") || it.startsWith("  ") }
                                .map { it -> it.replace("[ERROR] ${mavenizedProjectFolder}/src/main/${folder}/", "") }
                                .map { it -> it.replace("[ERROR] ${mavenizedProjectFolder}/src/test/${folder}/", "[TEST] ") })
            }
        }

        // parse test compilation errors
        run {
            val triggerStartOfTestCompilationOutput =
//                    if (language == Language.JAVA) "???" else
                        "\\[ERROR\\] Failed to execute goal org\\.jetbrains\\.kotlin:kotlin-maven-plugin.*test-compile.*".toRegex()

            var startIdx = -1;
            var endIdx = -1;
            for ((idx, mavenOutputLine) in mavenOutputLines.withIndex()) {
                if (triggerStartOfTestCompilationOutput.matches(mavenOutputLine)) {
                    startIdx = idx + 1
                }
                if (mavenOutputLine.startsWith("[ERROR] -> [Help 1]")) {
                    endIdx = idx
                }
            }

            if (startIdx > 0) {
                errors.addAll(
                        mavenOutputLines
                                .subList(startIdx, endIdx)
                                .filter { it -> it.startsWith("[ERROR] ") || it.startsWith("  ") }
                                .map { it -> it.replace("[ERROR] ${mavenizedProjectFolder}/src/main/${folder}/", "") }
                                .map { it -> it.replace("[ERROR] ${mavenizedProjectFolder}/src/test/${folder}/", "[TEST] ") })
            }
        }

        return errors
    }

    fun checkstyleErrors() : List<String> {

        val folder = if (assignment.language == Language.JAVA) "java" else "kotlin"

        when (assignment.language) {
            Language.JAVA -> {
                var startIdx = -1;
                var endIdx = -1;
                for ((idx, mavenOutputLine) in mavenOutputLines.withIndex()) {
                    if (mavenOutputLine.startsWith("[INFO] Starting audit...")) {
                        startIdx = idx + 1
                    }
                    if (mavenOutputLine.startsWith("Audit done.")) {
                        endIdx = idx
                    }
                }

                if (startIdx > 0) {
                    return mavenOutputLines
                            .subList(startIdx, endIdx)
                            .filter { it -> it.startsWith("[WARN] ") }
                            .map { it -> it.replace("[WARN] ${mavenizedProjectFolder}/src/main/${folder}/", "") }
                } else {
                    return emptyList()
                }
            }

            Language.KOTLIN -> {
                var startIdx = -1;
                var endIdx = -1;
                for ((idx, mavenOutputLine) in mavenOutputLines.withIndex()) {
                    if (mavenOutputLine.startsWith("[INFO] --- detekt-maven-plugin")) {
                        startIdx = idx + 1
                    }
                    if (idx > startIdx && mavenOutputLine.startsWith("detekt finished")) {
                        endIdx = idx
                    }
                }

                if (startIdx > 0) {
                    return mavenOutputLines
                            .subList(startIdx, endIdx)
                            .filter { it -> it.startsWith("\t") }
                            .map { it -> it.replace("\t", "") }
                            .map { it -> it.replace("${mavenizedProjectFolder}/src/main/${folder}/", "") }
                            .map { it -> translateDetektError(it) }
                } else {
                    return emptyList()
                }
            }
        }
    }

    fun checkstyleValidationActive() : Boolean {
        when (assignment.language) {
            Language.JAVA -> {
                for (mavenOutputLine in mavenOutputLines) {
                    if (mavenOutputLine.startsWith("[INFO] Starting audit...")) {
                        return true
                    }
                }

                return false
            }
            Language.KOTLIN -> {
                for (mavenOutputLine in mavenOutputLines) {
                    if (mavenOutputLine.startsWith("[INFO] --- detekt-maven-plugin")) {
                        return true
                    }
                }

                return false
            }
        }
    }

    fun PMDerrors() : List<String> {
        return mavenOutputLines
                .filter { it -> it.startsWith("[INFO] PMD Failure") }
                .map { it -> it.substring(19) }  // to remove "[INFO] PMD Failure: "
    }

    fun junitSummary(testType: TestType = TestType.TEACHER) : String? {

        val junitSummary = junitSummaryAsObject(testType)
        if (junitSummary != null) {
            return "Tests run: ${junitSummary.numTests}, Failures: ${junitSummary.numFailures}, " +
                    "Errors: ${junitSummary.numErrors}, Time elapsed: ${junitSummary.ellapsed} sec"
        } else {
            return null
        }
    }

    fun junitSummaryAsObject(testType: TestType = TestType.TEACHER) : JUnitSummary? {

        if (junitResults
                .filter{testType == TestType.TEACHER && it.isTeacherPublic(assignment) ||
                        testType == TestType.STUDENT && it.isStudent(assignment) ||
                        testType == TestType.HIDDEN && it.isTeacherHidden()}
                .isEmpty()) {
            return null
        }

        var totalTests = 0
        var totalErrors = 0
        var totalFailures = 0
        var totalElapsed = 0.0f

        for (junitResult in junitResults) {

            if (testType == TestType.TEACHER && junitResult.isTeacherPublic(assignment) ||
                    testType == TestType.STUDENT && junitResult.isStudent(assignment) ||
                    testType == TestType.HIDDEN && junitResult.isTeacherHidden()) {
                totalTests += junitResult.numTests
                totalErrors += junitResult.numErrors
                totalFailures += junitResult.numFailures
                totalElapsed += junitResult.timeEllapsed
            }
        }

        return JUnitSummary(totalTests, totalFailures, totalErrors, totalElapsed)

    }

    fun elapsedTimeJUnit() : BigDecimal? {
        var total : BigDecimal? = null
        val junitSummaryTeacher = junitSummaryAsObject(TestType.TEACHER)
        if (junitSummaryTeacher != null) {
            total = junitSummaryTeacher.ellapsed.toBigDecimal()
        }

        val junitSummaryHidden = junitSummaryAsObject(TestType.HIDDEN)
        if (junitSummaryHidden != null && total != null) {
            total += junitSummaryHidden.ellapsed.toBigDecimal()
        }

        return total
    }


    fun hasJUnitErrors(testType: TestType = TestType.TEACHER) : Boolean? {

        val junitSummary = junitSummaryAsObject(testType)
        if (junitSummary != null) {
            return junitSummary.numErrors > 0 || junitSummary.numFailures > 0
        } else {
            return null
        }
    }

    fun jUnitErrors(testType: TestType = TestType.TEACHER) : String? {

        var result = ""
        for (junitResult in junitResults) {
            if (testType == TestType.TEACHER && junitResult.isTeacherPublic(assignment) ||
                    testType == TestType.STUDENT && junitResult.isStudent(assignment) ||
                    testType == TestType.HIDDEN && junitResult.isTeacherHidden()) {
                result += junitResult.junitMethodResults
                        .filter { it.type != "Success" }
                        .map { it.filterStacktrace(assignment.packageName.orEmpty()); it }
                        .joinToString(separator = "\n")
            }
        }

        if (result.isEmpty()) {
            return null
        } else {
            return result
        }

//        if (hasJUnitErrors() == true) {
//            val testReport = File("${mavenizedProjectFolder}/target/surefire-reports")
//                    .walkTopDown()
//                    .filter { it -> it.name.endsWith(".txt") }
//                    .map { it -> String(Files.readAllBytes(it.toPath()))  }
//                    .joinToString(separator = "\n")
//            return testReport
//        }
//        return null
    }


    fun notEnoughStudentTestsMessage() : String? {

        if (!assignment.acceptsStudentTests) {
            throw IllegalArgumentException("This method shouldn't have been called!")
        }

        val junitSummary = junitSummaryAsObject(TestType.STUDENT)

        if (junitSummary == null) {
            return "The submission doesn't include unit tests. " +
                    "The assignment requires a minimum of ${assignment.minStudentTests} tests."
        }

        if (junitSummary.numTests < assignment.minStudentTests!!) {
            return "The submission only includes ${junitSummary.numTests} unit tests. " +
                    "The assignment requires a minimum of ${assignment.minStudentTests} tests."
        }

        return null
    }

    private fun translateDetektError(originalError: String) : String {

        return originalError
                .replace("VariableNaming -", "Nome da variável deve começar por letra minúscula. " +
                        "Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula) -")
                .replace("FunctionNaming -", "Nome da função deve começar por letra minúscula. " +
                        "Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula) -")
                .replace("FunctionParameterNaming -", "Nome do parâmetro de função deve começar por letra minúscula. " +
                        "Caso o nome tenha mais do que uma palavra, as palavras seguintes devem ser capitalizadas (iniciadas por uma maiúscula) -")
                .replace("VariableMinLength -", "Nome da variável demasiado pequeno -")
                .replace("VarCouldBeVal -", "Variável imutável declarada com var -")
                .replace("MandatoryBracesIfStatements -", "Instrução 'if' sem chaveta -")
                .replace("ComplexCondition -", "Condição demasiado complexa -")
                .replace("StringLiteralDuplication -", "String duplicada. Deve ser usada uma constante -")
                .replace("NestedBlockDepth -", "Demasiados níveis de blocos dentro de blocos -")
                .replace("UnsafeCallOnNullableType -", "Não é permitido usar o !! pois pode causar crashes -")
                .replace("MaxLineLength -", "Linha demasiado comprida -")
                .replace("LongMethod -", "Função com demasiadas linhas de código -")
                .replace("ForbiddenKeywords -", "Utilização de instruções proibidas -")
    }
}
