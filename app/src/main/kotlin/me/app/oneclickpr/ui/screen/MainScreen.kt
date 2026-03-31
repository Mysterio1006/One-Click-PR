package me.app.oneclickpr.ui.screen

import android.content.Intent
import android.net.Uri
import android.widget.Toast
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
import me.app.oneclickpr.MainViewModel
import me.app.oneclickpr.MainViewModel.Language
import me.app.oneclickpr.ui.components.SimpleMarkdown
import me.app.oneclickpr.ui.theme.AppIcons
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val currentRoute by vm.route.collectAsState()
    
    BackHandler(enabled = currentRoute != "home") { 
        if (currentRoute == "history") vm.route.value = "pr_create"
        else vm.route.value = "home" 
    }

    when (currentRoute) {
        "home" -> HomeContent(vm)
        "pr_create" -> PrCreateContent(vm)
        "history" -> HistoryContent(vm)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeContent(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    val savedRepos by vm.savedRepos.collectAsState()
    val showSettings by vm.showSettings.collectAsState()
    val githubUsername by vm.githubUsername.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(i18n("我的项目", "My Projects", language), fontWeight = FontWeight.Bold) },
                actions = {
                    TextButton(onClick = { vm.toggleLanguage() }) { Text(if (language == Language.ZH) "EN" else "中", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) }
                    IconButton(onClick = { vm.showSettings.value = true }) {
                        if (githubUsername.isNotBlank()) AsyncImage(model = "https://github.com/${githubUsername}.png", contentDescription = "GitHub Avatar", contentScale = ContentScale.Crop, modifier = Modifier.size(32.dp).clip(CircleShape))
                        else Icon(AppIcons.AccountCircle, contentDescription = "Settings", modifier = Modifier.size(32.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.openBlankPr() },
                shape = RoundedCornerShape(16.dp), // 正方形圆角悬浮卡片
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(AppIcons.Add, contentDescription = "New PR")
            }
        }
    ) { paddingValues ->
        if (savedRepos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(AppIcons.Folder, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(i18n("暂无项目", "No projects yet", language), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(i18n("点击右下角按钮创建新的 PR", "Click FAB to create a new PR", language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                items(savedRepos, key = { it.id }) { repo ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                        onClick = { vm.openRepo(repo) }
                    ) {
                        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                                Text(repo.name.firstOrNull()?.uppercase() ?: "R", color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = repo.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text(text = repo.owner, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Icon(AppIcons.KeyboardArrowRight, contentDescription = "Enter", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
    
    if (showSettings) SettingsDialog(vm)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrCreateContent(vm: MainViewModel) {
    val context = LocalContext.current
    val language by vm.language.collectAsState()
    val targetUrl by vm.targetUrl.collectAsState()
    val baseBranch by vm.baseBranch.collectAsState()
    val newBranch by vm.newBranch.collectAsState()
    val targetPathPrefix by vm.targetPathPrefix.collectAsState()
    
    val prTitle by vm.prTitle.collectAsState()
    val prDesc by vm.prDescription.collectAsState()
    val allowMaintainerEdit by vm.allowMaintainerEdit.collectAsState()
    val isDraftPr by vm.isDraftPr.collectAsState()
    
    val flatFileNodes by vm.flatFileNodes.collectAsState()
    val isParsingFolder by vm.isParsingFolder.collectAsState()
    val isSyncingRepo by vm.isSyncingRepo.collectAsState()
    
    val showProgress by vm.showProgress.collectAsState()
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
                is MainViewModel.UiEvent.ShowToast -> Toast.makeText(context, if (language == Language.ZH) event.msgZh else event.msgEn, Toast.LENGTH_SHORT).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (targetUrl.isBlank()) i18n("创建 PR", "Create PR", language) else "Pull Request", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { vm.route.value = "home" }) { Icon(AppIcons.ArrowBack, contentDescription = "Back") } },
                actions = { 
                    if (targetUrl.isNotBlank()) {
                        IconButton(onClick = { vm.navigateToHistory() }) { Icon(AppIcons.History, contentDescription = "History") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AnimatedVisibility(visible = isSyncingRepo) {
                Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = MaterialTheme.colorScheme.onTertiaryContainer, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(i18n("正在自动同步最新上游代码...", "Syncing upstream...", language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = i18n("目标开源项目", "Target Repository", language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(value = targetUrl, onValueChange = { vm.targetUrl.value = it }, placeholder = { Text("https://github.com/owner/repo") }, label = { Text(i18n("HTTPS 克隆链接 *", "HTTPS Clone URL *", language)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(value = baseBranch, onValueChange = { vm.baseBranch.value = it }, label = { Text(i18n("基础分支", "Base Branch", language)) }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = newBranch, onValueChange = { vm.newBranch.value = it }, label = { Text(i18n("新分支 (选填)", "New Branch", language)) }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            }
            
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(text = i18n("Pull Request 详情", "Pull Request Details", language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
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
                            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp)) {
                                if (isPreviewMode) {
                                    Box(modifier = Modifier.padding(16.dp).fillMaxWidth()) { SimpleMarkdown(content = prDesc.ifBlank { i18n("暂无描述", "No description", language) }, fileNodes = flatFileNodes) }
                                } else {
                                    TextField(value = prDesc, onValueChange = { vm.prDescription.value = it }, placeholder = { Text(i18n("支持 Markdown，可粘贴图片链接", "Markdown supported", language)) }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent), modifier = Modifier.fillMaxSize())
                                }
                            }
                        }
                    }
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.allowMaintainerEdit.value = !allowMaintainerEdit }) {
                                Checkbox(checked = allowMaintainerEdit, onCheckedChange = { vm.allowMaintainerEdit.value = it })
                                Text(text = i18n("允许维护者编辑", "Allow maintainer edits", language), style = MaterialTheme.typography.bodyMedium)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { vm.isDraftPr.value = !isDraftPr }) {
                                Checkbox(checked = isDraftPr, onCheckedChange = { vm.isDraftPr.value = it })
                                Text(text = i18n("作为 Draft 提交", "Submit as Draft", language), style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                        IconButton(onClick = { vm.requestAttachmentPicker() }) { Icon(imageVector = AppIcons.AttachFile, contentDescription = "Attach", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = i18n("文件改动区", "File Changes", language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        if (flatFileNodes.isNotEmpty()) { TextButton(onClick = { vm.clearFiles() }, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) { Text(i18n("清空", "Clear", language)) } }
                    }

                    OutlinedTextField(
                        value = targetPathPrefix, onValueChange = { vm.targetPathPrefix.value = it }, 
                        placeholder = { Text("app/src/main/java/") }, label = { Text(i18n("仓库目标路径前缀 (选填)", "Target Path Prefix", language)) }, 
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        ElevatedFilterChip(selected = false, onClick = { vm.requestFilePicker() }, label = { Text(i18n("添加文件", "Add File", language)) }, leadingIcon = { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp)) })
                        ElevatedFilterChip(selected = false, onClick = { vm.requestFolderPicker() }, label = { Text(i18n("添加文件夹", "Add Folder", language)) }, leadingIcon = { Icon(AppIcons.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp)) })
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Checkbox(checked = useGitignore, onCheckedChange = { vm.useGitignore.value = it })
                        Text(text = i18n("应用 .gitignore", "Use .gitignore", language), style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { vm.useGitignore.value = !useGitignore })
                        Spacer(modifier = Modifier.weight(1f))
                        AnimatedVisibility(visible = useGitignore) { TextButton(onClick = { vm.showGitignoreDialog.value = true }, contentPadding = PaddingValues(0.dp)) { Text(i18n("编辑规则", "Edit Rules", language), fontSize = 13.sp) } }
                    }
                    
                    if (isParsingFolder) {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    } else if (flatFileNodes.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) { Text(text = i18n("暂未添加任何文件", "No files added", language), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxWidth().heightIn(max = 350.dp).background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp)).padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(flatFileNodes, key = { it.id }) { node ->
                                val isOverSize = !node.isDirectory && node.sizeBytes > 15 * 1024 * 1024L
                                Row(
                                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(if (isOverSize) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else if (node.isDirectory) MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f) else Color.Transparent).padding(start = (node.depth * 16).dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (node.isDirectory) {
                                        IconButton(onClick = { vm.toggleNodeExpand(node.id) }, modifier = Modifier.size(24.dp)) { Icon(if (node.isExpanded) AppIcons.KeyboardArrowDown else AppIcons.KeyboardArrowRight, contentDescription = "Expand", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                                        Icon(AppIcons.Folder, contentDescription = "Folder", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    } else {
                                        Spacer(modifier = Modifier.width(24.dp))
                                        Icon(AppIcons.InsertDriveFile, contentDescription = "File", tint = if (isOverSize) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                    }
                                    
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = node.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        if (isOverSize) Text(text = "过大 (>15MB)", color = MaterialTheme.colorScheme.error, fontSize = 10.sp)
                                    }
                                    Checkbox(checked = node.isChecked, onCheckedChange = { vm.toggleNodeCheck(node.id, it) }, modifier = Modifier.scale(0.8f))
                                    if (node.depth == 0) { IconButton(onClick = { vm.removeNode(node.id) }, modifier = Modifier.size(24.dp)) { Icon(AppIcons.Close, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp)) } }
                                }
                            }
                        }
                    }
                }
            }

            Button(onClick = { vm.submitPr() }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(12.dp)) { Text(text = i18n("一键提交 Pull Request", "Submit Pull Request", language), fontSize = 16.sp, fontWeight = FontWeight.Bold) }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showGitignoreDialog) GitignoreDialog(vm)
    if (showProgress) ProgressDialog(vm)
}

@Composable
fun SettingsDialog(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    var username by remember { mutableStateOf(vm.githubUsername.value) }
    var token by remember { mutableStateOf(vm.githubToken.value) }
    var sshKey by remember { mutableStateOf(vm.sshKey.value) }
    
    var selectedTab by remember { mutableIntStateOf(0) }

    Dialog(onDismissRequest = { vm.showSettings.value = false }) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = i18n("全局凭证配置", "Global Credentials", language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { vm.showSettings.value = false }) { Icon(AppIcons.Close, contentDescription = "Close") }
                }
                
                TabRow(selectedTabIndex = selectedTab) {
                    Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text(i18n("Token (推荐)", "Token (Recommended)", language)) })
                    Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text(i18n("SSH 密钥", "SSH Key", language)) })
                }
                
                if (selectedTab == 0) {
                    Text(text = i18n("由于应用采用独家「免克隆云端直接修改树」技术，该底层接口强依赖 GitHub Token，请确保提供具有完整权限的 Token。", "Cloud PR engine uses REST API which strictly requires Token.", language), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text(i18n("GitHub 用户名 (非必填)", "GitHub Username", language)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = token, onValueChange = { token = it }, label = { Text(i18n("个人访问令牌 (Token) *", "Personal Access Token *", language)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
                } else {
                    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(8.dp)) {
                        Text(text = i18n("⚠️ SSH 密钥需搭配本地完整 Git Clone 引擎。我们先在此保存您的凭证，为未来版本的全量级引擎做准备。当前依然使用 Token 进行云端速传。", "SSH requires full local Git clone engine. Saving for future releases.", language), modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                    }
                    OutlinedTextField(value = sshKey, onValueChange = { sshKey = it }, label = { Text("id_ed25519 / id_rsa Private Key") }, modifier = Modifier.fillMaxWidth().height(150.dp))
                }
                
                Button(onClick = { vm.saveSettings(username, token, sshKey) }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) { Text(i18n("保存凭证", "Save Credentials", language)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryContent(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    val historyList by vm.currentRepoHistory.collectAsState() 
    val context = LocalContext.current
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(i18n("当前仓库 PR 历史", "Repository PR History", language), fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = { vm.route.value = "pr_create" }) { Icon(AppIcons.ArrowBack, contentDescription = "Back") } },
                actions = { if (historyList.isNotEmpty()) IconButton(onClick = { vm.clearCurrentRepoHistory() }) { Icon(AppIcons.Delete, contentDescription = "Clear", tint = MaterialTheme.colorScheme.error) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { paddingValues ->
        if (historyList.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(AppIcons.History, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(i18n("此仓库暂无提交记录", "No history for this repository", language), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(historyList, key = { it.id }) { item ->
                    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), onClick = { 
                        if (item.url.isNotBlank()) {
                            try { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }) } catch (e: Exception) {}
                        }
                    }) {
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
fun GitignoreDialog(vm: MainViewModel) {
    val language by vm.language.collectAsState()
    var content by remember { mutableStateOf(vm.gitignoreContent.value) }

    Dialog(onDismissRequest = { vm.showGitignoreDialog.value = false }) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), modifier = Modifier.fillMaxHeight(0.8f)) {
            Column(Modifier.padding(20.dp).fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = i18n("编辑过滤规则", "Edit Gitignore Rules", language), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { vm.showGitignoreDialog.value = false }) { Icon(AppIcons.Close, contentDescription = "Close") }
                }
                
                TextField(
                    value = content, onValueChange = { content = it },
                    placeholder = { Text("build/\n.idea/\n*.apk\n...") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    colors = TextFieldDefaults.colors(focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f), focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent)
                )

                Button(onClick = { vm.gitignoreContent.value = content; vm.applyGitignoreRules() }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(12.dp)) { 
                    Text(i18n("保存并重新扫描", "Save and Rescan", language)) 
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
    val error by vm.prError.collectAsState()
    val scrollState = rememberScrollState()
    val isFinished = success || error 
    
    LaunchedEffect(logs.size) { scrollState.animateScrollTo(scrollState.maxValue) }

    Dialog(onDismissRequest = { if (isFinished) vm.showProgress.value = false }, properties = DialogProperties(dismissOnBackPress = isFinished, dismissOnClickOutside = isFinished)) {
        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(text = i18n("正在执行云端流转", "Executing cloud workflow", language), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (!isFinished) { LinearProgressIndicator(modifier = Modifier.fillMaxWidth()) }
                Box(modifier = Modifier.fillMaxWidth().height(220.dp).background(Color(0xFF1E1E1E), RoundedCornerShape(8.dp)).padding(12.dp).verticalScroll(scrollState)) {
                    SelectionContainer { Text(text = logs.joinToString("\n"), color = Color(0xFFA6ACCD), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp) }
                }
                if (success) {
                    Text(text = i18n("Pull Request 提交成功", "PR Successfully Created", language), color = Color(0xFF10B981), fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Button(onClick = { vm.showProgress.value = false; vm.route.value = "home" }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)), modifier = Modifier.fillMaxWidth()) { Text(i18n("完成返回主页", "Done", language)) }
                } else if (error) {
                    Text(text = i18n("流转失败，请检查上方日志", "Workflow Failed. Check logs.", language), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Button(onClick = { vm.showProgress.value = false }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) { Text(i18n("关闭", "Close", language)) }
                }
            }
        }
    }
}

private fun i18n(zh: String, en: String, language: Language): String { return if (language == Language.ZH) zh else en }