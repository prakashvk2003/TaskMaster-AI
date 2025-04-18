package com.prakash.taskmaster.orchestrator;

import com.prakash.taskmaster.dto.AiScheduleSuggestion;
import com.prakash.taskmaster.model.Task;
import com.prakash.taskmaster.model.TaskStatus;
import com.prakash.taskmaster.service.TaskMasterService;
import com.prakash.taskmaster.service.agent.TaskSchedulingAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TaskMasterOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TaskMasterOrchestrator.class);

    private final TaskSchedulingAgent taskSchedulingAgent;
    private final TaskMasterService taskMasterService;

    // Inject the retention period from properties
    @Value("${taskmaster.cleanup.retention:P30D}") // Default to 30 days (ISO-8601 Duration)
    private String retentionPeriodString;

    @Autowired
    public TaskMasterOrchestrator(TaskSchedulingAgent taskSchedulingAgent,
                                  TaskMasterService taskMasterService) {
        this.taskSchedulingAgent = taskSchedulingAgent;
        this.taskMasterService = taskMasterService;
    }

    /**
     * Periodically runs the AI-powered task scheduling optimization.
     * Fetches tasks needing scheduling (with met dependencies), gets suggestions from the AI,
     * and applies the suggestions.
     */
    @Scheduled(fixedDelayString = "${taskmaster.scheduling.delay:PT1H}") // Default: Run every hour after previous completion
    public void runPeriodicScheduling() {
        log.info("==== Orchestrator: Starting periodic task scheduling run ====");
        LocalDateTime schedulingStartTime = LocalDateTime.now();

        try {
            // 1. Get schedule suggestions from the AI agent (agent handles dependency filtering)
            log.info("Orchestrator: Requesting schedule suggestions from TaskSchedulingAgent.");
            AiScheduleSuggestion suggestion = taskSchedulingAgent.suggestSchedule(schedulingStartTime);

            if (suggestion == null || suggestion.getScheduledTasks() == null || suggestion.getScheduledTasks().isEmpty()) {
                log.info("Orchestrator: No scheduling suggestions received or needed. Skipping application.");
            } else {
                // 2. Apply the suggestions
                log.info("Orchestrator: Applying schedule suggestions.");
                taskSchedulingAgent.applyScheduleSuggestion(suggestion);
            }

        } catch (Exception e) {
            log.error("Orchestrator: Error occurred during periodic scheduling run: {}", e.getMessage(), e);
        } finally {
            log.info("==== Orchestrator: Finished periodic task scheduling run ====");
        }
    }

    /**
     * Periodically checks for tasks that are SCHEDULED and due to start.
     * For each due task, ensures dependencies are met, an execution plan exists (generating if needed),
     * and updates the status to IN_PROGRESS.
     */
    @Scheduled(fixedDelayString = "${taskmaster.execution.delay:PT1M}") // Default: Check every minute
    public void triggerTaskExecution() {
        log.info("==== Orchestrator: Checking for tasks ready for execution ====");

        try {
            // 1. Find tasks that are scheduled and due
            List<Task> dueTasks = taskMasterService.findDueTasks();

            if (dueTasks.isEmpty()) {
                log.info("Orchestrator: No tasks are currently due for execution.");
            } else {
                log.info("Orchestrator: Found {} tasks due for execution. Attempting to initiate...", dueTasks.size());
                int successCount = 0;
                int failureCount = 0;
                int skippedCount = 0;

                for (Task task : dueTasks) {
                    try {
                        // 2. Initiate execution (checks deps, generates plan if needed, updates status)
                        Task updatedTask = taskMasterService.initiateTaskExecution(task);
                        if (updatedTask.getStatus() == TaskStatus.IN_PROGRESS) {
                            log.info("Orchestrator: Successfully initiated execution for Task ID: {}", task.getId());
                            successCount++;
                        } else if (updatedTask.getStatus() == TaskStatus.SCHEDULED) {
                            // Status didn't change, likely due to unmet dependencies logged in service
                            log.info("Orchestrator: Initiation skipped for Task ID: {} (likely unmet dependencies).", task.getId());
                            skippedCount++;
                        } else {
                            // Task might have been marked FAILED during initiation attempt
                            log.warn("Orchestrator: Initiation attempt for Task ID: {} resulted in status: {}", task.getId(), updatedTask.getStatus());
                            failureCount++;
                        }
                    } catch (Exception e) {
                        // Log error but continue processing other due tasks
                        log.error("Orchestrator: Failed to initiate execution for Task ID {}: {}", task.getId(), e.getMessage());
                        failureCount++;
                        // The service method initiateTaskExecution tries to mark the task as FAILED internally
                    }
                }
                log.info("Orchestrator: Initiation summary - Success: {}, Skipped: {}, Failed: {}", successCount, skippedCount, failureCount);
            }
        } catch (Exception e) {
            // Catch errors during the finding phase or unexpected issues
            log.error("Orchestrator: Error occurred during task execution triggering process: {}", e.getMessage(), e);
        } finally {
            log.info("==== Orchestrator: Finished checking for tasks ready for execution ====");
        }
    }

    /**
     * Periodically runs cleanup tasks, such as deleting old completed/failed/cancelled tasks
     * based on the configured retention period.
     */
    @Scheduled(cron = "${taskmaster.cleanup.cron:0 0 3 * * *}") // Default: Run daily at 3 AM
    public void runCleanupTasks() {
        log.info("==== Orchestrator: Starting periodic cleanup run ====");
        try {
            // Parse the retention period from the configured string
            Duration retentionPeriod = Duration.parse(retentionPeriodString);
            log.info("Orchestrator: Cleaning up tasks older than {}", retentionPeriodString);

            // Call the service method to perform cleanup
            long deletedCount = taskMasterService.cleanupOldTasks(retentionPeriod);

            log.info("Orchestrator: Cleanup finished. Deleted {} tasks.", deletedCount);

        } catch (Exception e) {
            // Log errors during parsing or cleanup, but don't stop the scheduler
            log.error("Orchestrator: Error occurred during cleanup run: {}", e.getMessage(), e);
        } finally {
            log.info("==== Orchestrator: Finished periodic cleanup run ====");
        }
    }
}