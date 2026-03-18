-- Seed default workflow definitions for document lifecycle
-- V1.0.5__Seed_Default_Workflow_Definitions.sql

-- 1) Definition headers
INSERT INTO workflow_definitions (
    id,
    workflow_code,
    workflow_name,
    applies_to,
    version_no,
    is_active,
    description,
    config_json,
    created_on
) VALUES
    (
        '10000000-0000-0000-0000-000000000001',
        'DOC_APPROVAL',
        'Document Approval Workflow',
        'DOCUMENT',
        1,
        TRUE,
        'Quy trinh trinh duyet va phe duyet van ban',
        '{"statusSource":"WORKFLOW","defaultBusinessStatus":"DRAFT"}'::jsonb,
        NOW()
    ),
    (
        '10000000-0000-0000-0000-000000000002',
        'DOC_ISSUANCE',
        'Document Issuance Workflow',
        'DOCUMENT',
        1,
        TRUE,
        'Quy trinh ky va ban hanh van ban',
        '{"statusSource":"WORKFLOW","defaultBusinessStatus":"IN_REVIEW"}'::jsonb,
        NOW()
    ),
    (
        '10000000-0000-0000-0000-000000000003',
        'DOC_RECALL',
        'Document Recall Workflow',
        'DOCUMENT',
        1,
        TRUE,
        'Quy trinh thu hoi van ban da ban hanh',
        '{"statusSource":"WORKFLOW","defaultBusinessStatus":"ARCHIVED"}'::jsonb,
        NOW()
    );

-- 2) Steps for DOC_APPROVAL
INSERT INTO workflow_definition_steps (
    id,
    definition_id,
    step_code,
    step_name,
    step_type,
    step_order,
    is_start,
    is_end,
    sla_hours,
    allowed_roles_json,
    metadata_json,
    created_on
) VALUES
    (
        '11000000-0000-0000-0000-000000000101',
        '10000000-0000-0000-0000-000000000001',
        'DRAFT',
        'Soan thao',
        'USER_TASK',
        1,
        TRUE,
        FALSE,
        24,
        '["DOC_EDITOR","ADMIN"]'::jsonb,
        '{"businessStatus":"DRAFT"}'::jsonb,
        NOW()
    ),
    (
        '11000000-0000-0000-0000-000000000102',
        '10000000-0000-0000-0000-000000000001',
        'REVIEW',
        'Cho duyet',
        'USER_TASK',
        2,
        FALSE,
        FALSE,
        48,
        '["DOC_APPROVER","ADMIN"]'::jsonb,
        '{"businessStatus":"IN_REVIEW"}'::jsonb,
        NOW()
    ),
    (
        '11000000-0000-0000-0000-000000000103',
        '10000000-0000-0000-0000-000000000001',
        'APPROVED',
        'Da phe duyet',
        'END_EVENT',
        3,
        FALSE,
        TRUE,
        NULL,
        '["DOC_APPROVER","ADMIN"]'::jsonb,
        '{"businessStatus":"APPROVED"}'::jsonb,
        NOW()
    ),
    (
        '11000000-0000-0000-0000-000000000104',
        '10000000-0000-0000-0000-000000000001',
        'REJECTED',
        'Tu choi',
        'END_EVENT',
        4,
        FALSE,
        TRUE,
        NULL,
        '["DOC_APPROVER","ADMIN"]'::jsonb,
        '{"businessStatus":"REJECTED"}'::jsonb,
        NOW()
    );

