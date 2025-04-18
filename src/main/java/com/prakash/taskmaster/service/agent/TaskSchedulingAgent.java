package com.prakash.taskmaster.service.agent;

import com.prakash.taskmaster.dto.AiScheduleSuggestion;
import com.prakash.taskmaster.dto.ScheduledTaskInfo;
import com.prakash.taskmaster.model.Task;
import com.prakash.taskmaster.model.TaskStatus;
import com.prakash.taskmaster.repository.TaskRepository;
import com.prakash.taskmaster.service.TaskMasterService; // Import TaskMasterService
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.parser.BeanOutputParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class TaskSchedulingAgent {

    private static final Logger log = LoggerFactory.getLogger(TaskSchedulingAgent.class);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final ChatModel chatModel;
    private final TaskRepository taskRepository;
    private final TaskMasterService taskMasterService; // Inject service for dependency checks

    // Updated prompt template to include dependency info
    private final String schedulingPromptTemplate = """
            You are an expert Task Scheduler AI. Your goal is to schedule the following tasks efficiently.
            Consider the tasks' priorities (CRITICAL > HIGH > MEDIUM > LOW), estimated durations, and dependencies.
            A task cannot be scheduled before all tasks it depends on are completed. (Assume completed tasks finish instantly for scheduling purposes).
            Assume tasks should be scheduled sequentially, starting from the reference time provided, respecting priorities and dependencies.
            Avoid scheduling tasks during non-working hours (e.g., assume standard 9 AM to 5 PM weekdays, or simply schedule back-to-back).
            For simplicity in this version, schedule them back-to-back starting from the reference time, respecting priority and dependencies.

            Reference Start Time for Scheduling: {referenceTime}

            Tasks to Schedule (only include tasks whose dependencies are met or have no dependencies):
            {taskList}

            Generate a schedule suggesting a specific start time ('suggestedStartTime' in ISO format like YYYY-MM-DDTHH:MM:SS) for each task ID provided in the list.
            Return the list of scheduled tasks with their IDs and suggested start times in the required JSON format. Ensure you only return schedules for tasks provided in the input list.

            {format}
            """;

    @Autowired
    public TaskSchedulingAgent(ChatModel chatModel,
                               TaskRepository taskRepository,
                               TaskMasterService taskMasterService) { // Inject service
        this.chatModel = chatModel;
        this.taskRepository = taskRepository;
        this.taskMasterService = taskMasterService; // Initialize service
    }

    /**
     * Fetches tasks needing scheduling, filters by met dependencies, and asks the AI for suggestions.
     *
     * @param referenceTime The time from which scheduling should ideally begin.
     * @return An AiScheduleSuggestion containing the AI's proposed schedule.
     */
    public AiScheduleSuggestion suggestSchedule(LocalDateTime referenceTime) {
        log.info("Requesting AI schedule suggestion starting from: {}", referenceTime.format(DATETIME_FORMATTER));

        // 1. Fetch candidate tasks (e.g., PENDING or SCHEDULED without a time)
        List<TaskStatus> candidateStatuses = List.of(TaskStatus.PENDING, TaskStatus.SCHEDULED);
        List<Task> candidateTasks = taskRepository.findByStatusInAndScheduledAtIsNull(candidateStatuses);

        if (CollectionUtils.isEmpty(candidateTasks)) {
            log.info("No candidate tasks found requiring scheduling.");
            return new AiScheduleSuggestion(); // Return empty suggestion
        }
        log.info("Found {} candidate tasks for potential scheduling.", candidateTasks.size());

        // 2. Filter tasks: Only include those whose dependencies are met
        List<Task> tasksReadyToSchedule = candidateTasks.stream()
                .filter(task -> {
                    boolean dependenciesMet = taskMasterService.areDependenciesMet(task);
                    if (!dependenciesMet) {
                        log.debug("Skipping Task ID {} from scheduling consideration: Dependencies not met.", task.getId());
                    }
                    return dependenciesMet;
                })
                .collect(Collectors.toList());

        if (CollectionUtils.isEmpty(tasksReadyToSchedule)) {
            log.info("No tasks found with met dependencies requiring scheduling.");
            return new AiScheduleSuggestion(); // Return empty suggestion
        }
        log.info("Filtered tasks: {} tasks have met dependencies and are ready for scheduling.", tasksReadyToSchedule.size());

        // 3. Format the *filtered* task list for the prompt
        String formattedTaskList = formatTasksForPromptWithDependencies(tasksReadyToSchedule);

        // 4. Prepare for AI interaction
        BeanOutputParser<AiScheduleSuggestion> outputParser = new BeanOutputParser<>(AiScheduleSuggestion.class);
        String formatInstructions = outputParser.getFormat();

        // 5. Create the prompt
        PromptTemplate promptTemplate = new PromptTemplate(schedulingPromptTemplate);
        Prompt prompt = promptTemplate.create(Map.of(
                "referenceTime", referenceTime.format(DATETIME_FORMATTER),
                "taskList", formattedTaskList,
                "format", formatInstructions
        ));
        log.debug("Sending scheduling prompt to AI: \n{}", prompt.getInstructions());

        // 6. Call AI and parse response
        try {
            var chatResponse = chatModel.call(prompt);
            String rawResponse = chatResponse.getResult().getOutput().getContent();
            log.debug("Received raw AI response for scheduling: \n{}", rawResponse);
            AiScheduleSuggestion suggestion = outputParser.parse(rawResponse);
            log.info("Successfully parsed AI schedule suggestion.");
            if (suggestion.getScheduledTasks() == null) {
                log.warn("AI response parsed, but 'scheduledTasks' list is null. Returning empty suggestion.");
                return new AiScheduleSuggestion();
            }
            return suggestion;
        } catch (Exception e) {
            log.error("Failed to generate or parse AI schedule suggestion: {}", e.getMessage(), e);
            throw new RuntimeException("AI scheduling suggestion failed", e);
        }
    }

    /**
     * Applies the AI-suggested schedule to the tasks in the database.
     */
    @Transactional // Ensure all updates happen together or none
    public void applyScheduleSuggestion(AiScheduleSuggestion suggestion) {
        if (suggestion == null || CollectionUtils.isEmpty(suggestion.getScheduledTasks())) {
            log.warn("Cannot apply schedule: Suggestion is null or empty.");
            return;
        }
        log.info("Applying AI schedule suggestion for {} tasks.", suggestion.getScheduledTasks().size());
        int updatedCount = 0;
        int failedCount = 0;
        for (ScheduledTaskInfo suggestedInfo : suggestion.getScheduledTasks()) {
            if (suggestedInfo.getTaskId() == null || suggestedInfo.getSuggestedStartTime() == null) {
                log.warn("Skipping invalid entry in schedule suggestion: {}", suggestedInfo);
                failedCount++;
                continue;
            }
            try {
                Task task = taskRepository.findById(suggestedInfo.getTaskId()).orElse(null);
                if (task != null) {
                    // Ensure task status is appropriate (e.g., still PENDING or SCHEDULED) before applying time
                    if (task.getStatus() == TaskStatus.PENDING || (task.getStatus() == TaskStatus.SCHEDULED && task.getScheduledAt() == null)) {
                        task.setScheduledAt(suggestedInfo.getSuggestedStartTime());
                        task.setStatus(TaskStatus.SCHEDULED); // Ensure status is SCHEDULED
                        taskRepository.save(task);
                        log.debug("Updated Task ID: {} with scheduled time: {}", task.getId(), task.getScheduledAt().format(DATETIME_FORMATTER));
                        updatedCount++;
                    } else {
                        log.warn("Skipping schedule update for Task ID {}. Task status is already {} or it already has a scheduled time.", task.getId(), task.getStatus());
                        failedCount++;
                    }
                } else {
                    log.warn("Task ID {} from schedule suggestion not found in database. Skipping.", suggestedInfo.getTaskId());
                    failedCount++;
                }
            } catch (Exception e) {
                log.error("Failed to apply schedule for Task ID {}: {}", suggestedInfo.getTaskId(), e.getMessage(), e);
                failedCount++;
            }
        }
        log.info("Finished applying schedule suggestion. Updated: {}, Failed/Skipped: {}", updatedCount, failedCount);
    }

    /**
     * Formats a list of tasks into a string suitable for the AI prompt, including dependency info.
     */
    private String formatTasksForPromptWithDependencies(List<Task> tasks) {
        if (CollectionUtils.isEmpty(tasks)) {
            return "No tasks ready to schedule.";
        }
        return tasks.stream()
                .map(task -> String.format(
                        "- Task ID: %s\n  Title: %s\n  Priority: %s\n  Estimated Duration (minutes): %d\n  Depends On: %s",
                        task.getId(),
                        task.getTitle(),
                        task.getPriority() != null ? task.getPriority() : "N/A",
                        task.getEstimatedDurationMinutes() != null ? task.getEstimatedDurationMinutes() : 0,
                        task.hasDependencies() ? task.getDependsOnTaskIds().toString() : "None"
                ))
                .collect(Collectors.joining("\n\n"));
    }
}