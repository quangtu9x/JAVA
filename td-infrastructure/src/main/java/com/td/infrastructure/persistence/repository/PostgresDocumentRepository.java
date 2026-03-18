package com.td.infrastructure.persistence.repository;

import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
public class PostgresDocumentRepository extends DocumentRepository {

	public PostgresDocumentRepository(DocumentJpaRepository jpaRepository) {
		super(jpaRepository);
	}

	@Override
	protected void appendAttributeKeyPredicates(
			StringBuilder sql,
			Map<String, Object> parameters,
			List<DocumentSearchSupport.AttributeFilterRule> filterRules) {
		for (int index = 0; index < filterRules.size(); index++) {
			String parameterName = "attributeKey" + index;
			sql.append(" AND jsonb_exists(CAST(d.attributes_json AS jsonb), :").append(parameterName).append(")");
			parameters.put(parameterName, filterRules.get(index).key());
		}
	}
}