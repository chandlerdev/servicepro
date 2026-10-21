package com.servicepros.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.servicepros.appointment.Appointment;
import com.servicepros.appointment.AppointmentRepository;
import com.servicepros.appointment.AppointmentStatus;
import com.servicepros.auth.User;
import com.servicepros.auth.UserRepository;
import com.servicepros.city.CityRepository;
import com.servicepros.common.TimeService;
import com.servicepros.customer.Customer;
import com.servicepros.customer.CustomerRepository;
import com.servicepros.route.ServiceRoute;
import com.servicepros.route.ServiceRouteRepository;
import com.servicepros.schedule.ScheduleService;
import com.servicepros.settings.SettingsDtos.SettingsRequest;
import com.servicepros.settings.SettingsService;
import com.servicepros.task.ServiceTask;
import com.servicepros.task.ServiceTaskRepository;
import com.servicepros.technician.Technician;
import com.servicepros.technician.TechnicianRepository;
import com.servicepros.vehicle.Vehicle;
import com.servicepros.vehicle.VehicleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import com.servicepros.tenant.TenantContext;

/**
 * TEST-ONLY fixture: fills the in-memory test database with the prototype's demo data (4 cities,
 * 6 technicians with logins, 4 vehicles, 6 tasks, 4 routes, 112 customers, today's + tomorrow's appointments).
 * It lives under src/test, so it is never part of the application.
 */
@Slf4j
@Order(10)
@Component
@RequiredArgsConstructor
public class TestDataSeeder implements ApplicationRunner {

    static final String ADMIN_EMAIL = "admin@servicepros.local";
    static final String ADMIN_PASSWORD = "Admin@12345";
    static final String TECH_PASSWORD = "Tech@12345";

    private record City(String name, double lat, double lng, String[] zips, String areaCode, String[] streets) {
    }

    private static final List<City> CITIES = List.of(
            new City("Katy", 29.7858, -95.8245, new String[]{"77449", "77450", "77494"}, "281",
                    new String[]{"Mason Rd", "Kingsland Blvd", "Fry Rd", "Cinco Ranch Blvd", "Westheimer Pkwy", "Morton Rd", "Franz Rd", "Gaston Rd"}),
            new City("Sugar Land", 29.6197, -95.6349, new String[]{"77478", "77479", "77498"}, "281",
                    new String[]{"Sweetwater Blvd", "Lexington Blvd", "Eldridge Rd", "Settlers Way Blvd", "Austin Pkwy", "Williams Trace Blvd", "Dulles Ave", "Soldiers Field Dr"}),
            new City("The Woodlands", 30.1658, -95.4613, new String[]{"77380", "77381", "77382"}, "936",
                    new String[]{"Woodlands Pkwy", "Research Forest Dr", "Grogans Mill Rd", "Panther Creek Dr", "Lake Front Cir", "Sawdust Rd", "Gosling Rd", "Flintridge Dr"}),
            new City("Galveston", 29.2900, -94.8100, new String[]{"77550", "77551", "77554"}, "409",
                    new String[]{"Seawall Blvd", "Broadway Ave", "Harborside Dr", "Stewart Rd", "Ave O", "Winnie St", "Church St", "61st St"}));

    private static final String[] FIRST = {"James", "Mary", "Robert", "Patricia", "John", "Jennifer", "Michael", "Linda", "David",
            "Elizabeth", "William", "Barbara", "Richard", "Susan", "Joseph", "Jessica", "Thomas", "Karen", "Charles", "Nancy",
            "Daniel", "Lisa", "Maria", "Carlos", "Ana", "Luis", "Sofia", "Miguel", "Priya", "Arjun", "Wei", "Mei", "Aisha",
            "Omar", "Fatima", "Kofi"};
    private static final String[] LAST = {"Smith", "Johnson", "Williams", "Brown", "Jones", "Garcia", "Miller", "Davis",
            "Rodriguez", "Martinez", "Hernandez", "Lopez", "Gonzalez", "Wilson", "Anderson", "Thomas", "Taylor", "Moore",
            "Jackson", "Martin", "Lee", "Perez", "Thompson", "White", "Harris", "Sanchez", "Clark", "Ramirez", "Lewis",
            "Robinson", "Patel", "Nguyen", "Kim", "Chen", "Okafor", "Reddy"};

    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final TechnicianRepository technicians;
    private final VehicleRepository vehicles;
    private final ServiceTaskRepository tasks;
    private final ServiceRouteRepository routes;
    private final CustomerRepository customers;
    private final AppointmentRepository appointments;
    private final ScheduleService scheduleService;
    private final SettingsService settingsService;
    private final CityRepository cityRepo;
    private final TimeService time;
    private final PlatformTransactionManager txManager;

