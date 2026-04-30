package com.project.models;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "audit_logs")
@Getter
@Setter
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ticket_id", foreignKey = @ForeignKey(name = "fk_audit_logs_ticket"))
    private Ticket ticket;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 80)
    private String resource;

    @Column(length = 64)
    private String resourceId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 16)
    private String requestMethod;

    @Column(length = 1024)
    private String requestUri;

    @Column(columnDefinition = "TEXT")
    private String queryString;

    @Column(columnDefinition = "TEXT")
    private String requestHeaders;

    @Column(columnDefinition = "TEXT")
    private String requestCookies;

    @Column(columnDefinition = "TEXT")
    private String requestParameters;

    @Column(columnDefinition = "TEXT")
    private String requestBody;

    @Column(length = 255)
    private String requestContentType;

    private Integer responseStatus;

    @Column(columnDefinition = "TEXT")
    private String responseHeaders;

    @Column(columnDefinition = "TEXT")
    private String responseBody;

    @Column(length = 255)
    private String responseContentType;

    @Column(columnDefinition = "TEXT")
    private String requestFlags;

    @Column(columnDefinition = "TEXT")
    private String responseFlags;

    @Column(length = 1024)
    private String userAgent;

    @Column(length = 1024)
    private String referer;

    private Boolean authenticated;

    private Boolean success;

    private Long durationMs;

    @PrePersist
    public void onCreate() {
        createdAt = Instant.now();
    }
}
