const AppState = {
  account: { owner: '', token: '' },
  files:[],
  history:[]
};

const els = {
  avatar: document.getElementById('user-avatar'),
  settingsModal: document.getElementById('settings-modal'),
  historyModal: document.getElementById('history-modal'),
  confOwner: document.getElementById('conf-owner'),
  confToken: document.getElementById('conf-token'),
  fileList: document.getElementById('file-list'),
  fileInput: document.getElementById('fileInput'),
  folderInput: document.getElementById('folderInput'),
  progressModal: document.getElementById('progress-modal'),
  progressFill: document.getElementById('progress-fill'),
  logBox: document.getElementById('log-box'),
  prSuccessArea: document.getElementById('pr-success-area'),
  prLinkBtn: document.getElementById('btn-go-pr'),
  
  // Markdown 新增元素
  tabWrite: document.getElementById('tab-write'),
  tabPreview: document.getElementById('tab-preview'),
  prDesc: document.getElementById('pr-desc'),
  prPreview: document.getElementById('pr-preview'),
  
  // 历史记录新增
  historyList: document.getElementById('history-list')
};

function appToast(msg) {
  let t = document.getElementById('app-toast');
  if (!t) { t = document.createElement('div'); t.id = 'app-toast'; t.className = 'app-toast'; document.body.appendChild(t); }
  t.textContent = msg; t.style.opacity = '1';
  setTimeout(() => t.style.opacity = '0', 2500);
}

document.addEventListener('DOMContentLoaded', () => {
  const saved = localStorage.getItem('gh_tool_account');
  if (saved) { AppState.account = JSON.parse(saved); updateAvatarUI(); } 
  else { els.settingsModal.classList.remove('hidden'); }
  
  const savedHistory = localStorage.getItem('gh_tool_history');
  if (savedHistory) AppState.history = JSON.parse(savedHistory);

  bindUIEvents();
  bindMarkdownEvents();
});

function updateAvatarUI() {
  if (AppState.account.owner) {
    els.avatar.src = `https://github.com/${AppState.account.owner}.png`;
    els.confOwner.value = AppState.account.owner;
  }
  if (AppState.account.token) els.confToken.value = AppState.account.token;
}

// -----------------------------------
// 核心：UI 事件与历史记录绑定
// -----------------------------------
function bindUIEvents() {
  document.getElementById('btn-open-settings').addEventListener('click', () => els.settingsModal.classList.remove('hidden'));
  document.getElementById('btn-close-settings').addEventListener('click', () => {
    if (!AppState.account.token) return appToast('请先配置并保存凭证');
    els.settingsModal.classList.add('hidden');
  });
  
  document.getElementById('btn-save-settings').addEventListener('click', () => {
    const owner = els.confOwner.value.trim();
    const token = els.confToken.value.trim();
    if (!owner || !token) return appToast('用户名和 Token 不能为空');
    AppState.account = { owner, token };
    localStorage.setItem('gh_tool_account', JSON.stringify(AppState.account));
    updateAvatarUI();
    els.settingsModal.classList.add('hidden');
    appToast('凭证已安全保存');
  });

  // 历史记录面板操作
  document.getElementById('btn-open-history').addEventListener('click', () => {
    els.historyModal.classList.remove('hidden');
    renderHistory();
  });
  document.getElementById('btn-close-history').addEventListener('click', () => els.historyModal.classList.add('hidden'));
  document.getElementById('btn-clear-history').addEventListener('click', () => {
    if (confirm("确定要清空本地的推送历史记录吗？")) {
      AppState.history =[];
      localStorage.removeItem('gh_tool_history');
      renderHistory();
      appToast('历史记录已清除');
    }
  });

  // 文件操作
  document.getElementById('btn-add-file').addEventListener('click', () => els.fileInput.click());
  document.getElementById('btn-add-folder').addEventListener('click', () => els.folderInput.click());
  document.getElementById('btn-clear').addEventListener('click', clearFiles);
  els.fileInput.addEventListener('change', handleFileSelect);
  els.folderInput.addEventListener('change', handleFileSelect);

  document.getElementById('btn-submit').addEventListener('click', onMainSubmit);
  document.getElementById('btn-close-progress').addEventListener('click', () => els.progressModal.classList.add('hidden'));
}

