package me.app.oneclickpr.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import me.app.oneclickpr.MainViewModel
import me.app.oneclickpr.MainViewModel.Language
import androidx.compose.foundation.text.selection.SelectionContainer

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val language by vm.language.collectAsState()
    val targetUrl by vm.targetUrl.collectAsState()
    val baseBranch by vm.baseBranch.collectAsState()
    val newBranch by vm.newBranch.collectAsState()
    val prTitle by vm.prTitle.collectAsState()
    val prDesc by vm.prDescription.collectAsState()
    val allowMaintainerEdit by vm.allowMaintainerEdit.collectAsState()
    val files by vm.files.collectAsState()
    val showSettings by vm.showSettings.collectAsState()
    val showProgress by vm.showProgress.collectAsState()
    val githubUsername by vm.githubUsername.collectAsState()

    var isPreviewMode by remember { mutableStateOf(false) }

    val folderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let { vm.onFolderSelected(it) } }

    val fileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { vm.onFileSelected(it) } }

    LaunchedEffect(Unit) {
        vm.uiEvents.collect { event ->
            when (event) {
                is MainViewModel.UiEvent.OpenFolderPicker -> folderLauncher.launch(null)
                is MainViewModel.UiEvent.OpenFilePicker -> fileLauncher.launch("*/*")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("One-Click PR", fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { vm.toggleLanguage() }) {
                        Text(if (language == Language.ZH) "EN" else "中", fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = { vm.showSettings.value = true }) {
                        if (githubUsername.isNotBlank()) {
                            AsyncImage(
                                model = "https://github.com/${githubUsername}.png",
                                contentDescription = "GitHub Avatar",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Settings", modifier = Modifier.size(32.dp))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        bottomBar = {
            Surface(
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = { vm.submitPr() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = i18n("提交 Pull Request", "Submit Pull Request", language),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = i18n("目标开源项目", "Target Repository", language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = targetUrl,
                        onValueChange = { vm.targetUrl.value = it },
                        label = { Text(i18n("HTTPS 克隆链接", "HTTPS Clone URL", language)) },
                        placeholder = { Text(i18n("https://github.com/作者/仓库.git", "https://github.com/author/repo.git", language)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = baseBranch,
                            onValueChange = { vm.baseBranch.value = it },
                            label = { Text(i18n("原分支", "Base Branch", language)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newBranch,
                            onValueChange = { vm.newBranch.value = it },
                            label = { Text(i18n("新分支 (选填)", "New Branch (Optional)", language)) },
                            placeholder = { Text("pr-xxx") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = i18n("Pull Request 内容", "Pull Request Content", language),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    OutlinedTextField(
                        value = prTitle,
                        onValueChange = { vm.prTitle.value = it },
                        label = { Text(i18n("标题 *", "Title *", language)) },
                        placeholder = { Text(i18n("feat: 修复了界面的显示异常", "feat: fixed display issue...", language)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                            .padding(top = 8.dp)
                    ) {
                        TabRow(
                            selectedTabIndex = if (isPreviewMode) 1 else 0,
                            containerColor = Color.Transparent,
                            divider = {},
                            modifier = Modifier.padding(horizontal = 8.dp)
                        ) {
                            Tab(
                                selected = !isPreviewMode,
                                onClick = { isPreviewMode = false },
                                text = { Text(i18n("编辑", "Edit", language), fontWeight = FontWeight.Bold) }
                            )
                            Tab(
                                selected = isPreviewMode,
                                onClick = { isPreviewMode = true },
                                text = { Text(i18n("预览", "Preview", language), fontWeight = FontWeight.Bold) }
                            )
                        }
                        
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 150.dp)
                                .padding(12.dp)
                        ) {
                            if (isPreviewMode) {
                                Text(
                                    text = parseSimpleMarkdown(prDesc.ifBlank { i18n("暂无描述内容", "No description provided", language) }),
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            } else {
                                TextField(
                                    value = prDesc,
                                    onValueChange = { vm.prDescription.value = it },
                                    placeholder = { Text(i18n("支持基础 Markdown 语法格式", "Supports basic Markdown styling", language)) },
                                    colors = TextFieldDefaults.colors(
                                        focusedContainerColor = Color.Transparent,
                                        unfocusedContainerColor = Color.Transparent,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent
                                    ),
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable { vm.allowMaintainerEdit.value = !allowMaintainerEdit }
                    ) {
                        Checkbox(
                            checked = allowMaintainerEdit,
                            onCheckedChange = { vm.allowMaintainerEdit.value = it }
                        )
                        Text(
                            text = i18n("允许维护者编辑此 PR", "Allow edits from maintainers", language),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = i18n("文件改动区", "File Changes", language),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (files.isNotEmpty()) {
                            TextButton(
                                onClick = { vm.clearFiles() },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(i18n("清空全部", "Clear All", language))
                            }
                        }
                    }
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ElevatedFilterChip(
                            selected = false,
                            onClick = { vm.requestFilePicker() },
                            label = { Text(i18n("添加文件", "Add File", language)) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                        ElevatedFilterChip(
                            selected = false,
                            onClick = { vm.requestFolderPicker() },
                            label = { Text(i18n("添加文件夹", "Add Folder", language)) },
                            leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                    
                    if (files.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = i18n("暂未添加任何文件改动", "No files added yet", language),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            files.forEach { file ->
                                ListItem(
                                    headlineContent = { 
                                        Text(file.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    supportingContent = {
                                        Text(file.virtualPath, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    },
                                    leadingContent = {
                                        Icon(
                                            imageVector = Icons.Default.InsertDriveFile,
                                            contentDescription = "File",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { vm.removeFile(file) }) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove File",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                                )
                            }
                        }
                    }
                }
            }
            
            // 底部占位符，避免内容被 fixed Button 遮挡
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showSettings) {
        SettingsDialog(vm)
    }
    
    if (showProgress) {
        ProgressDialog(vm)
    }
}

// 简易且无外部依赖的 Markdown 高亮解析器
fun parseSimpleMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var cursor = 0
        // 匹配粗体 **文本**，斜体 *文本*，单行代码 `文本`
        val regex = Regex("(\\*\\*(.*?)\\*\\*)|(\\*(.*?)\\*)|(`(.*?)`)")
        val matches = regex.findAll(text)
        
        for (match in matches) {
            append(text.substring(cursor, match.range.first))
            
            when {
                match.groups[1] != null -> { // **Bold**
                    pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                    append(match.groups[2]?.value)
                    pop()
                }
                match.groups[3] != null -> { // *Italic*
                    pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                    append(match.groups[4]?.value)
                    pop()
                }
                match.groups[5] != null -> { // `Code`
                    pushStyle(SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        background = Color(0x33888888),
                    ))
                    append(" ${match.groups[6]?.value} ")
                    pop()
                }
            }
            cursor = match.range.last + 1
        }
        append(text.substring(cursor, text.length))
    }
}

@Composable
fun SettingsDialog(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    var username by remember { mutableStateOf(vm.githubUsername.value) }
    var token by remember { mutableStateOf(vm.githubToken.value) }

    Dialog(onDismissRequest = { vm.showSettings.value = false }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = i18n("全局凭证配置", "Global Credentials", language),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = { vm.showSettings.value = false }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                
                Text(
                    text = i18n("凭证将保存在本地应用数据中，设置用户名后将自动拉取对应 GitHub 头像。", "Credentials are saved locally. Setting username fetches GitHub avatar.", language),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text(i18n("您的 GitHub 用户名", "Your GitHub Username", language)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text(i18n("个人访问令牌 (Token)", "Personal Access Token", language)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                
                Button(
                    onClick = { vm.saveSettings(username, token) },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp)
                ) {
                    Text(i18n("保存凭证", "Save Credentials", language))
                }
            }
        }
    }
}

@Composable
fun ProgressDialog(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    val logs by vm.logs.collectAsState()
    val success by vm.prSuccess.collectAsState()
    
    val scrollState = rememberScrollState()
    LaunchedEffect(logs.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Dialog(
        onDismissRequest = { if (success) vm.showProgress.value = false },
        properties = DialogProperties(dismissOnBackPress = success, dismissOnClickOutside = success)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = i18n("正在执行云端流转", "Executing cloud workflow", language),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                if (!success) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp))
                        .padding(12.dp)
                        .verticalScroll(scrollState)
                ) {
                    // 加入 SelectionContainer 允许拷贝日志文本
                    SelectionContainer {
                        Text(
                            text = logs.joinToString("\n"),
                            color = Color(0xFFA6ACCD),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
                
                if (success) {
                    Text(
                        text = i18n("Pull Request 提交成功", "PR Successfully Created", language),
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                    
                    Button(
                        onClick = { vm.showProgress.value = false },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(i18n("查看 Pull Request", "View Pull Request", language))
                    }
                }
            }
        }
    }
}

private fun i18n(zh: String, en: String, language: Language): String {
    return if (language == Language.ZH) zh else en
}