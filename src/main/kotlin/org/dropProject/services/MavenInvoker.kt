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

import org.apache.maven.shared.invoker.DefaultInvocationRequest
import org.apache.maven.shared.invoker.DefaultInvoker
import org.codehaus.plexus.util.cli.CommandLineTimeOutException
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.dropProject.data.MavenResult
import java.io.File
import java.security.Principal
import java.util.*
import java.util.logging.Logger


@Service
class MavenInvoker {

    val LOG = Logger.getLogger(this.javaClass.name)

    @Value("\${dropProject.maven.home}")
    val mavenHome : String = ""

    @Value("\${dropProject.maven.repository}")
    val mavenRepository : String = ""

    fun run(mavenizedProjectFolder: File, principalName: String?) : MavenResult {

        if (!File(mavenRepository).exists()) {
            val success = File(mavenRepository).mkdirs()
            if (!success) {
                LOG.severe("Couldn't create the maven repository folder: $mavenRepository")
            }
        }

        val outputLines = ArrayList<String>()

        val request = DefaultInvocationRequest()
        request.baseDirectory = mavenizedProjectFolder
        request.isDebug = false
        request.isBatchMode = true
        if (principalName != null) {
            request.mavenOpts = "-Ddp.argLine=\"-DdropProject.currentUserId=${principalName}\""
            // the above line only works if the surefire plugin is configured this way:
//            <properties>
//               ...
//               <!-- Default value for dp.argLine can be defined here -->
//               <dp.argLine></dp.argLine>
//               ...
//            </properties>

//            <plugin>
//              <groupId>org.apache.maven.plugins</groupId>
//              <artifactId>maven-surefire-plugin</artifactId>
//              <version>2.19.1</version>
//              <configuration>
//                <argLine>@{argLine} ${dp.argLine}</argLine>
//              </configuration>
//            </plugin>
        }

        request.setOutputHandler {
            line -> run {
                // println(">>> ${line}")
                outputLines.add(line)
            }
        }
        request.goals = Arrays.asList("clean", "compile", "test")  // "pmd:check", "checkstyle:check", "exec:java"

        val invoker = DefaultInvoker()
        invoker.mavenHome = File(mavenHome)
        invoker.localRepositoryDirectory = File(mavenRepository)

        val result = invoker.execute(request)

        if (result.exitCode != 0) {
            if (result.executionException != null) {
                if (result.executionException is CommandLineTimeOutException) {
                    LOG.severe("Maven execution too long. Aborting...")
                    return MavenResult(resultCode = result.exitCode, outputLines = outputLines, expiredByTimeout = true)
                } else {
                    LOG.severe("Error: ${result.executionException}")
                }
            } else {
                LOG.warning("Maven invoker ended with result code ${result.exitCode}")
            }
        }

        return MavenResult(resultCode = result.exitCode, outputLines = outputLines)
    }
}
