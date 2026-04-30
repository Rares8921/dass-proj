package com.project.services;

import com.project.dto.requests.TicketRequest;
import com.project.dto.responses.TicketResponse;
import com.project.exceptions.ResourceNotFoundException;
import com.project.models.Ticket;
import com.project.models.User;
import com.project.repository.TicketRepository;
import jakarta.servlet.http.HttpServletRequest;
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
    private final AuditService auditService;

    @Transactional
    public TicketResponse create(TicketRequest request, HttpServletRequest httpRequest) {
        User currentUser = userService.requireCurrentUser(httpRequest);

        Ticket ticket = new Ticket();
        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setSeverity(request.getSeverity());
        ticket.setStatus(request.getStatus());
        ticket.setOwner(currentUser);

        Ticket savedTicket = ticketRepository.save(ticket);
        auditService.record(currentUser, "CREATE_TICKET", "ticket", savedTicket.getId().toString(), httpRequest);
        return toResponse(savedTicket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getAll(HttpServletRequest httpRequest) {
        userService.requireCurrentUser(httpRequest);
        return ticketRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse getById(UUID id, HttpServletRequest httpRequest) {
        userService.requireCurrentUser(httpRequest);
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        return toResponse(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> search(String term, HttpServletRequest httpRequest) {
        userService.requireCurrentUser(httpRequest);
        String normalizedTerm = term == null || term.isBlank() ? null : term.trim();
        return ticketRepository.search(null, normalizedTerm)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TicketResponse update(UUID id, TicketRequest request, HttpServletRequest httpRequest) {
        User currentUser = userService.requireCurrentUser(httpRequest);
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        ticket.setTitle(request.getTitle());
        ticket.setDescription(request.getDescription());
        ticket.setSeverity(request.getSeverity());
        ticket.setStatus(request.getStatus());

        Ticket savedTicket = ticketRepository.save(ticket);
        auditService.record(currentUser, "UPDATE_TICKET", "ticket", savedTicket.getId().toString(), httpRequest);
        return toResponse(savedTicket);
    }

    @Transactional
    public void delete(UUID id, HttpServletRequest httpRequest) {
        User currentUser = userService.requireCurrentUser(httpRequest);
        Ticket ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));

        ticketRepository.delete(ticket);
        auditService.record(currentUser, "DELETE_TICKET", "ticket", id.toString(), httpRequest);
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
