package com.sameerasw.draft.data.git

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.RebaseResult
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class GitSyncManager(private val context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        "draft_git_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    val localRepoDir: File = File(context.filesDir, "draft-notes")

    fun isConfigured(): Boolean {
        return getPat().isNotBlank() && getRepoUrl().isNotBlank() && File(localRepoDir, ".git").exists()
    }

    fun saveConfig(repoUrl: String, pat: String, authorName: String, authorEmail: String) {
        prefs.edit()
            .putString(KEY_REPO_URL, repoUrl)
            .putString(KEY_PAT, pat)
            .putString(KEY_AUTHOR_NAME, authorName)
            .putString(KEY_AUTHOR_EMAIL, authorEmail)
            .apply()
    }

    fun getRepoUrl(): String = prefs.getString(KEY_REPO_URL, "") ?: ""
    fun getPat(): String = prefs.getString(KEY_PAT, "") ?: ""
    fun getAuthorName(): String = prefs.getString(KEY_AUTHOR_NAME, "Draft User") ?: "Draft User"
    fun getAuthorEmail(): String = prefs.getString(KEY_AUTHOR_EMAIL, "draft@local") ?: "draft@local"

    private fun getCredentials(): UsernamePasswordCredentialsProvider {
        return UsernamePasswordCredentialsProvider("token", getPat())
    }

    fun cloneRepo(repoUrl: String, pat: String, authorName: String, authorEmail: String) {
        saveConfig(repoUrl, pat, authorName, authorEmail)

        if (localRepoDir.exists()) {
            localRepoDir.deleteRecursively()
        }
        localRepoDir.mkdirs()

        Git.cloneRepository()
            .setURI(repoUrl)
            .setDirectory(localRepoDir)
            .setCredentialsProvider(UsernamePasswordCredentialsProvider("token", pat))
            .call()
            .close()
    }

    @Synchronized
    fun sync(): Result<Unit> {
        return runCatching {
            if (!isConfigured()) throw IllegalStateException("Git repository not configured")

            val git = Git.open(localRepoDir)
            val credentials = getCredentials()
            val authorName = getAuthorName()
            val authorEmail = getAuthorEmail()

            // 1. Stage both new/modified files and deleted files
            git.add().addFilepattern(".").call()
            git.add().addFilepattern(".").setUpdate(true).call()

            // 2. Commit if there are changes
            val status = git.status().call()
            if (!status.isClean) {
                git.commit()
                    .setMessage("auto: update notes from Android")
                    .setAuthor(authorName, authorEmail)
                    .setCommitter(authorName, authorEmail)
                    .call()
            }

            // 3. Pull rebase
            try {
                val pullResult = git.pull()
                    .setRebase(true)
                    .setCredentialsProvider(credentials)
                    .call()

                if (!pullResult.isSuccessful || pullResult.rebaseResult?.status == RebaseResult.Status.STOPPED) {
                    handleConflict(git, credentials, authorName, authorEmail)
                }
            } catch (e: Exception) {
                handleConflict(git, credentials, authorName, authorEmail)
            }

            // 4. Push
            git.push()
                .setCredentialsProvider(credentials)
                .call()

            git.close()
        }
    }

    private fun handleConflict(
        git: Git,
        credentials: UsernamePasswordCredentialsProvider,
        authorName: String,
        authorEmail: String
    ) {
        try {
            git.rebase().setOperation(org.eclipse.jgit.api.RebaseCommand.Operation.ABORT).call()
        } catch (_: Exception) {}

        val timestamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val statusBeforeReset = git.status().call()

        for (file in localRepoDir.listFiles { f -> f.isFile && f.name.endsWith(".md") } ?: emptyArray()) {
            if (statusBeforeReset.uncommittedChanges.contains(file.name) || statusBeforeReset.modified.contains(file.name) || statusBeforeReset.conflicting.contains(file.name)) {
                val conflictName = "${file.nameWithoutExtension} (Conflict - Android - $timestamp).md"
                file.copyTo(File(localRepoDir, conflictName), overwrite = true)
            }
        }

        try {
            git.reset().setMode(org.eclipse.jgit.api.ResetCommand.ResetType.HARD).call()
            git.pull().setRebase(false).setCredentialsProvider(credentials).call()
        } catch (_: Exception) {}

        git.add().addFilepattern(".").call()
        git.add().addFilepattern(".").setUpdate(true).call()
        val postStatus = git.status().call()
        if (!postStatus.isClean) {
            git.commit()
                .setMessage("auto: resolve conflict copy on Android")
                .setAuthor(authorName, authorEmail)
                .setCommitter(authorName, authorEmail)
                .call()
            git.push().setCredentialsProvider(credentials).call()
        }
    }

    companion object {
        private const val KEY_REPO_URL = "repo_url"
        private const val KEY_PAT = "pat"
        private const val KEY_AUTHOR_NAME = "author_name"
        private const val KEY_AUTHOR_EMAIL = "author_email"
    }
}
