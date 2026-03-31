package me.app.oneclickpr

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
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

    private val prefs = application.getSharedPreferences("oneclickpr_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    enum class Language { ZH, EN }
    
    // 数据模型
    data class PrHistory(val id: String, val title: String, val repo: String, val number: Int, val url: String, val timeMs: Long, var status: String = "Open")
    data class SavedRepo(val id: String, val url: String, val owner: String, val name: String, val lastSyncTime: Long)
    
    data class FileNode(
        val id: String,
        val name: String,
        val virtualPath: String,
        val uri: Uri,
        val isDirectory: Boolean,
        val depth: Int,
        val sizeBytes: Long = 0L,
        val isExpanded: Boolean = false,
        val isChecked: Boolean = true,
        val children: List<FileNode> = emptyList()
    )

    val language = MutableStateFlow(Language.ZH)
    val route = MutableStateFlow("home")
    
    // 仓库列表状态
    private val _savedRepos = MutableStateFlow<List<SavedRepo>>(emptyList())
    val savedRepos: StateFlow<List<SavedRepo>> = _savedRepos.asStateFlow()
    
    // PR 详情状态
    val targetUrl = MutableStateFlow("")
    val baseBranch = MutableStateFlow("main")
    val newBranch = MutableStateFlow("")
    val targetPathPrefix = MutableStateFlow("")
    
    val prTitle = MutableStateFlow("fix: ")
    val prDescription = MutableStateFlow("")
    val allowMaintainerEdit = MutableStateFlow(true)
    val isDraftPr = MutableStateFlow(false)
    
    // 凭证设置
    val showSettings = MutableStateFlow(false)
    val githubUsername = MutableStateFlow(prefs.getString("owner", "") ?: "")
    val githubToken = MutableStateFlow(prefs.getString("token", "") ?: "")
    val sshKey = MutableStateFlow(prefs.getString("ssh_key", "") ?: "")
    
    private val _fileNodes = MutableStateFlow<List<FileNode>>(emptyList())
    val flatFileNodes: StateFlow<List<FileNode>> = _fileNodes.map { flattenTree(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isParsingFolder = MutableStateFlow(false)
    val useGitignore = MutableStateFlow(true)
    val gitignoreContent = MutableStateFlow("")
    val showGitignoreDialog = MutableStateFlow(false)

    // 历史记录状态
    private val _historyList = MutableStateFlow<List<PrHistory>>(emptyList())
    val currentRepoHistory: StateFlow<List<PrHistory>> = combine(_historyList, targetUrl) { history, url ->
        val repoPath = extractRepoPath(url)
        if (repoPath.isEmpty()) emptyList() else history.filter { it.repo.equals(repoPath, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val showProgress = MutableStateFlow(false)
    val prSuccess = MutableStateFlow(false)
    val prError = MutableStateFlow(false)
    val logs = MutableStateFlow<List<String>>(emptyList())
    val isSyncingRepo = MutableStateFlow(false)

    sealed class UiEvent {
        data object OpenFolderPicker : UiEvent()
        data object OpenFilePicker : UiEvent()
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
    private val MAX_FILE_SIZE_BYTES = 15 * 1024 * 1024L 

    init { 
        loadHistoryFromPrefs()
        loadSavedRepos()
    }

    fun openBlankPr() {
        targetUrl.value = ""
        clearPrForm()
        route.value = "pr_create"
    }

    fun openRepo(repo: SavedRepo) {
        targetUrl.value = repo.url
        clearPrForm()
        route.value = "pr_create"
        checkAndSyncRepo(repo)
    }

    private fun clearPrForm() {
        prTitle.value = "fix: "
        prDescription.value = ""
        targetPathPrefix.value = ""
        newBranch.value = ""
        clearFiles()
    }

    private fun extractRepoPath(url: String): String {
        val cleanUrl = url.trim().trimEnd('/').removeSuffix(".git")
        val match = Regex("github\\.com/([^/]+)/([^/]+)$").find(cleanUrl) ?: return ""
        return "${match.groupValues[1]}/${match.groupValues[2]}"
    }

    private fun saveCurrentRepoToHome() {
        val url = targetUrl.value.trim().trimEnd('/').removeSuffix(".git")
        val match = Regex("github\\.com/([^/]+)/([^/]+)$").find(url) ?: return
        val owner = match.groupValues[1]
        val name = match.groupValues[2]
        
        val currentList = _savedRepos.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.url == url }
        
        if (existingIndex == -1) {
            currentList.add(0, SavedRepo(UUID.randomUUID().toString(), url, owner, name, System.currentTimeMillis()))
        } else {
            val item = currentList[existingIndex]
            currentList[existingIndex] = item.copy(lastSyncTime = System.currentTimeMillis())
        }
        
        _savedRepos.value = currentList
        prefs.edit().putString("saved_repos", gson.toJson(currentList)).apply()
    }

    private fun loadSavedRepos() {
        try {
            val json = prefs.getString("saved_repos", "[]")
            val array = gson.fromJson(json, Array<SavedRepo>::class.java)
            _savedRepos.value = array?.toList() ?: emptyList()
        } catch (e: Exception) {
            _savedRepos.value = emptyList()
        }
    }

    private fun checkAndSyncRepo(repo: SavedRepo) {
        viewModelScope.launch(Dispatchers.IO) {
            isSyncingRepo.value = true
            try {
                val token = githubToken.value
                if (token.isBlank()) return@launch
                val myUsername = apiRequest("GET", "https://api.github.com/user", null, token).get("login").asString
                
                if (!repo.owner.equals(myUsername, ignoreCase = true)) {
                    val workingRepo = "$myUsername/${repo.name}"
                    apiRequest("POST", "https://api.github.com/repos/$workingRepo/merge-upstream", JsonObject().apply { addProperty("branch", baseBranch.value) }, token)
                    _uiEvents.emit(UiEvent.ShowToast("仓库已自动同步至最新上游代码", "Repository automatically synced with upstream"))
                }
            } catch (e: Exception) {
            } finally {
                isSyncingRepo.value = false
            }
        }
    }

    private fun loadHistoryFromPrefs() { 
        try {
            val json = prefs.getString("pr_history", "[]")
            val array = gson.fromJson(json, Array<PrHistory>::class.java)
            _historyList.value = array?.toList() ?: emptyList()
        } catch (e: Exception) { 
            _historyList.value = emptyList() 
        } 
    }
    
    private fun saveHistoryToPrefs(list: List<PrHistory>) { 
        prefs.edit().putString("pr_history", gson.toJson(list)).apply() 
    }
    
    fun clearCurrentRepoHistory() { 
        val currentRepoPath = extractRepoPath(targetUrl.value)
        if (currentRepoPath.isEmpty()) return
        
        val newList = _historyList.value.filterNot { it.repo.equals(currentRepoPath, ignoreCase = true) }
        _historyList.value = newList
        saveHistoryToPrefs(newList) 
    }
    
    private fun refreshHistoryStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val token = githubToken.value
            if (token.isBlank()) return@launch
            val currentList = _historyList.value.toMutableList()
            var isChanged = false
            val currentRepoPath = extractRepoPath(targetUrl.value)
            
            for (i in currentList.indices) {
                val item = currentList[i]
                if (item.status != "Open" || !item.repo.equals(currentRepoPath, ignoreCase = true)) continue
                
                try {
                    val resp = apiRequest("GET", "https://api.github.com/repos/${item.repo}/pulls/${item.number}", null, token)
                    val state = resp.get("state")?.asString
                    val mergedAt = resp.get("merged_at")
                    val newStatus = if (state == "closed") {
                        if (mergedAt != null && !mergedAt.isJsonNull) "Merged" else "Closed"
                    } else "Open"
                    
                    if (item.status != newStatus) {
                        currentList[i] = item.copy(status = newStatus)
                        isChanged = true
                    }
                } catch (e: Exception) { }
            }
            if (isChanged) {
                _historyList.value = currentList
                saveHistoryToPrefs(currentList)
            }
        }
    }

    private fun flattenTree(nodes: List<FileNode>): List<FileNode> {
        val result = mutableListOf<FileNode>()
        fun traverse(list: List<FileNode>) {
            for (node in list) {
                result.add(node)
                if (node.isDirectory && node.isExpanded) traverse(node.children)
            }
        }
        traverse(nodes)
        return result
    }

    fun toggleNodeExpand(nodeId: String) {
        fun updateExpand(nodes: List<FileNode>): List<FileNode> = nodes.map { node ->
            if (node.id == nodeId) node.copy(isExpanded = !node.isExpanded)
            else node.copy(children = updateExpand(node.children))
        }
        _fileNodes.update { updateExpand(it) }
    }

    fun toggleNodeCheck(nodeId: String, isChecked: Boolean) {
        fun setCheckRecursively(node: FileNode, checked: Boolean): FileNode = node.copy(
            isChecked = checked,
            children = node.children.map { setCheckRecursively(it, checked) }
        )
        fun updateCheck(nodes: List<FileNode>): List<FileNode> = nodes.map { node ->
            if (node.id == nodeId) setCheckRecursively(node, isChecked)
            else node.copy(children = updateCheck(node.children))
        }
        _fileNodes.update { updateCheck(it) }
    }

    fun removeNode(nodeId: String) {
        fun removeRecursive(nodes: List<FileNode>): List<FileNode> {
            return nodes.filter { it.id != nodeId }.map { it.copy(children = removeRecursive(it.children)) }
        }
        _fileNodes.update { removeRecursive(it) }
    }

    fun clearFiles() { _fileNodes.value = emptyList() }

    fun onFolderSelected(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            isParsingFolder.value = true
            val root = DocumentFile.fromTreeUri(getApplication(), treeUri) ?: return@launch
            
            if (gitignoreContent.value.isBlank()) {
                val gitignoreFile = root.findFile(".gitignore")
                if (gitignoreFile != null) {
                    try {
                        val content = getApplication<Application>().contentResolver.openInputStream(gitignoreFile.uri)?.bufferedReader()?.use { it.readText() } ?: ""
                        gitignoreContent.value = content
                        useGitignore.value = true
                    } catch (e: Exception) {}
                }
            }

            val rules = if (useGitignore.value) gitignoreContent.value.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") } else emptyList()
            val children = buildTree(root, 1, "", rules)
            
            val folderNode = FileNode(UUID.randomUUID().toString(), root.name ?: "Project Folder", "", treeUri, true, 0, isExpanded = true, isChecked = true, children = children)
            _fileNodes.update { it + folderNode }
            isParsingFolder.value = false
        }
    }

    fun applyGitignoreRules() {
        showGitignoreDialog.value = false
        viewModelScope.launch(Dispatchers.IO) {
            isParsingFolder.value = true
            val rules = if (useGitignore.value) gitignoreContent.value.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") } else emptyList()
            val updatedNodes = _fileNodes.value.map { rootNode ->
                if (rootNode.isDirectory && rootNode.depth == 0) {
                    val rootDoc = DocumentFile.fromTreeUri(getApplication(), rootNode.uri)
                    if (rootDoc != null) rootNode.copy(children = buildTree(rootDoc, 1, "", rules)) else rootNode
                } else rootNode
            }
            _fileNodes.value = updatedNodes
            isParsingFolder.value = false
        }
    }

    private fun buildTree(folder: DocumentFile, depth: Int, pathPrefix: String, rules: List<String>): List<FileNode> {
        val result = mutableListOf<FileNode>()
        folder.listFiles().forEach { file ->
            val name = file.name ?: return@forEach
            val isDir = file.isDirectory
            val path = "$pathPrefix$name"

            if (isIgnored(name, isDir, rules)) return@forEach

            val children = if (isDir) buildTree(file, depth + 1, "$path/", rules) else emptyList()
            result.add(FileNode(UUID.randomUUID().toString(), name, path, file.uri, isDir, depth, sizeBytes = file.length(), isExpanded = false, isChecked = true, children = children))
        }
        return result.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }

    private fun isIgnored(name: String, isDir: Boolean, rules: List<String>): Boolean {
        if (name == ".git" || name == ".idea" || name == "build" || name == ".gradle") return true
        if (!useGitignore.value) return false

        for (rule in rules) {
            val r = rule.trim()
            if (r.isEmpty()) continue
            if (r.endsWith("/")) {
                if (isDir && name == r.dropLast(1)) return true
            } else if (r.startsWith("*.")) {
                if (!isDir && name.endsWith(r.drop(1))) return true
            } else {
                if (name == r) return true
            }
        }
        return false
    }

    fun onAttachmentSelected(uri: Uri) { addSingleFile(uri, true) }
    fun onFileSelected(uri: Uri) { addSingleFile(uri, false) }

    private fun addSingleFile(uri: Uri, isAttachment: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            var fileName = "file_${System.currentTimeMillis()}"
            var fileSize = 0L
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) fileName = cursor.getString(nameIdx)
                    if (sizeIdx != -1) fileSize = cursor.getLong(sizeIdx)
                }
            }

            if (fileSize > MAX_FILE_SIZE_BYTES) {
                _uiEvents.emit(UiEvent.ShowToast("文件 $fileName 过大 (>15MB)，已跳过", "File $fileName is too large (>15MB), skipped."))
                return@launch
            }

            val virtualPath = if (isAttachment) "pr_attachments/$fileName" else fileName
            val node = FileNode(UUID.randomUUID().toString(), fileName, virtualPath, uri, false, 0, fileSize)
            _fileNodes.update { it + node }

            if (isAttachment) {
                val isImage = fileName.lowercase().matches(Regex(".*\\.(png|jpg|jpeg|gif|webp)$")) || getApplication<Application>().contentResolver.getType(uri)?.startsWith("image/") == true
                val snippet = if (isImage) "![${fileName}](pr_attachments/${fileName})" else "[${fileName}](pr_attachments/${fileName})"
                val currentDesc = prDescription.value
                prDescription.value = if (currentDesc.isEmpty() || currentDesc.endsWith("\n")) currentDesc + snippet + "\n" else currentDesc + "\n\n" + snippet + "\n"
            }
        }
    }

    fun submitPr() {
        if (targetUrl.value.isBlank()) { sendToast("请填写目标开源项目克隆链接", "Target Repository URL is required"); return }
        if (prTitle.value.isBlank()) { sendToast("请填写 Pull Request 标题", "PR Title is required"); return }

        val actualFilesToUpload = mutableListOf<FileNode>()
        fun collectCheckedFiles(nodes: List<FileNode>) {
            for (node in nodes) {
                if (node.isChecked && !node.isDirectory) actualFilesToUpload.add(node)
                if (node.isDirectory && node.isChecked) collectCheckedFiles(node.children)
            }
        }
        collectCheckedFiles(_fileNodes.value)

        if (actualFilesToUpload.isEmpty()) {
            sendToast("没有选中任何要提交的文件！", "No files selected for PR!"); return
        }
        
        val overSizeFiles = actualFilesToUpload.filter { it.sizeBytes > MAX_FILE_SIZE_BYTES }
        if (overSizeFiles.isNotEmpty()) {
            sendToast("存在超过15MB的文件，请取消勾选大文件！", "Files >15MB detected. Please uncheck them."); return
        }

        viewModelScope.launch {
            showProgress.value = true
            prSuccess.value = false
            prError.value = false
            logs.value = listOf("🚀 启动云端极速 PR 引擎...", "📦 准备处理 ${actualFilesToUpload.size} 个文件...")
            
            withContext(Dispatchers.IO) {
                try {
                    val cleanUrl = targetUrl.value.trim().trimEnd('/').removeSuffix(".git")
                    val match = Regex("github\\.com/([^/]+)/([^/]+)$").find(cleanUrl)
                        ?: throw Exception("无法解析 Owner/Repo，请确保是标准的 GitHub 链接。")
                    
                    val (repoOwner, repoName) = match.destructured
                    val token = githubToken.value
                    if (token.isBlank()) throw Exception("系统检测到无凭证。云端 PR 引擎要求配置 GitHub Token。")

                    logs.update { it + "验证身份凭证..." }
                    val myUsername = apiRequest("GET", "https://api.github.com/user", null, token).get("login").asString
                    val targetRepo = "$repoOwner/$repoName"
                    val isOwner = repoOwner.equals(myUsername, ignoreCase = true)
                    val workingRepo = if (isOwner) targetRepo else "$myUsername/$repoName"
                    
                    if (!isOwner) {
                        logs.update { it + "检查工作区 Fork 状态 (${workingRepo})..." }
                        var hasFork = false
                        try {
                            apiRequest("GET", "https://api.github.com/repos/$workingRepo", null, token)
                            hasFork = true
                        } catch (e: Exception) {
                            logs.update { it + "正在为您创建 Fork (${workingRepo})..." }
                            apiRequest("POST", "https://api.github.com/repos/$targetRepo/forks", null, token)
                        }
                        
                        if (!hasFork) {
                            var forkReady = false
                            for (i in 1..15) {
                                delay(2000)
                                try {
                                    apiRequest("GET", "https://api.github.com/repos/$workingRepo", null, token)
                                    forkReady = true
                                    break
                                } catch (e: Exception) {
                                    logs.update { it + "等待 Fork 数据同步... ($i/15)" }
                                }
                            }
                            if (!forkReady) throw Exception("Fork 同步超时，请稍后重试。")
                        }

                        logs.update { it + "强制同步您的 Fork 仓库至最新上游代码..." }
                        try {
                            val syncResp = apiRequest("POST", "https://api.github.com/repos/$workingRepo/merge-upstream", JsonObject().apply { addProperty("branch", baseBranch.value) }, token)
                            val syncMsg = syncResp.get("message")?.asString ?: ""
                            logs.update { it + "同步完成" + if (syncMsg.isNotEmpty()) ": $syncMsg" else "" }
                            delay(2000)
                        } catch (e: Exception) {
                            val errorMsg = e.message ?: ""
                            if (errorMsg.contains("409")) {
                                logs.update { it + "⚠️ 无法自动同步：您的仓库与上游存在严重代码冲突，可能导致 PR 创建失败。建议您在网页端删除并重建 Fork。" }
                            } else {
                                logs.update { it + "仓库已是最新状态。" }
                            }
                        }
                    }
                    
                    // 【核心修复点：从工作区（Fork）获取快照，彻底消灭 404】
                    logs.update { it + "获取您的工作区代码快照..." }
                    val baseRefUrl = "https://api.github.com/repos/$workingRepo/git/ref/heads/${baseBranch.value}"
                    val baseSha = try {
                        apiRequest("GET", baseRefUrl, null, token).getAsJsonObject("object").get("sha").asString
                    } catch (e: Exception) {
                        throw Exception("在您的仓库 [$workingRepo] 中找不到 [${baseBranch.value}] 分支。\n建议：检查分支名字（是否应为 master？）或在网页端检查您的仓库状态。")
                    }
                    
                    val targetBranch = newBranch.value.ifBlank { "pr-${System.currentTimeMillis()}" }
                    
                    logs.update { it + "创建并切换到工作分支: $targetBranch" }
                    val branchRef = "heads/$targetBranch"
                    var branchExists = false
                    try {
                        apiRequest("GET", "https://api.github.com/repos/$workingRepo/git/ref/$branchRef", null, token)
                        branchExists = true
                    } catch (e: Exception) {}
                    
                    if (!branchExists) {
                        apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/refs", JsonObject().apply { addProperty("ref", "refs/heads/$targetBranch"); addProperty("sha", baseSha) }, token)
                    }

                    val treeBodyArray = JsonArray()
                    var uploadedCount = 0
                    
                    val prefix = targetPathPrefix.value.trim().let { if (it.isNotEmpty() && !it.endsWith("/")) "$it/" else it }

                    for (file in actualFilesToUpload) {
                        logs.update { it + "正在上传文件: ${file.name} (${uploadedCount + 1}/${actualFilesToUpload.size})" }
                        
                        val stream = getApplication<Application>().contentResolver.openInputStream(file.uri)
                            ?: throw Exception("无法读取文件: ${file.name}")
                        val buffer = ByteArrayOutputStream()
                        stream.use { it.copyTo(buffer) }
                        val base64Content = Base64.encodeToString(buffer.toByteArray(), Base64.NO_WRAP)
                        
                        val blobSha = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/blobs", JsonObject().apply { addProperty("content", base64Content); addProperty("encoding", "base64") }, token).get("sha").asString
                        
                        val finalPath = if (file.virtualPath.startsWith("pr_attachments/")) file.virtualPath else "$prefix${file.virtualPath}"
                        treeBodyArray.add(JsonObject().apply { addProperty("path", finalPath); addProperty("mode", "100644"); addProperty("type", "blob"); addProperty("sha", blobSha) })
                        uploadedCount++
                    }

                    logs.update { it + "云端重组 Git Tree 与 Commit..." }
                    val currentBranchSha = apiRequest("GET", "https://api.github.com/repos/$workingRepo/git/ref/heads/$targetBranch", null, token).getAsJsonObject("object").get("sha").asString
                    val currentTreeSha = apiRequest("GET", "https://api.github.com/repos/$workingRepo/git/commits/$currentBranchSha", null, token).getAsJsonObject("tree").get("sha").asString
                    
                    val newTreeSha = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/trees", JsonObject().apply { addProperty("base_tree", currentTreeSha); add("tree", treeBodyArray) }, token).get("sha").asString
                    
                    if (newTreeSha == currentTreeSha) {
                        throw Exception("提交被拦截：您上传的文件与线上代码完全一致，未产生任何实际修改（Empty Diff）。\n💡 检查「目标路径前缀」是否填错导致文件没覆盖对地方？")
                    }
                    
                    val newCommitSha = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/commits", JsonObject().apply { addProperty("message", prTitle.value); addProperty("tree", newTreeSha); add("parents", JsonArray().apply { add(currentBranchSha) }) }, token).get("sha").asString

                    apiRequest("PATCH", "https://api.github.com/repos/$workingRepo/git/refs/heads/$targetBranch", JsonObject().apply { addProperty("sha", newCommitSha) }, token)

                    logs.update { it + "请求跨仓合并 (Pull Request)..." }
                    val finalDesc = prDescription.value.replace(Regex("pr_attachments/([^\\s\\)\\]\"']+)"), "https://raw.githubusercontent.com/$workingRepo/$targetBranch/pr_attachments/$1")
                    
                    val headParam = if (isOwner) targetBranch else "$myUsername:$targetBranch"
                    val pullRequestBody = JsonObject().apply { 
                        addProperty("title", prTitle.value)
                        addProperty("body", finalDesc.ifBlank { "Automated PR created by OneClickPR Android App." })
                        addProperty("head", headParam)
                        addProperty("base", baseBranch.value)
                        addProperty("draft", isDraftPr.value)
                        if (!isOwner) {
                            addProperty("maintainer_can_modify", allowMaintainerEdit.value)
                        }
                    }
                    
                    val prResponse = apiRequest("POST", "https://api.github.com/repos/$targetRepo/pulls", pullRequestBody, token)
                    
                    val prUrl = prResponse.get("html_url")?.asString ?: "Unknown URL"
                    val historyItem = PrHistory(UUID.randomUUID().toString(), prTitle.value, targetRepo, prResponse.get("number")?.asInt ?: 0, prUrl, System.currentTimeMillis())
                    _historyList.value = listOf(historyItem) + _historyList.value
                    saveHistoryToPrefs(_historyList.value)
                    
                    saveCurrentRepoToHome()

                    prSuccess.value = true
                    logs.update { it + "✅ Pull Request 完美送达！\n🔗 $prUrl" }
                    
                } catch (e: Exception) {
                    prError.value = true
                    logs.update { it + "❌ ${e.message}" }
                }
            }
        }
    }

    private fun translateGithubError(originalMsg: String): String {
        return when {
            originalMsg.contains("has no history in common") -> "您的 Fork 仓库过于古老且代码已严重冲突分化，无法直接向原作者提交 PR。\n💡 解决办法：请前往 GitHub 网页端，删掉您名下的同名仓库，然后用本应用重新提交一次即可自动建个全新的 Fork！"
            originalMsg.contains("A pull request already exists") -> "已经存在一个名字和内容完全相同的未关闭的 Pull Request。\n💡 解决办法：请换个名字填写在[新分支]选填框内再试。"
            originalMsg.contains("Validation Failed") -> "参数校验失败。通常是因为未产生任何代码变更，或是该分支已提交过 PR。"
            originalMsg.contains("Not Found") -> "找不到指定的资源。\n1. 请检查您的 Token 是否拥有 'repo' 读写权限。\n2. 请检查仓库链接是否填写正确。\n3. 请检查您的分支名称是否正确（可能是 master 而不是 main）。"
            originalMsg.contains("Bad credentials") -> "Token 凭证无效或已过期，请在设置中重新配置。"
            else -> originalMsg
        }
    }

    private fun apiRequest(method: String, url: String, body: JsonObject? = null, token: String): JsonObject {
        val requestBuilder = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").addHeader("Accept", "application/vnd.github.v3+json")
        when (method) { "GET" -> requestBuilder.get(); "POST" -> requestBuilder.post((body?.toString() ?: "{}").toRequestBody(jsonMediaType)); "PATCH" -> requestBuilder.patch((body?.toString() ?: "{}").toRequestBody(jsonMediaType)) }
        val response = okHttpClient.newCall(requestBuilder.build()).execute()
        val respBodyStr = response.body?.string()
        
        if (!response.isSuccessful) {
            var errorMsg = response.message
            try {
                val errorObj = gson.fromJson(respBodyStr, JsonObject::class.java)
                errorMsg = errorObj.get("message")?.asString ?: errorMsg
                val errorsArray = errorObj.getAsJsonArray("errors")
                if (errorsArray != null && errorsArray.size() > 0) {
                    val specificMsg = errorsArray.get(0).asJsonObject.get("message")?.asString
                    if (specificMsg != null) errorMsg = "$errorMsg\n$specificMsg"
                }
            } catch (e: Exception) {}
            throw Exception("HTTP ${response.code}:\n" + translateGithubError(errorMsg))
        }
        return if (respBodyStr.isNullOrBlank()) JsonObject() else gson.fromJson(respBodyStr, JsonObject::class.java) ?: JsonObject()
    }

    private fun sendToast(zh: String, en: String) {
        viewModelScope.launch { _uiEvents.emit(UiEvent.ShowToast(zh, en)) }
    }

    fun toggleLanguage() { language.value = if (language.value == Language.ZH) Language.EN else Language.ZH }
    fun requestFolderPicker() { viewModelScope.launch { _uiEvents.emit(UiEvent.OpenFolderPicker) } }
    fun requestFilePicker() { viewModelScope.launch { _uiEvents.emit(UiEvent.OpenFilePicker) } }
    fun requestAttachmentPicker() { viewModelScope.launch { _uiEvents.emit(UiEvent.OpenAttachmentPicker) } }
    fun navigateToHistory() { route.value = "history"; refreshHistoryStatus() }
    
    fun saveSettings(owner: String, token: String, ssh: String) { 
        githubUsername.value = owner
        githubToken.value = token
        sshKey.value = ssh
        prefs.edit().putString("owner", owner).putString("token", token).putString("ssh_key", ssh).apply()
        showSettings.value = false 
    }
}