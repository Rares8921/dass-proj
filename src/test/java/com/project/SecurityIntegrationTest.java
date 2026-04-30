package com.project;

import com.project.config.RateLimitingService;
import com.project.models.User;
import com.project.models.enums.Role;
import com.project.repository.AuditLogRepository;
import com.project.repository.TicketRepository;
import com.project.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityIntegrationTest {

    private static final String SESSION_COOKIE = "AUTH_SESSION";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TicketRepository ticketRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private RateLimitingService rateLimitingService;

    @MockBean
    private JavaMailSender javaMailSender;

    @BeforeEach
    void setUp() {
        ticketRepository.deleteAll();
        auditLogRepository.deleteAll();
        rateLimitingService.clearAll();
        reset(javaMailSender);
        resetSeedUser("manager@authx.local", "Manager123!", Role.MANAGER);
        resetSeedUser("analyst@authx.local", "Analyst123!", Role.ANALYST);
        userRepository.findAll()
                .stream()
                .filter(user -> !List.of("manager@authx.local", "analyst@authx.local").contains(user.getEmail()))
                .forEach(userRepository::delete);
    }

    @Test
    void loginReturnsGenericErrorForUnknownUserAndWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"missing@authx.local","password":"WrongPassword123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        mockMvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"analyst@authx.local","password":"WrongPassword123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));
    }

    @Test
    void locksAccountAfterRepeatedFailures() throws Exception {
        for (int index = 0; index < 5; index++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"analyst@authx.local","password":"WrongPassword123!"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        rateLimitingService.clearAll();

        mockMvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"analyst@authx.local","password":"Analyst123!"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid credentials"));

        User user = userRepository.findByEmailIgnoreCase("analyst@authx.local").orElseThrow();
        assertThat(user.isLocked()).isTrue();
        assertThat(user.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void forgotPasswordSendsMailAndResetTokenIsOneTimeUse() throws Exception {
        registerUser("reset.user@authx.local", "ResetPassword123!");

        mockMvc.perform(post("/api/auth/forgot-password")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"reset.user@authx.local"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("If the account exists, reset instructions were sent."));

        User user = userRepository.findByEmailIgnoreCase("reset.user@authx.local").orElseThrow();
        assertThat(user.getResetPasswordToken()).isNotBlank();
        assertThat(user.getResetPasswordTokenExpiresAt()).isAfter(Instant.now());

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(javaMailSender, atLeastOnce()).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getTo()).contains("reset.user@authx.local");
        assertThat(messageCaptor.getValue().getText()).contains(user.getResetPasswordToken());

        mockMvc.perform(post("/api/auth/reset-password")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"BetterPassword123!"}
                                """.formatted(user.getResetPasswordToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully"));

        mockMvc.perform(post("/api/auth/reset-password")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"AnotherPassword123!"}
                                """.formatted(user.getResetPasswordToken())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void analystCannotAccessAnotherUsersTicket() throws Exception {
        String ownerCookie = registerUserAndExtractSession("owner@authx.local", "OwnerPassword123!");
        String attackerCookie = registerUserAndExtractSession("attacker@authx.local", "AttackerPassword123!");

        MvcResult createResult = mockMvc.perform(post("/api/tickets")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new Cookie(SESSION_COOKIE, ownerCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Private incident","description":"Only owner access","severity":"HIGH","status":"OPEN"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andReturn();

        String ticketId = JsonHelper.readString(createResult.getResponse().getContentAsString(), "id");

        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .cookie(new Cookie(SESSION_COOKIE, attackerCookie)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/tickets/" + ticketId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new Cookie(SESSION_COOKIE, attackerCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Tampered","description":"Tampered","severity":"LOW","status":"OPEN"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/tickets/" + ticketId)
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new Cookie(SESSION_COOKIE, attackerCookie)))
                .andExpect(status().isNotFound());
    }

    @Test
    void managerCanViewAuditTrail() throws Exception {
        String managerCookie = loginAndExtractSession("manager@authx.local", "Manager123!");

        mockMvc.perform(get("/api/audit")
                        .cookie(new Cookie(SESSION_COOKIE, managerCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void stateChangingRequestRequiresCsrfToken() throws Exception {
        String analystCookie = loginAndExtractSession("analyst@authx.local", "Analyst123!");

        mockMvc.perform(post("/api/tickets")
                        .cookie(new Cookie(SESSION_COOKIE, analystCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Blocked","description":"No csrf","severity":"LOW","status":"OPEN"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void analystCannotViewAuditTrailAndDeniedAccessIsAudited() throws Exception {
        String analystCookie = loginAndExtractSession("analyst@authx.local", "Analyst123!");
        auditLogRepository.deleteAll();

        mockMvc.perform(get("/api/audit")
                        .cookie(new Cookie(SESSION_COOKIE, analystCookie)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Access denied"));

        assertThat(auditLogRepository.findAll())
                .extracting("action")
                .contains("ACCESS_DENIED");
    }

    @Test
    void sessionCookieUsesHttpOnlyAndStrictSameSite() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"analyst@authx.local","password":"Analyst123!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")));
    }

    @Test
    void rootResponseIncludesSecurityHeaders() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'self'")))
                .andExpect(header().string("Permissions-Policy", containsString("geolocation=()")))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"));
    }

    @Test
    void expiredSessionIsRejectedCookieClearedAndAuditRecorded() throws Exception {
        String analystCookie = loginAndExtractSession("analyst@authx.local", "Analyst123!");
        User analyst = userRepository.findByEmailIgnoreCase("analyst@authx.local").orElseThrow();
        analyst.setSessionExpiresAt(Instant.now().minusSeconds(60));
        userRepository.save(analyst);
        auditLogRepository.deleteAll();

        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie(SESSION_COOKIE, analystCookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        assertThat(auditLogRepository.findAll())
                .extracting("action")
                .contains("SESSION_INVALIDATED", "AUTHENTICATION_REQUIRED");
    }

    @Test
    void loginRateLimitReturnsTooManyRequests() throws Exception {
        for (int index = 0; index < 5; index++) {
            mockMvc.perform(post("/api/auth/login")
                            .with(SecurityMockMvcRequestPostProcessors.csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email":"burst@authx.local","password":"WrongPassword123!"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"burst@authx.local","password":"WrongPassword123!"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Too many requests"));
    }

    @Test
    void ticketListingScopeDiffersForManagerAndAnalyst() throws Exception {
        String managerCookie = loginAndExtractSession("manager@authx.local", "Manager123!");
        String ownerCookie = registerUserAndExtractSession("owner.scope@authx.local", "OwnerScope123!");
        String analystCookie = registerUserAndExtractSession("analyst.scope@authx.local", "AnalystScope123!");

        mockMvc.perform(post("/api/tickets")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new Cookie(SESSION_COOKIE, ownerCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Owner scope","description":"Owner only","severity":"HIGH","status":"OPEN"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/tickets")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new Cookie(SESSION_COOKIE, analystCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Analyst scope","description":"Analyst only","severity":"LOW","status":"OPEN"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tickets")
                        .cookie(new Cookie(SESSION_COOKIE, managerCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));

        mockMvc.perform(get("/api/tickets")
                        .cookie(new Cookie(SESSION_COOKIE, analystCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].ownerEmail").value("analyst.scope@authx.local"));
    }

    @Test
    void searchViewAndLogoutAreAudited() throws Exception {
        String analystCookie = registerUserAndExtractSession("audit.user@authx.local", "AuditUser123!");

        MvcResult createResult = mockMvc.perform(post("/api/tickets")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new Cookie(SESSION_COOKIE, analystCookie))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Audit path","description":"Search and view","severity":"MEDIUM","status":"OPEN"}
                                """))
                .andExpect(status().isOk())
                .andReturn();

        String ticketId = JsonHelper.readString(createResult.getResponse().getContentAsString(), "id");
        auditLogRepository.deleteAll();

        mockMvc.perform(get("/api/tickets/search?term=Audit")
                        .cookie(new Cookie(SESSION_COOKIE, analystCookie)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/tickets/" + ticketId)
                        .cookie(new Cookie(SESSION_COOKIE, analystCookie)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/auth/logout")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new Cookie(SESSION_COOKIE, analystCookie)))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.findAll())
                .extracting("action")
                .contains("SEARCH_TICKETS", "VIEW_TICKET", "LOGOUT");
    }

    private void resetSeedUser(String email, String rawPassword, Role role) {
        User user = userRepository.findByEmailIgnoreCase(email).orElseThrow();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(role);
        user.setFailedLoginAttempts(0);
        user.setLocked(false);
        user.setLockedUntil(null);
        user.setResetPasswordToken(null);
        user.setResetPasswordTokenExpiresAt(null);
        user.setSessionToken(null);
        user.setSessionIssuedAt(null);
        user.setSessionExpiresAt(null);
        userRepository.save(user);
    }

    private void registerUser(String email, String password) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk());
    }

    private String registerUserAndExtractSession(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return extractCookieValue(result, SESSION_COOKIE);
    }

    private String loginAndExtractSession(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .with(SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn();
        return extractCookieValue(result, SESSION_COOKIE);
    }

    private String extractCookieValue(MvcResult result, String name) {
        return result.getResponse()
                .getHeaders(HttpHeaders.SET_COOKIE)
                .stream()
                .filter(header -> header.startsWith(name + "="))
                .findFirst()
                .map(header -> header.substring((name + "=").length(), header.indexOf(';')))
                .orElseThrow();
    }

    private static final class JsonHelper {

        private static String readString(String json, String field) {
            String marker = "\"" + field + "\":\"";
            int start = json.indexOf(marker);
            if (start < 0) {
                return "";
            }
            int valueStart = start + marker.length();
            int end = json.indexOf('"', valueStart);
            return end < 0 ? "" : json.substring(valueStart, end);
        }
    }
}
