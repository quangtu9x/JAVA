package com.td.infrastructure.persistence.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class PostgresDocumentRepository extends DocumentRepository {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
	private static final String USER_PROCESS_FIELD = "user_process";
	private static final String USER_PROCESSED_FIELD = "user_processed";
	private static final String PARENT_ID_FIELD = "parentid";

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

	@Override
	protected void appendPendingForUserPredicate(StringBuilder sql, Map<String, Object> parameters, String username) {
		sql.append(" AND d.dynamic_fields @> CAST(:userProcessFilter AS jsonb)");
		parameters.put("userProcessFilter", buildUserProcessFilter(username));
	}

	String buildUserProcessFilter(String username) {
		try {
			return OBJECT_MAPPER.writeValueAsString(Map.of(USER_PROCESS_FIELD, List.of(username)));
		} catch (Exception ex) {
			return "{}";
		}
	}

	@Override
	protected void appendProcessedForUserPredicate(StringBuilder sql, Map<String, Object> parameters, String username) {
		sql.append(" AND d.dynamic_fields @> CAST(:userProcessedFilter AS jsonb)");
		parameters.put("userProcessedFilter", buildUserProcessedFilter(username));
	}

	String buildUserProcessedFilter(String username) {
		try {
			return OBJECT_MAPPER.writeValueAsString(Map.of(USER_PROCESSED_FIELD, List.of(username)));
		} catch (Exception ex) {
			return "{}";
		}
	}

	@Override
	protected void appendParentIdPredicate(StringBuilder sql, Map<String, Object> parameters, String parentId) {
		sql.append(" AND d.dynamic_fields @> CAST(:parentIdFilter AS jsonb)");
		parameters.put("parentIdFilter", buildParentIdFilter(parentId));
	}

	String buildParentIdFilter(String parentId) {
		try {
			return OBJECT_MAPPER.writeValueAsString(Map.of(PARENT_ID_FIELD, parentId));
		} catch (Exception ex) {
			return "{}";
		}
	}

	@Override
	protected void appendFieldValuePredicate(
			StringBuilder sql, Map<String, Object> parameters, String fieldName, String fieldValue) {
		sql.append(" AND d.dynamic_fields @> CAST(:fieldValueFilter AS jsonb)");
		parameters.put("fieldValueFilter", buildFieldValueFilter(fieldName, fieldValue));
	}

	String buildFieldValueFilter(String fieldName, String fieldValue) {
		try {
			return OBJECT_MAPPER.writeValueAsString(Map.of(fieldName, fieldValue));
		} catch (Exception ex) {
			return "{}";
		}
	}

	// BL-210 (VBDen Core) - N03: OR containment tren N field JSON-array, moi field 1 dieu kien
	// rieng cung cong thuc voi buildUserProcessFilter (chi khac ten field tham so hoa).
	//
	// BUGFIX (21/08/2026, phat hien qua bao cao thuc te "GetSoVanBanDen khong co du lieu du da tao
	// van ban moi"): field nhu "identifier"/"identifier_creator" KHONG khop hau to _aut/_rdr/_tlist
	// (xem FieldTypeInferrer.infer) nen duoc AddDynamicDocumentUseCase luu dang SCALAR TEXT
	// (vd {"identifier_creator":"PHONG_TH"}), KHONG phai JSON array nhu unit_process/user_process
	// (nhung field do duoc ChuyenXuLyVbdenUseCase ghi truc tiep dang List<String> khi cap nhat, bo
	// qua FieldTypeInferrer). Chi dung "@>" containment (gia dinh field la array) se KHONG BAO GIO
	// khop voi field luu dang scalar - moi view dung "identifier"/"identifier_creator" lam org field
	// (N05 chophanxuly, N12/N22 cuatochuc/daxuly_cuatochuc, N13 8-field union, Slice 9 GetSoVanBanDen)
	// deu bi anh huong. Sua: OR THEM dieu kien scalar-equality cho MOI field, khong can biet truoc
	// field nao la array/scalar - field thuc su la array se khop nhanh "@>", field scalar se khop
	// nhanh "->>' = ", field khong khop ca 2 dang thi khong sao (dung).
	@Override
	protected void appendAnyFieldContainsPredicate(
			StringBuilder sql, Map<String, Object> parameters, List<String> fieldNames, String value) {
		if (fieldNames == null || fieldNames.isEmpty()) {
			sql.append(" AND 1 = 0");
			return;
		}
		sql.append(" AND (");
		for (int index = 0; index < fieldNames.size(); index++) {
			if (index > 0) {
				sql.append(" OR ");
			}
			String containsParam = "containsField" + index;
			String scalarParam = "containsFieldScalar" + index;
			String fieldName = fieldNames.get(index);
			sql.append("(d.dynamic_fields @> CAST(:").append(containsParam).append(" AS jsonb)")
				.append(" OR COALESCE(d.dynamic_fields->>'").append(fieldName).append("', '') = :")
				.append(scalarParam).append(")");
			parameters.put(containsParam, buildContainsFilter(fieldName, value));
			parameters.put(scalarParam, value == null ? "" : value);
		}
		sql.append(")");
	}

	String buildContainsFilter(String fieldName, String value) {
		try {
			return OBJECT_MAPPER.writeValueAsString(Map.of(fieldName, List.of(value)));
		} catch (Exception ex) {
			return "{}";
		}
	}

	// 01/09/2026 ("tim kiem nang cao"): tuong tu appendAnyFieldContainsPredicate nhung dao nguoc -
	// CUNG 1 field, NHIEU gia tri ung vien OR nhau (thay vi nhieu field cho CUNG 1 gia tri). Nhieu
	// field khac nhau trong map ket hop AND.
	@Override
	protected void appendContainsAnyFieldValuePredicates(
			StringBuilder sql, Map<String, Object> parameters, Map<String, List<String>> containsAnyFieldValues) {
		if (containsAnyFieldValues == null || containsAnyFieldValues.isEmpty()) {
			return;
		}
		int fieldIndex = 0;
		for (Map.Entry<String, List<String>> entry : containsAnyFieldValues.entrySet()) {
			String fieldName = entry.getKey();
			List<String> values = entry.getValue();
			if (fieldName == null || fieldName.isBlank() || values == null || values.isEmpty()) {
				continue;
			}
			sql.append(" AND (");
			boolean first = true;
			for (int valueIndex = 0; valueIndex < values.size(); valueIndex++) {
				String value = values.get(valueIndex);
				if (value == null || value.isBlank()) {
					continue;
				}
				if (!first) {
					sql.append(" OR ");
				}
				first = false;
				String containsParam = "anyVal" + fieldIndex + "_" + valueIndex;
				String scalarParam = "anyValScalar" + fieldIndex + "_" + valueIndex;
				sql.append("(d.dynamic_fields @> CAST(:").append(containsParam).append(" AS jsonb)")
					.append(" OR COALESCE(d.dynamic_fields->>'").append(fieldName).append("', '') = :")
					.append(scalarParam).append(")");
				parameters.put(containsParam, buildContainsFilter(fieldName, value));
				parameters.put(scalarParam, value);
			}
			if (first) {
				// khong co gia tri hop le nao - dieu kien khong bao gio dung (khop hanh vi "IS PRESENT" rong).
				sql.append("1 = 0");
			}
			sql.append(")");
			fieldIndex++;
		}
	}

	// Bugfix 31/08/2026 (khao sat sau khi port "toanbovanban" - xem docs/analysis/02-checklist-port-
	// vbden-con-lai.md): comment cu o day gia dinh deadline_date luu chuoi ISO "yyyy-MM-dd" -
	// XAC NHAN SAI qua doi chieu day du chuoi ghi du lieu that: FE (vbdenApi.js#toFieldString,
	// SubFormChuyenXuLy.jsx dong ~417) LUON ep deadline_date/received_date/promulgation_date qua
	// date_to_dd_mm_yyyy() truoc khi gui (khong co ngoai le nao), va DynamicFieldValueConverter phia
	// Java (case DATETIME) tra ve rawValue KHONG bien doi gi - nen deadline_date luu dang chuoi
	// "dd/MM/yyyy" y het received_date (da tu sua dung o dynamicSortExpression() ben duoi cho SORT,
	// nhung o day - dung cho FILTER khoang ngay - van con sai vi CAST(...AS date) + regex ISO se
	// KHONG BAO GIO khop du lieu that, lam saptoihan_cuatochuc/denhan_cuatochuc/quahan_cuatochuc/
	// sapdenhan_cuacanhan (4 view da port, dang dung production) LUON tra ve RONG voi moi van ban.
	// Sua giong het cong thuc dynamicSortExpression() da dung dung cho sort: TO_DATE(...,'DD/MM/YYYY')
	// + guard regex dd/MM/yyyy (khong throw neu du lieu cu/sai dang, chi khong khop dieu kien).
	private static final String DEADLINE_DATE_FIELD = "deadline_date";
	private static final String DD_MM_YYYY_REGEX = "^[0-9]{2}/[0-9]{2}/[0-9]{4}$";

	@Override
	protected void appendDeadlineInRangePredicate(
			StringBuilder sql, Map<String, Object> parameters, LocalDate minDeadline, LocalDate maxDeadline) {
		sql.append(" AND (d.dynamic_fields->>'").append(DEADLINE_DATE_FIELD).append("') ~ :deadlineDateFormat");
		parameters.put("deadlineDateFormat", DD_MM_YYYY_REGEX);

		if (minDeadline != null) {
			sql.append(" AND TO_DATE(d.dynamic_fields->>'").append(DEADLINE_DATE_FIELD)
				.append("', 'DD/MM/YYYY') >= :minDeadline");
			parameters.put("minDeadline", java.sql.Date.valueOf(minDeadline));
		}
		if (maxDeadline != null) {
			sql.append(" AND TO_DATE(d.dynamic_fields->>'").append(DEADLINE_DATE_FIELD)
				.append("', 'DD/MM/YYYY') <= :maxDeadline");
			parameters.put("maxDeadline", java.sql.Date.valueOf(maxDeadline));
		}
	}

	// 31/08/2026 (port "toanbovanban" - RangePicker "khoang ngay nhan van ban"): received_date cung
	// luu chuoi "dd/MM/yyyy" y het deadline_date (xem bugfix ben tren) - dung y nguyen cong thuc
	// TO_DATE(...,'DD/MM/YYYY') + guard regex, chi khac ten field va ten param de tranh dung do voi
	// appendDeadlineInRangePredicate khi ca 2 cung duoc goi chung 1 truy van (khong xay ra hien tai vi
	// chi "toanbovanban" dung received_date range, nhung tach rieng cho an toan/ro rang).
	private static final String RECEIVED_DATE_FIELD = "received_date";

	@Override
	protected void appendReceivedDateInRangePredicate(
			StringBuilder sql, Map<String, Object> parameters, LocalDate minReceivedDate, LocalDate maxReceivedDate) {
		sql.append(" AND (d.dynamic_fields->>'").append(RECEIVED_DATE_FIELD).append("') ~ :receivedDateFormat");
		parameters.put("receivedDateFormat", DD_MM_YYYY_REGEX);

		if (minReceivedDate != null) {
			sql.append(" AND TO_DATE(d.dynamic_fields->>'").append(RECEIVED_DATE_FIELD)
				.append("', 'DD/MM/YYYY') >= :minReceivedDate");
			parameters.put("minReceivedDate", java.sql.Date.valueOf(minReceivedDate));
		}
		if (maxReceivedDate != null) {
			sql.append(" AND TO_DATE(d.dynamic_fields->>'").append(RECEIVED_DATE_FIELD)
				.append("', 'DD/MM/YYYY') <= :maxReceivedDate");
			parameters.put("maxReceivedDate", java.sql.Date.valueOf(maxReceivedDate));
		}
	}

	// N03/N13: so khop scalar field dong (internal/document_internet/Reply/document_type...) IN/NOT
	// IN danh sach gia tri. COALESCE(...,'') de field vang mat duoc coi la "" - dung ngu nghia
	// @Field != "gia_tri" cua Domino khi field khong ton tai (khac NULL IN/NOT IN luon la NULL trong SQL).
	@Override
	protected void appendDynamicFieldValueInPredicate(
			StringBuilder sql, Map<String, Object> parameters, String fieldName, List<String> values,
			boolean include, String paramPrefix) {
		if (values == null || values.isEmpty()) {
			return;
		}
		List<String> paramRefs = new ArrayList<>();
		for (int index = 0; index < values.size(); index++) {
			String parameterName = paramPrefix + "_" + index;
			paramRefs.add(":" + parameterName);
			parameters.put(parameterName, values.get(index) == null ? "" : values.get(index));
		}
		sql.append(" AND COALESCE(d.dynamic_fields->>'").append(fieldName).append("', '')")
			.append(include ? " IN (" : " NOT IN (")
			.append(String.join(",", paramRefs)).append(")");
	}

	// N13 rieng cho phaixuly_cuatochuc: (deadline_date!="" | documentreply="1").
	private static final String DOCUMENT_REPLY_FIELD = "documentreply";

	@Override
	protected void appendDeadlineOrDocumentReplyPredicate(StringBuilder sql, Map<String, Object> parameters) {
		sql.append(" AND (COALESCE(d.dynamic_fields->>'").append(DEADLINE_DATE_FIELD).append("', '') != ''")
			.append(" OR COALESCE(d.dynamic_fields->>'").append(DOCUMENT_REPLY_FIELD).append("', '') = :documentReplyValue)");
		parameters.put("documentReplyValue", "1");
	}

	// N05 (chophanxuly): user_process="" - field dong luon la mang JSON (ChuyenXuLyVbdenUseCase luu
	// dang List<String>, rong = "[]" khi het nguoi xu ly, khong bao gio xoa han key). "Rong" = key
	// vang mat HOAC mang rong - jsonb_array_length(NULL) tra ve NULL (khong loi) nen thu tu danh gia
	// 2 nhanh OR khong quan trong.
	@Override
	protected void appendFieldEmptyPredicate(StringBuilder sql, Map<String, Object> parameters, String fieldName) {
		sql.append(" AND (d.dynamic_fields->'").append(fieldName).append("' IS NULL")
			.append(" OR jsonb_array_length(d.dynamic_fields->'").append(fieldName).append("') = 0)");
	}

	// Slice 7 (tim kiem/loc): tuong duong Domino FTSearch tren cac cot chi dinh (columnsSearchVBden
	// phia FE) - OR cac field dong scalar, khong phan biet hoa/thuong, khop % o giua (contains).
	@Override
	protected void appendKeywordPredicate(
			StringBuilder sql, Map<String, Object> parameters, String keyword, List<String> keywordColumns) {
		if (keyword == null || keyword.isBlank() || keywordColumns == null || keywordColumns.isEmpty()) {
			return;
		}
		String likeValue = "%" + keyword.trim().toLowerCase() + "%";
		sql.append(" AND (");
		for (int index = 0; index < keywordColumns.size(); index++) {
			if (index > 0) {
				sql.append(" OR ");
			}
			String parameterName = "keyword" + index;
			sql.append("LOWER(COALESCE(d.dynamic_fields->>'").append(keywordColumns.get(index)).append("', ''))")
				.append(" LIKE :").append(parameterName);
			parameters.put(parameterName, likeValue);
		}
		sql.append(")");
	}

	// N05-style nguoc lai appendFieldEmptyPredicate: field dong PHAI khac rong/vang mat.
	@Override
	protected void appendFieldNonEmptyPredicate(StringBuilder sql, Map<String, Object> parameters, String fieldName) {
		sql.append(" AND d.dynamic_fields->'").append(fieldName).append("' IS NOT NULL")
			.append(" AND jsonb_array_length(d.dynamic_fields->'").append(fieldName).append("') > 0");
	}

	// Slice 9 (sovanban - homqua/trongngay/trongthang/trongtuan): created_on la cot tinh TIMESTAMP
	// (AuditableEntity), khong can COALESCE/regex guard nhu deadline_date (field dong dang chuoi).
	private static final String CREATED_ON_COLUMN = "d.created_on";

	@Override
	protected void appendCreatedOnInRangePredicate(
			StringBuilder sql, Map<String, Object> parameters, LocalDate minCreatedOn, LocalDate maxCreatedOn) {
		if (minCreatedOn != null) {
			sql.append(" AND CAST(").append(CREATED_ON_COLUMN).append(" AS date) >= :minCreatedOn");
			parameters.put("minCreatedOn", java.sql.Date.valueOf(minCreatedOn));
		}
		if (maxCreatedOn != null) {
			sql.append(" AND CAST(").append(CREATED_ON_COLUMN).append(" AS date) <= :maxCreatedOn");
			parameters.put("maxCreatedOn", java.sql.Date.valueOf(maxCreatedOn));
		}
	}

	// Bugfix 21/08/2026 (phat hien qua loi 409 thuc te tren du lieu thuc): 2 gia dinh format ban dau
	// deu SAI, xac nhan qua truy van truc tiep du lieu nhieu tenant:
	// 1) "document_number" ket thuc hau to "_number" nen FieldTypeInferrer suy FieldType.NUMBER (ep
	//    ve Double) - Jackson serialize Double nguyen thanh chuoi co ".0" (vd "3.0" khong phai "3").
	//    CAST(...AS integer) KHONG nhan chuoi co dau cham -> loi "invalid input syntax for type
	//    integer" (DataIntegrityViolationException, 409). Sua: ep qua numeric truoc (numeric nhan ca
	//    "3" va "3.0"), roi moi ve integer.
	// 2) "received_date" (cung "promulgation_date"/"deadline_date" - dung chung VBDEN_DATE_FIELDS +
	//    date_to_dd_mm_yyyy() o FE, xem vbdenApi.js) luu THUC TE dang "dd/MM/yyyy" (vd
	//    "21/08/2026"), KHONG PHAI ISO "yyyy-MM-dd" nhu gia dinh truoc do (sai lech voi quy uoc
	//    deadline_date o appendDeadlineInRangePredicate/N13 - CAN KIEM TRA LAI rieng, chua sua o day
	//    vi chua co du lieu deadline_date thuc de xac nhan chac chan, xem D.16 tai lieu). Sua rieng
	//    cho sort (che nay): dung TO_DATE voi format ro rang + guard regex dd/MM/yyyy, tranh loi
	//    runtime neu gap du lieu cu/sai dang (tra NULL, khong throw).
	// Bugfix 21/08/2026 (lan 2 - loi cu phap SQL thuc te khi test lai): dung toan tu cast Postgres
	// "::" (2 dau ":") trong native SQL cua Hibernate (entityManager.createNativeQuery) bi chinh
	// Hibernate/JPA parser hieu nham la parameter marker (":ten_param") va nuot mat 1 dau ":", con
	// lai 1 dau ":" don + "numeric" lam sinh cau SQL sai cu phap thuc te ("... :numeric AS integer"
	// - xac nhan qua stack trace InvalidDataAccessResourceUsageException/syntax error at or near
	// ":"). Sua: dung CAST(... AS numeric) long nhau (chuan ANSI SQL, khong dung ky hieu "::") thay
	// vi toan tu "::" cua Postgres - tranh hoan toan van de parser named-parameter cua Hibernate.
	@Override
	protected String dynamicSortExpression(String fieldName, String sortType) {
		String path = "d.dynamic_fields->>'" + fieldName + "'";
		if ("integer".equals(sortType)) {
			return "CAST(CAST(NULLIF(" + path + ", '') AS numeric) AS integer)";
		}
		if ("date".equals(sortType)) {
			return "(CASE WHEN NULLIF(" + path + ", '') ~ '^[0-9]{2}/[0-9]{2}/[0-9]{4}$'"
				+ " THEN TO_DATE(" + path + ", 'DD/MM/YYYY') ELSE NULL END)";
		}
		return path;
	}
}