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

import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.test.context.junit4.SpringRunner


@RunWith(SpringRunner::class)
class TestGitClient {

    val gitClient = GitClient()

    @Test
    fun testCheckValidGithubURL() {

        assertTrue(gitClient.checkValidSSHGithubURL("git@github.com:ULHT-LP2-2018-19/paint-episodio-2-completo.git"))
        assertFalse(gitClient.checkValidSSHGithubURL("https://github.com/ULHT-LP2-2018-19/paint-episodio-2-completo.git"))
        assertFalse(gitClient.checkValidSSHGithubURL("https://github.com/ULHT-LP2-2018-19/paint-episodio-2-completo"))
        assertFalse(gitClient.checkValidSSHGithubURL("paint-episodio-2-completo"))

    }

    @Test
    fun testConvertSSHGithubURLtoHttpURL() {

        assertEquals("https://github.com/ULHT-LP2-2018-19/paint-episodio-2-completo",
                gitClient.convertSSHGithubURLtoHttpURL("git@github.com:ULHT-LP2-2018-19/paint-episodio-2-completo.git"))

    }


}
