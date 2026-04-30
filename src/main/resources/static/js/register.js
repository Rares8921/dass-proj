document.addEventListener("DOMContentLoaded", async () => {
    const user = await window.AuthX.initializePage();
    if (user) {
        window.AuthX.redirectAfterAuth(user);
        return;
    }

    const form = document.getElementById("register-form");
    form.addEventListener("submit", async event => {
        event.preventDefault();
        try {
            const payload = await window.AuthX.request("/api/auth/register", {
                method: "POST",
                body: {
                    email: document.getElementById("email").value.trim(),
                    password: document.getElementById("password").value
                }
            });
            window.AuthX.showFlash("Account created successfully.");
            window.AuthX.redirectAfterAuth(payload);
        } catch (error) {
            window.AuthX.showFlash(error.message, "error");
        }
    });
});