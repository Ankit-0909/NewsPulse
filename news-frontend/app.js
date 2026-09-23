const AUTH_BASE_URL = 'http://localhost:8080/api/auth';

document.addEventListener('DOMContentLoaded', async () => {

    try {
        const sessionCheck = await fetch(`${AUTH_BASE_URL}/refresh`, {
            method: 'POST',
            credentials: 'include'
        });

        if (sessionCheck.ok) {
            const data = await sessionCheck.json();
            if (data.accessToken) {
                window.location.href = 'feed.html';
                return;
            }
        }
    } catch (e) {

    }
    const loginSection = document.getElementById('loginSection');
    const registerSection = document.getElementById('registerSection');
    const otpSection = document.getElementById('otpSection');
    const forgotSection = document.getElementById('forgotSection');
    const resetPasswordSection = document.getElementById('resetPasswordSection');
    const socialWrapper = document.getElementById('socialWrapper');


    const loginTab = document.getElementById('loginTab');
    const registerTab = document.getElementById('registerTab');
    const formTitle = document.getElementById('formTitle');


    const linkToRegister = document.getElementById('linkToRegister');
    const linkToLogin = document.getElementById('linkToLogin');
    const linkToForgot = document.getElementById('linkToForgot');
    const backToRegister = document.getElementById('backToRegister');
    const backToForgotInit = document.getElementById('backToForgotInit');
    const targetEmailDisplay = document.getElementById('targetEmailDisplay');


    const loginForm = document.getElementById('loginForm');
    const registerForm = document.getElementById('registerForm');
    const otpForm = document.getElementById('otpForm');
    const forgotForm = document.getElementById('forgotForm');
    const resetPasswordForm = document.getElementById('resetPasswordForm');
    const authAlert = document.getElementById('authAlert');


    let tempUserEmail = "";
    let resetTargetEmail = "";


    const allSections = [loginSection, registerSection, otpSection, forgotSection, resetPasswordSection];

    function showSection(sectionToShow, { showSocial = false } = {}) {
        allSections.forEach(section => {
            if (!section) return;
            if (section === sectionToShow) {
                section.classList.remove('hidden');
            } else {
                section.classList.add('hidden');
            }
        });

        if (socialWrapper) {
            socialWrapper.classList.toggle('hidden', !showSocial);
        }

        hideAlert();
    }

    function setActiveTab(tab) {
        if (loginTab && registerTab) {
            loginTab.classList.toggle('active', tab === 'login');
            registerTab.classList.toggle('active', tab === 'register');
        }
        if (formTitle) {
            formTitle.textContent = tab === 'login' ? 'Sign In' : 'Create Account';
        }
    }

    if (loginTab) {
        loginTab.addEventListener('click', () => {
            showSection(loginSection, { showSocial: true });
            setActiveTab('login');
        });
    }

    if (registerTab) {
        registerTab.addEventListener('click', () => {
            showSection(registerSection, { showSocial: false });
            setActiveTab('register');
        });
    }

    linkToRegister.addEventListener('click', (e) => {
        e.preventDefault();
        showSection(registerSection, { showSocial: false });
        setActiveTab('register');
    });

    linkToLogin.addEventListener('click', (e) => {
        e.preventDefault();
        showSection(loginSection, { showSocial: true });
        setActiveTab('login');
    });

    linkToForgot.addEventListener('click', (e) => {
        e.preventDefault();
        showSection(forgotSection, { showSocial: false });
    });

    backToRegister.addEventListener('click', (e) => {
        e.preventDefault();
        showSection(registerSection, { showSocial: false });
        setActiveTab('register');
    });

    backToForgotInit.addEventListener('click', (e) => {
        e.preventDefault();
        showSection(forgotSection, { showSocial: false });
    });

    document.querySelectorAll('.backToLoginClear').forEach(btn => {
        btn.addEventListener('click', (e) => {
            e.preventDefault();
            showSection(loginSection, { showSocial: true });
            setActiveTab('login');
        });
    });


    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideAlert();

        const email = document.getElementById('loginEmail').value.trim();
        const password = document.getElementById('loginPassword').value.trim();

        try {
            const response = await fetch(`${AUTH_BASE_URL}/login`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },

                credentials: 'include',
                body: JSON.stringify({ email, password })
            });

            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                throw new Error(data.message || "Invalid Email or Password Credentials!");
            }

            const token = data.accessToken || data.token || data.jwt;
            if (!token) throw new Error("Authentication verified, but token key missing.");



            showAlert("success", "🟢 Logged in successfully!");
            window.location.href = 'feed.html';

        } catch (error) {
            showAlert("error", `❌ ${error.message}`);
        }
    });


    registerForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideAlert();

        const name = document.getElementById('regName').value.trim();
        const email = document.getElementById('regEmail').value.trim();
        const password = document.getElementById('regPassword').value.trim();

        tempUserEmail = email;

        try {
            const response = await fetch(`${AUTH_BASE_URL}/register`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ name, email, password })
            });

            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                const errorDetail = data.message || data.error || "Registration parameter initialization dropped.";
                throw new Error(errorDetail);
            }

            showAlert("success", "📩 " + (data.message || "Verification code dispatched successfully."));

            showSection(otpSection, { showSocial: false });
            targetEmailDisplay.innerText = tempUserEmail;

        } catch (error) {
            showAlert("error", `❌ ${error.message}`);
        }
    });


    otpForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideAlert();

        const otp = document.getElementById('otpInput').value.trim();

        try {
            const response = await fetch(`${AUTH_BASE_URL}/verify-otp`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: tempUserEmail, otp: otp })
            });

            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                throw new Error(data.message || "Incorrect code parameters or expiration limit hit.");
            }

            showAlert("success", "🎉 Account activated successfully! Shifting to login panel...");
            otpForm.reset();

            setTimeout(() => {
                showSection(loginSection, { showSocial: true });
                setActiveTab('login');
            }, 2500);

        } catch (error) {
            showAlert("error", `❌ ${error.message}`);
        }
    });


    forgotForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideAlert();
        resetTargetEmail = document.getElementById('forgotEmail').value.trim();

        try {
            const response = await fetch(`${AUTH_BASE_URL}/forgot-password/initiate?email=${encodeURIComponent(resetTargetEmail)}`, {
                method: 'POST'
            });
            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                throw new Error(data.message || "Failed to trigger recovery flow.");
            }

            showAlert("success", "📩 Secure recovery code dropped in your inbox!");
            showSection(resetPasswordSection, { showSocial: false });
        } catch (error) {
            showAlert("error", `❌ ${error.message}`);
        }
    });


    resetPasswordForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        hideAlert();

        const otp = document.getElementById('forgotOtpInput').value.trim();
        const newPassword = document.getElementById('forgotNewPassword').value.trim();

        try {
            const response = await fetch(`${AUTH_BASE_URL}/forgot-password/execute`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: resetTargetEmail, otp: otp, newPassword: newPassword })
            });
            const data = await response.json().catch(() => ({}));

            if (!response.ok) {
                throw new Error(data.message || "Failed to alter credential configuration.");
            }

            showAlert("success", "🎉 Security credentials updated! Shifting back to sign in...");
            resetPasswordForm.reset();

            setTimeout(() => {
                showSection(loginSection, { showSocial: true });
                setActiveTab('login');
            }, 2500);
        } catch (error) {
            showAlert("error", `❌ ${error.message}`);
        }
    });


    function showAlert(type, message) {
        authAlert.className = `auth-alert ${type}`;
        authAlert.innerText = message;
        authAlert.classList.remove('hidden');
    }

    function hideAlert() {
        authAlert.classList.add('hidden');
    }
});