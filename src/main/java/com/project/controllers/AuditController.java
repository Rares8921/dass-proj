package com.project.controllers;

import com.project.dto.responses.AuditResponse;
import com.project.services.AuditService;
import com.project.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final UserService userService;

    @GetMapping
    public ResponseEntity<List<AuditResponse>> getAll() {
        userService.requireManager();
        return ResponseEntity.ok(auditService.getAll());
    }
}
