package com.servicepros;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end flow against the seeded H2 database: auth, admin day plan, technician actions, invoicing. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:servicepros-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        // tests never call real external services, whatever is in .env
        "app.google.maps-api-key=",
        "app.twilio.account-sid=",
        "app.twilio.auth-token=",
        "spring.mail.host="
})
@AutoConfigureMockMvc
class ApiFlowIntegrationTest {

    @Autowired
    MockMvc mvc;

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.accessToken");
    }

    @Test
    void securityRules() throws Exception {
        mvc.perform(get("/api/admin/dashboard")).andExpect(status().isUnauthorized());
        String tech = login("ravi@servicepros.local", "Tech@12345");
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + tech)).andExpect(status().isForbidden());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"admin@servicepros.local\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect password"))
                .andExpect(jsonPath("$.fieldErrors.password").value("Incorrect password"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody@servicepros.local\",\"password\":\"Admin@12345\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("No account found with this email"))
                .andExpect(jsonPath("$.fieldErrors.email").value("No account found with this email"));
    }

    @Test
    void adminPlansDayAndTechnicianWorksIt() throws Exception {
        String admin = login("admin@servicepros.local", "Admin@12345");
        String bearer = "Bearer " + admin;

        mvc.perform(get("/api/admin/dashboard").header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointmentsToday").value(24))
                .andExpect(jsonPath("$.customers").value(112));

        String today = JsonPath.read(mvc.perform(get("/api/admin/dashboard").header("Authorization", bearer))
                .andReturn().getResponse().getContentAsString(), "$.date");
        String tomorrow = java.time.LocalDate.parse(today).plusDays(1).toString();

        // tomorrow is not allocated yet → allocate & notify all 16 customers
        mvc.perform(post("/api/admin/schedule/allocate-notify").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"" + tomorrow + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notified").value(16))
                .andExpect(jsonPath("$.schedule.routes", hasSize(4)))
                .andExpect(jsonPath("$.schedule.routes[0].optimized").value(true))
                .andExpect(jsonPath("$.schedule.routes[0].stops[0].plannedArrival").isNotEmpty());

        // sending again does not spam customers whose time didn't change
        mvc.perform(post("/api/admin/schedule/allocate-notify").header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"" + tomorrow + "\"}"))
                .andExpect(jsonPath("$.notified").value(0));

        // technician: today's stops on the Katy route
        String tech = "Bearer " + login("ravi@servicepros.local", "Tech@12345");
        String stops = mvc.perform(get("/api/tech/stops").param("date", today).header("Authorization", tech))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes", hasSize(1)))
                .andExpect(jsonPath("$.routes[0].stops", hasSize(6)))
                .andReturn().getResponse().getContentAsString();
        Integer first = JsonPath.read(stops, "$.routes[0].stops[0].appointmentId");
        Integer third = JsonPath.read(stops, "$.routes[0].stops[2].appointmentId");

        mvc.perform(post("/api/tech/appointments/" + first + "/on-my-way").header("Authorization", tech))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("EN_ROUTE"));

        // delay at stop 3 shifts stops 3..6 → 4 customers notified
        mvc.perform(post("/api/tech/appointments/" + third + "/delay").header("Authorization", tech)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"minutes\":30,\"reason\":\"Traffic on I-10\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.notified").value(4));

        mvc.perform(post("/api/tech/appointments/" + first + "/complete").header("Authorization", tech)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"notes\":\"Replaced capacitor\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointment.status").value("COMPLETED"));

        // completing twice is rejected
        mvc.perform(post("/api/tech/appointments/" + first + "/complete").header("Authorization", tech))
                .andExpect(status().isConflict());

        // a technician cannot touch another route's stops
        String other = JsonPath.read(mvc.perform(get("/api/admin/schedule").param("date", today)
                        .header("Authorization", bearer)).andReturn().getResponse().getContentAsString(),
                "$.routes[?(@.routeName=='Galveston Route')].stops[0].appointmentId").toString().replaceAll("\\D", "");
        mvc.perform(post("/api/tech/appointments/" + other + "/on-my-way").header("Authorization", tech))
                .andExpect(status().isForbidden());

        // the admin sees the completed job and the review SMS
        mvc.perform(get("/api/admin/appointments/" + first).header("Authorization", bearer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.city").value("Katy"));
        mvc.perform(get("/api/admin/notifications").param("type", "REVIEW").header("Authorization", bearer))
                .andExpect(jsonPath("$.totalElements").value(1));

        mvc.perform(get("/api/tech/notifications").header("Authorization", tech))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", greaterThan(0)));
    }

    @Test
    void customerValidationAndWebhookGuard() throws Exception {
        String bearer = "Bearer " + login("admin@servicepros.local", "Admin@12345");
        mvc.perform(post("/api/admin/customers").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"phone\":\"abc\",\"address\":\"1 Main St\",\"city\":\"Katy\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
        // only cities on the shared list are accepted; valid customer without coordinates gets the city centre
        mvc.perform(post("/api/admin/customers").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane Doe\",\"phone\":\"(281) 555-0142\",\"address\":\"1 Main St\",\"city\":\"Austin\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/customers").header("Authorization", bearer).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Jane Doe\",\"phone\":\"(281) 555-0142\",\"address\":\"1 Main St\",\"city\":\"katy\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.city").value("Katy"))
                .andExpect(jsonPath("$.lat").value(29.7858));
        mvc.perform(get("/api/admin/cities").header("Authorization", bearer))
                .andExpect(jsonPath("$", hasSize(4)));
        // Twilio isn't configured in tests → webhook refuses
        mvc.perform(post("/api/webhooks/twilio/sms").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("From", "+12815551234").param("Body", "C"))
                .andExpect(status().isServiceUnavailable());
    }
}
