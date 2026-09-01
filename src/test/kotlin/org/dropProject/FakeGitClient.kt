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

import org.dropproject.services.GitClient
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.springframework.context.annotation.Primary
import org.springframework.stereotype.Service
import java.io.File

/**
 * Test seam for [GitClient]: tests can map a github ssh url (e.g. git@github.com:someuser/somerepo.git)
 * to a local git repository, so that the whole git submission flow runs offline. Urls that were not
 * registered keep the real behavior (network + ssh), so existing integration tests are unaffected.
 *
 * Registered remotes are cleared after each test by [ResetStateExtension].
 */
@Service
@Primary
class FakeGitClient : GitClient() {

    private val fakeRemotes = mutableMapOf<String, File>()

    fun registerRemote(gitRepositoryUrl: String, localRepositoryFolder: File) {
        fakeRemotes[gitRepositoryUrl] = localRepositoryFolder
    }

    fun clearRemotes() {
        fakeRemotes.clear()
    }

    override fun clone(uri: String, directory: File, privateKey: ByteArray?): Git {
        val fakeRemote = fakeRemotes[uri] ?: return super.clone(uri, directory, privateKey)
        return Git.cloneRepository()
            .setURI(fakeRemote.toURI().toString())
            .setDirectory(directory)
            .call()
    }

    override fun pull(localRepository: File, privateKey: ByteArray?): Git {
        if (!clonedFromFakeRemote(localRepository)) {
            return super.pull(localRepository, privateKey)
        }

        val git = Git.open(localRepository)
        git.reset().setMode(ResetCommand.ResetType.HARD).call()
        git.pull().call()
        return git
    }

    override fun fetch(localRepository: File, privateKey: ByteArray): Git {
        if (!clonedFromFakeRemote(localRepository)) {
            return super.fetch(localRepository, privateKey)
        }

        val git = Git.open(localRepository)
        git.fetch().call()
        return git
    }

    // repositories cloned from a fake remote have a file:// origin, for which the ssh transport
    // used by the real pull/fetch doesn't apply
    private fun clonedFromFakeRemote(localRepository: File): Boolean {
        Git.open(localRepository).use { git ->
            val originUrl = git.repository.config.getString("remote", "origin", "url")
            return originUrl != null && originUrl.startsWith("file:")
        }
    }
}
