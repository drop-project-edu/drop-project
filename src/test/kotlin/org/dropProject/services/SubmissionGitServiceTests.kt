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
package org.dropproject.services

import org.eclipse.jgit.api.errors.TransportException
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test

class SubmissionGitServiceTests {

    private val url = "git@github.com:someone/project.git"

    @Test
    fun `a rejected deploy key is reported as such`() {
        val e = TransportException("$url: Auth fail", RuntimeException("Auth fail"))
        assertThat(SubmissionGitService.unreachableRepositoryMessage(url, e),
            startsWith("The git host rejected Drop Project's deploy key for $url"))
    }

    @Test
    fun `a repository that doesn't exist is reported as deleted`() {
        val e = TransportException("$url: ERROR: Repository not found.")
        assertThat(SubmissionGitService.unreachableRepositoryMessage(url, e),
            startsWith("The repository $url no longer exists"))
    }

    @Test
    fun `other failures keep their original message`() {
        val e = TransportException("$url: UnknownHostException: github.com")
        assertThat(SubmissionGitService.unreachableRepositoryMessage(url, e),
            startsWith("Couldn't reach the repository $url: $url: UnknownHostException"))
    }
}
