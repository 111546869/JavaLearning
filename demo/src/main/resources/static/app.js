const { createApp, ref, onMounted, computed } = Vue;

createApp({
  setup(){
    const sessions = ref([]); // {id,title,when,messages}
    const activeConvId = ref(null);
    const messages = ref([]); // [{role:'user'|'assistant', content:''}]
    const draft = ref('');
    const loading = ref(false);
    const newTitle = ref('');
    const messagesEl = ref(null);
    // ======== 新增：代码面板相关的状态 ========
    const showCodingPanel = ref(false);
    const problemText = ref('### 题目：两数之和\n给定一个整数数组 `nums` 和一个整数目标值 `target`，请你在该数组中找出和为目标值的那两个整数，并返回它们的数组下标。\n\n**示例：**\n输入：nums = [2,7,11,15], target = 9\n输出：[0,1]');
    const userCode = ref('function twoSum(nums, target) {\n    // 在此编写你的代码\n    \n}');
    const evaluating = ref(false);
    const evalResult = ref('');

    // ======== 模拟代码提交评测 ========
    async function submitCode() {
      evaluating.value = true;
      evalResult.value = '提交中...';
      try {
        const res = await fetch('/api/v1/evaluate', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ code: userCode.value })
        });
        const taskId = await res.text(); 
        
        // 建立 MQ / SSE 监听结果
        evalResult.value = '代码提交成功，后台评测执行中... (任务ID: ' + taskId + ')';
        const source = new EventSource(`/api/v1/evaluate/stream?taskId=${taskId}`);
        source.onmessage = function(event) {
          evalResult.value = event.data;
          source.close();
          evaluating.value = false;
        };
        source.onerror = function(e) {
          evalResult.value = '评测监听断开，请检查。';
          source.close();
          evaluating.value = false;
        };
      } catch(e) {
        evalResult.value = "运行报错：" + e.message;
        evaluating.value = false;
      }
    }
    function loadSessions(){
      const all = JSON.parse(localStorage.getItem('agent_sessions')||'[]');
      sessions.value = all;
      if (all.length > 0) {
        selectConversation(all[0].id);
      }
    }

    function persistSessions(){
      localStorage.setItem('agent_sessions', JSON.stringify(sessions.value));
    }

    function newConversation(){
      const id = 'conv_' + Date.now();
      const conv = { id, title: newTitle.value || '新会话', when: Date.now(), messages: [] };
      sessions.value.unshift(conv);
      persistSessions();
      newTitle.value='';
      selectConversation(id);
    }

    function selectConversation(id){
      activeConvId.value = id;
      const conv = sessions.value.find(s=>s.id===id);
      messages.value = conv?.messages || [];
      scrollToBottom();
    }

    function ensureCurrentSession(){
      let conv = sessions.value.find(s=>s.id===activeConvId.value);
      if (!conv){
        const id = 'conv_' + Date.now();
        conv = { id, title:'新会话', when: Date.now(), messages: [] };
        sessions.value.unshift(conv);
        activeConvId.value = id;
        messages.value = conv.messages;
      }
      return conv;
    }
    
    function scrollToBottom() {
        requestAnimationFrame(()=>{ 
            if (messagesEl.value) {
                messagesEl.value.scrollTop = messagesEl.value.scrollHeight;
            }
        });
    }

    function appendMessage(role, content){
      messages.value.push({ role, content });
      const conv = ensureCurrentSession();
      conv.messages = messages.value;
      conv.when = Date.now();
      persistSessions();
      scrollToBottom();
    }

    function appendToAssistant(text){
      let lastMsg = messages.value[messages.value.length - 1];
      if (!lastMsg || lastMsg.role !== 'assistant') {
        appendMessage('assistant', '');
        lastMsg = messages.value[messages.value.length - 1];
      }
      lastMsg.content += text;
      const conv = ensureCurrentSession();
      conv.messages = messages.value;
      persistSessions();
      scrollToBottom();
    }

    async function onSend(){
      if (!draft.value.trim()) return;
      const userMessage = draft.value;
      appendMessage('user', escapeHtml(userMessage));
      draft.value = '';

      appendMessage('assistant', ''); // Add assistant placeholder
      loading.value = true;

      try{
        const res = await fetch('/api/v1/agent/chat', {
          method:'POST', headers:{'Content-Type':'application/json'}, body: JSON.stringify({ message: userMessage })
        });
        if (!res.ok) throw new Error('服务响应错误，状态码: ' + res.status);

        const reader = res.body.getReader();
        const dec = new TextDecoder('utf-8');
        let buf = '';
        function processBlock(block) {
          // extract all data: lines (there may be multiple)
          const lines = block.split(/\r?\n/);
          for (const line of lines) {
            const trimmed = line.trim();
            if (trimmed.startsWith('data:')) {
              const dataContent = trimmed.slice(5).trim();
              if (!dataContent || dataContent === '[DONE]') continue;
              // try JSON, else treat as plain text fragment
              try {
                const parsed = JSON.parse(dataContent);
                if (parsed.answer) {
                  appendToAssistant(parsed.answer);
                }
              }
              catch (e) {
                // not JSON: append raw text fragment (handles Chinese fragments)
                appendToAssistant(dataContent);
              }
            } else {
              // sometimes server may send raw JSON line without "data:" prefix
              if (trimmed.startsWith('{') || trimmed.startsWith('[')) {
                try {
                  const obj = JSON.parse(trimmed);
                  if (obj.answer) appendToAssistant(obj.answer);
                } catch (e) {
                  appendToAssistant(trimmed);
                }
              }
            }
          }
        }
        while (true) {
          const { value, done } = await reader.read();
          if (done) break;
          buf += dec.decode(value, { stream: true });

          // process all complete event blocks separated by blank line
          while (true) {
            const idxR = buf.indexOf('\r\n\r\n');
            const idxN = buf.indexOf('\n\n');
            let cut = -1;
            let delimLen = 0;
            if (idxR !== -1 && (idxN === -1 || idxR < idxN)) { cut = idxR; delimLen = 4; }
            else if (idxN !== -1) { cut = idxN; delimLen = 2; }
            if (cut === -1) break;

            const block = buf.slice(0, cut);
            buf = buf.slice(cut + delimLen);
            if (block.trim()) processBlock(block);
          }
        }

        // flush any remaining buffered fragment
        if (buf.trim()) {
          // remaining may contain multiple lines
          const parts = buf.split(/\r?\n\r?\n/);
          for (const p of parts) {
            if (p.trim()) processBlock(p);
          }
        }
      } catch (e) {
        appendToAssistant('\n[错误] ' + e.message);
      } finally {
        loading.value = false;
      }
    }

    function onKeydown(e){ if (e.ctrlKey && e.key==='Enter') onSend(); }

    function clearCurrent(){ 
        const conv = ensureCurrentSession(); 
        conv.messages = []; 
        messages.value = [];
        persistSessions(); 
    }

    function formatMessage(text){ 
        if (!text) return '';
        // 使用 marked 解析 Markdown 内容
        return marked.parse(text); 
    }
    function escapeHtml(s){ return s.replace(/[&<>]/g, c=>({"&":"&amp;","<":"&lt;",">":"&gt;"})[c]); }

    onMounted(()=>{ loadSessions(); });

    const currentSessionTitle = computed(()=>{
      const conv = sessions.value.find(s=>s.id===activeConvId.value);
      return conv?.title || '新会话';
    });

    return { sessions, activeConvId, messages, draft, loading, newTitle, newConversation, selectConversation, onSend, onKeydown, messagesEl, clearCurrent, formatMessage, currentSessionTitle,
      showCodingPanel, problemText, userCode, evaluating, evalResult, submitCode
     };
  }
}).use(ElementPlus).mount('#app');