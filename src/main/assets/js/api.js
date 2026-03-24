const GitHubAPI = {
  parseUrl(url) {
    const match = url.match(/github\.com\/([^\/]+)\/([^\/\.]+)(\.git)?/i);
    return match ? { owner: match[1], repo: match[2].replace(/\.git$/, '') } : null;
  },

  fileToBase64(file) {
    return new Promise((resolve, reject) => {
      const reader = new FileReader();
      reader.onload = e => resolve(e.target.result.split(',')[1]);
      reader.onerror = e => reject(e);
      reader.readAsDataURL(file);
    });
  },

  // 调用 GitHub 官方 API 渲染 Markdown 预览
  async renderMarkdown(text, token) {
    if (!text.trim()) return '<p style="color:gray;">没有任何内容...</p>';
    try {
      const res = await fetch('https://api.github.com/markdown', {
        method: 'POST',
        headers: { 'Authorization': `token ${token}`, 'Content-Type': 'application/json' },
        body: JSON.stringify({ text, mode: 'gfm' })
      });
      return await res.text();
    } catch (err) {
      return `<p style="color:red;">渲染失败，请检查网络</p>`;
    }
  },

  // 获取某个历史 PR 的当前状态 (Open/Merged/Closed)
  async getPRStatus(apiUrl, token) {
    try {
      const res = await fetch(apiUrl, {
        headers: { 'Authorization': `token ${token}`, 'Accept': 'application/vnd.github.v3+json' }
      });
      if (!res.ok) return 'UNKNOWN';
      const data = await res.json();
      if (data.merged) return 'MERGED';
      if (data.state === 'closed') return 'CLOSED';
      return 'OPEN';
    } catch (e) {
      return 'UNKNOWN';
    }
  },

  async executeFlow(params, callbacks) {
    const { token, myAccount, origOwner, origRepo, baseBranch, newBranch, title, description, maintainerCanModify, files } = params;
    const { onProgress, onLog, onSuccess, onError } = callbacks;
    const headers = { 'Authorization': `token ${token}`, 'Content-Type': 'application/json' };

    try {
      onLog(`[1/7] 请求克隆 ${origOwner}/${origRepo}...`);
      let res = await fetch(`https://api.github.com/repos/${origOwner}/${origRepo}/forks`, { method: 'POST', headers });
      if (!res.ok) throw new Error('Fork 被拒绝，检查网络或 Token 权限。');
      const myRepo = (await res.json()).name;
      onProgress(10);

      onLog(`[2/7] 同步您的副本数据...`);
      let baseSha = null;
      for (let i = 0; i < 6; i++) {
        let refRes = await fetch(`https://api.github.com/repos/${myAccount}/${myRepo}/git/refs/heads/${baseBranch}`, { headers });
        if (refRes.ok) { baseSha = (await refRes.json()).object.sha; break; }
        await new Promise(r => setTimeout(r, 2000));
      }
      if (!baseSha) throw new Error(`同步超时：找不到分支 ${baseBranch}。`);
      onProgress(25);

      const treeList =[];
      for (let i = 0; i < files.length; i++) {
        const f = files[i];
        onLog(`[3/7] 上传数据块: ${f.targetPath}`);
        const b64 = await this.fileToBase64(f.file);
        res = await fetch(`https://api.github.com/repos/${myAccount}/${myRepo}/git/blobs`, {
          method: 'POST', headers, body: JSON.stringify({ content: b64, encoding: 'base64' })
        });
        if (!res.ok) throw new Error(`上传失败: ${f.targetPath}`);
        treeList.push({ path: f.targetPath, mode: '100644', type: 'blob', sha: (await res.json()).sha });
        onProgress(25 + Math.floor(((i + 1) / files.length) * 35)); 
      }

      onLog('[4/7] 映射云端文件树结构...');
      res = await fetch(`https://api.github.com/repos/${myAccount}/${myRepo}/git/trees`, {
        method: 'POST', headers, body: JSON.stringify({ base_tree: baseSha, tree: treeList })
      });
      const treeSha = (await res.json()).sha;
      onProgress(65);

      onLog('[5/7] 生成 Commit...');
      res = await fetch(`https://api.github.com/repos/${myAccount}/${myRepo}/git/commits`, {
        method: 'POST', headers, body: JSON.stringify({ message: title, tree: treeSha, parents:[baseSha] })
      });
      const commitSha = (await res.json()).sha;
      onProgress(75);

      onLog(`[6/7] 更新独立分支 \`${newBranch}\`...`);
      res = await fetch(`https://api.github.com/repos/${myAccount}/${myRepo}/git/refs`, {
        method: 'POST', headers, body: JSON.stringify({ ref: `refs/heads/${newBranch}`, sha: commitSha })
      });
      if (!res.ok) {
        res = await fetch(`https://api.github.com/repos/${myAccount}/${myRepo}/git/refs/heads/${newBranch}`, {
          method: 'PATCH', headers, body: JSON.stringify({ sha: commitSha, force: true })
        });
      }
      if (!res.ok) throw new Error('分支操作失败。');
      onProgress(85);

      onLog(`[7/7] 正在向原作者发起 Pull Request...`);
      
      // 魔法：将 PR 描述里的本地预览标记 (如 pr-assets/img-xxx.png) 替换为云端 Raw 绝对地址
      const rawBaseUrl = `https://raw.githubusercontent.com/${myAccount}/${myRepo}/${newBranch}/`;
      const finalBody = description.replace(/\]\((pr-assets\/[^)]+)\)/g, `](${rawBaseUrl}$1)`);

      res = await fetch(`https://api.github.com/repos/${origOwner}/${origRepo}/pulls`, {
        method: 'POST', headers, 
        body: JSON.stringify({
          title: title, 
          head: `${myAccount}:${newBranch}`, 
          base: baseBranch, 
          body: finalBody,
          maintainer_can_modify: maintainerCanModify // 支持维护者编辑选项
        })
      });
      
      if (res.ok) {
        const prData = await res.json();
        onLog(`✅ PR 创建成功！`);
        onProgress(100);
        // 返回包含 html_url (网页链接) 和 url (API链接) 的对象，方便历史记录读取
        onSuccess({ webUrl: prData.html_url, apiUrl: prData.url });
      } else {
        const errData = await res.json();
        if (errData.errors && errData.errors[0] && errData.errors[0].message.includes("No commits between")) {
          throw new Error("没有修改任何代码，原仓库已包含此内容。");
        } else if (errData.errors && errData.errors[0] && errData.errors[0].message.includes("A pull request already exists")) {
          throw new Error("您之前已经发过同分支的 PR，本次内容已追加提交！");
        } else {
          throw new Error(`PR 失败: ${errData.message}`);
        }
      }
    } catch (err) {
      onLog(`\n❌ 发生异常: ${err.message}`);
      onError(err.message);
    }
  }
};