// -----------------------------------
// 核心：Markdown 编辑器与图片拖拽/粘贴拦截
// -----------------------------------
function bindMarkdownEvents() {
  // Write 与 Preview 选项卡切换
  els.tabWrite.addEventListener('click', () => {
    els.tabWrite.classList.add('active'); els.tabPreview.classList.remove('active');
    els.prDesc.classList.remove('hidden'); els.prPreview.classList.add('hidden');
  });

  els.tabPreview.addEventListener('click', async () => {
    els.tabPreview.classList.add('active'); els.tabWrite.classList.remove('active');
    els.prDesc.classList.add('hidden'); els.prPreview.classList.remove('hidden');
    
    // 渲染 Markdown 前，将占位符替换为本地 Blob URL 以供预览
    let rawText = els.prDesc.value;
    AppState.files.forEach(f => {
      if (f.blobUrl && rawText.includes(f.targetPath)) {
        rawText = rawText.replace(new RegExp(f.targetPath, 'g'), f.blobUrl);
      }
    });
    
    els.prPreview.innerHTML = '<p style="color:gray;">正在通过 GitHub API 渲染...</p>';
    if (AppState.account.token) {
      els.prPreview.innerHTML = await GitHubAPI.renderMarkdown(rawText, AppState.account.token);
    } else {
      els.prPreview.innerHTML = '<p style="color:red;">请先在设置中配置 Token 才能使用渲染服务。</p>';
    }
  });

  // 拦截粘贴事件：处理图片直接粘贴
  els.prDesc.addEventListener('paste', (e) => {
    const items = (e.clipboardData || e.originalEvent.clipboardData).items;
    for (let index in items) {
      const item = items[index];
      if (item.kind === 'file' && item.type.includes('image/')) {
        const file = item.getAsFile();
        handleImageInject(file);
      }
    }
  });

  // 拦截拖拽事件
  els.prDesc.addEventListener('dragover', e => e.preventDefault());
  els.prDesc.addEventListener('drop', (e) => {
    e.preventDefault();
    if (e.dataTransfer.files.length > 0) {
      const file = e.dataTransfer.files[0];
      if (file.type.includes('image/')) handleImageInject(file);
    }
  });
}

// 将用户拖拽的图片直接挂载到提交队列中，并在编辑器插入 Markdown 占位符
function handleImageInject(file) {
  const filename = `img-${Date.now()}.png`;
  const targetPath = `pr-assets/${filename}`; // 放在仓库的一个隐藏资料夹里
  const blobUrl = URL.createObjectURL(file);
  
  // 注入文件队列
  AppState.files.push({ file, targetPath, blobUrl, id: Date.now() });
  renderFileList();
  
  // 在光标处插入 Markdown
  const cursor = els.prDesc.selectionStart;
  const text = els.prDesc.value;
  const mdImg = `\n![图片](${targetPath})\n`;
  els.prDesc.value = text.slice(0, cursor) + mdImg + text.slice(cursor);
  
  appToast('图片已成功注入本次提交队列');
}

// -----------------------------------
// 文件列表管理
// -----------------------------------
function handleFileSelect(e) {
  const files = e.target.files;
  if (files.length === 0) return;
  for (let file of files) {
    let targetPath = (file.webkitRelativePath || file.name).replace(/^\/+/, '');
    if (AppState.files.some(f => f.targetPath === targetPath)) continue;
    AppState.files.push({ file, targetPath, id: Date.now() + Math.random() });
  }
  e.target.value = '';
  renderFileList();
}

function renderFileList() {
  if (AppState.files.length === 0) { els.fileList.innerHTML = `<li class="empty-state">暂未添加任何文件</li>`; return; }
  els.fileList.innerHTML = '';
  AppState.files.forEach((f, idx) => {
    const li = document.createElement('li');
    // 如果是图片会有(资产)标记
    const isAsset = f.targetPath.startsWith('pr-assets/');
    li.innerHTML = `
      <div class="file-info">
        <span class="file-name">${f.file.name} ${isAsset ? '<span style="color:#10b981; font-size:10px;">(正文配图)</span>' : ''}</span>
        <button class="btn-text danger" onclick="removeFile(${idx})">移除</button>
      </div>
      <input type="text" class="file-path-input" value="${f.targetPath}" ${isAsset ? 'readonly' : ''} onchange="updatePath(${idx}, this.value)" placeholder="仓库内最终路径">
    `;
    els.fileList.appendChild(li);
  });
}

window.updatePath = (idx, val) => { AppState.files[idx].targetPath = val; };
window.removeFile = (idx) => { AppState.files.splice(idx, 1); renderFileList(); };
function clearFiles() { AppState.files =[]; renderFileList(); }

