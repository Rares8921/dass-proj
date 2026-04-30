package com.project.services;

import com.project.dto.responses.AuditResponse;
import com.project.models.AuditLog;
import com.project.models.Ticket;
import com.project.models.User;
import com.project.repository.AuditLogRepository;
import com.project.repository.TicketRepository;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;
    private final TicketRepository ticketRepository;

    @Transactional
    public void record(User user, String action, String resource, String resourceId, String ipAddress) {
        record(user, null, action, resource, resourceId, ipAddress);
    }

    @Transactional
    public void record(User user, Ticket ticket, String action, String resource, String resourceId, String ipAddress) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);
        auditLog.setTicket(ticket);
        auditLog.setAction(action);
        auditLog.setResource(resource);
        auditLog.setResourceId(resourceId);
        auditLog.setIpAddress(ipAddress);
        auditLogRepository.save(auditLog);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordHttpRequest(User user,
                                  Ticket ticket,
                                  String action,
                                  String resource,
                                  String resourceId,
                                  String ipAddress,
                                  HttpServletRequest request,
                                  ContentCachingResponseWrapper response,
                                  String requestHeaders,
                                  String requestCookies,
                                  String requestParameters,
                                  String requestBody,
                                  String responseHeaders,
                                  String responseBody,
                                  String requestFlags,
                                  String responseFlags,
                                  long durationMs) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);
        auditLog.setTicket(ticket);
        auditLog.setAction(action);
        auditLog.setResource(resource);
        auditLog.setResourceId(resourceId);
        auditLog.setIpAddress(ipAddress);
        auditLog.setRequestMethod(request.getMethod());
        auditLog.setRequestUri(request.getRequestURI());
        auditLog.setQueryString(request.getQueryString());
        auditLog.setRequestHeaders(requestHeaders);
        auditLog.setRequestCookies(requestCookies);
        auditLog.setRequestParameters(requestParameters);
        auditLog.setRequestBody(requestBody);
        auditLog.setRequestContentType(request.getContentType());
        auditLog.setResponseStatus(response.getStatus());
        auditLog.setResponseHeaders(responseHeaders);
        auditLog.setResponseBody(responseBody);
        auditLog.setResponseContentType(response.getContentType());
        auditLog.setRequestFlags(requestFlags);
        auditLog.setResponseFlags(responseFlags);
        auditLog.setUserAgent(request.getHeader(HttpHeaders.USER_AGENT));
        auditLog.setReferer(request.getHeader(HttpHeaders.REFERER));
        auditLog.setAuthenticated(user != null);
        auditLog.setSuccess(response.getStatus() >= 200 && response.getStatus() < 400);
        auditLog.setDurationMs(durationMs);
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> getAll() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<Ticket> findTicketById(UUID ticketId) {
        return ticketRepository.findById(ticketId);
    }

    private AuditResponse toResponse(AuditLog auditLog) {
        AuditResponse response = new AuditResponse();
        response.setId(auditLog.getId());
        response.setAction(auditLog.getAction());
        response.setResource(auditLog.getResource());
        response.setResourceId(auditLog.getResourceId());
        response.setIpAddress(auditLog.getIpAddress());
        response.setCreatedAt(auditLog.getCreatedAt());
        response.setRequestMethod(auditLog.getRequestMethod());
        response.setRequestUri(auditLog.getRequestUri());
        response.setQueryString(auditLog.getQueryString());
        response.setRequestHeaders(auditLog.getRequestHeaders());
        response.setRequestCookies(auditLog.getRequestCookies());
        response.setRequestParameters(auditLog.getRequestParameters());
        response.setRequestBody(auditLog.getRequestBody());
        response.setRequestContentType(auditLog.getRequestContentType());
        response.setResponseStatus(auditLog.getResponseStatus());
        response.setResponseHeaders(auditLog.getResponseHeaders());
        response.setResponseBody(auditLog.getResponseBody());
        response.setResponseContentType(auditLog.getResponseContentType());
        response.setRequestFlags(auditLog.getRequestFlags());
        response.setResponseFlags(auditLog.getResponseFlags());
        response.setUserAgent(auditLog.getUserAgent());
        response.setReferer(auditLog.getReferer());
        response.setAuthenticated(auditLog.getAuthenticated());
        response.setSuccess(auditLog.getSuccess());
        response.setDurationMs(auditLog.getDurationMs());

        if (auditLog.getUser() != null) {
            response.setUserId(auditLog.getUser().getId());
            response.setUserEmail(auditLog.getUser().getEmail());
        }

        if (auditLog.getTicket() != null) {
            response.setTicketId(auditLog.getTicket().getId());
        }

        return response;
    }
}
