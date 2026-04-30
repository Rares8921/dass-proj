document.addEventListener("DOMContentLoaded", async () => {
    const user = await window.AuthX.initializePage({ authRequired: true });
    if (!user) {
        return;
    }

    const state = {
        tickets: [],
        searchTerm: ""
    };

    const form = document.getElementById("ticket-form");
    const resetButton = document.getElementById("reset-button");
    const refreshButton = document.getElementById("refresh-button");
    const clearSearchButton = document.getElementById("clear-search-button");
    const searchForm = document.getElementById("search-form");
    const list = document.getElementById("tickets-list");
    const empty = document.getElementById("tickets-empty");

    form.addEventListener("submit", handleSubmit);
    resetButton.addEventListener("click", resetForm);
    refreshButton.addEventListener("click", () => loadTickets(state.searchTerm));
    clearSearchButton.addEventListener("click", () => {
        document.getElementById("search-term").value = "";
        state.searchTerm = "";
        loadTickets();
    });
    searchForm.addEventListener("submit", async event => {
        event.preventDefault();
        state.searchTerm = document.getElementById("search-term").value.trim();
        await loadTickets(state.searchTerm);
    });

    await loadTickets();

    async function handleSubmit(event) {
        event.preventDefault();
        const ticketId = document.getElementById("ticket-id").value.trim();
        const endpoint = ticketId ? `/api/tickets/${ticketId}` : "/api/tickets";
        const method = ticketId ? "PUT" : "POST";

        try {
            await window.AuthX.request(endpoint, {
                method,
                body: {
                    title: document.getElementById("title").value.trim(),
                    description: document.getElementById("description").value.trim(),
                    severity: document.getElementById("severity").value,
                    status: document.getElementById("status").value
                }
            });
            window.AuthX.showFlash(ticketId ? "Ticket updated successfully." : "Ticket created successfully.");
            resetForm();
            await loadTickets(state.searchTerm);
        } catch (error) {
            window.AuthX.showFlash(error.message, "error");
        }
    }

    async function loadTickets(term = "") {
        try {
            const endpoint = term ? `/api/tickets/search?term=${encodeURIComponent(term)}` : "/api/tickets";
            state.tickets = await window.AuthX.request(endpoint, { method: "GET" });
            renderTickets();
        } catch (error) {
            state.tickets = [];
            renderTickets();
            window.AuthX.showFlash(error.message, "error");
        }
    }

    function renderTickets() {
        list.innerHTML = "";
        empty.classList.toggle("hidden", state.tickets.length > 0);
        if (state.tickets.length === 0) {
            return;
        }

        const fragment = document.createDocumentFragment();
        state.tickets.forEach(ticket => {
            const card = document.createElement("article");
            card.className = "ticket-card";
            card.innerHTML = `
                <div class="ticket-header">
                    <div class="ticket-title">${window.AuthX.escapeHtml(ticket.title || "")}</div>
                    <div class="tag-row">
                        <span class="tag">${window.AuthX.escapeHtml(ticket.severity || "-")}</span>
                        <span class="tag">${window.AuthX.escapeHtml(ticket.status || "-")}</span>
                    </div>
                </div>
                <div class="ticket-copy">${window.AuthX.escapeHtml(ticket.description || "")}</div>
                <div class="ticket-meta">
                    <span>Owner: ${window.AuthX.escapeHtml(ticket.ownerEmail || "-")}</span>
                    <span>Updated: ${window.AuthX.formatDate(ticket.updatedAt || ticket.createdAt)}</span>
                </div>
                <div class="ticket-actions">
                    <button class="button secondary" type="button" data-action="edit" data-id="${ticket.id}">Edit</button>
                    <button class="button secondary" type="button" data-action="delete" data-id="${ticket.id}">Delete</button>
                </div>
            `;
            fragment.appendChild(card);
        });
        list.appendChild(fragment);

        list.querySelectorAll("[data-action='edit']").forEach(button => {
            button.addEventListener("click", () => {
                const ticket = state.tickets.find(item => item.id === button.dataset.id);
                if (!ticket) {
                    return;
                }
                document.getElementById("ticket-id").value = ticket.id;
                document.getElementById("title").value = ticket.title || "";
                document.getElementById("description").value = ticket.description || "";
                document.getElementById("severity").value = ticket.severity || "LOW";
                document.getElementById("status").value = ticket.status || "OPEN";
                document.getElementById("submit-button").textContent = "Update Ticket";
            });
        });

        list.querySelectorAll("[data-action='delete']").forEach(button => {
            button.addEventListener("click", async () => {
                try {
                    await window.AuthX.request(`/api/tickets/${button.dataset.id}`, { method: "DELETE" });
                    window.AuthX.showFlash("Ticket deleted successfully.");
                    if (document.getElementById("ticket-id").value === button.dataset.id) {
                        resetForm();
                    }
                    await loadTickets(state.searchTerm);
                } catch (error) {
                    window.AuthX.showFlash(error.message, "error");
                }
            });
        });
    }

    function resetForm() {
        form.reset();
        document.getElementById("ticket-id").value = "";
        document.getElementById("status").value = "OPEN";
        document.getElementById("severity").value = "LOW";
        document.getElementById("submit-button").textContent = "Create Ticket";
    }
});