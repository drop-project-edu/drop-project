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

import com.jcraft.jsch.JSch
import com.jcraft.jsch.KeyPair
import com.jcraft.jsch.Session
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.ResetCommand
import org.eclipse.jgit.api.TransportConfigCallback
import org.eclipse.jgit.diff.DiffFormatter
import org.eclipse.jgit.diff.Edit
import org.eclipse.jgit.transport.JschConfigSessionFactory
import org.eclipse.jgit.transport.OpenSshConfig
import org.eclipse.jgit.transport.SshTransport
import org.eclipse.jgit.transport.Transport
import org.eclipse.jgit.util.FS
import org.springframework.stereotype.Service
import org.dropProject.extensions.formatDefault
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.dropProject.dao.formatter
import org.eclipse.jgit.patch.HunkHeader
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.slf4j.LoggerFactory
import java.util.logging.Logger

@Service
class GitClient {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    val githubSshUrlRegex = """git@github.com:(.+)/(.+).git""".toRegex()

    data class CommitInfo(val sha1: String, val date: Date, val authorName: String,
                          val authorEmail: String, val message: String) {

        var diffs = ArrayList<CommitDiff>()

        override fun toString(): String {
            return "${date.formatDefault()} - ${authorName} - ${message}"
        }

        fun getDateAsStr() : String {
            return date.formatDefault()
        }

        fun numOfChanges() : Int {
            if (diffs.isEmpty()) {
                return 0
            }

            return diffs
                    .map { it.additions + it.replacements + it.deletetions }
                    .sum()
        }

        fun summaryOfChanges() : String {
            if (diffs.isEmpty()) {
                return ""
            }

            return diffs
                    .map { "${it.fileName} (+${it.additions}/*${it.replacements}/-${it.deletetions})" }
                    .joinToString("; ")
        }
    }


    data class CommitDiff(val fileName: String, val additions: Int, val replacements: Int, val deletetions: Int)

    class MyJschConfigSessionFactory(val privateKey : ByteArray?) : JschConfigSessionFactory() {

        override fun configure(host: OpenSshConfig.Host, session: Session) {
            session.setConfig("StrictHostKeyChecking", "no")
        }

        override fun createDefaultJSch(fs: FS): JSch {
            val defaultJSch = super.createDefaultJSch(fs)

            // prevent any existing ssh identities to interfere with the validation
            defaultJSch.removeAllIdentity()

            if (privateKey != null) {
                defaultJSch.addIdentity("drop-project", privateKey, null, "".toByteArray())
            }
            return defaultJSch
        }
    }

    class MyTransportConfigCallback(val privateKey : ByteArray? = null) : TransportConfigCallback {
        override fun configure(transport: Transport) {
            val sshTransport = transport as SshTransport
            sshTransport.sshSessionFactory = MyJschConfigSessionFactory(privateKey)
        }
    }

    fun clone(uri: String, directory: File, privateKey: ByteArray? = null) : Git {
        val git = Git.cloneRepository()
                    .setURI(uri)
                    .setDirectory(directory)
                    .setTransportConfigCallback(MyTransportConfigCallback(privateKey))
                    .call();
        return git
    }

    fun pull(localRepository: File, privateKey: ByteArray? = null) : Git {

        val git = Git.open(localRepository)

        // first reset all local changes to prevent conflicts
        git
            .reset()
            .setMode(ResetCommand.ResetType.HARD)
            .call();

        git
            .pull()
            .setTransportConfigCallback(MyTransportConfigCallback(privateKey))
            .call();

        return git
    }

    fun generateKeyPair() : Pair<ByteArray,ByteArray> {
        val kpair = KeyPair.genKeyPair(JSch(), KeyPair.RSA, 2048)
        val privKeyOutputStream = ByteArrayOutputStream()
        val publicKeyOutputStream = ByteArrayOutputStream()
        kpair.writePrivateKey(privKeyOutputStream)
        kpair.writePublicKey(publicKeyOutputStream, "")
        LOG.trace("Finger print: " + kpair.getFingerPrint())
        LOG.trace("PrivKey:" + privKeyOutputStream.toString())
        LOG.trace("PubKey:" + publicKeyOutputStream.toString())
        kpair.dispose()

        return privKeyOutputStream.toByteArray() to publicKeyOutputStream.toByteArray()
    }


