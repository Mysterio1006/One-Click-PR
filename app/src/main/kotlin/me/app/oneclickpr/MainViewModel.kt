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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("oneclickpr_prefs", Context.MODE_PRIVATE)

    enum class Language { ZH, EN }
    data class FileItem(val id: String, val name: String, val virtualPath: String, val uri: Uri, val isDirectory: Boolean = false)

    val language = MutableStateFlow(Language.ZH)
    
    val targetUrl = MutableStateFlow("")
    val baseBranch = MutableStateFlow("main")
    val newBranch = MutableStateFlow("")
    
    val prTitle = MutableStateFlow("fix: ")
    val prDescription = MutableStateFlow("")
    val allowMaintainerEdit = MutableStateFlow(true)
    
    val showSettings = MutableStateFlow(false)
    val githubUsername = MutableStateFlow(prefs.getString("owner", "") ?: "")
    val githubToken = MutableStateFlow(prefs.getString("token", "") ?: "")
    
    private val _files = MutableStateFlow<List<FileItem>>(emptyList())
    val files: StateFlow<List<FileItem>> = _files.asStateFlow()

    val showProgress = MutableStateFlow(false)
    val prSuccess = MutableStateFlow(false)
    val logs = MutableStateFlow<List<String>>(emptyList())

    sealed class UiEvent {
        data object OpenFolderPicker : UiEvent()
        data object OpenFilePicker : UiEvent()
    }

    private val _uiEvents = MutableSharedFlow<UiEvent>()
    val uiEvents = _uiEvents.asSharedFlow()

    // --- GitHub API Client ---
    private val okHttpClient = OkHttpClient()
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    private suspend fun apiRequest(method: String, url: String, body: JsonObject? = null, token: String): JsonObject {
        return withContext(Dispatchers.IO) {
            val requestBuilder = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Accept", "application/vnd.github.v3+json")
                .addHeader("X-GitHub-Api-Version", "2022-11-28")
                
            when (method) {
                "GET" -> requestBuilder.get()
                "POST" -> requestBuilder.post((body?.toString() ?: "{}").toRequestBody(jsonMediaType))
                "PATCH" -> requestBuilder.patch((body?.toString() ?: "{}").toRequestBody(jsonMediaType))
            }
            
            val request = requestBuilder.build()
            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body?.string()
            
            if (!response.isSuccessful) {
                val errorMsg = try {
                    gson.fromJson(responseBodyString, JsonObject::class.java).get("message").asString
                } catch (e: Exception) {
                    responseBodyString ?: "Unknown Error"
                }
                throw Exception("HTTP ${response.code}: $errorMsg")
            }
            gson.fromJson(responseBodyString, JsonObject::class.java) ?: JsonObject()
        }
    }

    fun toggleLanguage() { language.value = if (language.value == Language.ZH) Language.EN else Language.ZH }
    fun requestFolderPicker() { viewModelScope.launch { _uiEvents.emit(UiEvent.OpenFolderPicker) } }
    fun requestFilePicker() { viewModelScope.launch { _uiEvents.emit(UiEvent.OpenFilePicker) } }
    fun clearFiles() { _files.value = emptyList() }
    fun removeFile(fileItem: FileItem) { _files.update { current -> current.filter { it.id != fileItem.id } } }

    fun saveSettings(owner: String, token: String) {
        githubUsername.value = owner
        githubToken.value = token
        prefs.edit().putString("owner", owner).putString("token", token).apply()
        showSettings.value = false
    }

    // 真正的“一键发 PR”逻辑：免 Clone 纯云端构建流程
    fun submitPr() {
        viewModelScope.launch {
            showProgress.value = true
            prSuccess.value = false
            logs.value = listOf("🚀 启动云端一键 PR 流程...")
            
            try {
                // 解析 URL (支持 "owner/repo" 或 "https://github.com/owner/repo.git")
                val cleanUrl = targetUrl.value.trim().trimEnd('/')
                val match = Regex("([^/\\s]+)/([^/\\s.]+)(?:\\.git)?$").find(cleanUrl)
                if (match == null) {
                    logs.update { it + "❌ 无效的 GitHub 仓库链接或格式" }
                    return@launch
                }
                
                val (repoOwner, repoName) = match.destructured
                val token = githubToken.value
                if (token.isBlank()) {
                    logs.update { it + "❌ 请先在右上角设置您的 GitHub Token" }
                    return@launch
                }

                if (files.value.isEmpty()) {
                    logs.update { it + "⚠️ 注意：当前没有选择任何文件改动。如果仓库对空 PR 有限制可能会失败。" }
                }

                // 1. 获取当前鉴权用户信息
                logs.update { it + "正在验证 GitHub 账户信息..." }
                val userResponse = apiRequest("GET", "https://api.github.com/user", null, token)
                val myUsername = userResponse.get("login").asString
                
                val targetRepo = "$repoOwner/$repoName"
                val isOwner = repoOwner.equals(myUsername, ignoreCase = true)
                val workingRepo = if (isOwner) targetRepo else "$myUsername/$repoName"
                
                // 2. 如果不是自有仓库，则检查/触发 Fork
                if (!isOwner) {
                    logs.update { it + "目标为他人仓库，正在处理底层 Fork (${workingRepo})..." }
                    try {
                        apiRequest("POST", "https://api.github.com/repos/$targetRepo/forks", null, token)
                        delay(2500) // 延迟等待 GitHub 后台处理 Fork
                    } catch (e: Exception) {
                        logs.update { it + "⚠️ Fork 或许已就绪，尝试继续..." }
                    }
                }
                
                // 3. 获取目标仓库基础分支的最新 Commit SHA
                logs.update { it + "获取目标仓库基础分支(${baseBranch.value})快照..." }
                val refResponse = apiRequest("GET", "https://api.github.com/repos/$targetRepo/git/ref/heads/${baseBranch.value}", null, token)
                val baseSha = refResponse.getAsJsonObject("object").get("sha").asString
                
                // 4. 在工作仓库（自己账号下）创建指向该 SHA 的新分支
                val targetBranch = newBranch.value.ifBlank { "pr-${System.currentTimeMillis()}" }
                logs.update { it + "在工作区($workingRepo)拉取虚拟分支($targetBranch)..." }
                val createRefBody = JsonObject().apply {
                    addProperty("ref", "refs/heads/$targetBranch")
                    addProperty("sha", baseSha)
                }
                try {
                    apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/refs", createRefBody, token)
                } catch (e: Exception) {
                    logs.update { it + "⚠️ 分支创建异常或已被占用，继续尝试覆写推送: ${e.message}" }
                }

                // 5. 将文件编码并上传 Blobs
                val treeBodyArray = JsonArray()
                if (files.value.isNotEmpty()) {
                    logs.update { it + "开始上传 ${files.value.size} 个改动文件至云端..." }
                }
                for (file in files.value) {
                    val inputStream = getApplication<Application>().contentResolver.openInputStream(file.uri)
                    val bytes = inputStream?.readBytes() ?: ByteArray(0)
                    inputStream?.close()
                    val base64Content = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    
                    val blobBody = JsonObject().apply {
                        addProperty("content", base64Content)
                        addProperty("encoding", "base64")
                    }
                    val blobResponse = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/blobs", blobBody, token)
                    val blobSha = blobResponse.get("sha").asString
                    
                    val treeItem = JsonObject().apply {
                        addProperty("path", file.virtualPath)
                        addProperty("mode", "100644")
                        addProperty("type", "blob")
                        addProperty("sha", blobSha)
                    }
                    treeBodyArray.add(treeItem)
                }

                // 6. 基于基础 Commit 构建并覆写新 Git Tree
                logs.update { it + "云端重组 Git Tree 节点..." }
                val baseCommitResponse = apiRequest("GET", "https://api.github.com/repos/$targetRepo/git/commits/$baseSha", null, token)
                val baseTreeSha = baseCommitResponse.getAsJsonObject("tree").get("sha").asString

                val treeBody = JsonObject().apply {
                    addProperty("base_tree", baseTreeSha)
                    add("tree", treeBodyArray)
                }
                val treeResponse = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/trees", treeBody, token)
                val newTreeSha = treeResponse.get("sha").asString

                // 7. 创建 Commit
                logs.update { it + "签出并提交 Commit..." }
                val commitBody = JsonObject().apply {
                    addProperty("message", prTitle.value)
                    addProperty("tree", newTreeSha)
                    val parentsArray = JsonArray()
                    parentsArray.add(baseSha)
                    add("parents", parentsArray)
                }
                val commitResponse = apiRequest("POST", "https://api.github.com/repos/$workingRepo/git/commits", commitBody, token)
                val newCommitSha = commitResponse.get("sha").asString

                // 8. 更新分支指针至新 Commit
                logs.update { it + "推动 Commit 至远端仓库 ($targetBranch)..." }
                val updateRefBody = JsonObject().apply {
                    addProperty("sha", newCommitSha)
                }
                apiRequest("PATCH", "https://api.github.com/repos/$workingRepo/git/refs/heads/$targetBranch", updateRefBody, token)

                // 9. 创建 Pull Request 到原仓库
                logs.update { it + "跨仓库发起最终 Pull Request..." }
                val prBody = JsonObject().apply {
                    addProperty("title", prTitle.value)
                    addProperty("body", prDescription.value.ifBlank { "Automated PR created by OneClickPR." })
                    // PR Head: 如果是从 fork 发起，必须附带账号名称作为前缀
                    addProperty("head", "$myUsername:$targetBranch")
                    addProperty("base", baseBranch.value)
                    addProperty("maintainer_can_modify", allowMaintainerEdit.value)
                }
                val prResponse = apiRequest("POST", "https://api.github.com/repos/$targetRepo/pulls", prBody, token)
                val prUrl = prResponse.get("html_url")?.asString ?: "Unknown URL"

                prSuccess.value = true
                logs.update { it + "✅ Pull Request 完美送达！" }
                logs.update { it + "🔗 直达链接: $prUrl" }
                
            } catch (e: Exception) {
                logs.update { it + "❌ 发生毁灭性异常: ${e.message}" }
            }
        }
    }

    fun onFolderSelected(treeUri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val root = DocumentFile.fromTreeUri(getApplication(), treeUri) ?: return@launch
            val newFiles = mutableListOf<FileItem>()
            traverseFolder(root, "", newFiles)
            _files.update { it + newFiles }
        }
    }

    fun onFileSelected(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            var fileName = "attachment_${System.currentTimeMillis()}"
            getApplication<Application>().contentResolver
                .query(uri, null, null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx != -1) fileName = cursor.getString(idx)
                    }
                }
            val virtualPath = "pr_attachments/$fileName"
            val id = java.util.UUID.randomUUID().toString()
            _files.update { it + FileItem(id, fileName, virtualPath, uri, false) }
        }
    }

    private fun traverseFolder(folder: DocumentFile, pathPrefix: String, out: MutableList<FileItem>) {
        folder.listFiles().forEach { file ->
            if (file.isDirectory) {
                traverseFolder(file, "$pathPrefix${file.name}/", out)
            } else {
                val fullPath = "$pathPrefix${file.name}"
                val id = java.util.UUID.randomUUID().toString()
                out.add(FileItem(id, file.name ?: "unknown", fullPath, file.uri, false))
            }
        }
    }
}