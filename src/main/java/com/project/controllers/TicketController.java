package com.project.controllers;

import com.project.dto.requests.TicketRequest;
import com.project.dto.responses.TicketResponse;
import com.project.services.TicketService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;

    @GetMapping
    public ResponseEntity<List<TicketResponse>> getAll(HttpServletRequest request) {
        return ResponseEntity.ok(ticketService.getAll(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TicketResponse> getById(@PathVariable UUID id, HttpServletRequest request) {
        return ResponseEntity.ok(ticketService.getById(id, request));
    }

    @GetMapping("/search")
    public ResponseEntity<List<TicketResponse>> search(@RequestParam(required = false) String term,
                                                       HttpServletRequest request) {
        return ResponseEntity.ok(ticketService.search(term, request));
    }

    @PostMapping
    public ResponseEntity<TicketResponse> create(@Valid @RequestBody TicketRequest request,
                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ticketService.create(request, httpRequest));
    }

    @PutMapping("/{id}")
    public ResponseEntity<TicketResponse> update(@PathVariable UUID id,
                                                 @Valid @RequestBody TicketRequest request,
                                                 HttpServletRequest httpRequest) {
        return ResponseEntity.ok(ticketService.update(id, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request) {
        ticketService.delete(id, request);
        return ResponseEntity.noContent().build();
    }
}
