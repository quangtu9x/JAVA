package com.td.application.workflows;

import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class WorkflowActionRequest {

    private String comment;

    private Map<String, Object> payload = new HashMap<>();
}
