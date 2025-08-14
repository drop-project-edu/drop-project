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

import org.apache.maven.model.Dependency
import org.apache.maven.model.io.xpp3.MavenXpp3Reader
import org.apache.maven.model.io.xpp3.MavenXpp3Writer
import org.apache.maven.shared.invoker.DefaultInvocationRequest
import org.apache.maven.shared.invoker.DefaultInvoker
import org.dropProject.Constants
import org.dropProject.data.MavenResult
import org.slf4j.LoggerFactory
import org.dropProject.config.DropProjectProperties
import org.springframework.stereotype.Service
import java.io.File
import java.io.FileReader
import java.io.StringWriter
import java.util.*

/**
 * Utility to perform Maven related tasks.
 */
@Service
class MavenInvoker(
    val dropProjectProperties: DropProjectProperties
) {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    var securityManagerEnabled = true

    fun disableSecurity() {
        securityManagerEnabled = false
    }

    var showMavenOutput = false

    /**
     * Runs and project using Maven. This function executes the compilation and testing of a submitted project.
     *
     * @param mavenizedProjectFolder is a File containing the project's files
     * @param principalName is a String
     * @param maxMemoryMb is an Int
     *
     * @return a MavenResult
     */
    fun run(mavenizedProjectFolder: File, principalName: String?, maxMemoryMb: Int?) : MavenResult {

        if (!File(dropProjectProperties.maven.repository).exists()) {
            val success = File(dropProjectProperties.maven.repository).mkdirs()
            if (!success) {
                LOG.error("Couldn't create the maven repository folder: ${dropProjectProperties.maven.repository}")
            }
        }

        val outputLines = ArrayList<String>()

        var dpArgLine = ""
        if (maxMemoryMb != null) {
            dpArgLine += " -Xmx${maxMemoryMb}M"
        }

        val request = DefaultInvocationRequest()
        request.baseDirectory = mavenizedProjectFolder
        request.isDebug = false
        request.isBatchMode = true
        if (principalName != null) {
            dpArgLine += " -DdropProject.currentUserId=${principalName}"
        }

        if (securityManagerEnabled) {
            dpArgLine += " -Djava.security.manager=org.dropproject.security.SandboxSecurityManager"
            dpArgLine += " -DdropProject.maven.repository=${File(dropProjectProperties.maven.repository).absolutePath}"
            // uncomment this to diagnose problems within our custom security manager (SandboxSecurityManager)
//            dpArgLine += " -DdropProject.securityManager.debug=true"
        }

        // TODO: The line below doesn't work form multiple properties. so, for now, we'll replace the property
        //  directly the pom.file
        // request.mavenOpts = "-Ddp.argLine=\"${dpArgLine.trim()}\""

        val replacedPomFile = transformPomFile(mavenizedProjectFolder, dpArgLine)

        request.pomFile = replacedPomFile

        // the above line only works if the surefire plugin is configured this way:
//            <plugin>
//              <groupId>org.apache.maven.plugins</groupId>
//              <artifactId>maven-surefire-plugin</artifactId>
//              <version>2.19.1</version>
//              <configuration>
//                <argLine>${dp.argLine}</argLine>
//              </configuration>
//            </plugin>

        var numLines = 0
        request.setOutputHandler {
            line -> run {
                if (showMavenOutput) {
                    println(">>> ${line}")
                }
                if (!line.startsWith("\tat ")) {  // count lines that are not associated with stacktraces
                    numLines++
                }
                if (numLines < Constants.TOO_MUCH_OUTPUT_THRESHOLD) {
                    outputLines.add(line)
                }
                if (numLines == Constants.TOO_MUCH_OUTPUT_THRESHOLD) {
                    outputLines.add("*** Trimmed here by DP ***")
                }
            }
        }
        request.goals = Arrays.asList("clean", "compile", "test")  // "pmd:check", "checkstyle:check", "exec:java"

        val invoker = DefaultInvoker()
        invoker.mavenHome = File(dropProjectProperties.maven.home)
        invoker.localRepositoryDirectory = File(dropProjectProperties.maven.repository)

        val result = invoker.execute(request)

        replacedPomFile.delete()

        if (result.exitCode != 0) {
            if (result.executionException != null) {
                if (result.executionException is org.apache.maven.shared.utils.cli.CommandLineTimeOutException) {
                    LOG.error("Maven execution too long. Aborting...")
                    return MavenResult(resultCode = result.exitCode, outputLines = outputLines, expiredByTimeout = true)
                } else {
                    LOG.error("Error: ${result.executionException}")
                }
            } else {
                LOG.warn("Maven invoker ended with result code ${result.exitCode}")
            }
        }

        return MavenResult(resultCode = result.exitCode, outputLines = outputLines)
    }

    private fun transformPomFile(mavenizedProjectFolder: File, dpArgLine: String): File {
        val reader = MavenXpp3Reader()
        val model = reader.read(FileReader(File(mavenizedProjectFolder, "pom.xml")))

        if (securityManagerEnabled) {
            val securityManagerDependency = Dependency()
            securityManagerDependency.artifactId = "drop-project-security-manager"
            securityManagerDependency.groupId = "org.dropproject"
            securityManagerDependency.version = "0.2.5 "
            securityManagerDependency.scope = "test"
            model.dependencies.add(securityManagerDependency)
        }

        val stringWriter = StringWriter()
        MavenXpp3Writer().write(stringWriter, model)

        var replacedPomFileContent = stringWriter.buffer.toString()
        replacedPomFileContent = replacedPomFileContent.replace("\${dp.argLine}", dpArgLine.trim())

        val replacedPomFile = File(mavenizedProjectFolder, "pom_updated.xml")
        replacedPomFile.writeText(replacedPomFileContent)
        return replacedPomFile
    }
}
