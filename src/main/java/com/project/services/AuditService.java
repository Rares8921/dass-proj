package com.project.services;

import com.project.dto.responses.AuditResponse;
import com.project.models.AuditLog;
import com.project.models.User;
import com.project.repository.AuditLogRepository;
import com.project.utils.SessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    public void record(User user, String action, String resource, String resourceId, HttpServletRequest request) {
        AuditLog auditLog = new AuditLog();
        auditLog.setUser(user);
        auditLog.setAction(action);
        auditLog.setResource(resource);
        auditLog.setResourceId(resourceId);
        auditLog.setIpAddress(SessionUtils.resolveClientIp(request));
        auditLogRepository.save(auditLog);
    }

    @Transactional(readOnly = true)
    public List<AuditResponse> getAll() {
        return auditLogRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private AuditResponse toResponse(AuditLog auditLog) {
        AuditResponse response = new AuditResponse();
        response.setId(auditLog.getId());
        response.setAction(auditLog.getAction());
        response.setResource(auditLog.getResource());
        response.setResourceId(auditLog.getResourceId());
        response.setIpAddress(auditLog.getIpAddress());
        response.setCreatedAt(auditLog.getCreatedAt());
        if (auditLog.getUser() != null) {
            response.setUserId(auditLog.getUser().getId());
            response.setUserEmail(auditLog.getUser().getEmail());
        }
        return response;
    }
}
