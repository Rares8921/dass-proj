package com.project.aspect;

import com.project.dto.requests.LoginRequest;
import com.project.dto.requests.RegisterRequest;
import com.project.dto.requests.ResetPasswordRequest;
import com.project.dto.responses.TicketResponse;
import com.project.models.Ticket;
import com.project.models.User;
import com.project.services.AuditService;
import com.project.services.UserService;
import com.project.utils.SessionUtils;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditService auditService;
    private final UserService userService;

    @Around("""
            execution(public * com.project.services.AuthService.register(..)) ||
            execution(public * com.project.services.AuthService.login(..)) ||
            execution(public * com.project.services.AuthService.forgotPassword(..)) ||
            execution(public * com.project.services.AuthService.resetPassword(..)) ||
            execution(public * com.project.services.AuthService.logout(..)) ||
            execution(public * com.project.services.TicketService.getAll(..)) ||
            execution(public * com.project.services.TicketService.getById(..)) ||
            execution(public * com.project.services.TicketService.search(..)) ||
            execution(public * com.project.services.TicketService.create(..)) ||
            execution(public * com.project.services.TicketService.update(..)) ||
            execution(public * com.project.services.TicketService.delete(..))
            """)
    public Object audit(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        HttpServletRequest request = findRequest(joinPoint.getArgs()).orElseGet(this::currentRequest);
        String ipAddress = request == null ? null : SessionUtils.resolveClientIp(request);

        User currentUser = currentUser();
        User tokenUser = resolveResetTokenUser(joinPoint.getArgs());
        User emailUser = resolveEmailUser(joinPoint.getArgs());

        try {
            Object result = joinPoint.proceed();
            switch (methodName) {
                case "register" -> {
                    User resultUser = resolveResultUser(result).orElse(emailUser);
                    auditService.record(resultUser, "REGISTER", "auth", resourceId(resultUser), ipAddress);
                }
                case "login" -> {
                    User resultUser = resolveResultUser(result).orElse(emailUser);
                    auditService.record(resultUser, "LOGIN_SUCCESS", "auth", resourceId(resultUser), ipAddress);
                }
                case "forgotPassword" -> auditService.record(emailUser, "FORGOT_PASSWORD_REQUEST", "auth", resourceId(emailUser), ipAddress);
                case "resetPassword" -> auditService.record(tokenUser, "RESET_PASSWORD", "auth", resourceId(tokenUser), ipAddress);
                case "logout" -> auditService.record(currentUser, "LOGOUT", "auth", resourceId(currentUser), ipAddress);
                case "getAll" -> auditService.record(currentUser, "LIST_TICKETS", "ticket", null, ipAddress);
                case "getById" -> auditService.record(currentUser, resolveTicket(firstUuid(joinPoint.getArgs())), "VIEW_TICKET", "ticket", firstUuid(joinPoint.getArgs()), ipAddress);
                case "search" -> auditService.record(currentUser, "SEARCH_TICKETS", "ticket", null, ipAddress);
                case "create" -> auditService.record(currentUser, resolveTicket(resultTicketId(result)), "CREATE_TICKET", "ticket", resultTicketId(result), ipAddress);
                case "update" -> auditService.record(currentUser, resolveTicket(firstUuid(joinPoint.getArgs())), "UPDATE_TICKET", "ticket", firstUuid(joinPoint.getArgs()), ipAddress);
                case "delete" -> auditService.record(currentUser, resolveTicket(firstUuid(joinPoint.getArgs())), "DELETE_TICKET", "ticket", firstUuid(joinPoint.getArgs()), ipAddress);
                default -> {
                }
            }
            return result;
        } catch (Throwable throwable) {
            switch (methodName) {
                case "login" -> auditService.record(emailUser, "LOGIN_FAILURE", "auth", resourceId(emailUser), ipAddress);
                case "register" -> auditService.record(emailUser, "REGISTER_FAILURE", "auth", resourceId(emailUser), ipAddress);
                case "resetPassword" -> auditService.record(tokenUser, "RESET_PASSWORD_FAILURE", "auth", resourceId(tokenUser), ipAddress);
                default -> {
                }
            }
            throw throwable;
        }
    }

    private Optional<HttpServletRequest> findRequest(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest request) {
                return Optional.of(request);
            }
        }
        return Optional.empty();
    }

    private User currentUser() {
        try {
            return userService.requireCurrentUser();
        } catch (Exception exception) {
            return null;
        }
    }

    private User resolveResetTokenUser(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ResetPasswordRequest request) {
                return userService.findByResetToken(request.getToken()).orElse(null);
            }
        }
        return null;
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }

    private User resolveEmailUser(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof LoginRequest request) {
                return userService.findByEmail(request.getEmail()).orElse(null);
            }
            if (arg instanceof RegisterRequest request) {
                return userService.findByEmail(request.getEmail()).orElse(null);
            }
            if (arg instanceof ResetPasswordRequest request && request.getEmail() != null) {
                return userService.findByEmail(request.getEmail()).orElse(null);
            }
        }
        return null;
    }

    private Optional<User> resolveResultUser(Object result) {
        if (!(result instanceof Map<?, ?> map)) {
            return Optional.empty();
        }
        Object idValue = map.get("id");
        if (idValue instanceof UUID id) {
            return userService.findById(id);
        }
        if (idValue instanceof String id) {
            try {
                return userService.findById(UUID.fromString(id));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private String resultTicketId(Object result) {
        if (result instanceof TicketResponse ticketResponse && ticketResponse.getId() != null) {
            return ticketResponse.getId().toString();
        }
        return null;
    }

    private String firstUuid(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid.toString();
            }
        }
        return null;
    }

    private String resourceId(User user) {
        return user == null || user.getId() == null ? null : user.getId().toString();
    }

    private Ticket resolveTicket(String ticketId) {
        if (ticketId == null || ticketId.isBlank()) {
            return null;
        }
        try {
            return auditService.findTicketById(UUID.fromString(ticketId)).orElse(null);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