    private long seed = 20260721L;

    @Override
    public void run(ApplicationArguments args) {
        if (users.existsByRole(User.Role.ADMIN)) {
            return;
        }
        log.info("Seeding demo data…");
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Long adminId = tx.execute(status -> {
            User admin = new User();
            admin.setName("Chandra (Admin)");
            admin.setEmail(ADMIN_EMAIL);
            admin.setPasswordHash(encoder.encode(ADMIN_PASSWORD));
            admin.setRole(User.Role.ADMIN);
            return users.save(admin).getId();
        });
        // Everything else belongs to that admin (tenant must be set before the transaction opens).
        TenantContext.runAs(adminId, () -> tx.executeWithoutResult(status -> seedAdminData()));
    }

    private void seedAdminData() {
        settingsService.update(new SettingsRequest("ServicePros", "2100 Travis St, Houston, TX 77002", 29.7520, -95.3700,
                null, "https://g.page/r/servicepros/review", "https://facebook.com/servicepros/reviews",
                null, null, null, null, null, null, null, null));
        for (City c : CITIES) {
            com.servicepros.city.City city = new com.servicepros.city.City();
            city.setName(c.name());
            city.setLat(c.lat());
            city.setLng(c.lng());
            cityRepo.save(city);
        }
        String techPw = encoder.encode(TECH_PASSWORD);
        List<Technician> techs = new ArrayList<>();
        String[][] techData = {
                {"Ravi Kumar", "(832) 555-0141", "HVAC / Electrical", "ravi"},
                {"Miguel Santos", "(832) 555-0177", "Plumbing / Appliance", "miguel"},
                {"Sarah Lee", "(832) 555-0192", "HVAC / General", "sarah"},
                {"James Okafor", "(832) 555-0208", "Electrical / Solar", "james"},
                {"Dana Whitfield", "(832) 555-0233", "Appliance / HVAC", "dana"},
                {"Luis Herrera", "(832) 555-0269", "Plumbing / General", "luis"}};
        for (String[] d : techData) {
            Technician t = new Technician();
            t.setName(d[0]);
            t.setPhone(d[1]);
            t.setSkills(d[2]);
            t.setEmail(d[3] + "@servicepros.local");
            techs.add(technicians.save(t));
            User u = new User();
            u.setName(d[0]);
            u.setEmail(t.getEmail());
            u.setPasswordHash(techPw);
            u.setRole(User.Role.TECHNICIAN);
            u.setTechnician(t);
            users.save(u);
        }

        List<Vehicle> vans = new ArrayList<>();
        String[][] vehicleData = {{"Van A", "TX KTY-482", "2 techs"}, {"Van B", "TX SGL-773", "2 techs"},
                {"Van C", "TX WDL-219", "2 techs"}, {"Truck 1", "TX GAL-905", "3 techs"}};
        for (String[] d : vehicleData) {
            Vehicle v = new Vehicle();
            v.setName(d[0]);
            v.setPlate(d[1]);
            v.setCapacity(d[2]);
            vans.add(vehicles.save(v));
        }

        List<ServiceTask> taskList = new ArrayList<>();
        Object[][] taskData = {
                {"AC Repair", "120", 60}, {"HVAC Maintenance", "95", 45}, {"Water Heater Install", "340", 120},
                {"Electrical Inspection", "80", 30}, {"Plumbing Fix", "110", 60}, {"Appliance Repair", "130", 75}};
        for (Object[] d : taskData) {
            ServiceTask t = new ServiceTask();
            t.setName((String) d[0]);
            t.setRate(new BigDecimal((String) d[1]));
            t.setDurationMinutes((Integer) d[2]);
            taskList.add(tasks.save(t));
        }

        Object[][] routeData = {
                {"Katy Route", 0, new int[]{0, 1}, "Katy", "Katy / Cinco Ranch", "08:00"},
                {"Sugar Land Route", 1, new int[]{2}, "Sugar Land", "Sugar Land / Missouri City", "08:00"},
                {"The Woodlands Route", 2, new int[]{3, 4}, "The Woodlands", "The Woodlands / Spring", "08:30"},
                {"Galveston Route", 3, new int[]{5}, "Galveston", "Galveston Island", "08:30"}};
        Map<String, ServiceRoute> routeByCity = new LinkedHashMap<>();
        for (Object[] d : routeData) {
            ServiceRoute r = new ServiceRoute();
            r.setName((String) d[0]);
            r.setVehicle(vans.get((Integer) d[1]));
            for (int ti : (int[]) d[2]) r.getTechnicians().add(techs.get(ti));
            r.setCity((String) d[3]);
            r.setAreaDescription((String) d[4]);
            r.setStartTime(LocalTime.parse((String) d[5]));
            routeByCity.put((String) d[3], routes.save(r));
        }

        // 112 customers, 28 per city, deterministic like the prototype
        Map<String, List<Customer>> byCity = new LinkedHashMap<>();
        int n = 0;
        for (City c : CITIES) {
            List<Customer> list = new ArrayList<>();
            for (int i = 0; i < 28; i++) {
                n++;
                String fn = FIRST[(int) (rnd() * FIRST.length)];
                String ln = LAST[(int) (rnd() * LAST.length)];
                boolean gal = c.name().equals("Galveston");
                double latJ = gal ? (rnd() - 0.5) * 0.045 : (rnd() - 0.5) * 0.11;
                double lngJ = gal ? (rnd() - 0.5) * 0.16 : (rnd() - 0.5) * 0.13;
                Customer cu = new Customer();
                cu.setName(fn + " " + ln);
                cu.setPhone("(" + c.areaCode() + ") 555-" + (1000 + (int) (rnd() * 9000)));
                cu.setEmail((fn + "." + ln + n).toLowerCase() + "@mail.com");
                cu.setAddress((100 + (int) (rnd() * 9800)) + " " + c.streets()[(int) (rnd() * c.streets().length)] + ", "
                        + c.name() + ", TX " + c.zips()[(int) (rnd() * c.zips().length)]);
                cu.setCity(c.name());
                cu.setLat(Math.round((c.lat() + latJ) * 100000) / 100000.0);
                cu.setLng(Math.round((c.lng() + lngJ) * 100000) / 100000.0);
                list.add(customers.save(cu));
            }
            byCity.put(c.name(), list);
        }

        LocalDate today = time.today();
        LocalDate tomorrow = today.plusDays(1);
        int ai = 0;
        for (City c : CITIES) {
            ServiceRoute r = routeByCity.get(c.name());
            List<Customer> pool = byCity.get(c.name());
            for (int i = 0; i < 6; i++) {
                ai++;
                appointment(today, pool.get(i * 3), taskList.get(ai % taskList.size()), r, AppointmentStatus.CONFIRMED);
            }
            for (int i = 0; i < 4; i++) {
                ai++;
                appointment(tomorrow, pool.get(i * 3 + 1), taskList.get((ai * 2) % taskList.size()), r, AppointmentStatus.SCHEDULED);
            }
        }

        // Open the demo with today's routes already planned (no SMS sent)
        routeByCity.values().forEach(r -> scheduleService.optimizeRoute(today, r));

        log.info("Seed complete — admin: {} / {} ; technicians: ravi@servicepros.local … / {}",
                ADMIN_EMAIL, ADMIN_PASSWORD, TECH_PASSWORD);
    }

    private Appointment appointment(LocalDate date, Customer c, ServiceTask t, ServiceRoute r, AppointmentStatus status) {
        Appointment a = new Appointment();
        a.setDate(date);
        a.setCustomer(c);
        a.setTask(t);
        a.setRoute(r);
        a.setStatus(status);
        return appointments.save(a);
    }

    /** Same LCG as the prototype so the generated customers match. */
    private double rnd() {
        seed = (seed * 1103515245L + 12345L) % 2147483648L;
        return seed / 2147483648.0;
    }
}
