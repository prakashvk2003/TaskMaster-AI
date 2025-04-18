package com.prakash.taskmaster.model;

public enum TaskStatus {
    PENDING,     // Task created, awaiting AI analysis/scheduling
    SCHEDULED,   // Task analyzed and scheduled
    IN_PROGRESS, // Task execution has started
    COMPLETED,   // Task finished successfully
    CANCELLED,   // Task will not be executed
    FAILED       // Task execution attempted but failed
}