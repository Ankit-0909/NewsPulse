
const AUTH_BASE_URL = 'http://localhost:8080/api/auth';
const NEWS_FEED_URL = 'feed.html';
const ADMIN_DASHBOARD_URL = 'admin-dashboard.html';

document.addEventListener('DOMContentLoaded', async () => {
    const loginState = document.getElementById('loginState');
    const choiceState = document.getElementById('choiceState');
    const adminLoginForm = document.getElementById('adminLoginForm');
    const adminLoginBtn = document.getElementById('adminLoginBtn');
    const adminAlert = document.getElementById('adminAlert');
    const adminNameDisplay = document.getElementById('adminNameDisplay');
    const goToFeedBtn = document.getElementById('goToFeedBtn');
    const goToDashboardBtn = document.getElementById('goToDashboardBtn');
    const switchAccountBtn = document.getElementById('switchAccountBtn');


    try {
        const refreshResponse = await fetch(`${AUTH_BASE_URL}/refresh`, {
            method: 'POST',
            credentials: 'include'
        });

        if (refreshResponse.ok) {
            const data = await refreshResponse.json();
            const token = data.accessToken;
            if (token) {
                const payload = decodeJwt(token) || {};
                if (isAdminRole(payload)) {
                    showChoiceState({ ...payload, email: payload.email || payload.sub });
                }
            }
        }
    } catch (e) {

    }

    adminLoginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideAlert();
        setLoading(true);

        const email = document.getElementById('adminEmail').value.trim();
        const password = document.getElementById('adminPassword').value.trim();

        try {
            const response = await fetch(`${AUTH_BASE_URL}/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },

                credentials: 'include',
                body: JSON.stringify({ email, password })
            });

            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                throw new Error(data.message || 'Invalid email or password.');
            }

            const token = data.accessToken || data.token || data.jwt;
            if (!token) throw new Error('Login succeeded but no token was returned.');


            const payload = decodeJwt(token) || {};
            const role = data.role || payload.role || (Array.isArray(payload.roles) ? payload.roles[0] : payload.roles);

            if (!isAdminRole({ role })) {
                throw new Error('This account does not have admin access.');
            }


            showAlert('success', '🟢 Identity verified.');
            showChoiceState({ ...payload, email, role });

        } catch (error) {
            showAlert('error', `❌ ${error.message}`);
        } finally {
            setLoading(false);
        }
    });

    goToFeedBtn.addEventListener('click', () => {
        window.location.href = NEWS_FEED_URL;
    });

    goToDashboardBtn.addEventListener('click', () => {
        window.location.href = ADMIN_DASHBOARD_URL;
    });

    switchAccountBtn.addEventListener('click', async () => {

        try {
            await fetch(`${AUTH_BASE_URL}/logout`, { method: 'POST', credentials: 'include' });
        } catch (e) {

        }
        choiceState.classList.add('hidden');
        loginState.classList.remove('hidden');
        adminLoginForm.reset();
        hideAlert();
    });

    function showChoiceState(payload) {
        const displayName = payload.name || payload.email || 'Admin';
        adminNameDisplay.textContent = displayName;
        loginState.classList.add('hidden');
        choiceState.classList.remove('hidden');
    }

    function isAdminRole(payload) {
        if (!payload) return false;
        const role = payload.role;
        if (Array.isArray(role)) return role.includes('ADMIN') || role.includes('ROLE_ADMIN');
        return role === 'ADMIN' || role === 'ROLE_ADMIN';
    }

    function decodeJwt(token) {
        try {
            const base64Payload = token.split('.')[1];
            const decoded = atob(base64Payload.replace(/-/g, '+').replace(/_/g, '/'));
            return JSON.parse(decoded);
        } catch (e) {
            return null;
        }
    }

    function setLoading(isLoading) {
        adminLoginBtn.disabled = isLoading;
        adminLoginBtn.querySelector('.btn-label').classList.toggle('hidden', isLoading);
        adminLoginBtn.querySelector('.btn-spinner').classList.toggle('hidden', !isLoading);
    }

    function showAlert(type, message) {
        adminAlert.className = `admin-alert ${type}`;
        adminAlert.textContent = message;
    }

    function hideAlert() {
        adminAlert.className = 'admin-alert hidden';
    }
});