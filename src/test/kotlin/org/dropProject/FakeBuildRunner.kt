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
 * A successful java build where every registered test passes.
 *
 * @property passingTests maps a full test class name (e.g. com.acme.TestTeacherProject) to the names
 * of its (passing) test methods
 */
class FakeBuild(private val passingTests: Map<String, List<String>>) {

    fun writeSurefireReports(mavenizedProjectFolder: File) {
        val reportsFolder = File(mavenizedProjectFolder, "target/surefire-reports")
        reportsFolder.mkdirs()

        for ((fullClassName, methodNames) in passingTests) {
            val testCases = methodNames.joinToString("\n") {
                """  <testcase name="$it" classname="$fullClassName" time="0.01"/>"""
            }
            File(reportsFolder, "TEST-$fullClassName.xml").writeText(
                """<?xml version="1.0" encoding="UTF-8"?>""" + "\n" +
                """<testsuite name="$fullClassName" time="0.05" tests="${methodNames.size}" errors="0" skipped="0" failures="0">""" + "\n" +
                testCases + "\n" +
                "</testsuite>\n"
            )
        }
    }

    // the minimal output that BuildReport recognizes as a successful build with an active
    // (and passing) checkstyle validation
    fun mavenOutputLines(): List<String> {
        val numTests = passingTests.values.sumOf { it.size }
        return listOf(
            "[INFO] Scanning for projects...",
            "[INFO] --- maven-checkstyle-plugin:3.1.1:check (checkstyle-check) @ fake-project ---",
            "[INFO] Starting audit...",
            "Audit done.",
            "[INFO] --- maven-surefire-plugin:3.5.3:test (default-test) @ fake-project ---",
            "[INFO] Tests run: $numTests, Failures: 0, Errors: 0, Skipped: 0",
            "[INFO] BUILD SUCCESS"
        )
    }
}
