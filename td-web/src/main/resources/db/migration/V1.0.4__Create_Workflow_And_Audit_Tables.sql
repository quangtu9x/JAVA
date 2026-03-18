-- Separate workflow and audit domains from document domain
-- V1.0.4__Create_Workflow_And_Audit_Tables.sql

-- Workflow definition (template) tables
CREATE TABLE workflow_definitions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_code VARCHAR(100) NOT NULL,
    workflow_name VARCHAR(255) NOT NULL,
    applies_to VARCHAR(100) NOT NULL DEFAULT 'DOCUMENT',
    version_no INT NOT NULL DEFAULT 1,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    description TEXT,
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_by UUID,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    last_modified_by UUID,
    last_modified_on TIMESTAMP WITH TIME ZONE,
    deleted_on TIMESTAMP WITH TIME ZONE,
    deleted_by UUID,
    CONSTRAINT UQ_workflow_definitions_code_version UNIQUE (workflow_code, version_no)
);

CREATE INDEX IX_workflow_definitions_code ON workflow_definitions(workflow_code);
CREATE INDEX IX_workflow_definitions_applies_to ON workflow_definitions(applies_to);
CREATE INDEX IX_workflow_definitions_is_active ON workflow_definitions(is_active);
CREATE INDEX IX_workflow_definitions_deleted_on ON workflow_definitions(deleted_on);

CREATE TABLE workflow_definition_steps (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    definition_id UUID NOT NULL,
    step_code VARCHAR(100) NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(50) NOT NULL DEFAULT 'USER_TASK',
    step_order INT NOT NULL,
    is_start BOOLEAN NOT NULL DEFAULT FALSE,
    is_end BOOLEAN NOT NULL DEFAULT FALSE,
    sla_hours INT,
    allowed_roles_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    CONSTRAINT FK_workflow_definition_steps_definition FOREIGN KEY (definition_id) REFERENCES workflow_definitions(id),
    CONSTRAINT UQ_workflow_definition_steps_code UNIQUE (definition_id, step_code),
    CONSTRAINT UQ_workflow_definition_steps_order UNIQUE (definition_id, step_order)
);

CREATE INDEX IX_workflow_definition_steps_definition_id ON workflow_definition_steps(definition_id);
CREATE INDEX IX_workflow_definition_steps_step_type ON workflow_definition_steps(step_type);

CREATE TABLE workflow_definition_transitions (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    definition_id UUID NOT NULL,
    from_step_id UUID,
    to_step_id UUID NOT NULL,
    action_code VARCHAR(100) NOT NULL,
    action_name VARCHAR(255) NOT NULL,
    required_roles_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    condition_expression TEXT,
    is_auto BOOLEAN NOT NULL DEFAULT FALSE,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    CONSTRAINT FK_workflow_definition_transitions_definition FOREIGN KEY (definition_id) REFERENCES workflow_definitions(id),
    CONSTRAINT FK_workflow_definition_transitions_from_step FOREIGN KEY (from_step_id) REFERENCES workflow_definition_steps(id),
    CONSTRAINT FK_workflow_definition_transitions_to_step FOREIGN KEY (to_step_id) REFERENCES workflow_definition_steps(id),
    CONSTRAINT UQ_workflow_definition_transitions_rule UNIQUE (definition_id, from_step_id, action_code)
);

CREATE INDEX IX_workflow_definition_transitions_definition_id ON workflow_definition_transitions(definition_id);
CREATE INDEX IX_workflow_definition_transitions_from_step_id ON workflow_definition_transitions(from_step_id);
CREATE INDEX IX_workflow_definition_transitions_to_step_id ON workflow_definition_transitions(to_step_id);

-- Workflow runtime tables
CREATE TABLE workflow_instances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    definition_id UUID NOT NULL,
    entity_type VARCHAR(100) NOT NULL DEFAULT 'DOCUMENT',
    entity_id UUID NOT NULL,
    current_step_id UUID,
    current_status VARCHAR(50) NOT NULL,
    business_status VARCHAR(50),
    started_by UUID,
    started_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    completed_on TIMESTAMP WITH TIME ZONE,
    cancelled_on TIMESTAMP WITH TIME ZONE,
    cancelled_by UUID,
    cancel_reason TEXT,
    context_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    version_no BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT FK_workflow_instances_definition FOREIGN KEY (definition_id) REFERENCES workflow_definitions(id),
    CONSTRAINT FK_workflow_instances_current_step FOREIGN KEY (current_step_id) REFERENCES workflow_definition_steps(id)
);

CREATE INDEX IX_workflow_instances_definition_id ON workflow_instances(definition_id);
CREATE INDEX IX_workflow_instances_entity ON workflow_instances(entity_type, entity_id);
CREATE INDEX IX_workflow_instances_current_status ON workflow_instances(current_status);
CREATE INDEX IX_workflow_instances_started_on ON workflow_instances(started_on);
CREATE INDEX IX_workflow_instances_completed_on ON workflow_instances(completed_on);

CREATE UNIQUE INDEX UQ_workflow_instances_active_entity
    ON workflow_instances(entity_type, entity_id)
    WHERE completed_on IS NULL AND cancelled_on IS NULL;

CREATE TABLE workflow_tasks (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    workflow_instance_id UUID NOT NULL,
    step_id UUID NOT NULL,
    task_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    assignee_user_id UUID,
    assignee_group_code VARCHAR(100),
    due_on TIMESTAMP WITH TIME ZONE,
    completed_on TIMESTAMP WITH TIME ZONE,
    completed_by UUID,
    decision_comment TEXT,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    created_by UUID,
    CONSTRAINT FK_workflow_tasks_instance FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instances(id),
    CONSTRAINT FK_workflow_tasks_step FOREIGN KEY (step_id) REFERENCES workflow_definition_steps(id)
);

