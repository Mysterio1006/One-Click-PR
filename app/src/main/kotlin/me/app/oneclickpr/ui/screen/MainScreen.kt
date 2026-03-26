package me.app.oneclickpr.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.mikepenz.markdown.m3.Markdown
import me.app.oneclickpr.MainViewModel
import me.app.oneclickpr.MainViewModel.Language
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val currentRoute by vm.route.collectAsState()
    BackHandler(enabled = currentRoute == "history") { vm.route.value = "main" }

    if (currentRoute == "main") MainContent(vm) else HistoryContent(vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    val targetUrl by vm.targetUrl.collectAsState()
    val baseBranch by vm.baseBranch.collectAsState()
    val newBranch by vm.newBranch.collectAsState()
    val prTitle by vm.prTitle.collectAsState()
    val prDesc by vm.prDescription.collectAsState()
    val allowMaintainerEdit by vm.allowMaintainerEdit.collectAsState()
    val flatFileNodes by vm.flatFileNodes.collectAsState()
    val isParsingFolder by vm.isParsingFolder.collectAsState()
    
    val showSettings by vm.showSettings.collectAsState()
    val showProgress by vm.showProgress.collectAsState()
    val githubUsername by vm.githubUsername.collectAsState()

    val useGitignore by vm.useGitignore.collectAsState()
    val showGitignoreDialog by vm.showGitignoreDialog.collectAsState()

    var isPreviewMode by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let { vm.onFolderSelected(it) } }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.onFileSelected(it) } }
    val attachmentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.onAttachmentSelected(it) } }

    LaunchedEffect(Unit) {
        vm.uiEvents.collect { event ->
            when (event) {
                is MainViewModel.UiEvent.OpenFolderPicker -> folderLauncher.launch(null)
                is MainViewModel.UiEvent.OpenFilePicker -> fileLauncher.launch("*/*")
                is MainViewModel.UiEvent.OpenAttachmentPicker -> attachmentLauncher.launch("*/*")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("One-Click PR", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { vm.navigateToHistory() }) { Icon(Icons.Default.History, contentDescription = "History", tint = MaterialTheme.colorScheme.onSurface) }
                    TextButton(onClick = { vm.toggleLanguage() }) { Text(if (language == Language.ZH) "EN" else "中", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
                    IconButton(onClick = { vm.showSettings.value = true }) {
                        if (githubUsername.isNotBlank()) AsyncImage(model = "https://github.com/${githubUsername}.png", contentDescription = "GitHub Avatar", contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(CircleShape))
                        else Icon(Icons.Default.AccountCircle, contentDescription = "Settings", modifier = Modifier.size(32.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface, titleContentColor = MaterialTheme.colorScheme.onSurface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = i18n("目标开源项目", "Target Repository", language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = targetUrl, onValueChange = { vm.targetUrl.value = it }, label = { Text(i18n("HTTPS 克隆链接 *", "HTTPS Clone URL *", language)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = baseBranch, onValueChange = { vm.baseBranch.value = it }, label = { Text(i18n("原分支", "Base Branch", language)) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = newBranch, onValueChange = { vm.newBranch.value = it }, label = { Text(i18n("新分支 (选填)", "New Branch (Optional)", language)) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            }
            
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = i18n("Pull Request 内容", "Pull Request Content", language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = prTitle, onValueChange = { vm.prTitle.value = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text(buildAnnotatedString { append(i18n("标题 ", "Title ", language)); withStyle(SpanStyle(color = MaterialTheme.colorScheme.error)) { append("*") } }) }
                    )

                    Card(shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.fillMaxWidth().height(48.dp)) {
                                Surface(onClick = { isPreviewMode = false }, modifier = Modifier.weight(1f).fillMaxHeight(), color = if (!isPreviewMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RectangleShape) {
                                    Box(contentAlignment = Alignment.Center) { Text(text = i18n("编辑", "Edit", language), fontWeight = if (!isPreviewMode) FontWeight.Bold else FontWeight.Normal, color = MaterialTheme.colorScheme.onSurface); Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (!isPreviewMode) 2.dp else 1.dp).background(if (!isPreviewMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant)) }
                                }
                                Surface(onClick = { isPreviewMode = true }, modifier = Modifier.weight(1f).fillMaxHeight(), color = if (isPreviewMode) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), shape = RectangleShape) {
                                    Box(contentAlignment = Alignment.Center) { Text(text = i18n("预览", "Preview", language), fontWeight = if (isPreviewMode) FontWeight.Bold else FontWeight.Normal, color = if (isPreviewMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant); Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(if (isPreviewMode) 2.dp else 1.dp).background(if (isPreviewMode) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant)) }
                                }
                            }
                            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 220.dp)) {
                                if (isPreviewMode) Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) { Markdown(content = prDesc.ifBlank { i18n("暂无描述内容", "No description provided", language) }) }
                                else TextField(value = prDesc, onValueChange = { vm.prDescription.value = it }, placeholder = { Text(i18n("支持 Markdown 语法。您可以手动输入内容或粘贴图片链接。", "Supports Markdown syntax.", language)) }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), modifier = Modifier.fillMaxSize())
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f).clickable { vm.allowMaintainerEdit.value = !allowMaintainerEdit }) {
                            Checkbox(checked = allowMaintainerEdit, onCheckedChange = { vm.allowMaintainerEdit.value = it })
                            Text(text = i18n("允许维护者编辑此 PR", "Allow edits from maintainers", language), style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(onClick = { vm.requestAttachmentPicker() }) { Icon(imageVector = Icons.Default.AttachFile, contentDescription = "Attach", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = i18n("文件改动区", "File Changes", language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (flatFileNodes.isNotEmpty()) {
                            TextButton(onClick = { vm.clearFiles() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(i18n("清空全部", "Clear All", language)) }
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ElevatedFilterChip(selected = false, onClick = { vm.requestFilePicker() }, label = { Text(i18n("添加代码文件", "Add File", language)) }, leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) })
                        ElevatedFilterChip(selected = false, onClick = { vm.requestFolderPicker() }, label = { Text(i18n("添加文件夹", "Add Folder", language)) }, leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) })
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = useGitignore, onCheckedChange = { vm.useGitignore.value = it })
                        Text(text = i18n("启用 .gitignore 规则忽略编译产物", "Enable .gitignore filtering", language), style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { vm.useGitignore.value = !useGitignore })
                        Spacer(modifier = Modifier.weight(1f))
                        if (useGitignore) {
                            TextButton(onClick = { vm.showGitignoreDialog.value = true }, contentPadding = PaddingValues(0.dp)) { Text(i18n("编辑规则", "Edit Rules", language), fontSize = 13.sp) }
                        }
                    }
                    
                    if (isParsingFolder) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (flatFileNodes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            Text(text = i18n("暂未添加任何文件改动", "No files added yet", language), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(flatFileNodes, key = { it.id }) { node ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                                        .background(if (node.isDirectory) MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f) else Color.Transparent)
                                        .padding(start = (node.depth * 16).dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (node.isDirectory) {
                                        IconButton(onClick = { vm.toggleNodeExpand(node.id) }, modifier = Modifier.size(24.dp)) {
                                            Icon(if (node.isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowRight, contentDescription = "Expand", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        Icon(Icons.Default.Folder, contentDescription = "Folder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    } else {
                                        Spacer(modifier = Modifier.width(24.dp))
                                        Icon(Icons.Default.InsertDriveFile, contentDescription = "File", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(text = node.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                    
                                    Checkbox(
                                        checked = node.isChecked,
                                        onCheckedChange = { vm.toggleNodeCheck(node.id, it) },
                                        modifier = Modifier.scale(0.8f)
                                    )
                                    
                                    if (node.depth == 0) {
                                        IconButton(onClick = { vm.removeNode(node.id) }, modifier = Modifier.size(24.dp)) {
                                            Icon(Icons.Default.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Button(onClick = { vm.submitPr() }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) {
                Text(text = i18n("提交 Pull Request", "Submit Pull Request", language), fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSettings) SettingsDialog(vm)
    if (showGitignoreDialog) GitignoreDialog(vm)
    if (showProgress) ProgressDialog(vm)
}

@Composable
fun GitignoreDialog(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    var content by remember { mutableStateOf(vm.gitignoreContent.value) }

    Dialog(onDismissRequest = { vm.showGitignoreDialog.value = false }) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxHeight(0.8f)) {
            Column(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = i18n("编辑过滤规则", "Edit Gitignore Rules", language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { vm.showGitignoreDialog.value = false }) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                
                TextField(
                    value = content, onValueChange = { content = it },
                    placeholder = { Text("build/\n.idea/\n*.apk\n...") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                )

                Button(onClick = { vm.gitignoreContent.value = content; vm.applyGitignoreRules() }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) { 
                    Text(i18n("保存并重新扫描目录", "Save and Rescan", language)) 
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    val historyList by vm.historyList.collectAsState()
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(i18n("提交历史", "PR History", language), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { vm.route.value = "main" }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") } },
                actions = { if (historyList.isNotEmpty()) IconButton(onClick = { vm.clearHistory() }) { Icon(Icons.Default.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        if (historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(i18n("暂无提交记录", "No history available", language), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(historyList, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), onClick = { if (item.url.isNotBlank()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }) {
                        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = "${item.repo} #${item.number}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(text = sdf.format(Date(item.timeMs)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            val statusColor = when (item.status) { "Open" -> Color(0xFF10B981); "Merged" -> Color(0xFF8B5CF6); else -> Color(0xFFEF4444) }
                            Surface(shape = RoundedCornerShape(16.dp), color = statusColor.copy(alpha = 0.15f), border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))) {
                                Text(text = item.status, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = statusColor, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    var username by remember { mutableStateOf(vm.githubUsername.value) }
    var token by remember { mutableStateOf(vm.githubToken.value) }

    Dialog(onDismissRequest = { vm.showSettings.value = false }) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = i18n("全局凭证配置", "Global Credentials", language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { vm.showSettings.value = false }) { Icon(Icons.Default.Close, contentDescription = "Close") }
                }
                Text(text = i18n("凭证将保存在本地应用数据中，设置用户名后将自动拉取对应 GitHub 头像。", "Credentials are saved locally.", language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(i18n("您的 GitHub 用户名", "Your GitHub Username", language)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text(i18n("个人访问令牌 (Token)", "Personal Access Token", language)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                Button(onClick = { vm.saveSettings(username, token) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) { Text(i18n("保存凭证", "Save Credentials", language)) }
            }
        }
    }
}

@Composable
fun ProgressDialog(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    val logs by vm.logs.collectAsState()
    val success by vm.prSuccess.collectAsState()
    val error by vm.prError.collectAsState()
    val scrollState = rememberScrollState()
    val isFinished = success || error 
    
    LaunchedEffect(logs.size) { scrollState.animateScrollTo(scrollState.maxValue) }

    Dialog(onDismissRequest = { if (isFinished) vm.showProgress.value = false }, properties = DialogProperties(dismissOnBackPress = isFinished, dismissOnClickOutside = isFinished)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = i18n("正在执行云端流转", "Executing cloud workflow", language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!isFinished) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                Box(modifier = Modifier.fillMaxWidth().height(180.dp).background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp)).padding(12.dp).verticalScroll(scrollState)) {
                    SelectionContainer { Text(text = logs.joinToString("\n"), color = Color(0xFFA6ACCD), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp) }
                }
                if (success) {
                    Text(text = i18n("Pull Request 提交成功", "PR Successfully Created", language), color = Color(0xFF10B981), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Button(onClick = { vm.showProgress.value = false }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), modifier = Modifier.fillMaxWidth()) { Text(i18n("关闭", "Close", language)) }
                } else if (error) {
                    Text(text = i18n("流转失败，请检查报错内容", "Workflow Failed. Check logs.", language), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Button(onClick = { vm.showProgress.value = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text(i18n("关闭", "Close", language)) }
                }
            }
        }
    }
}

private fun i18n(zh: String, en: String, language: Language): String { return if (language == Language.ZH) zh else en }