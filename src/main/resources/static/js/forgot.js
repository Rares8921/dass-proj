document.addEventListener("DOMContentLoaded", async () => {
    await window.AuthX.initializePage();

    document.getElementById("forgot-form").addEventListener("submit", async event => {
        event.preventDefault();
        try {
            const payload = await window.AuthX.request("/api/auth/forgot-password", {
                method: "POST",
                body: {
                    email: document.getElementById("email").value.trim()
                }
            });
            window.AuthX.showFlash(payload.message || "Reset instructions sent.");
        } catch (error) {
            window.AuthX.showFlash(error.message, "error");
        }
    });
});