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

import org.apache.maven.plugins.surefire.report.ReportTestSuite
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import kotlin.math.round


data class JacocoResults(val linesMissed: Int, val linesCovered: Int, val lineCoveragePercent: Int)

@Service
class JacocoResultsParser {

    fun parseCsv(content: String): JacocoResults {

        val byteArrayIs = ByteArrayInputStream(content.toByteArray())
        val reader = BufferedReader(InputStreamReader(byteArrayIs, Charsets.UTF_8))

        var linesMissed = 0
        var linesCovered = 0

        reader.lines()
                .skip(1)  // ignore first line since it's the header
                .forEach {
                    val parts = it.split(",")
                    linesMissed += parts[7].toInt()
                    linesCovered += parts[8].toInt()
                }

        val lineCoveragePercent = round((linesCovered.toDouble() / (linesMissed + linesCovered)) * 100).toInt()

        return JacocoResults(linesMissed, linesCovered, lineCoveragePercent)
    }
}
