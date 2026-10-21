package com.servicepros.task;

import java.util.List;

import com.servicepros.appointment.AppointmentRepository;
import com.servicepros.appointment.AppointmentStatus;
import com.servicepros.common.ApiException;
import com.servicepros.common.TimeService;
import com.servicepros.schedule.PlanStaleMarker;
import com.servicepros.task.ServiceTaskDtos.ServiceTaskRequest;
import com.servicepros.task.ServiceTaskDtos.ServiceTaskResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ServiceTaskService {

    private static final List<AppointmentStatus> OPEN = List.of(AppointmentStatus.SCHEDULED,
            AppointmentStatus.CONFIRMED, AppointmentStatus.EN_ROUTE, AppointmentStatus.DELAYED);

    private final ServiceTaskRepository repo;
    private final AppointmentRepository appointments;
    private final PlanStaleMarker staleMarker;
    private final TimeService time;

    @Transactional(readOnly = true)
    public List<ServiceTaskResponse> list() {
        return repo.findByDeletedFalseOrderByNameAsc().stream().map(ServiceTaskResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceTaskResponse get(Long id) {
        return ServiceTaskResponse.from(find(id));
    }

    public ServiceTask find(Long id) {
        return repo.findById(id).filter(t -> !t.isDeleted()).orElseThrow(() -> ApiException.notFound("Task", id));
    }

    @Transactional
    public ServiceTaskResponse create(ServiceTaskRequest r) {
        ServiceTask t = new ServiceTask();
        apply(t, r);
        return ServiceTaskResponse.from(repo.save(t));
    }

    @Transactional
    public ServiceTaskResponse update(Long id, ServiceTaskRequest r) {
        ServiceTask t = find(id);
        boolean durationChanged = t.getDurationMinutes() != r.durationMinutes();
        apply(t, r);
        if (durationChanged) {
            staleMarker.markAll(appointments.findUpcomingForTask(id, time.today(), OPEN));
        }
        return ServiceTaskResponse.from(t);
    }

    /** Removes the task from the catalog; tasks already used by appointments are hidden (soft delete). */
    @Transactional
    public void delete(Long id) {
        ServiceTask t = find(id);
        if (appointments.existsByTaskId(id)) {
            t.setDeleted(true);
        } else {
            repo.delete(t);
        }
    }

    private void apply(ServiceTask t, ServiceTaskRequest r) {
        t.setName(r.name().trim());
        t.setRate(r.rate());
        t.setDurationMinutes(r.durationMinutes());
    }
}
