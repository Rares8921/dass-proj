window.AuthX = (() => {
    const state = {
        currentUser: null,
        csrfToken: ""
    };

    function getCookie(name) {
        return document.cookie
            .split(";")
            .map(part => part.trim())
            .filter(Boolean)
            .map(part => {
                const separatorIndex = part.indexOf("=");
                if (separatorIndex < 0) {
                    return [part, ""];
                }
                return [part.slice(0, separatorIndex), decodeURIComponent(part.slice(separatorIndex + 1))];
            })
            .find(([cookieName]) => cookieName === name)?.[1] || "";
    }

    async function ensureCsrfToken(force = false) {
        return "";
    }

    async function request(url, options = {}, configuration = {}) {
        const method = (options.method || "GET").toUpperCase();
        const headers = {
            Accept: "application/json",
            ...(options.headers || {})
        };

        let body = options.body;
        if (body && typeof body !== "string" && !(body instanceof FormData)) {
            body = JSON.stringify(body);
            headers["Content-Type"] = "application/json";
        }

        const response = await fetch(url, {
            credentials: "include",
            ...options,
            method,
            headers,
            body
        });

        const contentType = response.headers.get("content-type") || "";
        const payload = contentType.includes("application/json")
            ? await response.json().catch(() => null)
            : await response.text().catch(() => "");

        if (response.status === 401 && configuration.allowUnauthorized) {
            return null;
        }

        if (!response.ok) {
            throw new Error(extractError(payload, response.statusText));
        }

        return payload;
    }

    function extractError(payload, fallback) {
        if (!payload) {
            return fallback || "Request failed.";
        }
        if (typeof payload === "string") {
            return payload;
        }
        if (Array.isArray(payload.details) && payload.details.length > 0) {
            return `${payload.message || fallback}: ${payload.details.join(", ")}`;
        }
        return payload.message || payload.error || fallback || "Request failed.";
    }

    async function loadSession() {
        const payload = await request("/api/auth/me", { method: "GET" }, { allowUnauthorized: true, skipCsrf: true });
        state.currentUser = payload ? normalizeUser(payload) : null;
        return state.currentUser;
    }

    function normalizeUser(payload) {
        return {
            id: payload.id || "",
            email: payload.email || "",
            role: payload.role || "USER",
            sessionIssuedAt: payload.sessionIssuedAt || "",
            sessionExpiresAt: payload.sessionExpiresAt || ""
        };
    }

    function formatDate(value) {
        if (!value) {
            return "-";
        }
        const date = new Date(value);
        if (Number.isNaN(date.getTime())) {
            return value;
        }
        return new Intl.DateTimeFormat(undefined, {
            year: "numeric",
            month: "short",
            day: "2-digit",
            hour: "2-digit",
            minute: "2-digit"
        }).format(date);
    }

    function showFlash(message, tone = "success") {
        const banner = document.getElementById("flash-banner");
        if (!banner) {
            return;
        }

        if (!message) {
            banner.textContent = "";
            banner.className = "flash-banner hidden";
            return;
        }

        banner.textContent = message;
        banner.className = `flash-banner ${tone}`;
    }

    function updateShellSession(user) {
        const pill = document.getElementById("session-pill");
        const email = document.getElementById("session-email");
        const role = document.getElementById("session-role");
        const issuedAt = document.getElementById("session-issued-at");
        const expiresAt = document.getElementById("session-expires-at");
        const logoutButton = document.getElementById("logout-button");

        if (pill) {
            pill.textContent = user ? user.role : "Guest";
        }
        if (email) {
            email.textContent = user?.email || "-";
        }
        if (role) {
            role.textContent = user?.role || "-";
        }
        if (issuedAt) {
            issuedAt.textContent = formatDate(user?.sessionIssuedAt);
        }
        if (expiresAt) {
            expiresAt.textContent = formatDate(user?.sessionExpiresAt);
        }
        if (logoutButton) {
            logoutButton.classList.toggle("hidden", !user);
        }
    }

    async function initializePage(configuration = {}) {
        const user = await loadSession();
        updateShellSession(user);
        bindLogout();

        if (configuration.authRequired && !user) {
            window.location.href = "/login.html";
            return null;
        }

        if (configuration.managerOnly && user?.role !== "MANAGER") {
            window.location.href = "/tickets.html";
            return null;
        }

        return user;
    }

    function bindLogout() {
        const logoutButton = document.getElementById("logout-button");
        if (!logoutButton || logoutButton.dataset.bound === "true") {
            return;
        }

        logoutButton.dataset.bound = "true";
        logoutButton.addEventListener("click", async () => {
            try {
                await request("/api/auth/logout", { method: "POST" });
            } finally {
                state.currentUser = null;
                state.csrfToken = "";
                window.location.href = "/login.html";
            }
        });
    }

    function escapeHtml(value) {
        return String(value)
            .replaceAll("&", "&amp;")
            .replaceAll("<", "&lt;")
            .replaceAll(">", "&gt;")
            .replaceAll("\"", "&quot;")
            .replaceAll("'", "&#39;");
    }

    function redirectAfterAuth(user) {
        window.location.href = "/tickets.html";
    }

    return {
        state,
        request,
        initializePage,
        loadSession,
        showFlash,
        formatDate,
        escapeHtml,
        redirectAfterAuth
    };
})();