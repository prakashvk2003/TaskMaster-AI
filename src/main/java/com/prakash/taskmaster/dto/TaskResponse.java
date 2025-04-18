package com.prakash.taskmaster.dto;

import com.prakash.taskmaster.model.Task;
import com.prakash.taskmaster.model.TaskPriority;
import com.prakash.taskmaster.model.TaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TaskResponse {

    private String id;
    private String title;
    private String description;
    private TaskPriority priority;
    private TaskStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private Integer estimatedDurationMinutes;
    private List<String> suggestedSubtasks;
    private List<String> executionSteps; // Will be populated later by ExecutionAgent
    private Set<String> dependsOnTaskIds; // Add dependency info

    // Factory method to convert Task entity to TaskResponse DTO
    public static TaskResponse fromEntity(Task task) {
        if (task == null) {
            return null;
        }
        return TaskResponse.builder()
                .id(task.getId())
                .title(task.getTitle())
                .description(task.getDescription())
                .priority(task.getPriority())
                .status(task.getStatus())
                .createdAt(task.getCreatedAt())
                .scheduledAt(task.getScheduledAt())
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .estimatedDurationMinutes(task.getEstimatedDurationMinutes())
                .suggestedSubtasks(task.getSuggestedSubtasks())
                .executionSteps(task.getExecutionSteps())
                .dependsOnTaskIds(task.getDependsOnTaskIds()) // Map dependencies
                .build();
    }
}