    fun getLastCommitInfo(git: Git): CommitInfo? {
        val objectId = git.repository.resolve("refs/heads/master")
        if (objectId != null) {
            val commits = git.log().add(objectId).call()
            val lastCommit = commits.sortedByDescending { it -> it.commitTime }.first()
            val lastCommitDate = Date(lastCommit.commitTime * 1000L)
            val lastCommitInfo = CommitInfo(lastCommit.name(), lastCommitDate, lastCommit.authorIdent.name,
                    lastCommit.authorIdent.emailAddress, lastCommit.fullMessage)
            return lastCommitInfo
        } else {
            return null
        }
    }

    fun checkValidSSHGithubURL(gitRepositoryUrl : String) : Boolean {
        return githubSshUrlRegex.matches(gitRepositoryUrl)
    }

    fun convertSSHGithubURLtoHttpURL(gitRepositoryUrl : String) : String {
        if (!checkValidSSHGithubURL(gitRepositoryUrl)) {
            throw IllegalArgumentException("${gitRepositoryUrl} is not a valid ssh github url")
        }

        val (username,reponame) = getGitRepoInfo(gitRepositoryUrl)
        return "https://github.com/${username}/${reponame}"
    }

    fun getGitRepoInfo(gitRepositoryUrl : String) : Pair<String,String> {
        val matchResult = githubSshUrlRegex.find(gitRepositoryUrl)!!
        val username = matchResult.groupValues[1]
        val reponame = matchResult.groupValues[2]
        return username to reponame
    }

    fun getHistory(localRepository: File) : List<CommitInfo> {
        val git = Git.open(localRepository)
        val objectId = git.repository.resolve("refs/heads/master")
        if (objectId != null) {
            val logs = git.log().add(objectId).call()
            val myLog = logs
                    .map { CommitInfo(it.name(), Date(it.commitTime * 1000L), it.authorIdent.name, it.authorIdent.emailAddress, it.fullMessage) }
                    .toList()

            for (i in 0 until myLog.size - 1) {
                myLog[i].diffs = getDiffBetween(git.repository, myLog[i+1].sha1, myLog[i].sha1)
            }

            return myLog
        }

        return emptyList()
    }

    private fun getDiffBetween(repository: Repository, sha1FirstCommit: String, sha1SecondCommit: String) : ArrayList<CommitDiff> {

        val firstCommit = repository.resolve("${sha1FirstCommit}^{tree}")
        val secondCommit = repository.resolve("${sha1SecondCommit}^{tree}")

        val diffList = ArrayList<CommitDiff>()

        repository.newObjectReader()
                .use { reader ->
                    val oldTreeIter = CanonicalTreeParser()
                    oldTreeIter.reset(reader, firstCommit)
                    val newTreeIter = CanonicalTreeParser()
                    newTreeIter.reset(reader, secondCommit)

                    val diffFormatter = DiffFormatter(DisabledOutputStream.INSTANCE)
                    diffFormatter.setRepository(repository)
                    diffFormatter.setContext(0)

                    // finally get the list of changed files
                    Git(repository)
                            .use { git ->
                                val diffs = git.diff()
                                        .setNewTree(newTreeIter)
                                        .setOldTree(oldTreeIter)
                                        .call()
                                for (entry in diffs) {

                                    val fileHeader = diffFormatter.toFileHeader(entry)
                                    val hunks = fileHeader.getHunks()
                                    for (hunk in hunks) {

                                        var additions = 0
                                        var replacements = 0
                                        var deletions = 0
                                        for (change in hunk.toEditList()) {
                                            if (change.type == Edit.Type.DELETE) {
                                                deletions += change.lengthB
                                            }
                                            if (change.type == Edit.Type.INSERT) {
                                                additions += change.lengthB
                                            }
                                            if (change.type == Edit.Type.REPLACE) {
                                                replacements += change.lengthB
                                            }
                                        }

                                        diffList.add(CommitDiff(hunk.fileHeader.toString(), additions, replacements, deletions))
                                    }

                                }
                            }
                }

        return diffList
    }

}
