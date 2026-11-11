package com.example.CMS;

import com.example.CMS.Common.GlobalConstants.Role;
import com.example.CMS.Common.IdEncryptor;
import com.example.CMS.Model.User;
import com.example.CMS.Model.PasswordResetOtp;
import com.example.CMS.Repository.ArticleRepository;
import com.example.CMS.Repository.PasswordResetOtpRepository;
import com.example.CMS.Services.MailService;
import com.example.CMS.Repository.ReviewsRepository;
import com.example.CMS.Repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CmsApplicationTests {

    @Autowired MockMvc mvc;
    @Autowired UserRepository userRepository;
    @Autowired ArticleRepository articleRepository;
    @Autowired ReviewsRepository reviewsRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired IdEncryptor idEncryptor;
    @Autowired ObjectMapper json;
    @Autowired PasswordResetOtpRepository otpRepository;
    @MockitoBean MailService mailService;

    @BeforeEach
    void seed() {
        reviewsRepository.deleteAll();
        articleRepository.deleteAll();
        otpRepository.deleteAll();
        userRepository.deleteAll();
        saveUser("admin", Role.ADMIN);
        saveUser("writer", Role.WRITER);
        saveUser("editor", Role.EDITOR);
    }

    private void saveUser(String username, Role role) {
        User u = new User();
        u.setName(username);
        u.setUsername(username);
        u.setEmail(username + "@example.com");
        u.setPassword(passwordEncoder.encode("password123"));
        u.setRole(role);
        userRepository.save(u);
    }

    private String login(String username) throws Exception {
        MvcResult r = mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"password123\"}"))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(r.getResponse().getContentAsString()).get("token").asText();
    }

    private MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder b, String body) {
        return b.header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    void contextLoads() {
    }

    @Test
    void loginRejectsBadPasswordAndMissingFields() throws Exception {
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"nope\"}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\"}")).andExpect(status().isBadRequest());
    }

    @Test
    void anonymousAndGarbageTokensGet401() throws Exception {
        mvc.perform(get("/editor/article/list")).andExpect(status().isUnauthorized());
        mvc.perform(get("/editor/article/list").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rolesAreEnforced() throws Exception {
        mvc.perform(get("/admin/user/list").header("Authorization", "Bearer " + login("writer")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/editor/article/list").header("Authorization", "Bearer " + login("writer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void userListNeverLeaksPasswordHash() throws Exception {
        MvcResult r = mvc.perform(get("/admin/user/list").header("Authorization", "Bearer " + login("admin")))
                .andExpect(status().isOk()).andReturn();
        String body = r.getResponse().getContentAsString();
        assertFalse(body.contains("password"), body);
        assertFalse(body.contains("$2a$"), body);
    }

    @Test
    void adminCanCreateGetAndDeleteUsers() throws Exception {
        String admin = login("admin");
        String body = "{\"name\":\"Bob\",\"username\":\"bob\",\"email\":\"bob@example.com\",\"role\":\"WRITER\"}";
        mvc.perform(as(admin, post("/admin/user/store"), body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.generatedPassword").isNotEmpty());
        // duplicate username/email -> 409, bad input -> 400
        mvc.perform(as(admin, post("/admin/user/store"), body)).andExpect(status().isConflict());
        mvc.perform(as(admin, post("/admin/user/store"), "{\"name\":\"x\"}")).andExpect(status().isBadRequest());

        Long id = userRepository.findByUsername("bob").orElseThrow().getUserId();
        mvc.perform(as(admin, post("/admin/user/detail/" + id), "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("bob"));
        mvc.perform(delete("/admin/user/delete/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        mvc.perform(delete("/admin/user/delete/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetPasswordRequiresAuthAndOwnership() throws Exception {
        String body = "{\"username\":\"editor\",\"password\":\"brand-new-pass\"}";
        mvc.perform(post("/reset-password").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(as(login("writer"), post("/reset-password"), body)).andExpect(status().isForbidden());
        mvc.perform(as(login("editor"), post("/reset-password"), body)).andExpect(status().isOk());
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"editor\",\"password\":\"brand-new-pass\"}")).andExpect(status().isOk());
        // the frontend sends only the new password: it applies to the caller
        mvc.perform(as(login("writer"), post("/reset-password"), "{\"password\":\"writer-new-pass\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.message").value("Change password successfully"));
        mvc.perform(post("/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"writer\",\"password\":\"writer-new-pass\"}")).andExpect(status().isOk());
        // too-short password is rejected
        mvc.perform(as(login("admin"), post("/reset-password"), "{\"username\":\"editor\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest());
    }

    private String idOf(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString()).get("data").get("id").asText();
    }

    @Test
    void fullArticleWorkflow() throws Exception {
        String writer = login("writer");
        String editor = login("editor");
        String admin = login("admin");

        // Drafts may be incomplete; client-supplied status/user are ignored; both field names work.
        MvcResult saved = mvc.perform(as(writer, post("/writer/article/save-draft"),
                "{\"articleID\":99,\"status\":3,\"title\":\"T\",\"abstract\":\"\",\"content\":\"\"}"))
                .andExpect(status().isOk()).andReturn();
        String id = idOf(saved);
        assertEquals(1, articleRepository.count());
        assertEquals(0, articleRepository.findAll().get(0).getStatus().ordinal());
        assertNotEquals(99L, articleRepository.findAll().get(0).getArticleID());

        // Editing the same draft (by id, using "summary") updates it instead of creating another one
        mvc.perform(as(writer, post("/writer/article/save-draft"),
                "{\"id\":\"" + id + "\",\"summary\":\"S\",\"content\":\"C\",\"notes\":\"n1\"}"))
                .andExpect(status().isOk());
        assertEquals(1, articleRepository.count());

        // The writer sees it; the editor does not (still a private draft)
        mvc.perform(get("/writer/article/list").header("Authorization", "Bearer " + writer))
                .andExpect(jsonPath("$.data[0].encryptedId").isNotEmpty())
                .andExpect(jsonPath("$.data[0].id").doesNotExist())
                .andExpect(jsonPath("$.data[0].status").value(0))
                .andExpect(jsonPath("$.data[0].authorName").value("writer"))
                .andExpect(jsonPath("$.data[0].viewsList").isArray());
        mvc.perform(get("/editor/article/list").header("Authorization", "Bearer " + editor))
                .andExpect(jsonPath("$.data").isEmpty());
        mvc.perform(as(editor, post("/editor/article/detail"), "{\"id\":\"" + id + "\"}"))
                .andExpect(status().isNotFound());

        // Other writers cannot touch it
        saveUser("writer2", Role.WRITER);
        mvc.perform(as(login("writer2"), post("/writer/article/detail"), "{\"id\":\"" + id + "\"}"))
                .andExpect(status().isNotFound());

        // Sending needs every field
        mvc.perform(as(writer, post("/writer/article/send-to-editor"), "{\"id\":\"" + id + "\",\"title\":\"\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(as(writer, post("/writer/article/send-to-editor"), "{\"id\":\"" + id + "\",\"title\":\"T\"}"))
                .andExpect(status().isOk());
        assertEquals(1, articleRepository.findAll().get(0).getStatus().ordinal()); // PENDING
        // ...and then it is locked for the writer
        mvc.perform(as(writer, post("/writer/article/save-draft"), "{\"id\":\"" + id + "\",\"title\":\"X\"}"))
                .andExpect(status().isConflict());

        // Editor sees it, can read it, and reviews it
        mvc.perform(get("/editor/article/list").header("Authorization", "Bearer " + editor))
                .andExpect(jsonPath("$.data[0].status").value(1)).andExpect(jsonPath("$.data[0].notes").value("n1"));
        mvc.perform(as(editor, post("/editor/article/detail"), "{\"id\":\"" + id + "\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("T"));
        mvc.perform(as(editor, post("/editor/article/detail"), "{\"id\":\"garbage\"}")).andExpect(status().isBadRequest());
        mvc.perform(as(editor, post("/editor/article/detail"),
                "{\"id\":\"" + idEncryptor.encrypt(12345L) + "\"}")).andExpect(status().isNotFound());

        mvc.perform(as(editor, post("/editor/article/review"),
                "{\"id\":\"" + id + "\",\"article\":{\"status\":0}}")).andExpect(status().isBadRequest());
        mvc.perform(as(editor, post("/editor/article/review"), "{\"id\":\"" + id + "\",\"comments\":\"fix typos\","
                + "\"notes\":\"n2\",\"article\":{\"title\":\"T2\",\"abstract\":\"\",\"content\":\"\",\"status\":4}}"))
                .andExpect(status().isOk());
        assertEquals(4, articleRepository.findAll().get(0).getStatus().ordinal()); // RETURNED
        assertEquals("T2", articleRepository.findAll().get(0).getTitle());
        assertEquals("S", articleRepository.findAll().get(0).getAbstract()); // blank edit did not wipe it

        // Writer sees the editor's comment and can fix and resend; then the editor publishes
        mvc.perform(as(writer, post("/writer/article/detail"), "{\"id\":\"" + id + "\"}"))
                .andExpect(jsonPath("$.data.comments").value("fix typos"))
                .andExpect(jsonPath("$.data.reviewerName").value("editor"))
                .andExpect(jsonPath("$.data.status").value(4));
        mvc.perform(as(writer, post("/writer/article/send-to-editor"), "{\"id\":\"" + id + "\",\"title\":\"T3\"}"))
                .andExpect(status().isOk());
        mvc.perform(as(editor, post("/editor/article/review"),
                "{\"id\":\"" + id + "\",\"article\":{\"status\":3}}")).andExpect(status().isOk());
        assertEquals(3, articleRepository.findAll().get(0).getStatus().ordinal()); // PUBLISHED
        assertEquals(2, reviewsRepository.count());
        // finalised articles cannot be reviewed again
        mvc.perform(as(editor, post("/editor/article/review"),
                "{\"id\":\"" + id + "\",\"article\":{\"status\":4}}")).andExpect(status().isConflict());

        // Admin can list and open everything
        mvc.perform(get("/admin/article/list").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data[0].status").value(3));
        mvc.perform(as(admin, post("/admin/article/detail/" + id), ""))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.title").value("T3"));
        mvc.perform(get("/admin/article/list").header("Authorization", "Bearer " + writer))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanEditAccounts() throws Exception {
        String admin = login("admin");
        Long id = userRepository.findByUsername("writer").orElseThrow().getUserId();
        mvc.perform(as(admin, put("/admin/user/edit-account"),
                "{\"userId\":" + id + ",\"name\":\"New\",\"username\":\"writer\",\"email\":\"w2@example.com\",\"role\":\"EDITOR\"}"))
                .andExpect(status().isOk());
        User u = userRepository.findById(id).orElseThrow();
        assertEquals("New", u.getName());
        assertEquals(Role.EDITOR, u.getRole());
        assertTrue(passwordEncoder.matches("password123", u.getPassword())); // password untouched
        // collisions with other accounts are rejected
        mvc.perform(as(admin, put("/admin/user/edit-account"),
                "{\"userId\":" + id + ",\"name\":\"New\",\"username\":\"editor\",\"email\":\"w2@example.com\",\"role\":\"EDITOR\"}"))
                .andExpect(status().isConflict());
        mvc.perform(as(login("writer"), put("/admin/user/edit-account"), "{}")).andExpect(status().isForbidden());
    }

    // ---- Forgot password (OTP by email) ----

    private void postJson(String path, String body, int expectedStatus) throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expectedStatus));
    }

    /** Requests a code for writer@example.com and returns the code that "was emailed". */
    private String requestOtpForWriter() throws Exception {
        postJson("/forgot-password", "{\"email\":\"writer@example.com\"}", 200);
        ArgumentCaptor<String> code = ArgumentCaptor.forClass(String.class);
        verify(mailService).sendPasswordResetOtp(eq("writer@example.com"), eq("writer"), code.capture(), anyLong());
        return code.getValue();
    }

    private String resetBody(String email, String otp, String password) {
        return "{\"email\":\"" + email + "\",\"otp\":\"" + otp + "\",\"newPassword\":\"" + password + "\"}";
    }

    @Test
    void forgotPasswordAnswersTheSameForKnownAndUnknownEmails() throws Exception {
        String known = mvc.perform(post("/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"writer@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@example.com\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertEquals(known, unknown);
        verify(mailService, times(1)).sendPasswordResetOtp(eq("writer@example.com"), anyString(), anyString(), anyLong());
        verify(mailService, never()).sendPasswordResetOtp(eq("nobody@example.com"), any(), any(), anyLong());
    }

    @Test
    void forgotPasswordValidatesTheEmail() throws Exception {
        postJson("/forgot-password", "{}", 400);
        postJson("/forgot-password", "{\"email\":\"not-an-email\"}", 400);
    }

    @Test
    void otpResetsThePasswordOnceAndOnlyOnce() throws Exception {
        String code = requestOtpForWriter();
        assertTrue(code.matches("\\d{6}"), code);
        // The code is stored hashed, never in clear.
        PasswordResetOtp stored = otpRepository.findAll().get(0);
        assertNotEquals(code, stored.getOtpHash());

        postJson("/forgot-password/reset", resetBody("writer@example.com", code, "brandNewPass1"), 200);

        postJson("/login", "{\"username\":\"writer\",\"password\":\"brandNewPass1\"}", 200);
        postJson("/login", "{\"username\":\"writer\",\"password\":\"password123\"}", 401);
        // The code is spent.
        postJson("/forgot-password/reset", resetBody("writer@example.com", code, "anotherPass12"), 400);
        assertEquals(0, otpRepository.count());
    }

    @Test
    void wrongOtpIsRejectedAndTheCodeDiesAfterTooManyGuesses() throws Exception {
        String code = requestOtpForWriter();
        String wrong = code.equals("000000") ? "111111" : "000000";

        for (int i = 0; i < 5; i++) {
            postJson("/forgot-password/reset", resetBody("writer@example.com", wrong, "brandNewPass1"), 400);
        }
        // Even the right code no longer works: the attacker used up the attempts.
        postJson("/forgot-password/reset", resetBody("writer@example.com", code, "brandNewPass1"), 400);
        postJson("/login", "{\"username\":\"writer\",\"password\":\"password123\"}", 200);
    }

    @Test
    void aFewWrongGuessesDoNotBlockTheRealCode() throws Exception {
        String code = requestOtpForWriter();
        String wrong = code.equals("000000") ? "111111" : "000000";

        postJson("/forgot-password/reset", resetBody("writer@example.com", wrong, "brandNewPass1"), 400);
        postJson("/forgot-password/reset", resetBody("writer@example.com", code, "brandNewPass1"), 200);
    }

    @Test
    void expiredOtpIsRejected() throws Exception {
        String code = requestOtpForWriter();
        PasswordResetOtp stored = otpRepository.findAll().get(0);
        stored.setExpiresAt(Instant.now().minusSeconds(1));
        otpRepository.save(stored);

        postJson("/forgot-password/reset", resetBody("writer@example.com", code, "brandNewPass1"), 400);
        postJson("/login", "{\"username\":\"writer\",\"password\":\"password123\"}", 200);
    }

    @Test
    void otpResetFailsTheSameWayForUnknownEmailOrNeverRequested() throws Exception {
        postJson("/forgot-password/reset", resetBody("nobody@example.com", "123456", "brandNewPass1"), 400);
        postJson("/forgot-password/reset", resetBody("writer@example.com", "123456", "brandNewPass1"), 400);
    }

    @Test
    void otpResetValidatesItsInput() throws Exception {
        postJson("/forgot-password/reset", resetBody("writer@example.com", "12ab56", "brandNewPass1"), 400);
        postJson("/forgot-password/reset", resetBody("writer@example.com", "12345", "brandNewPass1"), 400);
        postJson("/forgot-password/reset", resetBody("writer@example.com", "123456", "short"), 400);
        postJson("/forgot-password/reset", resetBody("not-an-email", "123456", "brandNewPass1"), 400);
    }

    @Test
    void aSecondRequestWithinTheCooldownDoesNotSendAnotherCode() throws Exception {
        postJson("/forgot-password", "{\"email\":\"writer@example.com\"}", 200);
        postJson("/forgot-password", "{\"email\":\"writer@example.com\"}", 200);

        verify(mailService, times(1)).sendPasswordResetOtp(eq("writer@example.com"), anyString(), anyString(), anyLong());
    }

    @Test
    void aNewCodeReplacesTheOldOneAfterTheCooldown() throws Exception {
        String first = requestOtpForWriter();
        PasswordResetOtp stored = otpRepository.findAll().get(0);
        stored.setSentAt(Instant.now().minusSeconds(120)); // pretend the cooldown has passed
        otpRepository.save(stored);

        postJson("/forgot-password", "{\"email\":\"writer@example.com\"}", 200);
        ArgumentCaptor<String> codes = ArgumentCaptor.forClass(String.class);
        verify(mailService, times(2)).sendPasswordResetOtp(eq("writer@example.com"), anyString(), codes.capture(), anyLong());
        String second = codes.getAllValues().get(1);

        assertEquals(1, otpRepository.count());
        if (!first.equals(second)) {
            postJson("/forgot-password/reset", resetBody("writer@example.com", first, "brandNewPass1"), 400);
        }
        postJson("/forgot-password/reset", resetBody("writer@example.com", second, "brandNewPass1"), 200);
    }

    @Test
    void idEncryptorRoundTripsAndRejectsTampering() {
        String enc = idEncryptor.encrypt(42L);
        assertEquals(42L, idEncryptor.decrypt(enc));
        assertNotEquals(enc, idEncryptor.encrypt(42L)); // random IV
        char flipped = enc.charAt(enc.length() - 2) == 'A' ? 'B' : 'A';
        String tampered = enc.substring(0, enc.length() - 2) + flipped + enc.charAt(enc.length() - 1);
        assertThrows(IllegalArgumentException.class, () -> idEncryptor.decrypt(tampered));
        assertThrows(IllegalArgumentException.class, () -> idEncryptor.decrypt("short"));
    }
}