-- 3) Steps for DOC_ISSUANCE
INSERT INTO workflow_definition_steps (
    id,
    definition_id,
    step_code,
    step_name,
    step_type,
    step_order,
    is_start,
    is_end,
    sla_hours,
    allowed_roles_json,
    metadata_json,
    created_on
) VALUES
    (
        '12000000-0000-0000-0000-000000000201',
        '10000000-0000-0000-0000-000000000002',
        'PREPARED',
        'Da chuan bi',
        'USER_TASK',
        1,
        TRUE,
        FALSE,
        24,
        '["DOC_EDITOR","ADMIN"]'::jsonb,
        '{"businessStatus":"SUBMITTED"}'::jsonb,
        NOW()
    ),
    (
        '12000000-0000-0000-0000-000000000202',
        '10000000-0000-0000-0000-000000000002',
        'SIGNED',
        'Da ky',
        'USER_TASK',
        2,
        FALSE,
        FALSE,
        24,
        '["DOC_SIGNER","ADMIN"]'::jsonb,
        '{"businessStatus":"IN_REVIEW"}'::jsonb,
        NOW()
    ),
    (
        '12000000-0000-0000-0000-000000000203',
        '10000000-0000-0000-0000-000000000002',
        'ISSUED',
        'Da ban hanh',
        'END_EVENT',
        3,
        FALSE,
        TRUE,
        NULL,
        '["DOC_PUBLISHER","ADMIN"]'::jsonb,
        '{"businessStatus":"APPROVED"}'::jsonb,
        NOW()
    ),
    (
        '12000000-0000-0000-0000-000000000204',
        '10000000-0000-0000-0000-000000000002',
        'CANCELED',
        'Huy ban hanh',
        'END_EVENT',
        4,
        FALSE,
        TRUE,
        NULL,
        '["ADMIN"]'::jsonb,
        '{"businessStatus":"RETURNED"}'::jsonb,
        NOW()
    );

-- 4) Steps for DOC_RECALL
INSERT INTO workflow_definition_steps (
    id,
    definition_id,
    step_code,
    step_name,
    step_type,
    step_order,
    is_start,
    is_end,
    sla_hours,
    allowed_roles_json,
    metadata_json,
    created_on
) VALUES
    (
        '13000000-0000-0000-0000-000000000301',
        '10000000-0000-0000-0000-000000000003',
        'ACTIVE',
        'Van ban dang hieu luc',
        'USER_TASK',
        1,
        TRUE,
        FALSE,
        24,
        '["DOC_OWNER","ADMIN"]'::jsonb,
        '{"businessStatus":"ARCHIVED"}'::jsonb,
        NOW()
    ),
    (
        '13000000-0000-0000-0000-000000000302',
        '10000000-0000-0000-0000-000000000003',
        'RECALL_REVIEW',
        'Xem xet thu hoi',
        'USER_TASK',
        2,
        FALSE,
        FALSE,
        48,
        '["DOC_APPROVER","ADMIN"]'::jsonb,
        '{"businessStatus":"IN_REVIEW"}'::jsonb,
        NOW()
    ),
    (
        '13000000-0000-0000-0000-000000000303',
        '10000000-0000-0000-0000-000000000003',
        'RECALLED',
        'Da thu hoi',
        'END_EVENT',
        3,
        FALSE,
        TRUE,
        NULL,
        '["DOC_APPROVER","ADMIN"]'::jsonb,
        '{"businessStatus":"REJECTED"}'::jsonb,
        NOW()
    ),
    (
        '13000000-0000-0000-0000-000000000304',
        '10000000-0000-0000-0000-000000000003',
        'RECALL_REJECTED',
        'Tu choi thu hoi',
        'END_EVENT',
        4,
        FALSE,
        TRUE,
        NULL,
        '["DOC_APPROVER","ADMIN"]'::jsonb,
        '{"businessStatus":"APPROVED"}'::jsonb,
        NOW()
    );

-- 5) Transitions for DOC_APPROVAL
INSERT INTO workflow_definition_transitions (
    id,
    definition_id,
    from_step_id,
    to_step_id,
    action_code,
    action_name,
    required_roles_json,
    condition_expression,
    is_auto,
    metadata_json,
    created_on
) VALUES
    (
        '21000000-0000-0000-0000-000000000401',
        '10000000-0000-0000-0000-000000000001',
        '11000000-0000-0000-0000-000000000101',
        '11000000-0000-0000-0000-000000000102',
        'SUBMIT',
        'Trinh duyet',
        '["DOC_EDITOR","ADMIN"]'::jsonb,
        NULL,
        FALSE,
        '{}'::jsonb,
        NOW()
    ),
    (
        '21000000-0000-0000-0000-000000000402',
        '10000000-0000-0000-0000-000000000001',
        '11000000-0000-0000-0000-000000000102',
        '11000000-0000-0000-0000-000000000103',
        'APPROVE',
        'Phe duyet',
        '["DOC_APPROVER","ADMIN"]'::jsonb,
        NULL,
        FALSE,
        '{}'::jsonb,
        NOW()
    ),
    (
        '21000000-0000-0000-0000-000000000403',
        '10000000-0000-0000-0000-000000000001',
        '11000000-0000-0000-0000-000000000102',
        '11000000-0000-0000-0000-000000000104',
        'REJECT',
        'Tu choi',
        '["DOC_APPROVER","ADMIN"]'::jsonb,
        NULL,
        FALSE,
        '{}'::jsonb,
        NOW()
    ),
    (
        '21000000-0000-0000-0000-000000000404',
        '10000000-0000-0000-0000-000000000001',
        '11000000-0000-0000-0000-000000000102',
        '11000000-0000-0000-0000-000000000101',
        'RETURN',
        'Tra lai bo sung',
        '["DOC_APPROVER","ADMIN"]'::jsonb,
        NULL,
        FALSE,
        '{}'::jsonb,
        NOW()
    );

