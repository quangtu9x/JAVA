package com.td.worker.core.task;

/** Không tìm thấy handler cho taskType — unit sẽ FAILED (lỗi cấu hình producer). */
public class TaskHandlerNotFoundException extends RuntimeException {

    public TaskHandlerNotFoundException(String taskType) {
        super("Không có TaskHandler cho taskType '" + taskType + "' — kiểm tra producer/config");
    }
}