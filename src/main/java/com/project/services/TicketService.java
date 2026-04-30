package com.project.services;

import com.project.dto.requests.TicketRequest;
import com.project.dto.responses.TicketResponse;
import com.project.exceptions.ResourceNotFoundException;
import com.project.models.Ticket;
import com.project.models.User;
import com.project.repository.TicketRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketService {

    private final TicketRepository ticketRepository;
    private final UserService userService;

    @Transactional
    public TicketResponse create(TicketRequest request) {
        User currentUser = userService.requireCurrentUser();
        Ticket ticket = new Ticket();
        applyRequest(ticket, request);
        ticket.setOwner(currentUser);
        return toResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public List<TicketResponse> getAll() {
        User currentUser = userService.requireCurrentUser();
        List<Ticket> tickets = userService.isManager(currentUser)
                ? ticketRepository.findAllByOrderByCreatedAtDesc()
                : ticketRepository.findAllByOwnerIdOrderByCreatedAtDesc(currentUser.getId());
        return tickets.stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TicketResponse getById(UUID id) {
        return toResponse(findAccessibleTicket(id, userService.requireCurrentUser()));
    }

    @Transactional
    public List<TicketResponse> search(String term) {
        User currentUser = userService.requireCurrentUser();
        String normalizedTerm = term == null || term.isBlank() ? null : term.trim();
        UUID ownerId = userService.isManager(currentUser) ? null : currentUser.getId();
        return ticketRepository.search(ownerId, normalizedTerm)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TicketResponse update(UUID id, TicketRequest request) {
        User currentUser = userService.requireCurrentUser();
        Ticket ticket = findAccessibleTicket(id, currentUser);
        applyRequest(ticket, request);
        return toResponse(ticketRepository.save(ticket));
    }

    @Transactional
    public void delete(UUID id) {
        User currentUser = userService.requireCurrentUser();
        ticketRepository.delete(findAccessibleTicket(id, currentUser));
    }

    private Ticket findAccessibleTicket(UUID id, User currentUser) {
        if (userService.isManager(currentUser)) {
            return ticketRepository.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        }
        return ticketRepository.findByIdAndOwnerId(id, currentUser.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
    }

    private void applyRequest(Ticket ticket, TicketRequest request) {
        ticket.setTitle(requiredValue(request.getTitle(), "Title is required"));
        ticket.setDescription(requiredValue(request.getDescription(), "Description is required"));
        ticket.setSeverity(normalizeSeverity(request.getSeverity()));
        if (request.getStatus() == null) {
            throw new IllegalArgumentException("Status is required");
        }
        ticket.setStatus(request.getStatus());
    }

    private String normalizeSeverity(String severity) {
        String value = requiredValue(severity, "Severity is required").trim().toUpperCase();
        if (!List.of("LOW", "MEDIUM", "HIGH", "CRITICAL").contains(value)) {
            throw new IllegalArgumentException("Invalid severity");
        }
        return value;
    }

    private String requiredValue(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private TicketResponse toResponse(Ticket ticket) {
        TicketResponse response = new TicketResponse();
        response.setId(ticket.getId());
        response.setTitle(ticket.getTitle());
        response.setDescription(ticket.getDescription());
        response.setSeverity(ticket.getSeverity());
        response.setStatus(ticket.getStatus());
        response.setCreatedAt(ticket.getCreatedAt());
        response.setUpdatedAt(ticket.getUpdatedAt());
        if (ticket.getOwner() != null) {
            response.setOwnerId(ticket.getOwner().getId());
            response.setOwnerEmail(ticket.getOwner().getEmail());
        }
        return response;
    }
}
