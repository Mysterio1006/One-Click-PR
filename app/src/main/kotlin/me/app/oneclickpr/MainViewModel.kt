package me.app.oneclickpr

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Base64OutputStream
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.ByteArrayOutputStream
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        const val MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024L
        private const val TAG = "OneClickPR"
    }

    private val prefs = try {
        // security-crypto 1.0.0 稳定版 API：MasterKeys.getOrCreate() + 参数顺序 (filename, alias, context, ...)
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "oneclickpr_secure_prefs",
            masterKeyAlias,
            application,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        Log.w(TAG, "EncryptedSharedPreferences unavailable, falling back: ${e.message}")
        application.getSharedPreferences("oneclickpr_prefs", Context.MODE_PRIVATE)
    }

    private val gson = Gson()

    enum class Language { ZH, EN }

    sealed class Screen {
        object Home     : Screen()
        object PrCreate : Screen()
        object History  : Screen()
    }

    data class PrHistory(val id: String, val title: String, val repo: String, val number: Int, val url: String, val timeMs: Long, var status: String = "Open")
    data class SavedRepo(val id: String, val url: String, val owner: String, val name: String, val lastSyncTime: Long)
    data class FileNode(
        val id: String, val name: String, val virtualPath: String,
        val uri: Uri, val isDirectory: Boolean, val depth: Int,
        val sizeBytes: Long = 0L, val isExpanded: Boolean = false,
        val isChecked: Boolean = true, val children: List<FileNode> = emptyList()
    )

    val language = MutableStateFlow(
        if (prefs.getString("language", "ZH") == "EN") Language.EN else Language.ZH
    )
    val route = MutableStateFlow<Screen>(Screen.Home)

    private val _savedRepos = MutableStateFlow<List<SavedRepo>>(emptyList())
    val savedRepos: StateFlow<List<SavedRepo>> = _savedRepos.asStateFlow()

    val targetUrl        = MutableStateFlow("")
    val baseBranch       = MutableStateFlow("main")
    val newBranch        = MutableStateFlow("")
    val targetPathPrefix = MutableStateFlow("")
    val prTitle          = MutableStateFlow("fix: ")
    val prDescription    = MutableStateFlow("")
    val allowMaintainerEdit = MutableStateFlow(true)
    val isDraftPr           = MutableStateFlow(false)

    val showSettings   = MutableStateFlow(false)
    val githubUsername = MutableStateFlow(prefs.getString("owner", "") ?: "")
    private val _githubToken = MutableStateFlow(prefs.getString("token", "") ?: "")
    val githubToken: StateFlow<String> = _githubToken.asStateFlow()
    val hasToken: StateFlow<Boolean> = _githubToken.map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val sshKey = MutableStateFlow(prefs.getString("ssh_key", "") ?: "")

    private val _fileNodes = MutableStateFlow<List<FileNode>>(emptyList())
    val flatFileNodes: StateFlow<List<FileNode>> = _fileNodes.map { flattenTree(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isParsingFolder     = MutableStateFlow(false)
    val useGitignore        = MutableStateFlow(true)
    val gitignoreContent    = MutableStateFlow("")
    val showGitignoreDialog = MutableStateFlow(false)

    private val _historyList = MutableStateFlow<List<PrHistory>>(emptyList())
    val currentRepoHistory: StateFlow<List<PrHistory>> = combine(_historyList, targetUrl) { history, url ->
        val repoPath = extractRepoPath(url)
        if (repoPath.isEmpty()) emptyList() else history.filter { it.repo.equals(repoPath, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val showProgress  = MutableStateFlow(false)
    val prSuccess     = MutableStateFlow(false)
    val prError       = MutableStateFlow(false)
    val logs          = MutableStateFlow<List<String>>(emptyList())
    val isSyncingRepo = MutableStateFlow(false)

    sealed class UiEvent {
        data object OpenFolderPicker     : UiEvent()
        data object OpenFilePicker       : UiEvent()
        data object OpenAttachmentPicker : UiEvent()
        data class ShowToast(val msgZh: String, val msgEn: String) : UiEvent()
    }

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    init {
        loadHistoryFromPrefs()
        loadSavedRepos()
    }

    fun openBlankPr() { targetUrl.value = ""; clearPrForm(); route.value = Screen.PrCreate }

    fun openRepo(repo: SavedRepo) {
        targetUrl.value = repo.url; clearPrForm(); route.value = Screen.PrCreate; checkAndSyncRepo(repo)
    }

    fun openRepoFromDeepLink(url: String) {
        val clean = url.trim().trimEnd('/').removeSuffix(".git")
        val m = Regex("github\\.com/([^/]+)/([^/?#]+)").find(clean) ?: return
        targetUrl.value = "https://github.com/${m.groupValues[1]}/${m.groupValues[2]}"
        clearPrForm(); route.value = Screen.PrCreate
    }

    private fun clearPrForm() {
        prTitle.value = "fix: "; prDescription.value = ""; targetPathPrefix.value = ""; newBranch.value = ""; clearFiles()
    }

    private fun extractRepoPath(url: String): String {
        val clean = url.trim().trimEnd('/').removeSuffix(".git")
        val m = Regex("github\\.com/([^/]+)/([^/]+)$").find(clean) ?: return ""
        return "${m.groupValues[1]}/${m.groupValues[2]}"
    }

    private fun saveCurrentRepoToHome() {
        val url = targetUrl.value.trim().trimEnd('/').removeSuffix(".git")
        val m = Regex("github\\.com/([^/]+)/([^/]+)$").find(url) ?: return
        val list = _savedRepos.value.toMutableList()
        val idx  = list.indexOfFirst { it.url == url }
        if (idx == -1) list.add(0, SavedRepo(UUID.randomUUID().toString(), url, m.groupValues[1], m.groupValues[2], System.currentTimeMillis()))
        else list[idx] = list[idx].copy(lastSyncTime = System.currentTimeMillis())
        _savedRepos.value = list
        prefs.edit().putString("saved_repos", gson.toJson(list)).apply()
    }

    private fun loadSavedRepos() {
        try {
            _savedRepos.value = gson.fromJson(prefs.getString("saved_repos", "[]"), Array<SavedRepo>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) { _savedRepos.value = emptyList() }
    }

    fun deleteRepo(repoId: String) {
        val newList = _savedRepos.value.filter { it.id != repoId }
        _savedRepos.value = newList
        prefs.edit().putString("saved_repos", gson.toJson(newList)).apply()
    }

    private fun checkAndSyncRepo(repo: SavedRepo) {
        viewModelScope.launch(Dispatchers.IO) {
            isSyncingRepo.value = true
            try {
                val token = _githubToken.value
                if (token.isBlank()) return@launch
                val myUsername = apiRequest("GET", "https://api.github.com/user", null, token).get("login").asString
                if (!repo.owner.equals(myUsername, ignoreCase = true)) {
                    val workingRepo = "$myUsername/${repo.name}"
                    try {
                        apiRequest("POST", "https://api.github.com/repos/$workingRepo/merge-upstream",
                            JsonObject().apply { addProperty("branch", baseBranch.value) }, token)
                        _uiEvents.emit(UiEvent.ShowToast("仓库已自动同步至最新上游代码", "Repository automatically synced with upstream"))
                    } catch (syncError: Exception) {
                        val msg = syncError.message ?: ""
                        if (msg.contains("409") || msg.contains("Conflict")) {
                            _uiEvents.emit(UiEvent.ShowToast("⚠️ 自动同步失败：Fork 与上游存在冲突", "⚠️ Auto-sync failed: merge conflict"))
                        }
                        Log.d(TAG, "Background sync skipped: $msg")
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "checkAndSyncRepo: ${e.message}")
            } finally {
                isSyncingRepo.value = false
            }
        }
    }

    private fun loadHistoryFromPrefs() {
        try {
            _historyList.value = gson.fromJson(prefs.getString("pr_history", "[]"), Array<PrHistory>::class.java)?.toList() ?: emptyList()
        } catch (e: Exception) { _historyList.value = emptyList() }
    }
    private fun saveHistoryToPrefs(list: List<PrHistory>) { prefs.edit().putString("pr_history", gson.toJson(list)).apply() }

    fun clearCurrentRepoHistory() {
        val path = extractRepoPath(targetUrl.value); if (path.isEmpty()) return
        val newList = _historyList.value.filterNot { it.repo.equals(path, ignoreCase = true) }
        _historyList.value = newList; saveHistoryToPrefs(newList)
    }

    private fun refreshHistoryStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val token = _githubToken.value; if (token.isBlank()) return@launch
            val list = _historyList.value.toMutableList(); var changed = false
            val path = extractRepoPath(targetUrl.value)
            for (i in list.indices) {
                val item = list[i]
                if (item.status != "Open" || !item.repo.equals(path, ignoreCase = true)) continue
                try {
                    val resp = apiRequest("GET", "https://api.github.com/repos/${item.repo}/pulls/${item.number}", null, token)
                    val newStatus = if (resp.get("state")?.asString == "closed") {
                        val mergedAt = resp.get("merged_at"); if (mergedAt != null && !mergedAt.isJsonNull) "Merged" else "Closed"
                    } else "Open"
                    if (item.status != newStatus) { list[i] = item.copy(status = newStatus); changed = true }
                } catch (e: Exception) { Log.w(TAG, "refreshStatus PR#${item.number}: ${e.message}") }
            }
            if (changed) { _historyList.value = list; saveHistoryToPrefs(list) }
        }
    }

    private fun flattenTree(nodes: List<FileNode>): List<FileNode> {
        val result = mutableListOf<FileNode>()
        fun traverse(list: List<FileNode>) { for (n in list) { result.add(n); if (n.isDirectory && n.isExpanded) traverse(n.children) } }
        traverse(nodes); return result
    }

    fun toggleNodeExpand(nodeId: String) {
        fun update(nodes: List<FileNode>): List<FileNode> = nodes.map { n ->
            if (n.id == nodeId) n.copy(isExpanded = !n.isExpanded) else n.copy(children = update(n.children))
        }
        _fileNodes.update { update(it) }
    }

    fun toggleNodeCheck(nodeId: String, isChecked: Boolean) {
        fun setAll(n: FileNode, v: Boolean): FileNode = n.copy(isChecked = v, children = n.children.map { setAll(it, v) })
        fun update(nodes: List<FileNode>): List<FileNode> = nodes.map { n ->
            if (n.id == nodeId) setAll(n, isChecked) else n.copy(children = update(n.children))
        }
        _fileNodes.update { update(it) }
    }

    fun removeNode(nodeId: String) {
        fun remove(nodes: List<FileNode>): List<FileNode> =
            nodes.filter { it.id != nodeId }.map { it.copy(children = remove(it.children)) }
        _fileNodes.update { remove(it) }
    }

    fun clearFiles() { _fileNodes.value = emptyList() }

    fun onFolderSelected(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            isParsingFolder.value = true
            val root = DocumentFile.fromTreeUri(getApplication(), treeUri) ?: run { isParsingFolder.value = false; return@launch }
            if (gitignoreContent.value.isBlank()) {
                root.findFile(".gitignore")?.let { gf ->
                    try {
                        val text = getApplication<Application>().contentResolver.openInputStream(gf.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                        gitignoreContent.value = text; useGitignore.value = true
                    } catch (e: Exception) { Log.w(TAG, "Read .gitignore: ${e.message}") }
                }
            }
            val rules = parseGitignoreRules()
            val folderNode = FileNode(UUID.randomUUID().toString(), root.name ?: "Project Folder", "", treeUri, true, 0, isExpanded = true, isChecked = true, children = buildTree(root, 1, "", rules))
            _fileNodes.update { it + folderNode }; isParsingFolder.value = false
        }
    }

    fun applyGitignoreRules() {
        showGitignoreDialog.value = false
        viewModelScope.launch(Dispatchers.IO) {
            isParsingFolder.value = true
            val rules = parseGitignoreRules()
            _fileNodes.value = _fileNodes.value.map { rn ->
                if (rn.isDirectory && rn.depth == 0)
                    DocumentFile.fromTreeUri(getApplication(), rn.uri)?.let { rn.copy(children = buildTree(it, 1, "", rules)) } ?: rn
                else rn
            }
            isParsingFolder.value = false
        }
    }

    // ── 改进版 .gitignore 解析器：支持否定规则(!)、双星号(**)、根路径(/)、通配符(?) ──

    private data class GitignoreRule(val pattern: String, val isNegation: Boolean, val isDirOnly: Boolean, val isRooted: Boolean)

    private fun parseGitignoreRules(): List<GitignoreRule> {
        if (!useGitignore.value) return emptyList()
        return gitignoreContent.value.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { line ->
            val isNeg = line.startsWith("!"); var p = if (isNeg) line.drop(1) else line
            val isDir = p.endsWith("/"); if (isDir) p = p.dropLast(1)
            val isRooted = p.startsWith("/"); if (isRooted) p = p.drop(1)
            GitignoreRule(p, isNeg, isDir, isRooted)
        }
    }

    private fun isIgnoredByRules(name: String, path: String, isDir: Boolean, rules: List<GitignoreRule>): Boolean {
        if (name == ".git" || name == ".idea" || name == "build" || name == ".gradle") return true
        if (rules.isEmpty()) return false
        var ignored = false
        for (rule in rules) {
            if (rule.isDirOnly && !isDir) continue
            if (matchesGitignoreRule(name, path, rule)) ignored = !rule.isNegation
        }
        return ignored
    }

    private fun matchesGitignoreRule(name: String, path: String, rule: GitignoreRule): Boolean {
        val pattern = rule.pattern
        if (pattern.startsWith("**/")) return globMatch(name, pattern.removePrefix("**/"))
        return if (!pattern.contains("/") && !rule.isRooted) globMatch(name, pattern)
               else globMatch(path, pattern) || globMatch("/$path", "/$pattern")
    }

    private fun globMatch(text: String, pattern: String): Boolean {
        val regex = buildString {
            append("^"); var i = 0
            while (i < pattern.length) {
                when {
                    i + 1 < pattern.length && pattern[i] == '*' && pattern[i+1] == '*' -> { append(".*"); i += 2 }
                    pattern[i] == '*' -> { append("[^/]*"); i++ }
                    pattern[i] == '?' -> { append("[^/]"); i++ }
                    pattern[i] == '.' -> { append("\\."); i++ }
                    else -> { append(Regex.escape(pattern[i].toString())); i++ }
                }
            }
            append("$")
        }
        return try { Regex(regex, RegexOption.IGNORE_CASE).matches(text) } catch (e: Exception) { false }
    }

    private fun buildTree(folder: DocumentFile, depth: Int, pathPrefix: String, rules: List<GitignoreRule>): List<FileNode> {
        val result = mutableListOf<FileNode>()
        folder.listFiles().forEach { file ->
            val name = file.name ?: return@forEach; val isDir = file.isDirectory; val path = "$pathPrefix$name"
            if (isIgnoredByRules(name, path, isDir, rules)) return@forEach
            result.add(FileNode(UUID.randomUUID().toString(), name, path, file.uri, isDir, depth, sizeBytes = file.length(), isExpanded = false, isChecked = true,
                children = if (isDir) buildTree(file, depth + 1, "$path/", rules) else emptyList()))
        }
        return result.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }

    fun onAttachmentSelected(uri: Uri) = addSingleFile(uri, true)
    fun onFileSelected(uri: Uri) = addSingleFile(uri, false)

    private fun addSingleFile(uri: Uri, isAttachment: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            var fileName = "file_${System.currentTimeMillis()}"; var fileSize = 0L
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME); val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni != -1) fileName = c.getString(ni); if (si != -1) fileSize = c.getLong(si)
                }
            }
            if (fileSize > MAX_FILE_SIZE_BYTES) { _uiEvents.emit(UiEvent.ShowToast("文件 $fileName 过大 (>15MB)，已跳过", "File $fileName is too large (>15MB), skipped.")); return@launch }
            val vPath = if (isAttachment) "pr_attachments/$fileName" else fileName
            _fileNodes.update { it + FileNode(UUID.randomUUID().toString(), fileName, vPath, uri, false, 0, fileSize) }
            if (isAttachment) {
                val isImage = fileName.lowercase().matches(Regex(".*\\.(png|jpg|jpeg|gif|webp)$")) || getApplication<Application>().contentResolver.getType(uri)?.startsWith("image/") == true
                val snippet = if (isImage) "![${fileName}](pr_attachments/${fileName})" else "[${fileName}](pr_attachments/${fileName})"
                val cur = prDescription.value
                prDescription.value = if (cur.isEmpty() || cur.endsWith("\n")) cur + snippet + "\n" else "$cur\n\n$snippet\n"
            }
        }
    }

    // ── 提交前网络检查 ──
    private fun isNetworkAvailable(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val caps = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
               caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun submitPr() {
        if (!isNetworkAvailable()) { sendToast("网络不可用，请检查连接后重试", "No internet connection. Check your network."); return }
        if (targetUrl.value.isBlank()) { sendToast("请填写目标开源项目克隆链接", "Target Repository URL is required"); return }
        if (prTitle.value.isBlank()) { sendToast("请填写 Pull Request 标题", "PR Title is required"); return }

        val filesToUpload = mutableListOf<FileNode>()
        fun collect(nodes: List<FileNode>) { for (n in nodes) { if (n.isChecked && !n.isDirectory) filesToUpload.add(n); if (n.isDirectory && n.isChecked) collect(n.children) } }
        collect(_fileNodes.value)
        if (filesToUpload.isEmpty()) { sendToast("没有选中任何要提交的文件！", "No files selected for PR!"); return }
        if (filesToUpload.any { it.sizeBytes > MAX_FILE_SIZE_BYTES }) { sendToast("存在超过15MB的文件，请取消勾选大文件！", "Files >15MB detected. Please uncheck them."); return }

        viewModelScope.launch {
            showProgress.value = true; prSuccess.value = false; prError.value = false
            logs.value = listOf("🚀 启动云端极速 PR 引擎...", "📦 准备处理 ${filesToUpload.size} 个文件...")
            withContext(Dispatchers.IO) {
                try {
                    val cleanUrl = targetUrl.value.trim().trimEnd('/').removeSuffix(".git")
                    val match = Regex("github\\.com/([^/]+)/([^/]+)$").find(cleanUrl) ?: throw Exception("无法解析 Owner/Repo，请确保是标准的 GitHub 链接。")
                    val (repoOwner, repoName) = match.destructured
                    val token = _githubToken.value
                    if (token.isBlank()) throw Exception("未配置凭证，云端 PR 引擎需要有效的 GitHub Token。")

                    logs.update { it + "验证身份凭证..." }
                    val myUsername = apiRequest("GET", "https://api.github.com/user", null, token).get("login").asString
                    val targetRepo = "$repoOwner/$repoName"; val isOwner = repoOwner.equals(myUsername, ignoreCase = true)
                    val workingRepo = if (isOwner) targetRepo else "$myUsername/$repoName"

                    if (!isOwner) {
                        logs.update { it + "检查工作区 Fork 状态 ($workingRepo)..." }
                        var hasFork = false
                        try { apiRequest("GET", "https://api.github.com/repos/$workingRepo", null, token); hasFork = true }
                        catch (e: Exception) { logs.update { it + "正在为您创建 Fork ($workingRepo)..." }; apiRequest("POST", "https://api.github.com/repos/$targetRepo/forks", null, token) }
                        if (!hasFork) {
                            var ready = false
                            for (i in 1..15) { delay(2000); try { apiRequest("GET", "https://api.github.com/repos/$workingRepo", null, token); ready = true; break } catch (e: Exception) { logs.update { it + "等待 Fork 数据同步... ($i/15)" } } }
                            if (!ready) throw Exception("Fork 同步超时，请稍后重试。")
                        }
                        logs.update { it + "强制同步 Fork 至最新上游代码..." }
                        try {
                            val r = apiRequest("POST", "https://api.github.com/repos/$workingRepo/merge-upstream", JsonObject().apply { addProperty("branch", baseBranch.value) }, token)
                            logs.update { it + "同步完成" + (r.get("message")?.asString?.let { m -> if (m.isNotEmpty()) ": $m" else "" } ?: "") }; delay(2000)
                        } catch (e: Exception) {
                            logs.update { it + if ((e.message ?: "").contains("409")) "⚠️ 无法自动同步：Fork 与上游存在严重冲突，建议在网页端删除并重建 Fork。" else "仓库已是最新状态。" }
                        }
                    }

                    logs.update { it + "获取工作区代码快照..." }
                    val baseSha = try {
                        apiRequest("GET", "https://api.github.com/repos/$workingRepo/git/ref/heads/${baseBranch.value}", null, token).getAsJsonObject("object").get("sha").asString
                    } catch (e: Exception) { throw Exception("在仓库 [$workingRepo] 中找不到 [${baseBranch.value}] 分支。\n建议：检查分支名称（是否应为 master？）") }

                    val targetBranch = newBranch.value.ifBlank { "pr-${System.currentTimeMillis()}" }
                    logs.update { it + "创建工作分支: $targetBranch" }
                    var branchExists = false
                    try { apiRequest("GET", "https://api.github.com/repos/$workingRepo/git/ref/heads/$targetBranch", null, token); branchExists = true } catch (e: Exception) {}

                    if (branchExists) {
                        logs.update { it + "⚠️ 分支 [$targetBranch] 已存在，将在此分支追加提交。如不希望如此，请修改「新分支」字段。" }
                    } else {
                        apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/refs", JsonObject().apply { addProperty("ref", "refs/heads/$targetBranch"); addProperty("sha", baseSha) }, token)
                    }

                    // ── 并行上传所有文件 Blob（相比串行大幅提速） ──
                    logs.update { it + "⚡ 并行上传 ${filesToUpload.size} 个文件..." }
                    val prefix = targetPathPrefix.value.trim().let { p -> if (p.isNotEmpty() && !p.endsWith("/")) "$p/" else p }
                    val treeBodyArray = JsonArray()
                    coroutineScope {
                        val jobs = filesToUpload.mapIndexed { idx, file ->
                            async(Dispatchers.IO) {
                                logs.update { it + "上传: ${file.name} (${idx+1}/${filesToUpload.size})" }
                                // 流式 Base64 编码：避免同时持有原始字节和编码字节，降低内存峰值约 40%
                                val baos = ByteArrayOutputStream()
                                val b64  = Base64OutputStream(baos, Base64.NO_WRAP)
                                (getApplication<Application>().contentResolver.openInputStream(file.uri)
                                    ?: throw Exception("无法读取文件: ${file.name}")).use { it.copyTo(b64, 8192) }
                                b64.close()
                                val blobSha = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/blobs",
                                    JsonObject().apply { addProperty("content", baos.toString("UTF-8")); addProperty("encoding", "base64") }, token).get("sha").asString
                                val finalPath = if (file.virtualPath.startsWith("pr_attachments/")) file.virtualPath else "$prefix${file.virtualPath}"
                                JsonObject().apply { addProperty("path", finalPath); addProperty("mode", "100644"); addProperty("type", "blob"); addProperty("sha", blobSha) }
                            }
                        }
                        jobs.forEach { treeBodyArray.add(it.await()) }
                    }

                    logs.update { it + "云端重组 Git Tree 与 Commit..." }
                    val curBranchSha = apiRequest("GET", "https://api.github.com/repos/$workingRepo/git/ref/heads/$targetBranch", null, token).getAsJsonObject("object").get("sha").asString
                    val curTreeSha   = apiRequest("GET", "https://api.github.com/repos/$workingRepo/git/commits/$curBranchSha", null, token).getAsJsonObject("tree").get("sha").asString
                    val newTreeSha   = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/trees", JsonObject().apply { addProperty("base_tree", curTreeSha); add("tree", treeBodyArray) }, token).get("sha").asString
                    if (newTreeSha == curTreeSha) throw Exception("提交被拦截：上传文件与线上代码完全一致（Empty Diff）。\n💡 请检查「目标路径前缀」是否填写正确。")

                    val newCommitSha = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/commits",
                        JsonObject().apply { addProperty("message", prTitle.value); addProperty("tree", newTreeSha); add("parents", JsonArray().apply { add(curBranchSha) }) }, token).get("sha").asString
                    apiRequest("PATCH", "https://api.github.com/repos/$workingRepo/git/refs/heads/$targetBranch", JsonObject().apply { addProperty("sha", newCommitSha) }, token)

                    logs.update { it + "请求跨仓合并 (Pull Request)..." }
                    val finalDesc = prDescription.value.replace(Regex("pr_attachments/([^\\s\\)\\]\"']+)"), "https://raw.githubusercontent.com/$workingRepo/$targetBranch/pr_attachments/$1")
                    val prResp = apiRequest("POST", "https://api.github.com/repos/$targetRepo/pulls",
                        JsonObject().apply {
                            addProperty("title", prTitle.value)
                            addProperty("body", finalDesc.ifBlank { "Automated PR created by OneClickPR Android App." })
                            addProperty("head", if (isOwner) targetBranch else "$myUsername:$targetBranch")
                            addProperty("base", baseBranch.value); addProperty("draft", isDraftPr.value)
                            if (!isOwner) addProperty("maintainer_can_modify", allowMaintainerEdit.value)
                        }, token)
                    val prUrl = prResp.get("html_url")?.asString ?: "Unknown URL"
                    _historyList.value = listOf(PrHistory(UUID.randomUUID().toString(), prTitle.value, targetRepo, prResp.get("number")?.asInt ?: 0, prUrl, System.currentTimeMillis())) + _historyList.value
                    saveHistoryToPrefs(_historyList.value); saveCurrentRepoToHome()
                    prSuccess.value = true; logs.update { it + "✅ Pull Request 完美送达！\n🔗 $prUrl" }
                } catch (e: Exception) { prError.value = true; logs.update { it + "❌ ${e.message}" } }
            }
        }
    }

    private fun translateGithubError(msg: String): String = when {
        msg.contains("has no history in common") -> "Fork 仓库过于古老且代码已严重分化，无法提交 PR。\n💡 请在 GitHub 网页端删除同名仓库后重试，应用会自动创建全新 Fork。"
        msg.contains("A pull request already exists") -> "已存在名称和内容相同的未关闭 PR。\n💡 请在「新分支」填写不同名称后重试。"
        msg.contains("Validation Failed") -> "参数校验失败。通常是未产生任何代码变更，或该分支已存在同名 PR。"
        msg.contains("Not Found") -> "找不到指定资源。\n1. 检查 Token 是否拥有 repo 权限\n2. 检查仓库链接是否正确\n3. 检查分支名称（可能是 master 而非 main）"
        msg.contains("Bad credentials") -> "Token 凭证无效或已过期，请在设置中重新配置。"
        else -> msg
    }

    private fun apiRequest(method: String, url: String, body: JsonObject? = null, token: String): JsonObject {
        val rb = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").addHeader("Accept", "application/vnd.github.v3+json")
        when (method) { "GET" -> rb.get(); "POST" -> rb.post((body?.toString() ?: "{}").toRequestBody(jsonMediaType)); "PATCH" -> rb.patch((body?.toString() ?: "{}").toRequestBody(jsonMediaType)) }
        val resp = okHttpClient.newCall(rb.build()).execute(); val bodyStr = resp.body?.string()
        if (!resp.isSuccessful) {
            var errMsg = resp.message
            try { val obj = gson.fromJson(bodyStr, JsonObject::class.java); errMsg = obj.get("message")?.asString ?: errMsg
                obj.getAsJsonArray("errors")?.takeIf { it.size() > 0 }?.get(0)?.asJsonObject?.get("message")?.asString?.let { errMsg = "$errMsg\n$it" }
            } catch (e: Exception) {}
            throw Exception("HTTP ${resp.code}:\n" + translateGithubError(errMsg))
        }
        return if (bodyStr.isNullOrBlank()) JsonObject() else gson.fromJson(bodyStr, JsonObject::class.java) ?: JsonObject()
    }

    private fun sendToast(zh: String, en: String) { viewModelScope.launch { _uiEvents.emit(UiEvent.ShowToast(zh, en)) } }

    fun toggleLanguage() {
        language.value = if (language.value == Language.ZH) Language.EN else Language.ZH
        prefs.edit().putString("language", language.value.name).apply()
    }
    fun requestFolderPicker()     { viewModelScope.launch { _uiEvents.emit(UiEvent.OpenFolderPicker) } }
    fun requestFilePicker()       { viewModelScope.launch { _uiEvents.emit(UiEvent.OpenFilePicker) } }
    fun requestAttachmentPicker() { viewModelScope.launch { _uiEvents.emit(UiEvent.OpenAttachmentPicker) } }
    fun navigateToHistory()       { route.value = Screen.History; refreshHistoryStatus() }

    fun saveSettings(owner: String, token: String, ssh: String) {
        githubUsername.value = owner; _githubToken.value = token; sshKey.value = ssh
        prefs.edit().putString("owner", owner).putString("token", token).putString("ssh_key", ssh).apply()
        showSettings.value = false
    }
}
