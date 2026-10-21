package com.servicepros.task;

import java.util.List;

import com.servicepros.task.ServiceTaskDtos.ServiceTaskRequest;
import com.servicepros.task.ServiceTaskDtos.ServiceTaskResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin · Tasks")
@RestController
@RequestMapping("/api/admin/tasks")
@RequiredArgsConstructor
public class AdminServiceTaskController {

    private final ServiceTaskService service;

    @GetMapping
    public List<ServiceTaskResponse> list() {
        return service.list();
    }

    @GetMapping("/{id}")
    public ServiceTaskResponse get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceTaskResponse create(@Valid @RequestBody ServiceTaskRequest req) {
        return service.create(req);
    }

    @PutMapping("/{id}")
    public ServiceTaskResponse update(@PathVariable Long id, @Valid @RequestBody ServiceTaskRequest req) {
        return service.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
