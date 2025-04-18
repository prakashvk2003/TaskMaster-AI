package com.prakash.taskmaster.repository;

import com.prakash.taskmaster.model.Task;
import com.prakash.taskmaster.model.TaskPriority;
import com.prakash.taskmaster.model.TaskStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository // Optional, but good practice for clarity
public interface TaskRepository extends MongoRepository<Task, String> { // Use String for ID

    // Find tasks by status
    List<Task> findByStatus(TaskStatus status);

    // Find tasks by priority
    List<Task> findByPriority(TaskPriority priority);

    // Find tasks by status, ordered by creation date (useful for FIFO within status)
    List<Task> findByStatusOrderByCreatedAtAsc(TaskStatus status);

    // Find tasks by status and priority, ordered by scheduled time (if available)
    List<Task> findByStatusAndPriorityOrderByScheduledAtAsc(TaskStatus status, TaskPriority priority);

    // Find tasks scheduled within a specific time range
    List<Task> findByScheduledAtBetween(LocalDateTime start, LocalDateTime end);

    // Find pending or scheduled tasks (useful for optimization)
    List<Task> findByStatusIn(List<TaskStatus> statuses);

    /**
     * Finds tasks in the specified statuses that do not have a scheduledAt time set yet.
     *
     * @param statuses List of statuses to check (e.g., PENDING, SCHEDULED).
     * @return List of tasks needing scheduling.
     */
    List<Task> findByStatusInAndScheduledAtIsNull(List<TaskStatus> statuses);

    /**
     * Finds tasks that are in SCHEDULED status and whose scheduled time is
     * less than or equal to the current time.
     *
     * @param status The status to filter by (typically SCHEDULED).
     * @param currentTime The current time to compare against scheduledAt.
     * @return A list of tasks ready to be started.
     */
    @Query("{ 'status': ?0, 'scheduledAt': { $lte: ?1 } }")
    List<Task> findByStatusAndScheduledAtLessThanEqual(TaskStatus status, LocalDateTime currentTime);

    /**
     * Finds tasks that are in one of the specified terminal statuses
     * AND whose completion timestamp is older than the given threshold time.
     *
     * @param terminalStatuses A list of statuses considered terminal (e.g., COMPLETED, FAILED, CANCELLED).
     * @param olderThan       The timestamp threshold; tasks completed before this time will be returned.
     * @return A list of old, terminal-state tasks.
     */
    List<Task> findByStatusInAndCompletedAtBefore(List<TaskStatus> terminalStatuses, LocalDateTime olderThan);

}