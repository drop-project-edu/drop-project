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
package org.dropproject

import org.dropproject.config.DropProjectProperties
import org.dropproject.data.MavenResult
import org.dropproject.services.MavenInvoker
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import java.io.File

/**
 * Test seam for [MavenInvoker]: a test can register a [FakeBuild] and every build that would run a
 * real Maven process instead writes the corresponding surefire reports and returns a plausible Maven
 * output, taking milliseconds instead of tens of seconds. When no fake build is registered, the real
 * Maven build runs, so existing integration tests are unaffected.
 *
 * The registered fake build is cleared after each test by [ResetStateExtension].
 */
@Service
@Primary
class FakeBuildRunner(dropProjectProperties: DropProjectProperties) : MavenInvoker(dropProjectProperties) {

    private var fakeBuild: FakeBuild? = null

    fun fakeNextBuilds(fakeBuild: FakeBuild) {
        this.fakeBuild = fakeBuild
    }

    fun reset() {
        fakeBuild = null
    }

    override fun run(mavenizedProjectFolder: File, principalName: String?, maxMemoryMb: Int?,
                     submissionId: Long?): MavenResult {
        val fake = fakeBuild ?: return super.run(mavenizedProjectFolder, principalName, maxMemoryMb, submissionId)

        fake.writeSurefireReports(mavenizedProjectFolder)
        return MavenResult(resultCode = 0, outputLines = fake.mavenOutputLines())
    }
}

/**
 * A java build where every registered test runs, passing or failing as registered.
 *
 * @property passingTests maps a full test class name (e.g. com.acme.TestTeacherProject) to the names
 * of its passing test methods
 * @property failingTests maps a full test class name to the names of its failing test methods. A class
 * can appear in both maps, since a test class usually fails only some of its tests
 */
class FakeBuild(private val passingTests: Map<String, List<String>>,
                private val failingTests: Map<String, List<String>> = emptyMap()) {

    fun writeSurefireReports(mavenizedProjectFolder: File) {
        val reportsFolder = File(mavenizedProjectFolder, "target/surefire-reports")
        reportsFolder.mkdirs()

        for (fullClassName in passingTests.keys + failingTests.keys) {
            val passed = passingTests[fullClassName].orEmpty()
            val failed = failingTests[fullClassName].orEmpty()

            val testCases = (passed.map { """  <testcase name="$it" classname="$fullClassName" time="0.01"/>""" } +
                    failed.map {
                        """  <testcase name="$it" classname="$fullClassName" time="0.01">""" + "\n" +
                        """    <failure message="expected:&lt;1&gt; but was:&lt;2&gt;" type="java.lang.AssertionError">""" +
                        """java.lang.AssertionError: expected:&lt;1&gt; but was:&lt;2&gt;""" + "\n" +
                        """	at $fullClassName.$it($fullClassName.java:1)""" + "\n" +
                        """    </failure>""" + "\n" +
                        """  </testcase>"""
                    }).joinToString("\n")

            File(reportsFolder, "TEST-$fullClassName.xml").writeText(
                """<?xml version="1.0" encoding="UTF-8"?>""" + "\n" +
                """<testsuite name="$fullClassName" time="0.05" tests="${passed.size + failed.size}" errors="0" skipped="0" failures="${failed.size}">""" + "\n" +
                testCases + "\n" +
                "</testsuite>\n"
            )
        }
    }

    // the minimal output that BuildReport recognizes as a build that ran to the end, with an active
    // (and passing) checkstyle validation
    fun mavenOutputLines(): List<String> {
        val numPassed = passingTests.values.sumOf { it.size }
        val numFailed = failingTests.values.sumOf { it.size }
        return listOf(
            "[INFO] Scanning for projects...",
            "[INFO] --- maven-checkstyle-plugin:3.1.1:check (checkstyle-check) @ fake-project ---",
            "[INFO] Starting audit...",
            "Audit done.",
            "[INFO] --- maven-surefire-plugin:3.5.3:test (default-test) @ fake-project ---",
            "[INFO] Tests run: ${numPassed + numFailed}, Failures: $numFailed, Errors: 0, Skipped: 0",
            if (numFailed == 0) "[INFO] BUILD SUCCESS" else "[INFO] BUILD FAILURE"
        )
    }
}