-- 6) Transitions for DOC_ISSUANCE
INSERT INTO workflow_definition_transitions (
    id,
    definition_id,
    from_step_id,
    to_step_id,
    action_code,
    action_name,
    required_roles_json,
    condition_expression,
    is_auto,
    metadata_json,
    created_on
) VALUES
    (
        '22000000-0000-0000-0000-000000000501',
        '10000000-0000-0000-0000-000000000002',
        '12000000-0000-0000-0000-000000000201',
        '12000000-0000-0000-0000-000000000202',
        'SIGN',
        'Ky van ban',
        '["DOC_SIGNER","ADMIN"]'::jsonb,
        NULL,
        FALSE,
        '{}'::jsonb,
        NOW()
    ),
    (
        '22000000-0000-0000-0000-000000000502',
        '10000000-0000-0000-0000-000000000002',
        '12000000-0000-0000-0000-000000000202',
        '12000000-0000-0000-0000-000000000203',
        'ISSUE',
        'Ban hanh',
        '["DOC_PUBLISHER","ADMIN"]'::jsonb,
        NULL,
        FALSE,
        '{}'::jsonb,
        NOW()
    ),
    (
        '22000000-0000-0000-0000-000000000503',
        '10000000-0000-0000-0000-000000000002',
        '12000000-0000-0000-0000-000000000201',
        '12000000-0000-0000-0000-000000000204',
        'CANCEL',
        'Huy ban hanh',
        '["ADMIN"]'::jsonb,
        NULL,
        FALSE,
        '{}'::jsonb,
        NOW()
    ),
    (
        '22000000-0000-0000-0000-000000000504',
        '10000000-0000-0000-0000-000000000002',
        '12000000-0000-0000-0000-000000000202',
        '12000000-0000-0000-0000-000000000204',
        'CANCEL',
        'Huy ban hanh',
        '["ADMIN"]'::jsonb,
        NULL,
        FALSE,
        '{}'::jsonb,
        NOW()
    );

-- 7) Transitions for DOC_RECALL
INSERT INTO workflow_definition_transitions (
    id,
    definition_id,
    from_step_id,
    to_step_id,
    action_code,
    action_name,
    required_roles_json,
    condition_expression,
    is_auto,
    metadata_json,
    created_on
) VALUES
    (
        '23000000-0000-0000-0000-000000000601',
        '10000000-0000-0000-0000-000000000003',
        '13000000-0000-0000-0000-000000000301',
        '13000000-0000-0000-0000-000000000302',
        'REQUEST_RECALL',
        'Yeu cau thu hoi',
        '["DOC_OWNER","ADMIN"]'::jsonb,
        NULL,
        FALSE,
        '{}'::jsonb,
        NOW()
    ),
    (
        '23000000-0000-0000-0000-000000000602',
        '10000000-0000-0000-0000-000000000003',
        '13000000-0000-0000-0000-000000000302',
        '13000000-0000-0000-0000-000000000303',
        'APPROVE_RECALL',
        'Dong y thu hoi',
        '["DOC_APPROVER","ADMIN"]'::jsonb,
        NULL,
        FALSE,
        '{}'::jsonb,
        NOW()
    ),
    (
        '23000000-0000-0000-0000-000000000603',
        '10000000-0000-0000-0000-000000000003',
        '13000000-0000-0000-0000-000000000302',
        '13000000-0000-0000-0000-000000000304',
        'REJECT_RECALL',
        'Tu choi thu hoi',
        '["DOC_APPROVER","ADMIN"]'::jsonb,
        NULL,
        FALSE,
        '{}'::jsonb,
        NOW()
    );