// -----------------------------------
// 历史记录渲染与状态刷新
// -----------------------------------
async function renderHistory() {
  els.historyList.innerHTML = '<div style="text-align:center; padding: 20px; color:gray;">加载中...</div>';
  if (AppState.history.length === 0) {
    els.historyList.innerHTML = '<div class="empty-state" style="text-align:center; padding: 30px; color:gray;">暂无 PR 历史记录</div>';
    return;
  }

  els.historyList.innerHTML = '';
  // 倒序展示，最新的在上面
  for (const record of [...AppState.history].reverse()) {
    const card = document.createElement('div');
    card.className = 'history-card';
    card.innerHTML = `
      <div class="history-header">
        <div class="history-title">${record.title}</div>
        <div class="status-badge status-loading" id="badge-${record.id}">检测中</div>
      </div>
      <div class="history-repo">📦 ${record.repo}</div>
      <div style="display: flex; justify-content: space-between; align-items: center; margin-top: 4px;">
        <span class="history-date">${record.date}</span>
        <a href="${record.webUrl}" target="_system" style="font-size: 13px; color: #3730a3; text-decoration: none; font-weight: bold;">查看网页 &rarr;</a>
      </div>
    `;
    els.historyList.appendChild(card);

    // 异步获取最新状态
    GitHubAPI.getPRStatus(record.apiUrl, AppState.account.token).then(status => {
      const badge = document.getElementById(`badge-${record.id}`);
      if(badge) {
        badge.className = `status-badge status-${status.toLowerCase()}`;
        badge.textContent = status === 'MERGED' ? '已合并' : status === 'CLOSED' ? '已关闭' : '开启中';
      }
    });
  }
}

// -----------------------------------
// 发起主提交流程
// -----------------------------------
function onMainSubmit() {
  const repoUrl = document.getElementById('target-url').value.trim();
  const baseBranch = document.getElementById('base-branch').value.trim();
  let newBranch = document.getElementById('new-branch').value.trim();
  const title = document.getElementById('pr-title').value.trim();
  const desc = document.getElementById('pr-desc').value.trim();
  const maintainerCanModify = document.getElementById('maintainer-edit').checked;
  
  const { token, owner } = AppState.account;

  if (!token || !owner) return els.settingsModal.classList.remove('hidden');
  if (!repoUrl) return appToast('请输入目标开源项目的 HTTPS 链接');
  if (!baseBranch) return appToast('请填写原仓库的分支');
  if (!title) return appToast('标题不可为空');
  if (AppState.files.length === 0) return appToast('请至少添加一个改动文件');

  const parsedRepo = GitHubAPI.parseUrl(repoUrl);
  if (!parsedRepo) return appToast('HTTPS 链接格式错误！(必须包含 github.com)');
  if (!newBranch) { newBranch = `pr-update-${Date.now()}`; document.getElementById('new-branch').value = newBranch; }

  els.progressModal.classList.remove('hidden');
  els.prSuccessArea.classList.add('hidden');
  els.logBox.innerHTML = '';
  els.progressFill.style.width = '0%';
  document.getElementById('progress-title').textContent = '正在执行云端流转...';

  const flowParams = {
    token, myAccount: owner,
    origOwner: parsedRepo.owner, origRepo: parsedRepo.repo,
    baseBranch, newBranch,
    title, description: desc, maintainerCanModify,
    files: AppState.files
  };

  GitHubAPI.executeFlow(flowParams, {
    onProgress: (percent) => els.progressFill.style.width = `${percent}%`,
    onLog: (text) => { els.logBox.textContent += text + '\n'; els.logBox.scrollTop = els.logBox.scrollHeight; },
    onSuccess: (result) => {
      els.progressFill.style.width = '100%';
      document.getElementById('progress-title').textContent = '🎉 全自动 PR 成功！';
      els.prSuccessArea.classList.remove('hidden');
      
      els.prLinkBtn.onclick = () => { window.open(result.webUrl, '_system') || window.open(result.webUrl, '_blank'); };
      appToast('处理完成！PR 已发送给原作者');
      
      // 保存至历史记录
      AppState.history.push({
        id: Date.now(),
        title: title,
        repo: `${parsedRepo.owner}/${parsedRepo.repo}`,
        webUrl: result.webUrl,
        apiUrl: result.apiUrl,
        date: new Date().toLocaleString()
      });
      localStorage.setItem('gh_tool_history', JSON.stringify(AppState.history));
    },
    onError: (errMsg) => {
      document.getElementById('progress-title').textContent = '投递失败';
      els.progressFill.style.background = '#ff3b30';
      appToast('执行失败，请查看日志');
    }
  });
}