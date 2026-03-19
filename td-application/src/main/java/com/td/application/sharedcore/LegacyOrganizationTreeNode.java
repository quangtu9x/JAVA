package com.td.application.sharedcore;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LegacyOrganizationTreeNode {
    private String id;
    private String text;
    private String form;
    private Map<String, String> data;
    private List<LegacyOrganizationTreeNode> children;
}
