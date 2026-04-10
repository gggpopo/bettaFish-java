/**
 * BettaFish Dashboard — vanilla JS SPA
 * Connects to /api/analysis REST + SSE endpoints
 */
const App = (() => {
    const API = '/api/analysis';
    let currentTaskId = null;
    let eventSource = null;
    let pollTimer = null;

    // ─── Task lifecycle ───

    async function createTask() {
        const input = document.getElementById('queryInput');
        const query = input.value.trim();
        if (!query) { input.focus(); return; }

        const btn = document.getElementById('createBtn');
        btn.disabled = true;
        btn.textContent = '提交中...';

        try {
            const resp = await fetch(API, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ query })
            });
            const task = await resp.json();
            input.value = '';
            await refreshTaskList();
            selectTask(task.taskId);
        } catch (e) {
            alert('创建失败: ' + e.message);
        } finally {
            btn.disabled = false;
            btn.textContent = '开始分析';
        }
    }

    async function cancelTask() {
        if (!currentTaskId) return;
        try {
            await fetch(`${API}/${currentTaskId}/cancel`, { method: 'POST' });
            await refreshCurrentTask();
        } catch (e) {
            console.error('Cancel failed', e);
        }
    }

    async function refreshTaskList() {
        try {
            const resp = await fetch(API);
            const tasks = await resp.json();
            tasks.sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));
            renderTaskList(tasks);
        } catch (e) {
            console.error('Failed to load tasks', e);
        }
    }

    async function selectTask(taskId) {
        currentTaskId = taskId;
        closeEventStream();

        document.getElementById('createPanel').style.display = 'none';
        document.getElementById('detailPanel').style.display = '';

        // highlight in sidebar
        document.querySelectorAll('.task-item').forEach(el => {
            el.classList.toggle('active', el.dataset.taskId === taskId);
        });

        await refreshCurrentTask();
    }

    async function refreshCurrentTask() {
        if (!currentTaskId) return;
        try {
            const resp = await fetch(`${API}/${currentTaskId}`);
            const task = await resp.json();
            renderDetail(task);

            if (task.status === 'RUNNING') {
                openEventStream(currentTaskId);
                startPoll(currentTaskId);
            } else {
                closeEventStream();
                stopPoll();
            }
        } catch (e) {
            console.error('Failed to load task', e);
        }
    }

    function showCreate() {
        currentTaskId = null;
        closeEventStream();
        stopPoll();
        document.getElementById('createPanel').style.display = '';
        document.getElementById('detailPanel').style.display = 'none';
        document.querySelectorAll('.task-item').forEach(el => el.classList.remove('active'));
        document.getElementById('queryInput').focus();
    }

    // ─── SSE ───

    function openEventStream(taskId) {
        closeEventStream();
        eventSource = new EventSource(`${API}/${taskId}/events`);

        eventSource.addEventListener('EngineStarted', e => {
            appendEventLog('EngineStarted', parseJson(e.data), 'info');
        });
        eventSource.addEventListener('AgentSpeech', e => {
            appendEventLog('AgentSpeech', parseJson(e.data), 'info');
        });
        eventSource.addEventListener('DeltaChunk', e => {
            appendEventLog('DeltaChunk', parseJson(e.data), 'info');
        });
        eventSource.addEventListener('HostComment', e => {
            appendEventLog('HostComment', parseJson(e.data), 'info');
        });
        eventSource.addEventListener('ToolCalled', e => {
            appendEventLog('ToolCalled', parseJson(e.data), 'info');
        });
        eventSource.addEventListener('NodeStarted', e => {
            appendEventLog('NodeStarted', parseJson(e.data), 'info');
        });
        eventSource.addEventListener('AnalysisComplete', e => {
            appendEventLog('AnalysisComplete', parseJson(e.data), 'success');
            refreshCurrentTask();
            refreshTaskList();
        });
        eventSource.addEventListener('AnalysisFailed', e => {
            appendEventLog('AnalysisFailed', parseJson(e.data), 'error');
            refreshCurrentTask();
            refreshTaskList();
        });
        eventSource.addEventListener('AnalysisTimedOut', e => {
            appendEventLog('AnalysisTimedOut', parseJson(e.data), 'error');
            refreshCurrentTask();
            refreshTaskList();
        });
        eventSource.addEventListener('AnalysisCancelled', e => {
            appendEventLog('AnalysisCancelled', parseJson(e.data), 'info');
            refreshCurrentTask();
            refreshTaskList();
        });

        eventSource.onerror = () => {
            appendEventLog('CONNECTION', { message: 'SSE disconnected' }, 'error');
        };

        appendEventLog('CONNECTION', { message: 'SSE connected to ' + taskId.substring(0, 8) }, 'info');
    }

    function closeEventStream() {
        if (eventSource) {
            eventSource.close();
            eventSource = null;
        }
    }

    // ─── Polling fallback ───

    function startPoll(taskId) {
        stopPoll();
        pollTimer = setInterval(async () => {
            if (currentTaskId !== taskId) { stopPoll(); return; }
            try {
                const resp = await fetch(`${API}/${taskId}`);
                const task = await resp.json();
                renderDetail(task);
                if (task.status !== 'RUNNING') {
                    stopPoll();
                    closeEventStream();
                    refreshTaskList();
                }
            } catch (e) { /* ignore transient failures */ }
        }, 5000);
    }

    function stopPoll() {
        if (pollTimer) { clearInterval(pollTimer); pollTimer = null; }
    }

    // ─── Render: task list ───

    function renderTaskList(tasks) {
        const container = document.getElementById('taskList');
        if (tasks.length === 0) {
            container.innerHTML = '<div class="detail-empty" style="padding:40px 20px"><p>暂无任务</p></div>';
            return;
        }
        container.innerHTML = tasks.map(t => `
            <div class="task-item ${t.taskId === currentTaskId ? 'active' : ''}"
                 data-task-id="${t.taskId}"
                 onclick="App.selectTask('${t.taskId}')">
                <div class="task-query">${esc(t.query)}</div>
                <div class="task-meta">
                    ${statusBadge(t.status)}
                    <span>${formatTime(t.createdAt)}</span>
                </div>
            </div>
        `).join('');
    }

    // ─── Render: detail ───

    function renderDetail(task) {
        document.getElementById('detailQuery').textContent = task.query;
        document.getElementById('detailTaskId').textContent = task.taskId.substring(0, 8) + '...';
        document.getElementById('detailTime').textContent = formatDateTime(task.createdAt);
        document.getElementById('detailStatus').outerHTML =
            `<span id="detailStatus">${statusBadge(task.status)}</span>`;

        // Cancel button
        document.getElementById('cancelBtn').style.display =
            task.status === 'RUNNING' ? '' : 'none';

        renderProgress(task);
        renderEngines(task.engineResults || []);
        renderForum(task.forumSummary);
        renderReport(task.report);
    }

    function renderProgress(task) {
        const engines = task.engineResults || [];
        const totalSteps = 5; // engines(3) + forum + report
        let doneSteps = engines.length;
        if (task.forumSummary) doneSteps++;
        if (task.report) doneSteps++;
        if (task.status !== 'RUNNING') doneSteps = totalSteps;

        const pct = Math.round((doneSteps / totalSteps) * 100);
        const fill = document.getElementById('progressFill');
        fill.style.width = pct + '%';
        fill.className = 'progress-fill' + (task.status === 'COMPLETED' ? ' done' : '');

        const steps = [
            { label: 'QUERY', done: engines.some(e => e.engineType === 'QUERY') },
            { label: 'MEDIA', done: engines.some(e => e.engineType === 'MEDIA') },
            { label: 'INSIGHT', done: engines.some(e => e.engineType === 'INSIGHT') },
            { label: '论坛辩论', done: !!task.forumSummary },
            { label: '报告生成', done: !!task.report }
        ];

        document.getElementById('progressSteps').innerHTML = steps.map(s => {
            let cls = 'step';
            if (s.done) cls += ' done';
            else if (task.status === 'RUNNING') cls += ' active';
            else if (task.status === 'FAILED' || task.status === 'TIMED_OUT') cls += ' error';
            return `<span class="${cls}">${s.done ? '\u2713 ' : ''}${s.label}</span>`;
        }).join('');

        // Show/hide error
        if (task.errorMessage) {
            document.getElementById('progressPanel').insertAdjacentHTML('beforeend',
                `<div style="margin-top:8px;font-size:13px;color:var(--danger);word-break:break-all" id="errorMsg">
                    ${esc(task.errorMessage)}
                </div>`);
        }
        const oldErr = document.getElementById('errorMsg');
        if (oldErr && !task.errorMessage) oldErr.remove();
    }

    function renderEngines(results) {
        const grid = document.getElementById('engineGrid');
        if (results.length === 0) {
            grid.innerHTML = '<div class="card" style="text-align:center;color:var(--text-secondary)">等待引擎返回结果...</div>';
            return;
        }
        grid.innerHTML = results.map(r => {
            const degraded = r.metadata && r.metadata.degraded === 'true';
            const cls = degraded ? 'engine-card timed-out' : 'engine-card';
            return `
                <div class="${cls}">
                    <h4>
                        ${engineIcon(r.engineType)} ${r.engineType}
                        ${degraded ? '<span class="badge badge-timed-out">降级</span>' : ''}
                    </h4>
                    <div class="headline">${esc(r.headline)}</div>
                    <div class="summary">${esc(r.summary)}</div>
                    ${r.keyPoints && r.keyPoints.length ? `
                        <ul class="key-points">
                            ${r.keyPoints.map(p => `<li>${esc(p)}</li>`).join('')}
                        </ul>
                    ` : ''}
                    ${r.sources && r.sources.length ? `
                        <div style="margin-top:10px;font-size:12px;color:var(--text-secondary)">
                            ${r.sources.length} 个来源
                        </div>
                    ` : ''}
                </div>
            `;
        }).join('');
    }

    function renderForum(forum) {
        if (!forum) return;

        document.getElementById('forumOverview').textContent = forum.overview || '无概要';

        if (forum.consensusPoints && forum.consensusPoints.length) {
            document.getElementById('consensusCard').style.display = '';
            document.getElementById('consensusList').innerHTML =
                forum.consensusPoints.map(p => `<li>${esc(p)}</li>`).join('');
        }

        if (forum.openQuestions && forum.openQuestions.length) {
            document.getElementById('questionsCard').style.display = '';
            document.getElementById('questionsList').innerHTML =
                forum.openQuestions.map(q => `<li>${esc(q)}</li>`).join('');
        }

        const timeline = document.getElementById('forumTimeline');
        if (forum.transcript && forum.transcript.length) {
            timeline.innerHTML = forum.transcript.map(msg => {
                const isHost = msg.role === 'host';
                return `
                    <div class="forum-msg ${isHost ? 'host' : 'agent'}">
                        <div class="msg-header">
                            <span class="msg-role">${isHost ? 'HOST' : msg.agentName || 'AGENT'}</span>
                            <span>${msg.agentName || ''}</span>
                            ${msg.timestamp ? `<span>${formatTime(msg.timestamp)}</span>` : ''}
                        </div>
                        <div class="msg-body">${esc(msg.content)}</div>
                    </div>
                `;
            }).join('');
        }
    }

    function renderReport(report) {
        if (!report) return;

        document.getElementById('reportTitle').textContent = report.title || '分析报告';
        document.getElementById('reportSummary').textContent = report.summary || '';

        const frame = document.getElementById('reportFrame');
        if (report.html) {
            const iframe = document.createElement('iframe');
            iframe.srcdoc = report.html;
            iframe.style.width = '100%';
            iframe.style.minHeight = '600px';
            iframe.style.border = 'none';
            iframe.onload = () => {
                // Auto-resize iframe to content
                try {
                    iframe.style.height = iframe.contentDocument.body.scrollHeight + 40 + 'px';
                } catch (e) { /* cross-origin fallback */ }
            };
            frame.innerHTML = '';
            frame.appendChild(iframe);
        }
    }

    // ─── Tabs ───

    function switchTab(name) {
        document.querySelectorAll('.tab').forEach(el => {
            el.classList.toggle('active', el.dataset.tab === name);
        });
        document.querySelectorAll('.tab-content').forEach(el => {
            el.style.display = 'none';
        });
        const target = document.getElementById('tab-' + name);
        if (target) target.style.display = '';
    }

    // ─── Event log ───

    function appendEventLog(type, data, level) {
        const log = document.getElementById('eventLog');
        const now = new Date().toLocaleTimeString('zh-CN', { hour12: false });
        const cls = level === 'error' ? 'event-error' : 'event-data';

        let detail = '';
        if (data) {
            if (typeof data === 'string') {
                detail = data;
            } else if (data.message) {
                detail = data.message;
            } else if (data.engineName) {
                detail = data.engineName + (data.content ? ': ' + truncate(data.content, 120) : '');
            } else if (data.agentName) {
                detail = data.agentName + ': ' + truncate(data.content || data.speech || '', 120);
            } else if (data.chunkId) {
                detail = data.chunkId + ' ' + truncate(data.content || '', 80);
            } else {
                detail = truncate(JSON.stringify(data), 150);
            }
        }

        const line = document.createElement('div');
        line.className = 'event-line';
        line.innerHTML = `
            <span class="event-time">${now}</span>
            <span class="event-type">${type}</span>
            <span class="${cls}">${esc(detail)}</span>
        `;
        log.appendChild(line);
        log.scrollTop = log.scrollHeight;
    }

    // ─── Helpers ───

    function statusBadge(status) {
        const map = {
            RUNNING: 'badge-running',
            COMPLETED: 'badge-completed',
            FAILED: 'badge-failed',
            TIMED_OUT: 'badge-timed-out',
            CANCELLED: 'badge-cancelled'
        };
        const labels = {
            RUNNING: '运行中',
            COMPLETED: '已完成',
            FAILED: '失败',
            TIMED_OUT: '超时',
            CANCELLED: '已取消'
        };
        const cls = map[status] || 'badge-cancelled';
        return `<span class="badge ${cls}">${labels[status] || status}</span>`;
    }

    function engineIcon(type) {
        const icons = { QUERY: '\uD83D\uDD0D', MEDIA: '\uD83C\uDFA5', INSIGHT: '\uD83D\uDCA1' };
        return icons[type] || '\u2699\uFE0F';
    }

    function formatTime(isoStr) {
        if (!isoStr) return '';
        const d = new Date(isoStr);
        return d.toLocaleTimeString('zh-CN', { hour12: false, hour: '2-digit', minute: '2-digit' });
    }

    function formatDateTime(isoStr) {
        if (!isoStr) return '';
        const d = new Date(isoStr);
        return d.toLocaleDateString('zh-CN') + ' ' + d.toLocaleTimeString('zh-CN', { hour12: false });
    }

    function esc(str) {
        if (!str) return '';
        const el = document.createElement('span');
        el.textContent = str;
        return el.innerHTML;
    }

    function truncate(str, len) {
        if (!str) return '';
        return str.length > len ? str.substring(0, len) + '...' : str;
    }

    function parseJson(data) {
        try { return JSON.parse(data); }
        catch { return { message: data }; }
    }

    // ─── Init ───

    async function init() {
        await refreshTaskList();
        document.getElementById('queryInput').focus();
    }

    document.addEventListener('DOMContentLoaded', init);

    // Public API
    return { createTask, cancelTask, selectTask, showCreate, switchTab };
})();
