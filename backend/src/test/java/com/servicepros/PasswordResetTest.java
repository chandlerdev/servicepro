package com.servicepros;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.jayway.jsonpath.JsonPath;
import com.servicepros.notification.MailGateway;
import com.servicepros.notification.Notification;
import com.servicepros.notification.SmsGateway;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Forgot password: emailed 6-digit OTP for admins and technicians, never for the Super Admin. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reset;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.google.maps-api-key=", "app.twilio.account-sid=", "app.twilio.auth-token=",
        "app.super-admin.email=super@test.local", "app.super-admin.password=Test-Super-1!",
        "app.rate-limit.login-per-minute=1000"
})
@AutoConfigureMockMvc
class PasswordResetTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    MailGateway mail;

    private void forgot(String email, int expectedStatus) throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private String sentCode(String email) {
        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(mail).send(eq(email), anyString(), body.capture());
        Matcher m = Pattern.compile("code is: (\\d{6})").matcher(body.getValue());
        if (!m.find()) throw new AssertionError("no code in email: " + body.getValue());
        return m.group(1);
    }

    private void reset(String email, String code, String password, int expectedStatus) throws Exception {
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"code\":\"" + code + "\",\"newPassword\":\"" + password + "\"}"))
                .andExpect(status().is(expectedStatus));
    }

    private int login(String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void adminAndTechnicianResetWithOtpButNotSuperAdmin() throws Exception {
        when(mail.send(anyString(), anyString(), anyString()))
                .thenReturn(new SmsGateway.Result(Notification.Status.SENT, null, null));

        // unknown email and the Super Admin are told so under the email field, and no email is sent
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@test.local\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Email is not registered..!"))
                .andExpect(jsonPath("$.fieldErrors.email").value("Email is not registered..!"));
        forgot("super@test.local", 400);
        verify(mail, never()).send(anyString(), anyString(), anyString());

        // admin: request → wrong code counts an attempt → right code sets the password
        String admin = "admin@servicepros.local";
        String refresh = JsonPath.read(mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + admin + "\",\"password\":\"Admin@12345\"}"))
                .andReturn().getResponse().getContentAsString(), "$.refreshToken");
        forgot(admin, 200);
        String code = sentCode(admin);
        forgot(admin, 429); // resend cool-down
        String wrong = code.equals("000000") ? "111111" : "000000";
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + admin + "\",\"code\":\"" + wrong + "\",\"newPassword\":\"NewAdmin@123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Incorrect code — 4 attempts left"));
        reset(admin, "12", "NewAdmin@123", 400);             // not 6 digits
        reset(admin, code, "short", 400);                     // password too short
        reset(admin, code, "NewAdmin@123", 204);
        reset(admin, code, "Another@123", 400);               // a code works once
        if (login(admin, "Admin@12345") != 401) throw new AssertionError("old password still works");
        if (login(admin, "NewAdmin@123") != 200) throw new AssertionError("new password rejected");
        // every old session was signed out
        mvc.perform(post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\":\"" + refresh + "\"}")).andExpect(status().isUnauthorized());

        // technician: same flow
        clearInvocations(mail);
        String tech = "ravi@servicepros.local";
        forgot(tech, 200);
        reset(tech, sentCode(tech), "NewTech@123", 204);
        if (login(tech, "NewTech@123") != 200) throw new AssertionError("technician's new password rejected");
    }
}
