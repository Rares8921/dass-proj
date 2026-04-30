package com.project.dto.requests;

import com.project.models.enums.TicketStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TicketRequest {

    @NotBlank
    @Size(max = 180)
    private String title;

    @NotBlank
    @Size(max = 10000)
    private String description;

    @NotBlank
    @Size(max = 32)
    private String severity;

    @NotNull
    private TicketStatus status;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public TicketStatus getStatus() {
        return status;
    }

    public void setStatus(TicketStatus status) {
        this.status = status;
    }
}
