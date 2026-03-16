package com.td.infrastructure.persistence.repository;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@Profile("mariadb")
public class MariaDbDocumentRepository extends DocumentRepository {

	public MariaDbDocumentRepository(DocumentJpaRepository jpaRepository) {
		super(jpaRepository);
	}

	@Override
	protected void appendAttributeKeyPredicates(
			StringBuilder sql,
			Map<String, Object> parameters,
			List<DocumentSearchSupport.AttributeFilterRule> filterRules) {
		for (int index = 0; index < filterRules.size(); index++) {
			String parameterName = "attributePath" + index;
			sql.append(" AND JSON_EXTRACT(d.attributes_json, :").append(parameterName).append(") IS NOT NULL");
			parameters.put(parameterName, toJsonPath(filterRules.get(index).key()));
		}
	}
}