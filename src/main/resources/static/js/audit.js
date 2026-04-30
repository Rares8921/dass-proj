document.addEventListener("DOMContentLoaded", async () => {
    const user = await window.AuthX.initializePage({ authRequired: true, managerOnly: true });
    if (!user) {
        return;
    }

    const list = document.getElementById("audit-list");
    const empty = document.getElementById("audit-empty");
    document.getElementById("refresh-button").addEventListener("click", loadAudit);

    await loadAudit();

    async function loadAudit() {
        try {
            const logs = await window.AuthX.request("/api/audit", { method: "GET" });
            renderAudit(logs);
        } catch (error) {
            renderAudit([]);
            window.AuthX.showFlash(error.message, "error");
        }
    }

    function renderAudit(logs) {
        list.innerHTML = "";
        empty.classList.toggle("hidden", logs.length > 0);
        if (logs.length === 0) {
            return;
        }

        const fragment = document.createDocumentFragment();
        logs.forEach(log => {
            const card = document.createElement("article");
            card.className = "audit-card";
            card.innerHTML = `
                <div class="audit-header">
                    <strong>${window.AuthX.escapeHtml(log.action || "-")}</strong>
                    <span class="tag">${window.AuthX.escapeHtml(log.resource || "-")}</span>
                </div>
                <div class="audit-meta">
                    <span>User: ${window.AuthX.escapeHtml(log.userEmail || "anonymous")}</span>
                    <span>Ticket: ${window.AuthX.escapeHtml(log.ticketId || "-")}</span>
                    <span>Status: ${window.AuthX.escapeHtml(String(log.responseStatus ?? "-"))}</span>
                    <span>At: ${window.AuthX.formatDate(log.createdAt)}</span>
                </div>
                <div class="audit-meta">
                    <span>Method: ${window.AuthX.escapeHtml(log.requestMethod || "-")}</span>
                    <span>URI: ${window.AuthX.escapeHtml(log.requestUri || "-")}</span>
                    <span>Duration: ${window.AuthX.escapeHtml(String(log.durationMs ?? 0))} ms</span>
                    <span>IP: ${window.AuthX.escapeHtml(log.ipAddress || "-")}</span>
                </div>
                <div class="audit-details">
                    ${detailBlock("Request Headers", log.requestHeaders)}
                    ${detailBlock("Request Cookies", log.requestCookies)}
                    ${detailBlock("Request Parameters", log.requestParameters)}
                    ${detailBlock("Request Body", log.requestBody)}
                    ${detailBlock("Request Flags", log.requestFlags)}
                    ${detailBlock("Response Headers", log.responseHeaders)}
                    ${detailBlock("Response Body", log.responseBody)}
                    ${detailBlock("Response Flags", log.responseFlags)}
                </div>
            `;
            fragment.appendChild(card);
        });
        list.appendChild(fragment);
    }

    function detailBlock(label, value) {
        if (!value) {
            return "";
        }
        return `
            <details>
                <summary>${window.AuthX.escapeHtml(label)}</summary>
                <pre>${window.AuthX.escapeHtml(value)}</pre>
            </details>
        `;
    }
});