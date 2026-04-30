document.addEventListener("DOMContentLoaded", async () => {
    const user = await window.AuthX.initializePage();
    if (user) {
        window.AuthX.redirectAfterAuth(user);
        return;
    }

    await window.AuthX.ensureCsrfToken();
    const form = document.getElementById("login-form");
    form.addEventListener("submit", async event => {
        event.preventDefault();
        try {
            const payload = await window.AuthX.request("/api/auth/login", {
                method: "POST",
                body: {
                    email: document.getElementById("email").value.trim(),
                    password: document.getElementById("password").value
                }
            });
            window.AuthX.showFlash("Login successful.");
            window.AuthX.redirectAfterAuth(payload);
        } catch (error) {
            window.AuthX.showFlash(error.message, "error");
        }
    });
});