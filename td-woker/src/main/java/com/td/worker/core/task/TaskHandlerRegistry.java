package com.td.worker.core.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Registry handler theo {@code taskType}. Tự động thu thập mọi {@link TaskHandler} bean trong
 * context — thêm task mới chỉ cần thêm 1 {@code @Component} mới, KHÔNG sửa Core.
 */
@Slf4j
@Component
public class TaskHandlerRegistry {

    private final Map<String, TaskHandler> handlers = new ConcurrentHashMap<>();

    public TaskHandlerRegistry(List<TaskHandler> handlerBeans) {
        for (TaskHandler handler : handlerBeans) {
            if (handler.taskType() == null || handler.taskType().isBlank()) {
                log.warn("Bỏ qua TaskHandler {} vì taskType rỗng", handler.getClass().getName());
                continue;
            }
            TaskHandler previous = handlers.putIfAbsent(handler.taskType(), handler);
            if (previous != null) {
                log.warn("Trùng taskType '{}' giữa {} và {} — giữ handler đầu tiên",
                    handler.taskType(), previous.getClass().getName(), handler.getClass().getName());
            }
        }
        log.info("Đăng ký {} TaskHandler: {}", handlers.size(), handlers.keySet());
    }

    public Optional<TaskHandler> find(String taskType) {
        return Optional.ofNullable(handlers.get(taskType));
    }

    public TaskHandler require(String taskType) {
        TaskHandler handler = handlers.get(taskType);
        if (handler == null) {
            throw new TaskHandlerNotFoundException(taskType);
        }
        return handler;
    }

    public Map<String, TaskHandler> all() {
        return Map.copyOf(handlers);
    }
}