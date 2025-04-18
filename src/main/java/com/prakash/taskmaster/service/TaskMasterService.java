package com.prakash.taskmaster.service;

import com.prakash.taskmaster.dto.AiExecutionPlan;
import com.prakash.taskmaster.dto.AiTaskAnalysis;
import com.prakash.taskmaster.dto.CreateTaskRequest;
import com.prakash.taskmaster.exception.CircularDependencyException;
import com.prakash.taskmaster.exception.InvalidTaskStateException;
import com.prakash.taskmaster.exception.TaskNotFoundException;
import com.prakash.taskmaster.model.Task;
import com.prakash.taskmaster.model.TaskStatus;
import com.prakash.taskmaster.repository.TaskRepository;
import com.prakash.taskmaster.service.agent.TaskExecutionAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class TaskMasterService {

    private static final Logger log = LoggerFactory.getLogger(TaskMasterService.class);

    private final TaskRepository taskRepository;
    private final ChatModel chatModel;
    private final TaskExecutionAgent taskExecutionAgent;

    // Prompt template for AI task analysis
    private final String analysisPromptTemplate = """
            Analyze the following task details and provide the requested information.
            Task Title: "{title}"
            Task Description: "{description}"

            Based on the description, determine the following:
            1.  Priority: Assign a priority level (LOW, MEDIUM, HIGH, CRITICAL). Consider urgency, importance, and keywords.
            2.  Estimated Duration (minutes): Estimate the time needed in minutes. Provide only an integer.
            3.  Suggested Subtasks: List key subtasks or steps required. Provide a list of strings. If none are obvious, provide an empty list [].

            {format}
            """;

    @Autowired
    public TaskMasterService(TaskRepository taskRepository,
                             ChatModel chatModel,
                             TaskExecutionAgent taskExecutionAgent) {
        this.taskRepository = taskRepository;
        this.chatModel = chatModel;
        this.taskExecutionAgent = taskExecutionAgent;
    }

    /**
     * Creates a new task, performs AI analysis, checks for circular dependencies, and saves.
     *
     * @param request The request containing task title, description, and optional dependencies.
     * @return The created and analyzed Task object.
     * @throws TaskNotFoundException if a dependency task doesn't exist.
     * @throws CircularDependencyException if the dependencies create a cycle.
     * @throws RuntimeException if AI analysis fails.
     */
    @Transactional
    public Task createTask(CreateTaskRequest request) {
        log.info("Creating new task with title: {}", request.getTitle());
        Set<String> dependencyIds = request.getDependsOnTaskIds();

        // Validate provided dependency IDs exist
        if (dependencyIds != null && !dependencyIds.isEmpty()) {
            for (String depId : dependencyIds) {
                if (!taskRepository.existsById(depId)) {
                    log.error("Cannot create task: Dependency task with ID {} does not exist.", depId);
                    throw new TaskNotFoundException("Dependency task not found with ID: " + depId);
                }
            }
            log.debug("Validated {} dependency tasks exist.", dependencyIds.size());
        }

        // 1. Create and save initial task (to get an ID for cycle check)
        Task task = Task.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .status(TaskStatus.PENDING)
                .createdAt(LocalDateTime.now())
                .dependsOnTaskIds(dependencyIds)
                .build();
        Task savedTask = taskRepository.save(task); // Save to get an ID

        // 2. Check for Circular Dependencies
        if (savedTask.hasDependencies()) {
            try {
                checkForCircularDependencies(savedTask.getId(), savedTask.getDependsOnTaskIds());
                log.debug("No circular dependencies detected for new task ID: {}", savedTask.getId());
            } catch (CircularDependencyException e) {
                log.error("Circular dependency detected while creating task {}: {}", savedTask.getId(), e.getMessage());
                // Throwing exception will trigger rollback due to @Transactional
                throw e;
            }
        }
        log.debug("Initial task saved with ID: {} and dependencies: {}", savedTask.getId(), savedTask.getDependsOnTaskIds());


        // 3. Perform AI Analysis
        try {
            AiTaskAnalysis analysis = analyzeTaskWithAi(savedTask.getTitle(), savedTask.getDescription());
            // Fetch task again inside transaction to ensure we have the managed entity
            Task taskForAnalysis = taskRepository.findById(savedTask.getId()).orElseThrow(() -> new TaskNotFoundException("Task disappeared after save: " + savedTask.getId()));
            taskForAnalysis.setPriority(analysis.getPriority());
            taskForAnalysis.setEstimatedDurationMinutes(analysis.getEstimatedDurationMinutes());
            taskForAnalysis.setSuggestedSubtasks(analysis.getSuggestedSubtasks());
            // Scheduling agent will handle setting schedule time based on dependencies later
            taskForAnalysis.setStatus(TaskStatus.SCHEDULED); // Mark as ready for scheduling check
            Task updatedTask = taskRepository.save(taskForAnalysis);
            log.info("Task {} analyzed and updated by AI.", updatedTask.getId());
            return updatedTask;
        } catch (Exception e) {
            log.error("AI analysis failed for task {}: {}", savedTask.getId(), e.getMessage(), e);
            // Rollback should occur due to @Transactional.
            // Re-throw as a runtime exception to ensure rollback.
            throw new RuntimeException("Failed to analyze task using AI", e);
        }
    }

    private AiTaskAnalysis analyzeTaskWithAi(String title, String description) {
        log.debug("Performing AI analysis for task title: {}", title);
        BeanOutputParser<AiTaskAnalysis> outputParser = new BeanOutputParser<>(AiTaskAnalysis.class);
        String formatInstructions = outputParser.getFormat();
        PromptTemplate promptTemplate = new PromptTemplate(analysisPromptTemplate);
        Prompt prompt = promptTemplate.create(Map.of(
                "title", title,
                "description", description,
                "format", formatInstructions
        ));
        log.debug("Sending prompt to AI: \n{}", prompt.getInstructions());
        var chatResponse = chatModel.call(prompt);
        String rawResponse = chatResponse.getResult().getOutput().getContent();
        log.debug("Received raw AI response: \n{}", rawResponse);
        AiTaskAnalysis analysis = outputParser.parse(rawResponse);
        log.info("Successfully parsed AI analysis response.");
        return analysis;
    }

    /**
     * Checks for circular dependencies starting from a given task ID and its proposed dependencies.
     */
    private void checkForCircularDependencies(String taskId, Set<String> dependencyIds) throws CircularDependencyException {
        if (dependencyIds == null || dependencyIds.isEmpty()) return;
        Set<String> visited = new HashSet<>();
        for (String dependencyId : dependencyIds) {
            if (dfsCheckCycle(dependencyId, taskId, visited, new HashSet<>())) {
                throw new CircularDependencyException("Circular dependency detected: Task " + taskId + " cannot depend on Task " + dependencyId + " as it creates a cycle.");
            }
        }
    }

    /**
     * Performs Depth First Search to detect cycles back to a target node.
     */
    private boolean dfsCheckCycle(String currentNodeId, String targetNodeId, Set<String> visited, Set<String> recursionStack) {
        if (currentNodeId.equals(targetNodeId)) return true; // Cycle back to original task
        if (recursionStack.contains(currentNodeId)) return true; // Cycle within dependencies
        if (visited.contains(currentNodeId)) return false; // Already explored this path

        visited.add(currentNodeId);
        recursionStack.add(currentNodeId);

        Task currentNode = taskRepository.findById(currentNodeId).orElse(null);
        if (currentNode != null && currentNode.hasDependencies()) {
            for (String neighborId : currentNode.getDependsOnTaskIds()) {
                if (dfsCheckCycle(neighborId, targetNodeId, visited, recursionStack)) {
                    return true;
                }
            }
        }
        recursionStack.remove(currentNodeId);
        return false;
    }


    /**
     * Generates and saves the AI-powered execution plan for a specific task.
     */
    @Transactional
    public Task generateAndSaveExecutionPlan(String taskId) {
        log.info("Request received to generate execution plan for Task ID: {}", taskId);
        Task task = getTaskById(taskId);
        if (task.getExecutionSteps() != null && !task.getExecutionSteps().isEmpty()) {
            log.warn("Execution plan already exists for Task ID: {}. Overwriting.", taskId);
        }
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED) {
            log.warn("Skipping execution plan generation for task {} as it is already {}", taskId, task.getStatus());
            return task;
        }
        try {
            AiExecutionPlan executionPlan = taskExecutionAgent.generateExecutionPlan(task);
            task.setExecutionSteps(executionPlan.getExecutionSteps());
            Task updatedTask = taskRepository.save(task);
            log.info("Execution plan successfully generated and saved for Task ID: {}", taskId);
            return updatedTask;
        } catch (Exception e) {
            log.error("Failed to generate and save execution plan for Task ID {}: {}", taskId, e.getMessage(), e);
            throw new RuntimeException("Failed to generate execution plan for task " + taskId, e);
        }
    }

    /**
     * Checks if all dependencies for a given task are met (i.e., are COMPLETED).
     */
    public boolean areDependenciesMet(Task task) {
        if (!task.hasDependencies()) return true;
        Set<String> dependencyIds = task.getDependsOnTaskIds();
        log.debug("Checking {} dependencies for Task ID: {}", dependencyIds.size(), task.getId());
        List<Task> dependencyTasks = taskRepository.findAllById(dependencyIds);
        if (dependencyTasks.size() != dependencyIds.size()) {
            Set<String> foundIds = dependencyTasks.stream().map(Task::getId).collect(Collectors.toSet());
            Set<String> missingIds = dependencyIds.stream().filter(id -> !foundIds.contains(id)).collect(Collectors.toSet());
            log.warn("Could not find all dependencies for Task ID: {}. Missing IDs: {}. Treating as unmet.", task.getId(), missingIds);
            return false;
        }
        for (Task depTask : dependencyTasks) {
            if (depTask.getStatus() != TaskStatus.COMPLETED) {
                log.debug("Dependency not met for Task ID: {}. Prerequisite Task ID: {} has status: {}", task.getId(), depTask.getId(), depTask.getStatus());
                return false;
            }
        }
        log.debug("All dependencies met for Task ID: {}", task.getId());
        return true;
    }

    /**
     * Initiates the execution of a given task if dependencies are met.
     * Ensures an execution plan exists and updates status to IN_PROGRESS.
     */
    @Transactional
    public Task initiateTaskExecution(Task task) {
        if (task == null || task.getId() == null) {
            log.warn("Attempted to initiate execution for a null task.");
            return null;
        }
        String taskId = task.getId();
        log.info("Attempting to initiate execution for Task ID: {}", taskId);

        // Check dependencies first
        if (!areDependenciesMet(task)) {
            log.warn("Cannot initiate execution for Task ID: {}. Dependencies are not met.", taskId);
            return task; // Return unchanged task
        }
        log.info("Dependencies met for Task ID: {}. Proceeding with initiation.", taskId);

        try {
            Task currentTask = task; // Use the passed-in task initially
            // Ensure execution plan exists
            if (currentTask.getExecutionSteps() == null || currentTask.getExecutionSteps().isEmpty()) {
                log.info("Execution plan not found for Task ID: {}. Generating now...", taskId);
                // generateAndSaveExecutionPlan fetches and saves, returns the updated task
                currentTask = generateAndSaveExecutionPlan(taskId);
                if (currentTask.getExecutionSteps() == null || currentTask.getExecutionSteps().isEmpty()) {
                    log.error("Execution plan generation failed for Task ID {} but no exception was caught directly. Cannot proceed.", taskId);
                    throw new RuntimeException("Execution plan generation failed unexpectedly for task " + taskId);
                }
                log.info("Execution plan generated successfully for Task ID: {}", taskId);
            } else {
                log.debug("Execution plan already exists for Task ID: {}", taskId);
            }

            // Update status to IN_PROGRESS using the potentially updated task object
            log.info("Updating status to IN_PROGRESS for Task ID: {}", taskId);
            // Use the latest task state (currentTask) for the status update
            currentTask.setStatus(TaskStatus.IN_PROGRESS);
            currentTask.setStartedAt(LocalDateTime.now());
            return taskRepository.save(currentTask);

        } catch (Exception e) {
            log.error("Failed to initiate execution for Task ID {}: {}", taskId, e.getMessage(), e);
            try {
                Task failedTask = getTaskById(taskId); // Get fresh state
                failedTask.setStatus(TaskStatus.FAILED);
                failedTask.setCompletedAt(LocalDateTime.now());
                failedTask.setAiAnalysisNotes((failedTask.getAiAnalysisNotes() == null ? "" : failedTask.getAiAnalysisNotes() + "\n") + "Execution initiation failed: " + e.getMessage());
                taskRepository.save(failedTask);
                log.warn("Marked Task ID {} as FAILED due to initiation error.", taskId);
            } catch (Exception ex) {
                log.error("Failed to mark task {} as FAILED after initiation error: {}", taskId, ex.getMessage());
            }
            // Do not re-throw here if we want the orchestrator to continue with other tasks,
            // return the task in its FAILED state.
            return getTaskById(taskId); // Return the task which should now be FAILED
        }
    }


    // --- Basic CRUD and Status Update Methods ---

    public List<Task> getAllTasks() {
        log.debug("Fetching all tasks");
        return taskRepository.findAll();
    }

    public Task getTaskById(String id) {
        log.debug("Fetching task by ID: {}", id);
        return taskRepository.findById(id)
                .orElseThrow(() -> new TaskNotFoundException("Task not found with ID: " + id));
    }

    public List<Task> getTasksByStatus(TaskStatus status) {
        log.debug("Fetching tasks by status: {}", status);
        return taskRepository.findByStatus(status);
    }

    @Transactional
    public Task updateTaskStatus(String id, TaskStatus newStatus) {
        Task task = getTaskById(id); // Reuse getTaskById to handle not found case
        log.info("Updating status for task {} from {} to {}", id, task.getStatus(), newStatus);
        task.setStatus(newStatus);

        switch (newStatus) {
            case IN_PROGRESS:
                if (task.getStartedAt() == null) task.setStartedAt(LocalDateTime.now());
                break;
            case COMPLETED:
            case CANCELLED:
            case FAILED:
                if (task.getCompletedAt() == null) task.setCompletedAt(LocalDateTime.now());
                break;
        }
        return taskRepository.save(task);
    }

    public void deleteTask(String id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException("Task not found with ID: " + id);
        }
        // TODO: Add check? Cannot delete if other tasks depend on it?
        log.warn("Deleting task with ID: {}", id); // Use warn for destructive actions
        taskRepository.deleteById(id);
    }

    /**
     * Finds all tasks that are currently scheduled and due to start based on the current time.
     */
    public List<Task> findDueTasks() {
        LocalDateTime now = LocalDateTime.now();
        log.debug("Finding tasks due for execution (Status: SCHEDULED, Scheduled At <= {})", now);
        List<Task> dueTasks = taskRepository.findByStatusAndScheduledAtLessThanEqual(TaskStatus.SCHEDULED, now);
        log.info("Found {} tasks due for execution.", dueTasks.size());
        return dueTasks;
    }

    /**
     * Marks a task as COMPLETED. Validates status.
     */
    @Transactional
    public Task completeTask(String taskId) {
        log.info("Attempting to mark Task ID: {} as COMPLETED", taskId);
        Task task = getTaskById(taskId);
        if (task.getStatus() != TaskStatus.IN_PROGRESS) {
            log.warn("Cannot complete Task ID: {}. Task is not IN_PROGRESS. Current status: {}", taskId, task.getStatus());
            throw new InvalidTaskStateException("Task " + taskId + " cannot be completed as it is not IN_PROGRESS (status: " + task.getStatus() + ")");
        }
        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        Task updatedTask = taskRepository.save(task);
        log.info("Task ID: {} successfully marked as COMPLETED.", taskId);
        return updatedTask;
    }

    /**
     * Marks a task as FAILED. Validates status.
     */
    @Transactional
    public Task failTask(String taskId, String failureReason) {
        log.warn("Attempting to mark Task ID: {} as FAILED. Reason: {}", taskId, failureReason != null ? failureReason : "N/A");
        Task task = getTaskById(taskId);
        if (task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.FAILED) {
            log.warn("Cannot mark Task ID: {} as FAILED. Task is already in a terminal state: {}", taskId, task.getStatus());
            throw new InvalidTaskStateException("Task " + taskId + " cannot be marked as failed as it is already in a terminal state (" + task.getStatus() + ")");
        }
        task.setStatus(TaskStatus.FAILED);
        task.setCompletedAt(LocalDateTime.now());
        if (failureReason != null && !failureReason.isBlank()) {
            String existingNotes = task.getAiAnalysisNotes() == null ? "" : task.getAiAnalysisNotes();
            task.setAiAnalysisNotes(existingNotes + "\nFailure Reason: " + failureReason);
        }
        Task updatedTask = taskRepository.save(task);
        log.info("Task ID: {} successfully marked as FAILED.", taskId);
        return updatedTask;
    }

    /**
     * Deletes tasks that have been in a terminal state longer than the retention period.
     */
    @Transactional
    public long cleanupOldTasks(Duration retentionPeriod) {
        LocalDateTime cutoffTime = LocalDateTime.now().minus(retentionPeriod);
        List<TaskStatus> terminalStatuses = List.of(TaskStatus.COMPLETED, TaskStatus.FAILED, TaskStatus.CANCELLED);
        log.info("Starting cleanup of tasks in terminal states ({}) completed before {}", terminalStatuses, cutoffTime);
        List<Task> tasksToDelete = taskRepository.findByStatusInAndCompletedAtBefore(terminalStatuses, cutoffTime);
        if (tasksToDelete.isEmpty()) {
            log.info("No old tasks found to cleanup.");
            return 0;
        }
        log.warn("Found {} tasks to delete (completed before {})", tasksToDelete.size(), cutoffTime);
        try {
            List<String> idsToDelete = tasksToDelete.stream().map(Task::getId).toList();
            log.warn("Deleting Task IDs: {}", idsToDelete);
            taskRepository.deleteAll(tasksToDelete);
            log.info("Successfully deleted {} old tasks.", tasksToDelete.size());
            return tasksToDelete.size();
        } catch (Exception e) {
            log.error("Error occurred during task cleanup deletion: {}", e.getMessage(), e);
            return 0;
        }
    }
}