package me.app.oneclickpr

import android.app.Application
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("oneclickpr_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    enum class Language { ZH, EN }
    data class PrHistory(val id: String, val title: String, val repo: String, val number: Int, val url: String, val timeMs: Long, var status: String = "Open")
    
    data class FileNode(
        val id: String,
        val name: String,
        val virtualPath: String,
        val uri: Uri,
        val isDirectory: Boolean,
        val depth: Int,
        val isExpanded: Boolean = false,
        val isChecked: Boolean = true,
        val children: List<FileNode> = emptyList()
    )

    val language = MutableStateFlow(Language.ZH)
    val route = MutableStateFlow("main")
    
    val targetUrl = MutableStateFlow("")
    val baseBranch = MutableStateFlow("main")
    val newBranch = MutableStateFlow("")
    
    val prTitle = MutableStateFlow("fix: ")
    val prDescription = MutableStateFlow("")
    val allowMaintainerEdit = MutableStateFlow(true)
    
    val showSettings = MutableStateFlow(false)
    val githubUsername = MutableStateFlow(prefs.getString("owner", "") ?: "")
    val githubToken = MutableStateFlow(prefs.getString("token", "") ?: "")
    
    private val _fileNodes = MutableStateFlow<List<FileNode>>(emptyList())
    val flatFileNodes: StateFlow<List<FileNode>> = _fileNodes.map { flattenTree(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val isParsingFolder = MutableStateFlow(false)

    val useGitignore = MutableStateFlow(true)
    val gitignoreContent = MutableStateFlow("")
    val showGitignoreDialog = MutableStateFlow(false)

    private val _historyList = MutableStateFlow<List<PrHistory>>(emptyList())
    val historyList: StateFlow<List<PrHistory>> = _historyList.asStateFlow()

    val showProgress = MutableStateFlow(false)
    val prSuccess = MutableStateFlow(false)
    val prError = MutableStateFlow(false)
    val logs = MutableStateFlow<List<String>>(emptyList())

    sealed class UiEvent {
        data object OpenFolderPicker : UiEvent()
        data object OpenFilePicker : UiEvent()
        data object OpenAttachmentPicker : UiEvent()
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

    fun clearFiles() {
        _fileNodes.value = emptyList()
    }

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

            val rules = if (useGitignore.value) {
                gitignoreContent.value.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            } else emptyList()

            val children = buildTree(root, 1, "", rules)
            
            val folderNode = FileNode(
                id = UUID.randomUUID().toString(),
                name = root.name ?: "Project Folder",
                virtualPath = "",
                uri = treeUri,
                isDirectory = true,
                depth = 0,
                isExpanded = true,
                isChecked = true,
                children = children
            )

            _fileNodes.update { it + folderNode }
            isParsingFolder.value = false
        }
    }

    fun applyGitignoreRules() {
        showGitignoreDialog.value = false
        viewModelScope.launch(Dispatchers.IO) {
            isParsingFolder.value = true
            val rules = if (useGitignore.value) {
                gitignoreContent.value.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            } else emptyList()

            val updatedNodes = _fileNodes.value.map { rootNode ->
                if (rootNode.isDirectory && rootNode.depth == 0) {
                    val rootDoc = DocumentFile.fromTreeUri(getApplication(), rootNode.uri)
                    if (rootDoc != null) {
                        val newChildren = buildTree(rootDoc, 1, "", rules)
                        rootNode.copy(children = newChildren)
                    } else rootNode
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
            result.add(FileNode(UUID.randomUUID().toString(), name, path, file.uri, isDir, depth, isExpanded = false, isChecked = true, children = children))
        }
        return result.sortedWith(compareBy({ !it.isDirectory }, { it.name }))
    }

    private fun isIgnored(name: String, isDir: Boolean, rules: List<String>): Boolean {
        if (name == ".git" || name == ".idea") return true
        if (!useGitignore.value) return false

        for (rule in rules) {
            val r = rule.trim()
            if (r.isEmpty()) continue
            
            if (r.endsWith("/")) {
                val dirName = r.dropLast(1)
                if (isDir && name == dirName) return true
            } else if (r.startsWith("*.")) {
                val ext = r.drop(1)
                if (!isDir && name.endsWith(ext)) return true
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
            getApplication<Application>().contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx != -1) fileName = cursor.getString(idx)
                }
            }
            val virtualPath = if (isAttachment) "pr_attachments/$fileName" else fileName
            val node = FileNode(UUID.randomUUID().toString(), fileName, virtualPath, uri, false, 0)
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
        val isZh = language.value == Language.ZH
        if (targetUrl.value.isBlank()) { Toast.makeText(getApplication(), if (isZh) "请填写目标开源项目克隆链接" else "Target Repository URL is required", Toast.LENGTH_SHORT).show(); return }
        if (prTitle.value.isBlank()) { Toast.makeText(getApplication(), if (isZh) "请填写 Pull Request 标题" else "PR Title is required", Toast.LENGTH_SHORT).show(); return }

        val actualFilesToUpload = mutableListOf<FileNode>()
        fun collectCheckedFiles(nodes: List<FileNode>) {
            for (node in nodes) {
                if (node.isChecked && !node.isDirectory) actualFilesToUpload.add(node)
                if (node.isDirectory && node.isChecked) collectCheckedFiles(node.children)
            }
        }
        collectCheckedFiles(_fileNodes.value)

        if (actualFilesToUpload.isEmpty()) {
            Toast.makeText(getApplication(), if (isZh) "没有选中任何要提交的文件！" else "No files selected for PR!", Toast.LENGTH_SHORT).show()
            return
        }

        viewModelScope.launch {
            showProgress.value = true
            prSuccess.value = false
            prError.value = false
            logs.value = listOf("🚀 启动云端一键 PR 流程...", "📦 准备上传 ${actualFilesToUpload.size} 个改动文件...")
            
            try {
                val cleanUrl = targetUrl.value.trim().trimEnd('/')
                val match = Regex("([^/\\s]+)/([^/\\s.]+)(?:\\.git)?$").find(cleanUrl)
                    ?: throw Exception("无效的 GitHub 仓库链接或格式。")
                
                val (repoOwner, repoName) = match.destructured
                val token = githubToken.value
                if (token.isBlank()) throw Exception("请先在右上角设置您的 GitHub Token。")

                val myUsername = apiRequest("GET", "https://api.github.com/user", null, token).get("login").asString
                val targetRepo = "$repoOwner/$repoName"
                val isOwner = repoOwner.equals(myUsername, ignoreCase = true)
                val workingRepo = if (isOwner) targetRepo else "$myUsername/$repoName"
                
                if (!isOwner) {
                    logs.update { it + "检查 Fork 状态 (${workingRepo})..." }
                    var hasFork = false
                    try {
                        apiRequest("GET", "https://api.github.com/repos/$workingRepo", null, token)
                        hasFork = true
                    } catch (e: Exception) {
                        logs.update { it + "触发首次 Fork (${workingRepo})..." }
                        apiRequest("POST", "https://api.github.com/repos/$targetRepo/forks", null, token)
                        delay(3500)
                    }
                    if (hasFork) {
                        try {
                            apiRequest("POST", "https://api.github.com/repos/$workingRepo/merge-upstream", JsonObject().apply { addProperty("branch", baseBranch.value) }, token)
                        } catch (e: Exception) { }
                    }
                }
                
                logs.update { it + "获取目标仓库基础分支快照..." }
                val baseSha = apiRequest("GET", "https://api.github.com/repos/$targetRepo/git/ref/heads/${baseBranch.value}", null, token).getAsJsonObject("object").get("sha").asString
                
                val targetBranch = newBranch.value.ifBlank { "pr-${System.currentTimeMillis()}" }
                try {
                    apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/refs", JsonObject().apply { addProperty("ref", "refs/heads/$targetBranch"); addProperty("sha", baseSha) }, token)
                } catch (e: Exception) { }

                val treeBodyArray = JsonArray()
                for (file in actualFilesToUpload) {
                    val bytes = getApplication<Application>().contentResolver.openInputStream(file.uri)?.use { it.readBytes() } ?: ByteArray(0)
                    val blobSha = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/blobs", JsonObject().apply { addProperty("content", Base64.encodeToString(bytes, Base64.NO_WRAP)); addProperty("encoding", "base64") }, token).get("sha").asString
                    treeBodyArray.add(JsonObject().apply { addProperty("path", file.virtualPath); addProperty("mode", "100644"); addProperty("type", "blob"); addProperty("sha", blobSha) })
                }

                logs.update { it + "云端重组 Git Tree 与 Commit..." }
                val baseTreeSha = apiRequest("GET", "https://api.github.com/repos/$targetRepo/git/commits/$baseSha", null, token).getAsJsonObject("tree").get("sha").asString
                val newTreeSha = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/trees", JsonObject().apply { addProperty("base_tree", baseTreeSha); add("tree", treeBodyArray) }, token).get("sha").asString
                val newCommitSha = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/commits", JsonObject().apply { addProperty("message", prTitle.value); addProperty("tree", newTreeSha); add("parents", JsonArray().apply { add(baseSha) }) }, token).get("sha").asString

                apiRequest("PATCH", "https://api.github.com/repos/$workingRepo/git/refs/heads/$targetBranch", JsonObject().apply { addProperty("sha", newCommitSha) }, token)

                logs.update { it + "跨仓 Pull Request 生成..." }
                val finalDesc = prDescription.value.replace(Regex("pr_attachments/([^\\s\\)\\]\"']+)"), "https://raw.githubusercontent.com/$workingRepo/$targetBranch/pr_attachments/$1")
                val prResponse = apiRequest("POST", "https://api.github.com/repos/$targetRepo/pulls", JsonObject().apply { addProperty("title", prTitle.value); addProperty("body", finalDesc.ifBlank { "Automated PR created by OneClickPR." }); addProperty("head", "$myUsername:$targetBranch"); addProperty("base", baseBranch.value); addProperty("maintainer_can_modify", allowMaintainerEdit.value) }, token)
                
                val prUrl = prResponse.get("html_url")?.asString ?: "Unknown URL"
                val historyItem = PrHistory(UUID.randomUUID().toString(), prTitle.value, targetRepo, prResponse.get("number")?.asInt ?: 0, prUrl, System.currentTimeMillis())
                _historyList.value = listOf(historyItem) + _historyList.value
                saveHistoryToPrefs(_historyList.value)

                prSuccess.value = true
                logs.update { it + "✅ Pull Request 完美送达！\n🔗 $prUrl" }
                
            } catch (e: Exception) {
                prError.value = true
                logs.update { it + "❌ 异常: ${e.message}" }
            }
        }
    }

    private suspend fun apiRequest(method: String, url: String, body: JsonObject? = null, token: String): JsonObject {
        return withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder().url(url).addHeader("Authorization", "Bearer $token").addHeader("Accept", "application/vnd.github.v3+json")
            when (method) { "GET" -> requestBuilder.get(); "POST" -> requestBuilder.post((body?.toString() ?: "{}").toRequestBody(jsonMediaType)); "PATCH" -> requestBuilder.patch((body?.toString() ?: "{}").toRequestBody(jsonMediaType)) }
            val response = okHttpClient.newCall(requestBuilder.build()).execute()
            if (!response.isSuccessful) throw Exception("HTTP ${response.code}: " + (try { gson.fromJson(response.body?.string(), JsonObject::class.java).get("message").asString } catch (e: Exception) { "Unknown Error" }))
            gson.fromJson(response.body?.string(), JsonObject::class.java) ?: JsonObject()
        }
    }

    fun toggleLanguage() { language.value = if (language.value == Language.ZH) Language.EN else Language.ZH }
    fun requestFolderPicker() { viewModelScope.launch { _uiEvents.emit(UiEvent.OpenFolderPicker) } }
    fun requestFilePicker() { viewModelScope.launch { _uiEvents.emit(UiEvent.OpenFilePicker) } }
    fun requestAttachmentPicker() { viewModelScope.launch { _uiEvents.emit(UiEvent.OpenAttachmentPicker) } }
    fun navigateToHistory() { route.value = "history"; refreshHistoryStatus() }
    
    fun saveSettings(owner: String, token: String) { 
        githubUsername.value = owner
        githubToken.value = token
        prefs.edit().putString("owner", owner).putString("token", token).apply()
        showSettings.value = false 
    }
    
    private fun loadHistoryFromPrefs() { 
        _historyList.value = try { 
            gson.fromJson(prefs.getString("pr_history", "[]"), object : TypeToken<List<PrHistory>>() {}.type) ?: emptyList() 
        } catch (e: Exception) { 
            emptyList() 
        } 
    }
    
    private fun saveHistoryToPrefs(list: List<PrHistory>) { 
        prefs.edit().putString("pr_history", gson.toJson(list)).apply() 
    }
    
    fun clearHistory() { 
        _historyList.value = emptyList()
        saveHistoryToPrefs(emptyList()) 
    }
    
    private fun refreshHistoryStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val token = githubToken.value
            if (token.isBlank()) return@launch
            val currentList = _historyList.value.toMutableList()
            var isChanged = false
            for (i in currentList.indices) {
                val item = currentList[i]
                if (item.status != "Open") continue
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
}