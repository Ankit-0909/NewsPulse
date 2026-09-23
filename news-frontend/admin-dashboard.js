
const API_BASE = 'http://localhost:8080/api';
const AUTH_BASE_URL = 'http://localhost:8080/api/auth';
const ADMIN_LOGIN_URL = 'admin-login.html';

const ENDPOINTS = {
    feedHealth: `${API_BASE}/admin/feeds/health`,
    fetchNow: `${API_BASE}/admin/feeds/fetch-now`,
    newsList: `${API_BASE}/news`,
    newsCreate: `${API_BASE}/admin/news`,
    newsUpdate: (id) => `${API_BASE}/admin/news/${id}`,
    newsDelete: (id) => `${API_BASE}/admin/news/${id}`,
    sources: `${API_BASE}/admin/sources`,
    sourceUpdate: (id) => `${API_BASE}/admin/sources/${id}`,
    sourceDelete: (id) => `${API_BASE}/admin/sources/${id}`,
};


let accessToken = null;

document.addEventListener('DOMContentLoaded', async () => {

    const restored = await bootstrapSession();
    if (!restored) {
        window.location.href = ADMIN_LOGIN_URL;
        return;
    }

    const payload = decodeJwt(accessToken);
    if (!isAdminRole(payload)) {

        window.location.href = ADMIN_LOGIN_URL;
        return;
    }

    const adminEmailChip = document.getElementById('adminEmailChip');
    if (payload && adminEmailChip) {
        adminEmailChip.textContent = payload.email || payload.sub || 'Admin';
    }


    const navItems = document.querySelectorAll('.nav-item');
    const panels = document.querySelectorAll('.panel');

    navItems.forEach(item => {
        item.addEventListener('click', () => {
            navItems.forEach(n => n.classList.remove('active'));
            panels.forEach(p => p.classList.remove('active'));

            item.classList.add('active');
            document.getElementById(item.dataset.panel).classList.add('active');
        });
    });

    document.getElementById('logoutBtn').addEventListener('click', async () => {
        try {
            await fetch(`${AUTH_BASE_URL}/logout`, { method: 'POST', credentials: 'include' });
        } catch (e) {

        }
        accessToken = null;
        window.location.href = ADMIN_LOGIN_URL;
    });


    function authHeaders(extra = {}) {
        return { Authorization: `Bearer ${accessToken}`, ...extra };
    }

    const toastEl = document.getElementById('dashToast');
    let toastTimer;
    function toast(type, message) {
        clearTimeout(toastTimer);
        toastEl.className = `dash-toast ${type}`;
        toastEl.textContent = message;
        toastTimer = setTimeout(() => toastEl.classList.add('hidden'), 3200);
    }

    function openModal(id) { document.getElementById(id).classList.remove('hidden'); }
    function closeModal(id) { document.getElementById(id).classList.add('hidden'); }

    document.querySelectorAll('[data-close]').forEach(btn => {
        btn.addEventListener('click', () => closeModal(btn.dataset.close));
    });

    async function apiFetch(url, options = {}, isRetry = false) {
        const response = await fetch(url, {
            ...options,
            headers: authHeaders({ 'Content-Type': 'application/json', ...(options.headers || {}) })
        });


        if ((response.status === 401 || response.status === 403) && !isRetry) {
            const refreshed = await bootstrapSession();
            if (refreshed) {
                return apiFetch(url, options, true);
            }
        }

        if (response.status === 401 || response.status === 403) {
            toast('error', 'Session expired — please sign in again.');
            accessToken = null;
            setTimeout(() => (window.location.href = ADMIN_LOGIN_URL), 1200);
            throw new Error('Unauthorized');
        }

        const data = await response.json().catch(() => null);
        if (!response.ok) {
            const message = (data && (data.message || data.error)) || `Request failed (${response.status})`;
            throw new Error(message);
        }
        return data;
    }

    const healthTableBody = document.getElementById('healthTableBody');
    const healthStatRow = document.getElementById('healthStatRow');

    async function loadFeedHealth() {
        healthTableBody.innerHTML = `<tr><td colspan="4" class="empty-row">Loading feed health…</td></tr>`;
        try {
            const list = await apiFetch(ENDPOINTS.feedHealth);
            renderFeedHealth(Array.isArray(list) ? list : []);
        } catch (err) {
            healthTableBody.innerHTML = `<tr><td colspan="4" class="empty-row">Couldn't load feed health — ${escapeHtml(err.message)}</td></tr>`;
        }
    }

    function renderFeedHealth(list) {
        if (list.length === 0) {
            healthStatRow.innerHTML = '';
            healthTableBody.innerHTML = `<tr><td colspan="4" class="empty-row">No feeds are being tracked yet.</td></tr>`;
            return;
        }

        const normalized = list.map(item => ({
            name: item.sourceName || item.feedName || item.name || 'Unnamed feed',
            healthy: item.healthy ?? item.isHealthy ?? (String(item.status).toUpperCase() === 'SUCCESS' || String(item.status).toUpperCase() === 'HEALTHY' || String(item.status).toUpperCase() === 'OK'),
            lastChecked: item.lastFetchedAt || item.lastCheckedAt || item.checkedAt || item.updatedAt || null,
            detail: item.failureReason || item.errorMessage || item.lastError || item.detail || (item.responseTimeMs ? `${item.responseTimeMs}ms` : '—')
        }));

        const healthyCount = normalized.filter(f => f.healthy).length;
        const unhealthyCount = normalized.length - healthyCount;

        healthStatRow.innerHTML = `
            <div class="stat-chip">
                <div class="stat-value">${normalized.length}</div>
                <div class="stat-label">Total feeds</div>
            </div>
            <div class="stat-chip stat-ok">
                <div class="stat-value">${healthyCount}</div>
                <div class="stat-label">Healthy</div>
            </div>
            <div class="stat-chip stat-bad">
                <div class="stat-value">${unhealthyCount}</div>
                <div class="stat-label">Unhealthy</div>
            </div>
        `;

        healthTableBody.innerHTML = normalized.map(f => `
            <tr>
                <td class="cell-title">${escapeHtml(f.name)}</td>
                <td>
                    <span class="badge ${f.healthy ? 'badge-ok' : 'badge-bad'}">${f.healthy ? 'Healthy' : 'Unhealthy'}</span>
                </td>
                <td class="cell-mono">${formatDate(f.lastChecked)}</td>
                <td class="cell-muted">${escapeHtml(String(f.detail))}</td>
            </tr>
        `).join('');
    }

    document.getElementById('refreshHealthBtn').addEventListener('click', loadFeedHealth);
     

document.getElementById('fetchNowBtn').addEventListener('click', async () => {
    const btn = document.getElementById('fetchNowBtn');
    btn.disabled = true;
    btn.textContent = '📡 Fetching…';
 
    try {
        const result = await apiFetch(ENDPOINTS.fetchNow, { method: 'POST' });
        toast('success', result.message || 'Feed fetch triggered!');
 

        setTimeout(() => {
            loadFeedHealth();
            loadNews();
        }, 6000);
 
    } catch (err) {
        toast('error', err.message);
    } finally {
        btn.disabled = false;
        btn.textContent = '📡 Fetch News Now';
    }
});


    const newsTableBody = document.getElementById('newsTableBody');
    const newsModalOverlay = document.getElementById('newsModalOverlay');
    const newsForm = document.getElementById('newsForm');
    const newsModalTitle = document.getElementById('newsModalTitle');

    async function loadNews() {
        newsTableBody.innerHTML = `<tr><td colspan="5" class="empty-row">Loading articles…</td></tr>`;
        try {
            const allArticles = [];
            let page = 0;
            let totalPages = 1;
            const pageSize = 100;

            do {
                const pageResponse = await apiFetch(`${ENDPOINTS.newsList}?page=${page}&size=${pageSize}`);
                const content = pageResponse.content || (Array.isArray(pageResponse) ? pageResponse : []);
                allArticles.push(...content);
                totalPages = pageResponse.totalPages || 1;
                page++;
            } while (page < totalPages);

            renderNews(allArticles);
        } catch (err) {
            newsTableBody.innerHTML = `<tr><td colspan="5" class="empty-row">Couldn't load articles — ${escapeHtml(err.message)}</td></tr>`;
        }
    }

    function renderNews(list) {
        if (list.length === 0) {
            newsTableBody.innerHTML = `<tr><td colspan="5" class="empty-row">No articles yet — add your first one.</td></tr>`;
            return;
        }

        newsTableBody.innerHTML = list.map(article => `
            <tr>
                <td class="cell-title">${escapeHtml(article.title || 'Untitled')}</td>
                <td><span class="badge badge-neutral">${escapeHtml(article.category || '—')}</span></td>
                <td class="cell-muted">${escapeHtml(article.sourceName || '—')}</td>
                <td class="cell-mono">${formatDate(article.publishedAt)}</td>
                <td>
                    <div class="row-actions">
                        <button class="icon-btn" title="Edit" data-edit-news="${article.id}">✎</button>
                        <button class="icon-btn danger" title="Delete" data-delete-news="${article.id}">🗑</button>
                    </div>
                </td>
            </tr>
        `).join('');

        newsTableBody.querySelectorAll('[data-edit-news]').forEach(btn => {
            btn.addEventListener('click', () => {
                const article = list.find(a => String(a.id) === btn.dataset.editNews);
                openNewsModal(article);
            });
        });

        newsTableBody.querySelectorAll('[data-delete-news]').forEach(btn => {
            btn.addEventListener('click', () => deleteNews(btn.dataset.deleteNews));
        });
    }

    function openNewsModal(article = null) {
        newsForm.reset();
        document.getElementById('newsId').value = article ? article.id : '';
        document.getElementById('newsTitle').value = article ? article.title || '' : '';
        document.getElementById('newsCategory').value = article ? article.category || '' : '';
        document.getElementById('newsContent').value = article ? article.originalContent || '' : '';
        newsModalTitle.textContent = article ? 'Edit article' : 'Add article';
        openModal('newsModalOverlay');
    }

    document.getElementById('addNewsBtn').addEventListener('click', () => openNewsModal());
    document.getElementById('refreshNewsBtn').addEventListener('click', loadNews);

    newsForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const id = document.getElementById('newsId').value;
        const body = {
            title: document.getElementById('newsTitle').value.trim(),
            category: document.getElementById('newsCategory').value.trim(),
            originalContent: document.getElementById('newsContent').value.trim()
        };

        try {
            if (id) {
                await apiFetch(ENDPOINTS.newsUpdate(id), { method: 'PUT', body: JSON.stringify(body) });
                toast('success', 'Article updated.');
            } else {
                await apiFetch(ENDPOINTS.newsCreate, { method: 'POST', body: JSON.stringify(body) });
                toast('success', 'Article created.');
            }
            closeModal('newsModalOverlay');
            loadNews();
        } catch (err) {
            toast('error', err.message);
        }
    });

    async function deleteNews(id) {
        if (!confirm('Delete this article? This cannot be undone.')) return;
        try {
            await apiFetch(ENDPOINTS.newsDelete(id), { method: 'DELETE' });
            toast('success', 'Article deleted.');
            loadNews();
        } catch (err) {
            toast('error', err.message);
        }
    }


    const sourcesTableBody = document.getElementById('sourcesTableBody');
    const sourceModalOverlay = document.getElementById('sourceModalOverlay');
    const sourceForm = document.getElementById('sourceForm');
    const sourceModalTitle = document.getElementById('sourceModalTitle');

    async function loadSources() {
        sourcesTableBody.innerHTML = `<tr><td colspan="5" class="empty-row">Loading sources…</td></tr>`;
        try {
            const list = await apiFetch(ENDPOINTS.sources);
            renderSources(Array.isArray(list) ? list : []);
        } catch (err) {
            sourcesTableBody.innerHTML = `<tr><td colspan="5" class="empty-row">Couldn't load sources — ${escapeHtml(err.message)}</td></tr>`;
        }
    }

    function renderSources(list) {
        if (list.length === 0) {
            sourcesTableBody.innerHTML = `<tr><td colspan="5" class="empty-row">No sources yet — add your first RSS feed.</td></tr>`;
            return;
        }

        sourcesTableBody.innerHTML = list.map(source => `
            <tr>
                <td class="cell-title">${escapeHtml(source.sourceName || '—')}</td>
                <td class="cell-mono">${escapeHtml(source.rssUrl || '—')}</td>
                <td><span class="badge badge-neutral">${escapeHtml(source.category || '—')}</span></td>
                <td>
                    <button class="toggle-pill ${source.active ? 'is-active' : 'is-inactive'}" data-toggle-source="${source.id}">
                        ${source.active ? 'Active' : 'Inactive'}
                    </button>
                </td>
                <td>
                    <div class="row-actions">
                        <button class="icon-btn" title="Edit" data-edit-source="${source.id}">✎</button>
                        <button class="icon-btn danger" title="Delete" data-delete-source="${source.id}">🗑</button>
                    </div>
                </td>
            </tr>
        `).join('');

        sourcesTableBody.querySelectorAll('[data-edit-source]').forEach(btn => {
            btn.addEventListener('click', () => {
                const source = list.find(s => String(s.id) === btn.dataset.editSource);
                openSourceModal(source);
            });
        });

        sourcesTableBody.querySelectorAll('[data-delete-source]').forEach(btn => {
            btn.addEventListener('click', () => deleteSource(btn.dataset.deleteSource));
        });

        sourcesTableBody.querySelectorAll('[data-toggle-source]').forEach(btn => {
            btn.addEventListener('click', () => {
                const source = list.find(s => String(s.id) === btn.dataset.toggleSource);
                toggleSourceActive(source);
            });
        });
    }

    function openSourceModal(source = null) {
        sourceForm.reset();
        document.getElementById('sourceId').value = source ? source.id : '';
        document.getElementById('sourceName').value = source ? source.sourceName || '' : '';
        document.getElementById('sourceRssUrl').value = source ? source.rssUrl || '' : '';
        document.getElementById('sourceCategory').value = source ? source.category || '' : '';
        document.getElementById('sourceActive').checked = source ? !!source.active : true;
        sourceModalTitle.textContent = source ? 'Edit source' : 'Add source';
        openModal('sourceModalOverlay');
    }

    document.getElementById('addSourceBtn').addEventListener('click', () => openSourceModal());
    document.getElementById('refreshSourcesBtn').addEventListener('click', loadSources);

    sourceForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        const id = document.getElementById('sourceId').value;
        const body = {
            sourceName: document.getElementById('sourceName').value.trim(),
            rssUrl: document.getElementById('sourceRssUrl').value.trim(),
            category: document.getElementById('sourceCategory').value.trim(),
            active: document.getElementById('sourceActive').checked
        };

        try {
            if (id) {
                await apiFetch(ENDPOINTS.sourceUpdate(id), { method: 'PUT', body: JSON.stringify(body) });
                toast('success', 'Source updated.');
            } else {
                await apiFetch(ENDPOINTS.sources, { method: 'POST', body: JSON.stringify(body) });
                toast('success', 'Source added.');
            }
            closeModal('sourceModalOverlay');
            loadSources();
        } catch (err) {
            toast('error', err.message);
        }
    });

    async function toggleSourceActive(source) {
        try {
            await apiFetch(ENDPOINTS.sourceUpdate(source.id), {
                method: 'PUT',
                body: JSON.stringify({ ...source, active: !source.active })
            });
            loadSources();
        } catch (err) {
            toast('error', err.message);
        }
    }

    async function deleteSource(id) {
        if (!confirm('Delete this source? Existing articles will stay, but new ones will stop.')) return;
        try {
            await apiFetch(ENDPOINTS.sourceDelete(id), { method: 'DELETE' });
            toast('success', 'Source deleted.');
            loadSources();
        } catch (err) {
            toast('error', err.message);
        }
    }


    function formatDate(value) {
        if (!value) return '—';
        const d = new Date(value);
        if (isNaN(d.getTime())) return String(value);
        return d.toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
    }

    function escapeHtml(str) {
        return String(str)
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;');
    }


    loadFeedHealth();
    loadNews();
    loadSources();
});


async function bootstrapSession() {
    try {
        const response = await fetch(`${AUTH_BASE_URL}/refresh`, {
            method: 'POST',
            credentials: 'include'
        });
        if (!response.ok) return false;

        const data = await response.json();
        if (!data.accessToken) return false;

        accessToken = data.accessToken;
        return true;
    } catch (err) {
        return false;
    }
}

function decodeJwt(token) {
    if (!token) return null;
    try {
        const base64Payload = token.split('.')[1];
        return JSON.parse(atob(base64Payload.replace(/-/g, '+').replace(/_/g, '/')));
    } catch (e) {
        return null;
    }
}

function isAdminRole(payload) {
    if (!payload) return false;
    const role = payload.role;
    if (Array.isArray(role)) return role.includes('ADMIN') || role.includes('ROLE_ADMIN');
    return role === 'ADMIN' || role === 'ROLE_ADMIN';
}