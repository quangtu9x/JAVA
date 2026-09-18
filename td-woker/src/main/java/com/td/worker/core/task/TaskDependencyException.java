package com.td.worker.core.task;

/** Handler ném exception này khi dependency tạm thời chưa sẵn sàng (không tiêu maxAttempts). */
public class TaskDependencyException extends RuntimeException {
    public TaskDependencyException(String message) { super(message); }
    public TaskDependencyException(String message, Throwable cause) { super(message, cause); }
}