CREATE INDEX IX_workflow_tasks_instance_id ON workflow_tasks(workflow_instance_id);
CREATE INDEX IX_workflow_tasks_task_status ON workflow_tasks(task_status);
CREATE INDEX IX_workflow_tasks_assignee_user_id ON workflow_tasks(assignee_user_id);
CREATE INDEX IX_workflow_tasks_assignee_group_code ON workflow_tasks(assignee_group_code);
CREATE INDEX IX_workflow_tasks_due_on ON workflow_tasks(due_on);

CREATE TABLE workflow_history (
    id BIGSERIAL PRIMARY KEY,
    workflow_instance_id UUID NOT NULL,
    transition_id UUID,
    from_step_id UUID,
    to_step_id UUID,
    from_status VARCHAR(50),
    to_status VARCHAR(50),
    action_code VARCHAR(100) NOT NULL,
    action_name VARCHAR(255),
    actor_id UUID,
    actor_username VARCHAR(200),
    actor_roles_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    action_comment TEXT,
    action_payload_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_channel VARCHAR(50),
    request_id VARCHAR(100),
    correlation_id VARCHAR(100),
    occurred_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    CONSTRAINT FK_workflow_history_instance FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instances(id),
    CONSTRAINT FK_workflow_history_transition FOREIGN KEY (transition_id) REFERENCES workflow_definition_transitions(id),
    CONSTRAINT FK_workflow_history_from_step FOREIGN KEY (from_step_id) REFERENCES workflow_definition_steps(id),
    CONSTRAINT FK_workflow_history_to_step FOREIGN KEY (to_step_id) REFERENCES workflow_definition_steps(id)
);

CREATE INDEX IX_workflow_history_instance_id ON workflow_history(workflow_instance_id);
CREATE INDEX IX_workflow_history_action_code ON workflow_history(action_code);
CREATE INDEX IX_workflow_history_actor_id ON workflow_history(actor_id);
CREATE INDEX IX_workflow_history_occurred_on ON workflow_history(occurred_on);
CREATE INDEX IX_workflow_history_correlation_id ON workflow_history(correlation_id);

-- Append-only audit log domain
CREATE TABLE audit_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_time TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    actor_id UUID,
    actor_username VARCHAR(200),
    actor_roles_json JSONB NOT NULL DEFAULT '[]'::jsonb,
    action VARCHAR(100) NOT NULL,
    result VARCHAR(30) NOT NULL DEFAULT 'SUCCESS',
    object_type VARCHAR(100) NOT NULL,
    object_id VARCHAR(100) NOT NULL,
    object_display VARCHAR(300),
    before_data_json JSONB,
    after_data_json JSONB,
    delta_json JSONB,
    metadata_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    source_service VARCHAR(100) NOT NULL DEFAULT 'td-webapi',
    source_module VARCHAR(100),
    ip_address VARCHAR(64),
    user_agent VARCHAR(500),
    request_id VARCHAR(100),
    correlation_id VARCHAR(100),
    trace_id VARCHAR(100)
);

CREATE INDEX IX_audit_events_event_time ON audit_events(event_time);
CREATE INDEX IX_audit_events_object ON audit_events(object_type, object_id);
CREATE INDEX IX_audit_events_actor_id ON audit_events(actor_id);
CREATE INDEX IX_audit_events_action ON audit_events(action);
CREATE INDEX IX_audit_events_result ON audit_events(result);
CREATE INDEX IX_audit_events_correlation_id ON audit_events(correlation_id);
CREATE INDEX IX_audit_events_trace_id ON audit_events(trace_id);

-- Outbox table for reliable event publish to Redis/WebSocket/Elasticsearch updaters
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID,
    event_type VARCHAR(150) NOT NULL,
    payload_json JSONB NOT NULL,
    headers_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    next_retry_on TIMESTAMP WITH TIME ZONE,
    published_on TIMESTAMP WITH TIME ZONE,
    last_error TEXT,
    created_on TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX IX_outbox_events_status ON outbox_events(status);
CREATE INDEX IX_outbox_events_created_on ON outbox_events(created_on);
CREATE INDEX IX_outbox_events_next_retry_on ON outbox_events(next_retry_on);
CREATE INDEX IX_outbox_events_aggregate ON outbox_events(aggregate_type, aggregate_id);

-- Document domain remains separate but references workflow runtime
ALTER TABLE documents
    ADD COLUMN workflow_instance_id UUID,
    ADD COLUMN status_source VARCHAR(30) NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN submitted_on TIMESTAMP WITH TIME ZONE,
    ADD COLUMN approved_on TIMESTAMP WITH TIME ZONE,
    ADD COLUMN rejected_on TIMESTAMP WITH TIME ZONE;

ALTER TABLE documents
    ADD CONSTRAINT FK_documents_workflow_instance
    FOREIGN KEY (workflow_instance_id) REFERENCES workflow_instances(id);

CREATE INDEX IX_documents_workflow_instance_id ON documents(workflow_instance_id);
CREATE INDEX IX_documents_status_source ON documents(status_source);

COMMENT ON TABLE workflow_definitions IS 'Workflow templates and versions';
COMMENT ON TABLE workflow_instances IS 'Workflow runtime instances, one active per business entity';
COMMENT ON TABLE workflow_history IS 'Immutable history of workflow transitions';
COMMENT ON TABLE audit_events IS 'Immutable append-only audit log for compliance and traceability';
COMMENT ON TABLE outbox_events IS 'Reliable integration events for async delivery';
COMMENT ON COLUMN documents.status_source IS 'MANUAL for legacy updates, WORKFLOW when owned by workflow service';