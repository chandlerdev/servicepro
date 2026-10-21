package com.servicepros;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Super Admin → Admins → Technicians: each admin's data is invisible to every other admin. */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:tenants;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "app.google.maps-api-key=", "app.twilio.account-sid=", "app.twilio.auth-token=", "spring.mail.host=",
        "app.super-admin.email=super@test.local", "app.super-admin.password=Test-Super-1!"
})
@AutoConfigureMockMvc
class MultiAdminIsolationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    com.servicepros.customer.CustomerRepository customers;

    @Autowired
    com.servicepros.technician.TechnicianRepository technicians;

    private String login(String email, String password) throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return "Bearer " + JsonPath.read(body, "$.accessToken");
    }

    private static String adminJson(String email, String password, String card, String expiry) {
        return "{\"companyName\":\"Branch B Ltd\",\"contactName\":\"Bea Branch\",\"contactCountryCode\":\"1\",\"contactPhone\":\"2815550100\","
                + "\"email\":\"" + email + "\",\"password\":\"" + password + "\",\"cardHolder\":\"Bea Branch\","
                + "\"cardNumber\":\"" + card + "\",\"cardExpiry\":\"" + expiry + "\",\"active\":true}";
    }

    private static String techJson(String name, String phone, String email, String password) {
        return "{\"name\":\"" + name + "\",\"phoneCountryCode\":\"1\",\"phone\":\"" + phone + "\",\"email\":\"" + email
                + "\",\"skills\":\"HVAC\"" + (password == null ? "" : ",\"password\":\"" + password + "\"") + "}";
    }

    private String json(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return r.andReturn().getResponse().getContentAsString();
    }

    @Test
    void superAdminManagesAdminsAndEachAdminOnlySeesItsOwnData() throws Exception {
        String sup = login("super@test.local", "Test-Super-1!");
        String adminA = login("admin@servicepros.local", "Admin@12345");

        // role boundaries
        mvc.perform(get("/api/super/admins").header("Authorization", adminA)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/customers").header("Authorization", sup)).andExpect(status().isForbidden());

        // super admin lists admins (the seeded admin A owns 6 technicians / 112 customers)
        mvc.perform(get("/api/super/admins").header("Authorization", sup))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.email=='admin@servicepros.local')].technicians").value(hasItem(6)))
                .andExpect(jsonPath("$[?(@.email=='admin@servicepros.local')].customers").value(hasItem(112)));

        // super admin creates admin B — every field is validated; only a masked card is kept
        mvc.perform(post("/api/super/admins").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("b@test.local", "Branch@123", "4242 4242 4242 4241", "12/49")))
                .andExpect(status().isBadRequest()); // fails the Luhn check
        mvc.perform(post("/api/super/admins").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("b@test.local", "Branch@123", "4242 4242 4242 4242", "01/20")))
                .andExpect(status().isBadRequest()); // expired
        mvc.perform(post("/api/super/admins").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"\",\"contactName\":\"\",\"contactPhone\":\"12\",\"email\":\"x\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.companyName").exists())
                .andExpect(jsonPath("$.fieldErrors.contactName").exists())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.cardHolder").exists())
                .andExpect(jsonPath("$.fieldErrors.cardExpiry").exists());
        mvc.perform(post("/api/super/admins").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("b@test.local", "Branch@123", "4242 4242 4242 4242", "12/49")
                                .replace("2815550100", "281555010")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").value("Mobile number for +1 must have 10 digits"));
        mvc.perform(post("/api/super/admins").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("b@test", "Branch@123", "4242 4242 4242 4242", "12/49")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        String b = json(mvc.perform(post("/api/super/admins").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("b@test.local", "Branch@123", "4242 4242 4242 4242", "12/49")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyName").value("Branch B Ltd"))
                .andExpect(jsonPath("$.contactName").value("Bea Branch"))
                .andExpect(jsonPath("$.contactCountryCode").value("+1"))
                .andExpect(jsonPath("$.contactPhone").value("2815550100"))
                .andExpect(jsonPath("$.card.brand").value("Visa"))
                .andExpect(jsonPath("$.card.last4").value("4242"))
                .andExpect(jsonPath("$.card.expMonth").value(12))
                .andExpect(jsonPath("$.card.expYear").value(2049)));
        org.junit.jupiter.api.Assertions.assertFalse(b.contains("42424242"), "full card number must never be returned");
        // duplicates: same email (any case), same company name, same mobile (any formatting) → 409 per field
        mvc.perform(post("/api/super/admins").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("B@Test.Local", "Branch@123", "4242 4242 4242 4242", "12/49")
                                .replace("\"contactCountryCode\":\"1\"", "\"contactCountryCode\":\"+1\"")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.email").value("This email is already registered"))
                .andExpect(jsonPath("$.fieldErrors.companyName").exists())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").exists());
        mvc.perform(post("/api/super/admins").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("ravi@servicepros.local", "Branch@123", "4242 4242 4242 4242", "12/49")
                                .replace("Branch B Ltd", "Other Co").replace("2815550100", "2815550199")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.email").value("This email is already registered")); // a technician's login
        Integer bId = JsonPath.read(b, "$.id");
        String adminB = login("b@test.local", "Branch@123");

        // B starts empty
        mvc.perform(get("/api/admin/customers").header("Authorization", adminB)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/technicians").header("Authorization", adminB)).andExpect(jsonPath("$", hasSize(0)));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", adminB)).andExpect(jsonPath("$.appointmentsToday").value(0));

        // B builds its own team: technician (+ app login), vehicle, task, route, customer, appointment
        // a technician needs name, phone (+ country code), email and password — the login is created with them
        mvc.perform(post("/api/admin/technicians").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"phoneCountryCode\":\"1\",\"phone\":\"\",\"email\":\"bob@test\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.email").exists());
        mvc.perform(post("/api/admin/technicians").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content(techJson("Bob B", "2815550001", "bob@test.local", null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.password").exists());
        Integer techB = JsonPath.read(json(mvc.perform(post("/api/admin/technicians").header("Authorization", adminB)
                .contentType(MediaType.APPLICATION_JSON).content(techJson("Bob B", "2815550001", "bob@test.local", "Tech@12345")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.hasLogin").value(true))
                .andExpect(jsonPath("$.phone").value("+1 (281) 555-0001"))), "$.id");
        login("bob@test.local", "Tech@12345"); // the login works right away
        // duplicates: same name / phone in this team, or an email used by any login (here A's technician)
        mvc.perform(post("/api/admin/technicians").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content(techJson("bob b", "2815550001", "ravi@servicepros.local", "Tech@12345")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.name").value("A resource with this name already exists"))
                .andExpect(jsonPath("$.fieldErrors.phone").value("This mobile number is already registered"))
                .andExpect(jsonPath("$.fieldErrors.email").value("This email is already registered"));
        // the phone is also unique across companies and against admins' contact mobiles (B's is +1 2815550100)
        mvc.perform(post("/api/admin/technicians").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content(techJson("Carl B", "2815550100", "carl@test.local", "Tech@12345")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.phone").value("This mobile number is already registered"));
        mvc.perform(post("/api/super/admins").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("d@test.local", "Branch@123", "4242 4242 4242 4242", "12/49")
                                .replace("Branch B Ltd", "Branch D Ltd").replace("2815550100", "2815550001")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").value("This mobile number is already registered to a technician"));
        // editing: blank password keeps it; own values are not duplicates; a new password works
        mvc.perform(put("/api/admin/technicians/" + techB).header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content(techJson("Bob B", "2815550001", "bob@test.local", null)))
                .andExpect(status().isOk());
        login("bob@test.local", "Tech@12345");
        Integer vanB = JsonPath.read(json(mvc.perform(post("/api/admin/vehicles").header("Authorization", adminB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Van B\",\"plate\":\"TX B-1\",\"capacity\":\"2\"}"))), "$.id");
        Integer taskB = JsonPath.read(json(mvc.perform(post("/api/admin/tasks").header("Authorization", adminB)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Gutter Clean\",\"rate\":90,\"durationMinutes\":45}"))), "$.id");
        // City Master is ONE shared list: B sees the same 4 cities; admins can't manage it ...
        mvc.perform(get("/api/admin/cities").header("Authorization", adminB)).andExpect(jsonPath("$", hasSize(4)));
        mvc.perform(get("/api/super/cities").header("Authorization", adminB)).andExpect(status().isForbidden());
        // ... but can add a missing city from a dropdown (shared with everyone); an existing name is just returned
        mvc.perform(post("/api/admin/cities").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Baytown\",\"lat\":29.7355,\"lng\":-94.9774}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Baytown"));
        mvc.perform(post("/api/admin/cities").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"katy\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Katy"));
        mvc.perform(post("/api/admin/cities").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nowhereville\"}"))
                .andExpect(status().isBadRequest()); // not found on the map (Google isn't configured in tests)
        mvc.perform(get("/api/admin/cities").header("Authorization", adminA)).andExpect(jsonPath("$", hasSize(5)));
        mvc.perform(post("/api/admin/customers").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"X\",\"phone\":\"(281) 555-0009\",\"address\":\"1 Main St\",\"city\":\"Pasadena\"}"))
                .andExpect(status().isBadRequest()); // not in the list yet
        Integer pasadena = JsonPath.read(json(mvc.perform(post("/api/super/cities").header("Authorization", sup)
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Pasadena\",\"lat\":29.69,\"lng\":-95.2}"))
                .andExpect(status().isCreated())), "$.id");
        mvc.perform(post("/api/super/cities").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"pasadena\",\"lat\":1,\"lng\":1}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/super/cities").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Nowhere\"}"))
                .andExpect(status().isBadRequest()); // no coordinates and Google isn't configured in tests
        // every admin sees the new city straight away
        mvc.perform(get("/api/admin/cities").header("Authorization", adminA)).andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(get("/api/admin/cities").header("Authorization", adminB)).andExpect(jsonPath("$", hasSize(6)));
        mvc.perform(post("/api/admin/routes").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"B Route\",\"vehicleId\":" + vanB + ",\"technicianIds\":[" + techB + "],\"city\":\"Pasadena\",\"startTime\":\"08:00\"}"))
                .andExpect(status().isCreated());
        Integer custB = JsonPath.read(json(mvc.perform(post("/api/admin/customers").header("Authorization", adminB)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Carol B\",\"phone\":\"(281) 555-0002\",\"address\":\"1 Main St\",\"city\":\"pasadena\",\"lat\":29.78,\"lng\":-95.82}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.city").value("Pasadena"))), "$.id");
        mvc.perform(post("/api/admin/appointments").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2030-01-02\",\"customerId\":" + custB + ",\"taskId\":" + taskB + ",\"sendConfirmation\":false}"))
                .andExpect(status().isCreated());

        // a city an admin uses can't be deleted by the Super Admin; a rename reaches every admin's customers / routes
        mvc.perform(delete("/api/super/cities/" + pasadena).header("Authorization", sup)).andExpect(status().isConflict());
        mvc.perform(put("/api/super/cities/" + pasadena).header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Pasadena TX\",\"lat\":29.69,\"lng\":-95.2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customers").value(1))
                .andExpect(jsonPath("$.routes").value(1));
        mvc.perform(get("/api/admin/customers/" + custB).header("Authorization", adminB))
                .andExpect(jsonPath("$.city").value("Pasadena TX"));
        // Katy is used by A's 28 customers (the Super Admin sees usage across all admins)
        mvc.perform(get("/api/super/cities").param("q", "kat").header("Authorization", sup))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("Katy"))
                .andExpect(jsonPath("$.content[0].customers").value(28));
        // paging: 6 cities, 4 per page
        mvc.perform(get("/api/super/cities").param("page", "1").param("size", "4").header("Authorization", sup))
                .andExpect(jsonPath("$.totalElements").value(6))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content", hasSize(2)));

        // A does not see B's records, and B does not see A's
        mvc.perform(get("/api/admin/technicians").header("Authorization", adminA))
                .andExpect(jsonPath("$", hasSize(6))).andExpect(jsonPath("$[*].name").value(not(hasItem("Bob B"))));
        mvc.perform(get("/api/admin/customers").header("Authorization", adminA).param("q", "Carol B"))
                .andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/admin/tasks").header("Authorization", adminB)).andExpect(jsonPath("$", hasSize(1)));

        // …not even by id
        Integer custA = JsonPath.read(json(mvc.perform(get("/api/admin/customers").header("Authorization", adminA)
                .param("size", "1"))), "$.content[0].id");
        mvc.perform(get("/api/admin/customers/" + custA).header("Authorization", adminB)).andExpect(status().isNotFound());
        mvc.perform(get("/api/admin/customers/" + custB).header("Authorization", adminA)).andExpect(status().isNotFound());
        mvc.perform(put("/api/admin/tasks/" + taskB).header("Authorization", adminA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"hijack\",\"rate\":1,\"durationMinutes\":30}")).andExpect(status().isNotFound());
        mvc.perform(post("/api/admin/appointments").header("Authorization", adminA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"date\":\"2030-01-02\",\"customerId\":" + custB + ",\"taskId\":" + taskB + "}")).andExpect(status().isNotFound());

        // B's technician sees only B's stop in the app
        String bob = login("bob@test.local", "Tech@12345");
        mvc.perform(get("/api/tech/stops").param("date", "2030-01-02").header("Authorization", bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.routes", hasSize(1)))
                .andExpect(jsonPath("$.routes[0].stops[0].customerName").value("Carol B"));
        mvc.perform(get("/api/tech/notifications").header("Authorization", bob)).andExpect(jsonPath("$.totalElements").value(0));

        // per-admin settings: a new admin sees what the Super Admin entered (no made-up defaults); no depot yet
        mvc.perform(get("/api/admin/settings").header("Authorization", adminB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyName").value("Branch B Ltd"))
                .andExpect(jsonPath("$.contactName").value("Bea Branch"))
                .andExpect(jsonPath("$.contactCountryCode").value("+1"))
                .andExpect(jsonPath("$.contactPhone").value("2815550100"))
                .andExpect(jsonPath("$.loginEmail").value("b@test.local"))
                .andExpect(jsonPath("$.card.last4").value("4242"))
                .andExpect(jsonPath("$.officeAddress").doesNotExist())
                .andExpect(jsonPath("$.officeLat").doesNotExist());
        mvc.perform(post("/api/admin/schedule/optimize").header("Authorization", adminB)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2030-01-02\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/admin/settings").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Branch B Services\",\"officeAddress\":\"9 B St\",\"officeLat\":29.7,\"officeLng\":-95.4,"
                                + "\"contactName\":\"Bea B. Branch\",\"contactCountryCode\":\"91\",\"contactPhone\":\"9876543210\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactCountryCode").value("+91"));
        // the admin can also change their card (validated like the New Admin form; only last 4 kept)
        mvc.perform(put("/api/admin/settings").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Branch B Services\",\"officeAddress\":\"9 B St\",\"officeLat\":29.7,\"officeLng\":-95.4,"
                                + "\"contactName\":\"Bea B. Branch\",\"contactCountryCode\":\"91\",\"contactPhone\":\"9876543210\","
                                + "\"cardHolder\":\"Bea B\",\"cardNumber\":\"4111111111111112\",\"cardExpiry\":\"10/45\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.cardNumber").exists());
        mvc.perform(put("/api/admin/settings").header("Authorization", adminB).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"Branch B Services\",\"officeAddress\":\"9 B St\",\"officeLat\":29.7,\"officeLng\":-95.4,"
                                + "\"contactName\":\"Bea B. Branch\",\"contactCountryCode\":\"91\",\"contactPhone\":\"9876543210\","
                                + "\"cardHolder\":\"Bea B\",\"cardNumber\":\"5555555555554444\",\"cardExpiry\":\"10/45\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.card.brand").value("Mastercard"))
                .andExpect(jsonPath("$.card.last4").value("4444"))
                .andExpect(jsonPath("$.card.holder").value("Bea B"))
                .andExpect(jsonPath("$.loginEmail").value("b@test.local"));
        // the Super Admin sees the admin's own changes
        mvc.perform(get("/api/super/admins/" + bId).header("Authorization", sup))
                .andExpect(jsonPath("$.companyName").value("Branch B Services"))
                .andExpect(jsonPath("$.contactName").value("Bea B. Branch"))
                .andExpect(jsonPath("$.card.last4").value("4444"))
                .andExpect(jsonPath("$.contactPhone").value("9876543210"));
        // an admin can't take another admin's company name
        mvc.perform(put("/api/admin/settings").header("Authorization", adminA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"branch b services\",\"officeAddress\":\"1 A St\",\"officeLat\":29.7,\"officeLng\":-95.4,"
                                + "\"contactName\":\"A\",\"contactCountryCode\":\"1\",\"contactPhone\":\"2815550111\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.companyName").exists());
        // nor another admin's phone number
        mvc.perform(put("/api/admin/settings").header("Authorization", adminA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"ServicePros\",\"officeAddress\":\"1 A St\",\"officeLat\":29.7,\"officeLng\":-95.4,"
                                + "\"contactCountryCode\":\"+91\",\"contactPhone\":\"9876543210\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.fieldErrors.contactPhone").value("This mobile number is already used by another admin"));
        mvc.perform(get("/api/admin/settings").header("Authorization", adminA))
                .andExpect(jsonPath("$.companyName").value(not("Branch B Services")));
        mvc.perform(get("/api/super/admins/" + bId).header("Authorization", sup))
                .andExpect(jsonPath("$.technicians").value(1));

        // disabling locks out the admin AND their technicians
        mvc.perform(patch("/api/super/admins/" + bId + "/active").header("Authorization", sup).contentType(MediaType.APPLICATION_JSON)
                .content("{\"active\":false}")).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));
        for (String[] u : new String[][]{{"b@test.local", "Branch@123"}, {"bob@test.local", "Tech@12345"}}) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"" + u[0] + "\",\"password\":\"" + u[1] + "\"}")).andExpect(status().isUnauthorized());
        }

        // delete is a soft delete — allowed even with data, which stays in the database
        mvc.perform(delete("/api/super/admins/" + bId).header("Authorization", sup)).andExpect(status().isNoContent());
        mvc.perform(get("/api/super/admins/" + bId).header("Authorization", sup)).andExpect(status().isNotFound());
        mvc.perform(get("/api/super/admins").header("Authorization", sup))
                .andExpect(jsonPath("$[?(@.id==" + bId + ")]").isEmpty());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"bob@test.local\",\"password\":\"Tech@12345\"}")).andExpect(status().isUnauthorized());
        if (customers.countByOwnerId(bId.longValue()) != 1 || technicians.countByOwnerId(bId.longValue()) != 1) {
            throw new AssertionError("soft delete must keep the admin's data");
        }
        // the email, company name and mobile are free again
        Integer b2 = JsonPath.read(json(mvc.perform(post("/api/super/admins").header("Authorization", sup)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(adminJson("b@test.local", "Branch@123", "5555 5555 5555 4444", "06/48")))
                .andExpect(status().isCreated())), "$.id");
        String b2Token = login("b@test.local", "Branch@123");
        mvc.perform(get("/api/admin/customers").header("Authorization", b2Token)).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(delete("/api/super/admins/" + b2).header("Authorization", sup)).andExpect(status().isNoContent());
    }
}
