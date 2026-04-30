package com.project.dto.responses;

import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
public class AuditResponse {

    private UUID id;
    private UUID userId;
    private String userEmail;
    private UUID ticketId;
    private String action;
    private String resource;
    private String resourceId;
    private String ipAddress;
    private Instant createdAt;
    private String requestMethod;
    private String requestUri;
    private String queryString;
    private String requestHeaders;
    private String requestCookies;
    private String requestParameters;
    private String requestBody;
    private String requestContentType;
    private Integer responseStatus;
    private String responseHeaders;
    private String responseBody;
    private String responseContentType;
    private String requestFlags;
    private String responseFlags;
    private String userAgent;
    private String referer;
    private Boolean authenticated;
    private Boolean success;
    private Long durationMs;
}
