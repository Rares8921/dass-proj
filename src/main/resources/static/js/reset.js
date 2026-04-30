document.addEventListener("DOMContentLoaded", async () => {
    await window.AuthX.initializePage();

    const tokenInput = document.getElementById("token");
    const urlToken = new URLSearchParams(window.location.search).get("token");
    if (urlToken) {
        tokenInput.value = urlToken;
    }

    document.getElementById("reset-form").addEventListener("submit", async event => {
        event.preventDefault();
        try {
            const payload = await window.AuthX.request("/api/auth/reset-password", {
                method: "POST",
                body: {
                    token: tokenInput.value.trim(),
                    newPassword: document.getElementById("new-password").value
                }
            });
            window.AuthX.showFlash(payload.message || "Password updated.");
        } catch (error) {
            window.AuthX.showFlash(error.message, "error");
        }
    });
});