package com.td.worker.tasks.sample;

import com.fasterxml.jackson.databind.JsonNode;
import com.td.worker.core.task.TaskContext;
import com.td.worker.core.task.TaskHandler;
import com.td.worker.core.task.UnitResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handler MẪU (taskType {@code sample.echo}) — minh hoạ cách thêm 1 task mới vào Worker Core:
 * chỉ cần 1 {@code @Component} implements {@link TaskHandler}; không sửa gì trong Core.
 * Producer publish message với {@code "taskType":"sample.echo"} là được.
 */
@Slf4j
@Component
public class SampleEchoTaskHandler implements TaskHandler {

    @Override
    public String taskType() {
        return "sample.echo";
    }

    @Override
    public UnitResult execute(TaskContext context, JsonNode payload) {
        log.info("[sample.echo] messageId={}, unitId={}, tenantId={}, payload={}",
            context.messageId(), context.unitId(), context.tenantId(),
            payload == null ? "null" : payload.toString());
        return UnitResult.success();
    }
}