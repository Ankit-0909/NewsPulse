
const FEED_API_BASE = 'http://localhost:8080/api/news';
const AUTH_BASE_URL = 'http://localhost:8080/api/auth';

const BOOKMARK_API_BASE = 'http://localhost:8080/api/bookmarks';
const PERSONALIZATION_API_BASE = 'http://localhost:8080/api/personalization';
const HISTORY_API_BASE = 'http://localhost:8080/api/history';


let currentPage = 0;
let currentSize = 9;
let currentCategory = 'ALL';
let currentSearchKeyword = '';


let currentViewMode = 'feed';


let bookmarkedArticleIds = new Set();



let followedCategories = new Set(JSON.parse(localStorage.getItem('syncrail_followed_categories') || '[]'));
let followedSources = new Set(JSON.parse(localStorage.getItem('syncrail_followed_sources') || '[]'));


let accessToken = null;

document.addEventListener('DOMContentLoaded', async () => {

    const urlParams = new URLSearchParams(window.location.search);
    const oauthCode = urlParams.get('code');
    if (oauthCode) {
        try {
            const exchangeResponse = await fetch(`${AUTH_BASE_URL}/oauth/exchange`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                body: JSON.stringify({ code: oauthCode })
            });
            if (exchangeResponse.ok) {
                const data = await exchangeResponse.json();
                accessToken = data.accessToken;
            }
        } catch (err) {
            console.warn('OAuth code exchange failed:', err);
        }
        window.history.replaceState({}, document.title, window.location.pathname);
    }


    if (!accessToken) {
        const restored = await bootstrapSession();
        if (!restored) {
            alert('⚠️ Unauthorized access! Please sign in first.');
            window.location.href = 'index.html';
            return;
        }
    }


    const payload = decodeJwt(accessToken) || {};
    const emailElement = document.getElementById('userEmailDisplay');
    if (emailElement) {
        emailElement.innerText = payload.email || payload.sub || 'Reader';
    }


    Promise.all([loadCategoryPills(), loadBookmarkedIds()]).then(() => {
        initializeFeed();
    });
    setupEventListeners();
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


async function authFetch(url, options = {}) {
    const withAuth = (opts) => ({
        ...opts,
        credentials: 'include',
        headers: {
            ...(opts.headers || {}),
            'Authorization': `Bearer ${accessToken}`
        }
    });

    let response = await fetch(url, withAuth(options));

    if (response.status === 401) {
        const restored = await bootstrapSession();
        if (restored) {
            response = await fetch(url, withAuth(options));
        }
    }

    return response;
}

function decodeJwt(token) {
    if (!token) return null;
    try {
        const base64Payload = token.split('.')[1];
        const decoded = atob(base64Payload.replace(/-/g, '+').replace(/_/g, '/'));
        return JSON.parse(decoded);
    } catch (e) {
        return null;
    }
}

function initializeFeed() {
    loadNewsFeed(currentPage, currentSize, currentCategory, currentSearchKeyword);
}


async function loadCategoryPills() {
    try {
        const response = await authFetch(`${FEED_API_BASE}/categories`);
        if (!response.ok) return;

        const categories = await response.json();
        const wrapper = document.getElementById('categoryPillsWrapper');
        if (!wrapper) return;

        categories.forEach(category => {

            if (wrapper.querySelector(`[data-category="${category.toUpperCase()}"]`)) return;

            const pill = document.createElement('button');
            pill.className = 'category-pill';
            pill.dataset.category = category.toUpperCase();
            pill.textContent = category;
            wrapper.appendChild(pill);
        });

        bindCategoryPillListeners();
    } catch (err) {
        console.warn('Could not load dynamic categories:', err);
    }
}


function bindCategoryPillListeners() {
    const pills = document.querySelectorAll('.category-pill');
    pills.forEach(pill => {
        pill.addEventListener('click', (e) => {
            const clickedPill = e.currentTarget;


            if (clickedPill.id === 'forYouPill') {
                pills.forEach(p => p.classList.remove('active'));
                clickedPill.classList.add('active');
                switchViewMode('foryou');
                return;
            }

            pills.forEach(p => p.classList.remove('active'));
            clickedPill.classList.add('active');

            currentCategory = clickedPill.getAttribute('data-category');
            currentPage = 0;
            currentSearchKeyword = '';
            document.getElementById('globalSearchInput').value = '';


            currentViewMode = 'feed';
            hideViewModeBanner();
            loadNewsFeed(currentPage, currentSize, currentCategory, currentSearchKeyword);
        });
    });
}


async function handleLogout() {
    try {
        await fetch(`${AUTH_BASE_URL}/logout`, {
            method: 'POST',
            credentials: 'include'
        });
    } catch (error) {
        console.warn('Logout API call failed, clearing local session anyway:', error);
    } finally {

        accessToken = null;
        window.location.href = 'index.html';
    }
}

function setupEventListeners() {
    const logoutBtn = document.getElementById('btnLogout');
    if (logoutBtn) {
        logoutBtn.addEventListener('click', handleLogout);
    }


    const savedBtn = document.getElementById('btnSaved');
    if (savedBtn) {
        savedBtn.addEventListener('click', () => switchViewMode('saved'));
    }

    const historyBtn = document.getElementById('btnHistory');
    if (historyBtn) {
        historyBtn.addEventListener('click', () => switchViewMode('history'));
    }

    const backToFeedBtn = document.getElementById('btnBackToFeed');
    if (backToFeedBtn) {
        backToFeedBtn.addEventListener('click', () => {
            currentViewMode = 'feed';
            hideViewModeBanner();
            document.querySelectorAll('.category-pill').forEach(p => p.classList.remove('active'));
            const allPill = document.querySelector('.category-pill[data-category="ALL"]');
            if (allPill) allPill.classList.add('active');
            currentCategory = 'ALL';
            currentPage = 0;
            loadNewsFeed(currentPage, currentSize, currentCategory, currentSearchKeyword);
        });
    }


    const workspace = document.getElementById('newsGridWorkspace');
    if (workspace) {
        workspace.addEventListener('click', (e) => {
            const bookmarkBtn = e.target.closest('.btn-bookmark');
            if (bookmarkBtn) {
                handleBookmarkToggle(bookmarkBtn.dataset.articleId, bookmarkBtn);
                return;
            }

            const followBtn = e.target.closest('.btn-follow');
            if (followBtn) {
                const type = followBtn.dataset.followType;
                const value = followBtn.dataset.followValue;
                handleFollowToggle(type, value, followBtn);
                return;
            }

            const summarizeBtn = e.target.closest('.btn-ai-summarize');
            if (summarizeBtn) {
                const articleId = Number(summarizeBtn.dataset.summarizeId);
                const article = currentArticles.find(a => a.id === articleId);
                if (article) triggerAiSummaryExtraction(article);
            }
        });
    }


    const searchInput = document.getElementById('globalSearchInput');
    let searchDebounceTimeout;
    searchInput.addEventListener('input', (e) => {
        clearTimeout(searchDebounceTimeout);
        searchDebounceTimeout = setTimeout(() => {
            currentSearchKeyword = e.target.value.trim();
            currentPage = 0;
            currentViewMode = 'feed';
            hideViewModeBanner();
            loadNewsFeed(currentPage, currentSize, currentCategory, currentSearchKeyword);
        }, 400);
    });


    document.getElementById('btnPrevPage').addEventListener('click', () => {
        if (currentPage > 0) {
            currentPage--;
            if (currentViewMode === 'feed') {
                loadNewsFeed(currentPage, currentSize, currentCategory, currentSearchKeyword);
            } else {
                loadSpecialView(currentViewMode);
            }
        }
    });

    document.getElementById('btnNextPage').addEventListener('click', () => {
        currentPage++;
        if (currentViewMode === 'feed') {
            loadNewsFeed(currentPage, currentSize, currentCategory, currentSearchKeyword);
        } else {
            loadSpecialView(currentViewMode);
        }
    });


    document.getElementById('btnCloseModal').addEventListener('click', closeAiSummaryTray);
    document.getElementById('aiSummaryModal').addEventListener('click', (e) => {
        if (e.target.id === 'aiSummaryModal') closeAiSummaryTray();
    });
}


async function loadNewsFeed(page, size, category, keyword) {
    const workspace = document.getElementById('newsGridWorkspace');
    const loader = document.getElementById('feedLoader');

    workspace.innerHTML = '';
    loader.classList.remove('hidden');

    let fetchUrl = `${FEED_API_BASE}?page=${page}&size=${size}`;

    if (keyword !== '') {
        fetchUrl = `${FEED_API_BASE}/search?keyword=${encodeURIComponent(keyword)}&page=${page}&size=${size}`;
    } else if (category !== 'ALL') {
        fetchUrl = `${FEED_API_BASE}/category/${encodeURIComponent(category)}?page=${page}&size=${size}`;
    }

    try {
        console.log(`📡 Fetching Stream from: ${fetchUrl}`);

        const response = await authFetch(fetchUrl, {
            method: 'GET',
            headers: { 'Content-Type': 'application/json' }
        });

        if (response.status === 401) {
            throw new Error('Your session has expired. Please login again.');
        }

        if (!response.ok) throw new Error('System failed to process incoming news stream matrices.');

        const pageResponse = await response.json();
        const articles = pageResponse.content || [];

        loader.classList.add('hidden');
        renderArticlesToGrid(articles);
        updatePaginationInterface(pageResponse);

    } catch (error) {
        loader.classList.add('hidden');
        workspace.innerHTML = `
            <div style="grid-column: 1/-1; text-align: center; padding: 3rem; color: #ef4444;">
                <i class="fa-solid fa-triangle-exclamation" style="font-size: 2.5rem; margin-bottom: 1rem;"></i>
                <p style="font-weight:600;">Data Sync Interrupted: ${error.message}</p>
                <small style="color:#777;">Please verify your login session or local server configuration.</small>
            </div>
        `;
    }
}


let currentArticles = [];

function renderArticlesToGrid(articles) {
    const workspace = document.getElementById('newsGridWorkspace');
    currentArticles = articles;

    if (articles.length === 0) {
        workspace.innerHTML = `
            <div style="grid-column: 1/-1; text-align: center; padding: 4rem; color: #888;">
                <i class="fa-solid fa-box-open" style="font-size: 3rem; margin-bottom: 1rem;"></i>
                <p>No valid news coordinates discovered matching your tracking context.</p>
            </div>
        `;
        return;
    }

    articles.forEach(article => {
        const publishedDate = article.publishedAt ? new Date(article.publishedAt).toLocaleString() : 'Recent';
        const rawContent = article.originalContent || 'No description summary logged inside database node.';

        const isSaved = bookmarkedArticleIds.has(article.id);
        const isCategoryFollowed = article.category && followedCategories.has(article.category);
        const isSourceFollowed = article.sourceName && followedSources.has(article.sourceName);

        const historyMeta = article.__historyMeta;
        const historyProgressHtml = historyMeta ? `
            <div class="history-progress-wrap">
                <div class="history-progress-track">
                    <div class="history-progress-fill" style="width:${historyMeta.progress || 0}%"></div>
                </div>
                <span class="history-progress-label">${historyMeta.completed ? 'Completed' : (historyMeta.progress || 0) + '% read'}</span>
            </div>
        ` : '';

        const cardHtml = `
            <div class="news-card">
                <div>
                    <div class="card-meta-header">
                        <span class="category-badge">${escapeHtml(article.category || 'GENERAL')}<button class="btn-follow ${isCategoryFollowed ? 'is-following' : ''}" data-follow-type="category" data-follow-value="${escapeHtml(article.category || '')}" title="${isCategoryFollowed ? 'Unfollow category' : 'Follow category'}"><i class="fa-solid fa-star"></i></button></span>
                        <span class="source-label">${escapeHtml(article.sourceName || 'Global Feed')}<button class="btn-follow ${isSourceFollowed ? 'is-following' : ''}" data-follow-type="source" data-follow-value="${escapeHtml(article.sourceName || '')}" title="${isSourceFollowed ? 'Unfollow source' : 'Follow source'}"><i class="fa-solid fa-star"></i></button></span>
                    </div>
                    <h3 class="news-title">${escapeHtml(article.title)}</h3>
                    <p class="news-snippet">${escapeHtml(rawContent)}</p>
                    ${historyProgressHtml}
                </div>
                <div class="card-footer-actions">
                    <button class="btn-ai-summarize" data-summarize-id="${article.id}">
                        <i class="fa-solid fa-wand-magic-sparkles"></i> AI Summarize
                    </button>
                    <div class="footer-right">
                        <span class="timestamp">${publishedDate}</span>
                        <button class="btn-bookmark ${isSaved ? 'is-saved' : ''}" data-article-id="${article.id}" title="${isSaved ? 'Remove bookmark' : 'Save for later'}">
                            <i class="fa-${isSaved ? 'solid' : 'regular'} fa-bookmark"></i>
                        </button>
                    </div>
                </div>
            </div>
        `;
        workspace.insertAdjacentHTML('beforeend', cardHtml);
    });
}


function triggerAiSummaryExtraction(article) {
    document.getElementById('modalArticleTitle').innerText = article.title;
    document.getElementById('modalArticleSource').innerHTML = `<i class="fa-solid fa-globe"></i> ${escapeHtml(article.sourceName || 'Verified Anchor')}`;
    document.getElementById('modalArticleCategory').innerHTML = `<i class="fa-solid fa-layer-group"></i> ${escapeHtml(article.category || 'General')}`;

    const aiSummaryWorkspaceText = article.aiSummary || article.summary;

    if (aiSummaryWorkspaceText && aiSummaryWorkspaceText.trim() !== "") {
        document.getElementById('modalAiSummaryText').innerText = aiSummaryWorkspaceText;
    } else {
        document.getElementById('modalAiSummaryText').innerHTML = `
            <span style="color:var(--text-muted); font-style: italic;">
                <i class="fa-solid fa-triangle-exclamation" style="color:var(--accent-gold)"></i>
                Executive briefing missing. Manual entry ingestion process did not trigger synthesis operations for this record node.
            </span>`;
    }

    document.getElementById('aiSummaryModal').classList.remove('hidden');
    document.body.style.overflow = 'hidden';


    trackReadingProgress(article.id, 100);
}

function closeAiSummaryTray() {
    document.getElementById('aiSummaryModal').classList.add('hidden');
    document.body.style.overflow = '';
}


function updatePaginationInterface(pageResponse) {
    const totalPages = pageResponse.totalPages || 1;
    const isFirst = pageResponse.number === 0 || pageResponse.first === true;
    const isLast = pageResponse.last === true || (currentPage + 1) >= totalPages;

    document.getElementById('pageInfoDisplay').innerText = `Page ${currentPage + 1} of ${totalPages}`;
    document.getElementById('btnPrevPage').disabled = isFirst;
    document.getElementById('btnNextPage').disabled = isLast;
}


function escapeHtml(str) {
   if (!str) return '';
    let cleanStr = str.replace(/<[^>]*>/g, '');
    cleanStr = cleanStr.replace(/&nbsp;/g, ' ').replace(/&amp;/g, '&');
    return cleanStr.trim();
}



async function loadBookmarkedIds() {
    if (!accessToken) return;

    try {
        const ids = new Set();
        let page = 0;
        let totalPages = 1;
        const pageSize = 100;

        do {
            const response = await authFetch(`${BOOKMARK_API_BASE}?page=${page}&size=${pageSize}`);
            if (!response.ok) break;

            const pageResponse = await response.json();
            const content = pageResponse.content || [];
            content.forEach(bookmark => {
                if (bookmark.article && bookmark.article.id) ids.add(bookmark.article.id);
            });
            totalPages = pageResponse.totalPages || 1;
            page++;
        } while (page < totalPages);

        bookmarkedArticleIds = ids;
    } catch (err) {
        console.warn('Could not load bookmarked articles:', err);
    }
}

async function handleBookmarkToggle(articleId, buttonEl) {
    const id = Number(articleId);

    try {
        const response = await authFetch(`${BOOKMARK_API_BASE}/toggle/${id}`, {
            method: 'POST'
        });

        if (!response.ok) throw new Error('Bookmark action failed.');

        const nowSaved = !bookmarkedArticleIds.has(id);
        if (nowSaved) {
            bookmarkedArticleIds.add(id);
        } else {
            bookmarkedArticleIds.delete(id);
        }

        document.querySelectorAll(`.btn-bookmark[data-article-id="${id}"]`).forEach(btn => {
            btn.classList.toggle('is-saved', nowSaved);
            btn.title = nowSaved ? 'Remove bookmark' : 'Save for later';
            btn.innerHTML = `<i class="fa-${nowSaved ? 'solid' : 'regular'} fa-bookmark"></i>`;
        });

        if (!nowSaved && currentViewMode === 'saved' && buttonEl) {
            const card = buttonEl.closest('.news-card');
            if (card) card.remove();
        }
    } catch (err) {
        console.warn('Bookmark toggle failed:', err);
    }
}



async function handleFollowToggle(type, value, buttonEl) {
    if (!value) return;
    const set = type === 'category' ? followedCategories : followedSources;
    const storageKey = type === 'category' ? 'syncrail_followed_categories' : 'syncrail_followed_sources';
    const isCurrentlyFollowing = set.has(value);
    const action = isCurrentlyFollowing ? 'unsubscribe' : 'subscribe';
    const paramName = type === 'category' ? 'category' : 'source';

    try {
        const response = await authFetch(`${PERSONALIZATION_API_BASE}/${type === 'category' ? 'categories' : 'sources'}/${action}?${paramName}=${encodeURIComponent(value)}`, {
            method: 'POST'
        });

        if (!response.ok) throw new Error(`${action} failed`);

        if (isCurrentlyFollowing) {
            set.delete(value);
        } else {
            set.add(value);
        }
        localStorage.setItem(storageKey, JSON.stringify([...set]));

        document.querySelectorAll(`.btn-follow[data-follow-type="${type}"][data-follow-value="${CSS.escape(value)}"]`).forEach(btn => {
            btn.classList.toggle('is-following', !isCurrentlyFollowing);
            btn.title = !isCurrentlyFollowing ? `Unfollow ${type}` : `Follow ${type}`;
        });
    } catch (err) {
        console.warn(`Could not ${action} ${type}:`, err);
    }
}



async function trackReadingProgress(articleId, progress) {
    if (!articleId) return;
    try {
        await authFetch(`${HISTORY_API_BASE}/track?articleId=${articleId}&progress=${progress}`, {
            method: 'POST'
        });
    } catch (err) {
        console.warn('Could not record reading progress:', err);
    }
}



function showViewModeBanner(label) {
    const banner = document.getElementById('viewModeBanner');
    const labelEl = document.getElementById('viewModeLabel');
    if (!banner || !labelEl) return;
    labelEl.textContent = label;
    banner.classList.remove('hidden');
}

function hideViewModeBanner() {
    const banner = document.getElementById('viewModeBanner');
    if (banner) banner.classList.add('hidden');
}

function switchViewMode(mode) {
    currentViewMode = mode;
    currentPage = 0;

    document.querySelectorAll('.category-pill').forEach(p => p.classList.remove('active'));

    const labels = {
        foryou: '⭐ For You — based on what you follow',
        saved: '🔖 Saved articles',
        history: '🕐 Reading history'
    };

    if (mode === 'foryou') {
        const pill = document.getElementById('forYouPill');
        if (pill) pill.classList.add('active');
    }

    showViewModeBanner(labels[mode] || '');
    loadSpecialView(mode);
}

async function loadSpecialView(mode) {
    const workspace = document.getElementById('newsGridWorkspace');
    const loader = document.getElementById('feedLoader');
    workspace.innerHTML = '';
    loader.classList.remove('hidden');

    let url;
    if (mode === 'foryou') {
        url = `${PERSONALIZATION_API_BASE}/for-you?page=${currentPage}&size=${currentSize}`;
    } else if (mode === 'saved') {
        url = `${BOOKMARK_API_BASE}?page=${currentPage}&size=${currentSize}`;
    } else if (mode === 'history') {
        url = `${HISTORY_API_BASE}?page=${currentPage}&size=${currentSize}`;
    } else {
        return;
    }

    try {
        const response = await authFetch(url);

        if (response.status === 401) {
            throw new Error('Your session has expired. Please login again.');
        }
        if (!response.ok) throw new Error('Could not load this screen.');

        const pageResponse = await response.json();
        const content = pageResponse.content || [];

        let articles;
        if (mode === 'foryou') {
            articles = content;
        } else if (mode === 'saved') {
            articles = content.filter(b => b.article).map(b => b.article);
        } else {
            articles = content.filter(h => h.article).map(h => ({
                ...h.article,
                __historyMeta: { progress: h.progressPercentage, completed: h.isCompleted }
            }));
        }

        loader.classList.add('hidden');
        renderArticlesToGrid(articles);
        updatePaginationInterface(pageResponse);
    } catch (error) {
        loader.classList.add('hidden');
        workspace.innerHTML = `
            <div style="grid-column: 1/-1; text-align: center; padding: 3rem; color: #ef4444;">
                <i class="fa-solid fa-triangle-exclamation" style="font-size: 2.5rem; margin-bottom: 1rem;"></i>
                <p style="font-weight:600;">Data Sync Interrupted: ${error.message}</p>
            </div>
        `;
    }
}