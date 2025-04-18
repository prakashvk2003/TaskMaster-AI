package com.prakash.taskmaster.controller;

import com.prakash.taskmaster.dto.CreateTaskRequest;
import com.prakash.taskmaster.dto.TaskResponse;
import com.prakash.taskmaster.exception.CircularDependencyException;
import com.prakash.taskmaster.exception.InvalidTaskStateException;
import com.prakash.taskmaster.exception.TaskNotFoundException;
import com.prakash.taskmaster.model.Task;
import com.prakash.taskmaster.model.TaskStatus;
import com.prakash.taskmaster.service.TaskMasterService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/tasks") // Base path for task-related endpoints
public class TaskMasterController {

    private static final Logger log = LoggerFactory.getLogger(TaskMasterController.class);

    private final TaskMasterService taskMasterService;

    @Autowired
    public TaskMasterController(TaskMasterService taskMasterService) {
        this.taskMasterService = taskMasterService;
    }

    /**
     * Endpoint to create a new task.
     * The service layer handles the AI analysis and dependency checks.
     *
     * @param request The request body containing title, description, and optional dependencies.
     * @return The created task details including AI analysis results.
     */
    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@Valid @RequestBody CreateTaskRequest request) {
        log.info("Received request to create task: {}", request.getTitle());
        try {
            Task createdTask = taskMasterService.createTask(request);
            TaskResponse responseDto = TaskResponse.fromEntity(createdTask);
            return ResponseEntity.status(HttpStatus.CREATED).body(responseDto);
        } catch (TaskNotFoundException | CircularDependencyException e) {
            // These have @ResponseStatus, so re-throwing allows default handling (404, 409)
            log.warn("Failed to create task '{}': {}", request.getTitle(), e.getMessage());
            throw e;
        } catch (Exception e) {
            // Handle potential AI analysis failures or other unexpected errors
            log.error("Error creating task '{}': {}", request.getTitle(), e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // Or a custom error response
        }
    }

    /**
     * Endpoint to retrieve all tasks or filter by status.
     *
     * @param status Optional query parameter to filter tasks by status.
     * @return A list of tasks.
     */
    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllTasks(
            @RequestParam(required = false) TaskStatus status) {
        log.debug("Received request to get tasks. Filter status: {}", status);
        List<Task> tasks;
        if (status != null) {
            tasks = taskMasterService.getTasksByStatus(status);
        } else {
            tasks = taskMasterService.getAllTasks();
        }
        List<TaskResponse> responseDtos = tasks.stream()
                .map(TaskResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responseDtos);
    }

    /**
     * Endpoint to retrieve a specific task by its ID.
     *
     * @param id The ID of the task.
     * @return The task details or 404 if not found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable String id) {
        log.debug("Received request to get task by ID: {}", id);
        // TaskNotFoundException will be handled by default Spring Boot exception handling (404)
        Task task = taskMasterService.getTaskById(id);
        TaskResponse responseDto = TaskResponse.fromEntity(task);
        return ResponseEntity.ok(responseDto);
    }

    /**
     * Endpoint to update the status of a task.
     *
     * @param id The ID of the task to update.
     * @param statusUpdate Request body containing the new status, e.g., {"status": "IN_PROGRESS"}.
     * @return The updated task details.
     */
    @PatchMapping("/{id}/status") // Use PATCH for partial updates like status change
    public ResponseEntity<TaskResponse> updateTaskStatus(
            @PathVariable String id,
            @RequestBody Map<String, String> statusUpdate) { // Simple map for status

        String statusString = statusUpdate.get("status");
        if (statusString == null) {
            // Consider a more informative error response DTO
            return ResponseEntity.badRequest().build();
        }
        try {
            TaskStatus newStatus = TaskStatus.valueOf(statusString.toUpperCase());
            log.info("Received request to update status for task {} to {}", id, newStatus);
            Task updatedTask = taskMasterService.updateTaskStatus(id, newStatus);
            return ResponseEntity.ok(TaskResponse.fromEntity(updatedTask));
        } catch (IllegalArgumentException e) {
            log.warn("Invalid status value provided for task {}: {}", id, statusString);
            // Consider a more informative error response DTO
            return ResponseEntity.badRequest().build();
        } catch (TaskNotFoundException e) {
            log.warn("Cannot update status for task {}: {}", id, e.getMessage());
            throw e; // Let default handler return 404
        } catch (Exception e) {
            log.error("Error updating status for task {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint to delete a task by its ID.
     *
     * @param id The ID of the task to delete.
     * @return No content (204) on successful deletion or 404 if not found.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable String id) {
        log.info("Received request to delete task by ID: {}", id);
        try {
            taskMasterService.deleteTask(id);
            return ResponseEntity.noContent().build(); // 204
        } catch (TaskNotFoundException e) {
            log.warn("Cannot delete task: {}", e.getMessage());
            throw e; // Let default handler return 404
        } catch (Exception e) {
            log.error("Error deleting task {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Endpoint to trigger the generation of an execution plan for a specific task.
     *
     * @param id The ID of the task.
     * @return The updated task details including the generated execution plan, or an error response.
     */
    @PostMapping("/{id}/generate-plan")
    public ResponseEntity<TaskResponse> generateExecutionPlan(@PathVariable String id) {
        log.info("Received request to generate execution plan for task ID: {}", id);
        try {
            Task updatedTask = taskMasterService.generateAndSaveExecutionPlan(id);
            TaskResponse responseDto = TaskResponse.fromEntity(updatedTask);
            return ResponseEntity.ok(responseDto);
        } catch (TaskNotFoundException e) {
            log.warn("Cannot generate execution plan: {}", e.getMessage());
            throw e; // Re-throw for default handling (404)
        } catch (RuntimeException e) {
            // Catch failures from AI generation/parsing or other runtime issues in the service
            log.error("Error generating execution plan for task {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null); // Or custom error DTO
        }
    }

    /**
     * Endpoint to mark a task as COMPLETED.
     * The task must be in the IN_PROGRESS state.
     *
     * @param id The ID of the task to complete.
     * @return The updated task details or an error response (404, 409).
     */
    @PostMapping("/{id}/complete") // Using POST for triggering an action/state change
    public ResponseEntity<TaskResponse> markTaskAsCompleted(@PathVariable String id) {
        log.info("Received request to mark task {} as COMPLETED", id);
        try {
            Task completedTask = taskMasterService.completeTask(id);
            return ResponseEntity.ok(TaskResponse.fromEntity(completedTask));
        } catch (TaskNotFoundException | InvalidTaskStateException e) {
            // Handled by default Spring (404, 409 via @ResponseStatus)
            log.warn("Cannot complete task {}: {}", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error completing task {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Endpoint to mark a task as FAILED.
     * The task must not be in a terminal state already (COMPLETED, CANCELLED, FAILED).
     * Accepts an optional failure reason in the request body.
     *
     * @param id The ID of the task to mark as failed.
     * @param body Optional request body containing a "reason" field. e.g., {"reason": "External service unavailable"}
     * @return The updated task details or an error response (404, 409).
     */
    @PostMapping("/{id}/fail") // Using POST for triggering an action/state change
    public ResponseEntity<TaskResponse> markTaskAsFailed(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, String> body) { // Reason is optional

        String reason = (body != null) ? body.get("reason") : null;
        log.info("Received request to mark task {} as FAILED. Reason: {}", id, reason != null ? reason : "N/A");

        try {
            Task failedTask = taskMasterService.failTask(id, reason);
            return ResponseEntity.ok(TaskResponse.fromEntity(failedTask));
        } catch (TaskNotFoundException | InvalidTaskStateException e) {
            // Handled by default Spring (404, 409 via @ResponseStatus)
            log.warn("Cannot fail task {}: {}", id, e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Error failing task {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }

    /**
     * Endpoint to update the dependencies of a task.
     * Placeholder: Requires service implementation.
     *
     * @param id The ID of the task to update.
     * @param dependsOnTaskIds A set of Task IDs this task should depend on.
     * @return The updated task details or an error response.
     */
    @PutMapping("/{id}/dependencies")
    public ResponseEntity<TaskResponse> updateTaskDependencies(
            @PathVariable String id,
            @RequestBody Set<String> dependsOnTaskIds) { // Expect a set of IDs in the body

        log.info("Received request to update dependencies for task {} to: {}", id, dependsOnTaskIds);
        // TODO: Implement service logic for updating dependencies:
        //       taskMasterService.updateDependencies(id, dependsOnTaskIds);
        try {
            log.warn("Update dependencies endpoint not fully implemented in service yet.");
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
            // Task updatedTask = taskMasterService.updateDependencies(id, dependsOnTaskIds); // Implement this
            // return ResponseEntity.ok(TaskResponse.fromEntity(updatedTask));
        } catch (TaskNotFoundException | CircularDependencyException | InvalidTaskStateException e) {
            log.warn("Failed to update dependencies for task {}: {}", id, e.getMessage());
            throw e; // Let default handler manage 404/409
        } catch (Exception e) {
            log.error("Error updating dependencies for task {}: {}", id, e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);
        }
    }
}