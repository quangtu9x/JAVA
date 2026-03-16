package com.td.infrastructure.persistence.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.td.application.documents.SearchDocumentsRequest;
import com.td.domain.documents.BusinessDocument;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

final class DocumentSearchSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    static final String OPERATOR_EQUALS = "equals";
    static final String OPERATOR_CONTAINS = "contains";
    static final String OPERATOR_RANGE = "range";

    private DocumentSearchSupport() {
    }

    static Page<BusinessDocument> searchWithJpa(
            DocumentJpaRepository repository,
            SearchDocumentsRequest request,
            Pageable pageable) {
        return repository.findAll(baseSpecification(request), pageable, false);
    }

    static Page<BusinessDocument> filterSortAndPage(
            List<BusinessDocument> candidates,
            List<AttributeFilterRule> filterRules,
            Pageable pageable) {
        List<BusinessDocument> filtered = (candidates == null ? List.<BusinessDocument>of() : candidates).stream()
            .filter(document -> matchesAllFilters(document, filterRules))
            .toList();

        return toPage(sortDocuments(filtered, pageable == null ? Sort.unsorted() : pageable.getSort()), pageable);
    }

    static List<AttributeFilterRule> parseAttributeFilters(Map<String, Object> attributeFilters) {
        if (attributeFilters == null || attributeFilters.isEmpty()) {
            return List.of();
        }

        List<AttributeFilterRule> rules = new ArrayList<>();
        for (Map.Entry<String, Object> entry : attributeFilters.entrySet()) {
            AttributeFilterRule parsed = parseFilter(entry.getKey(), entry.getValue());
            if (parsed != null) {
                rules.add(parsed);
            }
        }

        return rules;
    }

    static Specification<BusinessDocument> withAttributeKeys(List<AttributeFilterRule> filterRules) {
        return (root, query, cb) -> {
            if (filterRules == null || filterRules.isEmpty()) {
                return cb.conjunction();
            }

            List<Predicate> predicates = new ArrayList<>();
            var attributeJson = cb.lower(root.get("attributesJson"));

            for (AttributeFilterRule filterRule : filterRules) {
                String keyPattern = "%\"" + filterRule.key().toLowerCase() + "\":%";
                predicates.add(cb.like(attributeJson, keyPattern));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static Specification<BusinessDocument> baseSpecification(SearchDocumentsRequest request) {
        if (request == null) {
            return Specification.where(null);
        }

        return Specification.where(withKeyword(request.getKeyword()))
            .and(withDocumentType(request.getDocumentType()))
            .and(withStatus(request.getStatus()));
    }

    private static AttributeFilterRule parseFilter(String rawKey, Object rawValue) {
        if (rawKey == null || rawKey.isBlank() || rawValue == null) {
            return null;
        }

        String key = rawKey.trim();

        if (rawValue instanceof Map<?, ?> descriptor) {
            String operator = normalizeOperator(descriptor.get("operator"));

            if (OPERATOR_RANGE.equals(operator)) {
                Object from = descriptor.get("from");
                Object to = descriptor.get("to");
                if (from == null && to == null) {
                    return null;
                }
                return new AttributeFilterRule(key, operator, null, from, to);
            }

            Object value = descriptor.get("value");
            if (value == null) {
                return null;
            }
            return new AttributeFilterRule(key, operator, value, null, null);
        }

        return new AttributeFilterRule(key, OPERATOR_EQUALS, rawValue, null, null);
    }

    private static String normalizeOperator(Object rawOperator) {
        if (rawOperator == null) {
            return OPERATOR_EQUALS;
        }

        String normalized = String.valueOf(rawOperator).trim().toLowerCase();
        if (OPERATOR_CONTAINS.equals(normalized) || OPERATOR_RANGE.equals(normalized) || OPERATOR_EQUALS.equals(normalized)) {
            return normalized;
        }

        return OPERATOR_EQUALS;
    }

    private static boolean matchesAllFilters(BusinessDocument document, List<AttributeFilterRule> filterRules) {
        if (filterRules == null || filterRules.isEmpty()) {
            return true;
        }

        Map<String, Object> attributes = parseAttributes(document.getAttributesJson());
        for (AttributeFilterRule filterRule : filterRules) {
            Object attributeValue = attributes.get(filterRule.key());
            if (!matchesFilter(attributeValue, filterRule)) {
                return false;
            }
        }

        return true;
    }

    private static Map<String, Object> parseAttributes(String attributesJson) {
        try {
            if (attributesJson == null || attributesJson.isBlank()) {
                return Collections.emptyMap();
            }

            return OBJECT_MAPPER.readValue(attributesJson, MAP_TYPE);
        } catch (Exception ex) {
            return Collections.emptyMap();
        }
    }

    private static boolean matchesFilter(Object attributeValue, AttributeFilterRule filterRule) {
        if (attributeValue == null || filterRule == null) {
            return false;
        }

        return switch (filterRule.operator()) {
            case OPERATOR_CONTAINS -> containsValue(attributeValue, filterRule.value());
            case OPERATOR_RANGE -> withinRange(attributeValue, filterRule.from(), filterRule.to());
            default -> equalsValue(attributeValue, filterRule.value());
        };
    }

    private static boolean equalsValue(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }

        if (actual instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (equalsValue(value, expected)) {
                    return true;
                }
            }
            return false;
        }

        BigDecimal actualNumber = toBigDecimal(actual);
        BigDecimal expectedNumber = toBigDecimal(expected);
        if (actualNumber != null && expectedNumber != null) {
            return actualNumber.compareTo(expectedNumber) == 0;
        }

        return String.valueOf(actual).trim().equalsIgnoreCase(String.valueOf(expected).trim());
    }

    private static boolean containsValue(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return false;
        }

        String needle = String.valueOf(expected).trim().toLowerCase();
        if (needle.isBlank()) {
            return false;
        }

        if (actual instanceof Iterable<?> iterable) {
            for (Object value : iterable) {
                if (containsValue(value, expected)) {
                    return true;
                }
            }
            return false;
        }

        return String.valueOf(actual).toLowerCase().contains(needle);
    }

    private static boolean withinRange(Object actual, Object from, Object to) {
        if (actual == null || (from == null && to == null)) {
            return false;
        }

        if (from != null && compareValues(actual, from) < 0) {
            return false;
        }

        if (to != null && compareValues(actual, to) > 0) {
            return false;
        }

        return true;
    }

    private static int compareValues(Object left, Object right) {
        BigDecimal leftNumber = toBigDecimal(left);
        BigDecimal rightNumber = toBigDecimal(right);
        if (leftNumber != null && rightNumber != null) {
            return leftNumber.compareTo(rightNumber);
        }

        Instant leftInstant = toInstant(left);
        Instant rightInstant = toInstant(right);
        if (leftInstant != null && rightInstant != null) {
            return leftInstant.compareTo(rightInstant);
        }

        String leftText = String.valueOf(left).trim().toLowerCase();
        String rightText = String.valueOf(right).trim().toLowerCase();
        return leftText.compareTo(rightText);
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof Number number) {
                return new BigDecimal(number.toString());
            }

            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                return null;
            }
            return new BigDecimal(text);
        } catch (Exception ex) {
            return null;
        }
    }

    private static Instant toInstant(Object value) {
        if (value == null) {
            return null;
        }

        try {
            if (value instanceof Instant instant) {
                return instant;
            }

            if (value instanceof LocalDateTime dateTime) {
                return dateTime.toInstant(ZoneOffset.UTC);
            }

            if (value instanceof LocalDate localDate) {
                return localDate.atStartOfDay().toInstant(ZoneOffset.UTC);
            }

            String text = String.valueOf(value).trim();
            if (text.isBlank()) {
                return null;
            }

            try {
                return Instant.parse(text);
            } catch (Exception ignored) {
                // Ignore and continue fallback parsing.
            }

            try {
                return LocalDateTime.parse(text).toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {
                // Ignore and continue fallback parsing.
            }

            return LocalDate.parse(text).atStartOfDay().toInstant(ZoneOffset.UTC);
        } catch (Exception ex) {
            return null;
        }
    }

    private static List<BusinessDocument> sortDocuments(List<BusinessDocument> documents, Sort sort) {
        if (documents == null || documents.size() <= 1 || sort == null || sort.isUnsorted()) {
            return documents == null ? List.of() : documents;
        }

        List<BusinessDocument> result = new ArrayList<>(documents);
        java.util.Comparator<BusinessDocument> comparator = null;

        for (Sort.Order order : sort) {
            java.util.Comparator<BusinessDocument> currentComparator = (left, right) ->
                compareComparable(propertyValue(left, order.getProperty()), propertyValue(right, order.getProperty()));

            if (order.isDescending()) {
                currentComparator = currentComparator.reversed();
            }

            comparator = comparator == null
                ? currentComparator
                : comparator.thenComparing(currentComparator);
        }

        if (comparator != null) {
            result.sort(comparator);
        }

        return result;
    }

    private static int compareComparable(Comparable<?> left, Comparable<?> right) {
        if (left == null && right == null) {
            return 0;
        }

        if (left == null) {
            return 1;
        }

        if (right == null) {
            return -1;
        }

        @SuppressWarnings({"rawtypes", "unchecked"})
        int result = ((Comparable) left).compareTo(right);
        return result;
    }

    private static Comparable<?> propertyValue(BusinessDocument document, String property) {
        return switch (property) {
            case "title" -> document.getTitle();
            case "documentType" -> document.getDocumentType();
            case "status" -> document.getStatus();
            case "versionNo" -> document.getVersionNo();
            case "createdOn" -> document.getCreatedOn();
            case "lastModifiedOn" -> document.getLastModifiedOn();
            case "deletedOn" -> document.getDeletedOn();
            default -> document.getLastModifiedOn();
        };
    }

    private static Page<BusinessDocument> toPage(List<BusinessDocument> documents, Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return new PageImpl<>(documents == null ? List.of() : documents);
        }

        List<BusinessDocument> source = documents == null ? List.of() : documents;
        int start = (int) pageable.getOffset();
        if (start >= source.size()) {
            return new PageImpl<>(List.of(), pageable, source.size());
        }

        int end = Math.min(start + pageable.getPageSize(), source.size());
        return new PageImpl<>(source.subList(start, end), pageable, source.size());
    }

    private static Specification<BusinessDocument> withKeyword(String keyword) {
        return (root, query, cb) -> {
            if (keyword == null || keyword.isBlank()) {
                return cb.conjunction();
            }

            String search = "%" + keyword.trim().toLowerCase() + "%";
            return cb.or(
                cb.like(cb.lower(root.get("title")), search),
                cb.like(cb.lower(root.get("content")), search),
                cb.like(cb.lower(root.get("documentType")), search),
                cb.like(cb.lower(root.get("status")), search),
                cb.like(cb.lower(root.get("tagsJson")), search),
                cb.like(cb.lower(root.get("attributesJson")), search)
            );
        };
    }

    private static Specification<BusinessDocument> withDocumentType(String documentType) {
        return (root, query, cb) -> {
            if (documentType == null || documentType.isBlank()) {
                return cb.conjunction();
            }

            return cb.equal(cb.lower(root.get("documentType")), documentType.trim().toLowerCase());
        };
    }

    private static Specification<BusinessDocument> withStatus(String status) {
        return (root, query, cb) -> {
            if (status == null || status.isBlank()) {
                return cb.conjunction();
            }

            return cb.equal(cb.lower(root.get("status")), status.trim().toLowerCase());
        };
    }

    static record AttributeFilterRule(String key, String operator, Object value, Object from, Object to) {
    }
}