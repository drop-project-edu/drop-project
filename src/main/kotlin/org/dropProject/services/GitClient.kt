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
package org.dropproject.services

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
import org.dropproject.extensions.formatDefault
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.*
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.treewalk.CanonicalTreeParser
import org.dropproject.dao.formatter
import org.eclipse.jgit.patch.HunkHeader
import org.eclipse.jgit.util.io.DisabledOutputStream
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.util.logging.Logger

/**
 * Provides functionality related with GitHub repositories (for example, pulling the contents of a repository).
 */
@Service
class GitClient {

    val LOG = LoggerFactory.getLogger(this.javaClass.name)

    val githubSshUrlRegex = """git@github.com:(.+)/(.+).git""".toRegex()

    /**
     * Represents information about a Git commit.
     *
     * @property diffs is an ArrayList of [CommitDiff]s
     * @property sha1 is a String with the commit's hash
     * @property date is the Date of the commit
     * @property authorName is a String with the name of the author of the commit
     * @property authorEmail is a String with the email of the author of the commit
     * @property message is a String with the commit message
     */
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

    /**
     * Represents the differences between two different commits.
     *
     * @property fileName
     * @property additions is an Int representing the lines that were added between the commits
     * @property replacements is an Int representing the lines that were changed between the commits
     * @property deletions is an Int representing the lines that were deleted between the commits
     */
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

    /**
     * Clones the Git repository identifyed by [uri].
     *
     * @return a [Git]
     */
    fun clone(uri: String, directory: File, privateKey: ByteArray? = null) : Git {
        val git = Git.cloneRepository()
                    .setURI(uri)
                    .setDirectory(directory)
                    .setTransportConfigCallback(MyTransportConfigCallback(privateKey))
                    .call();
        return git
    }

    /**
     * Pulls code from the Git repository identified by [localRepository].
     *
     * @return a [Git]
     */
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

    /**
     * Executes a git fetch from the Git repository identified by [localRepository].
     * This can be used to refresh the ssh key, since github removes ssh keys that are not used for more than a year.
     *
     * @return a [Git]
     */
    fun fetch(localRepository: File, privateKey: ByteArray) : Git {

        val git = Git.open(localRepository)

        git
            .fetch()
            .setTransportConfigCallback(MyTransportConfigCallback(privateKey))
            .call();

        return git
    }

    /**
     * Generates a pair of public/private keys.
     *
     * @return a Pair with two ByteArray
     */
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

    /**
     * Returns information about the last commit that is available in a GitHub repository.
     *
     * @return a [CommitInfo]
     */
    fun getLastCommitInfo(git: Git): CommitInfo? {
        // try main and master (github is changing the default branch to main - https://github.com/github/renaming)
        val objectId = git.repository.resolve("refs/heads/main") ?: git.repository.resolve("refs/heads/master")
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

    fun computeSshFingerprint(publicKeyContent: String): String {
        // Remove newlines
        val keyContent = publicKeyContent.replace("\n", "")

        // Extract base64 part of the key (ignoring 'ssh-rsa' or 'ssh-ed25519' prefix)
        val parts = keyContent.split(" ")
        if (parts.size < 2) {
            throw IllegalArgumentException("Invalid SSH public key format.")
        }
        val keyBytes = Base64.getDecoder().decode(parts[1])

        // Compute SHA-256 hash
        val sha256 = MessageDigest.getInstance("SHA-256")
        val hash = sha256.digest(keyBytes)

        // Encode in Base64 for GitHub-style fingerprint
        return "SHA256:" + Base64.getEncoder().encodeToString(hash)
    }

    /**
     * Returns the history of a Git repository.
     *
     * @return a List of [CommitInfo]
     */
    fun getHistory(localRepository: File) : List<CommitInfo> {
        val git = Git.open(localRepository)
        // try main and master (github is changing the default branch to main - https://github.com/github/renaming)
        val objectId = git.repository.resolve("refs/heads/main") ?: git.repository.resolve("refs/heads/master")
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

    /**
     * Calculates the differences between two Git commits
     *
     * @property repository is a [Repository]
     * @property sha1FirstCommit is the hash of the first commit
     * @property sha1SecondCommit is the hash of the second commit
     *
     * @return an ArrayList of [CommitDiff]
     */
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
