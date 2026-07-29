/*-
 * ========================LICENSE_START=================================
 * DropProject
 * %%
 * Copyright (C) 2019 - 2025 Pedro Alves
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
package org.dropproject.config

import jakarta.validation.Valid
import jakarta.validation.constraints.Min
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * Configuration properties for DropProject application.
 * 
 * This class consolidates all core DropProject-specific configuration properties
 * under the "drop-project" prefix for better organization and type safety.
 * 
 * Note: LTI properties are kept separate and continue to use @Value annotations
 * as they are profile-specific and managed differently.
 */
@Validated
@ConfigurationProperties(prefix = "drop-project")
data class DropProjectProperties(
    
    /** Storage configuration */
    val storage: Storage = Storage(),
    
    /** Assignments configuration */
    val assignments: Assignments = Assignments(),
    
    /** Mavenized projects configuration */
    val mavenizedProjects: MavenizedProjects = MavenizedProjects(),
    
    /** Maven configuration */
    val maven: Maven = Maven(),
    
    /** Admin configuration */
    val admin: Admin = Admin(),
    
    /** Async configuration */
    @field:Valid val async: Async = Async(),
    
    /** GitHub integration */
    val github: GitHub = GitHub(),

    /** Git repositories configuration */
    @field:Valid val git: Git = Git(),
    
    /** Application configuration */
    val config: Config = Config(),
    
    /** Footer configuration */
    val footer: Footer = Footer(),
    
    /** MCP configuration */
    val mcp: Mcp = Mcp(),

    /** Actuator configuration */
    val actuator: Actuator = Actuator()
) {

    data class Storage(
        /** Root location for all submissions */
        val rootLocation: String = "submissions",
        /** Location for upload submissions */
        val uploadLocation: String = "submissions/upload",
        /** Location for git submissions */
        val gitLocation: String = "submissions/git"
    )

    data class Assignments(
        /** Root location for assignments */
        val rootLocation: String = "assignments"
    )

    data class MavenizedProjects(
        /** Root location for mavenized projects */
        val rootLocation: String = "mavenized-projects"
    )

    data class Maven(
        /** Maven home directory */
        val home: String = "",
        /** Maven local repository */
        val repository: String = "",
        /** Force Maven to use the same Java version running Drop Project */
        val useCurrentJdk: Boolean = true
    )

    data class Admin(
        /** Administrator email address */
        val email: String = "admin@dropproject.org"
    )

    data class Async(
        /** Maximum time in seconds for async tasks (such as maven execution) */
        val timeout: Int = 180,
        /** Number of threads in the async task executor thread pool */
        @field:Min(value = 1, message = "drop-project.async.thread-pool-size must be at least 1")
        val threadPoolSize: Int = 1
    )

    data class GitHub(
        /** GitHub token for repository access (optional) */
        val token: String = "no-token"
    )

    data class Git(
        /**
         * Refuse to connect (or to refresh) a student repository that anyone can read, since that
         * would allow other students to copy the submission
         */
        val rejectPublicStudentRepositories: Boolean = true,
        /** Timeout, in seconds, for the check that determines if a repository is publicly readable */
        @field:Min(value = 1, message = "drop-project.git.visibility-check-timeout must be at least 1")
        val visibilityCheckTimeout: Int = 10
    )

    data class Config(
        /** Configuration location folder */
        val location: String = ""
    )

    data class Footer(
        /** Custom footer message */
        val message: String = ""
    )

    data class Mcp(
        /** Enable or disable MCP endpoints */
        val enabled: Boolean = true
    )

    data class Actuator(
        /** Username for accessing the /actuator/health endpoint */
        val username: String = "",
        /** Password for accessing the /actuator/health endpoint */
        val password: String = ""
    )

    override fun toString(): String {
        return super.toString()
    }
}