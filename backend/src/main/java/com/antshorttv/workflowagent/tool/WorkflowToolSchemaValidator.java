package com.antshorttv.workflowagent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class WorkflowToolSchemaValidator {
    public void validate(JsonNode schema, JsonNode value) {
        validateAt(schema, value, "$", true);
    }

    private void validateAt(JsonNode schema, JsonNode value, String path, boolean root) {
        JsonNode type = schema.path("type");
        if (!matches(type, value)) {
            throw new IllegalArgumentException(path + " must be " + typeDescription(type));
        }
        if (value == null || value.isNull()) {
            return;
        }
        validateBounds(schema, value, path);
        if (value.isObject() && schema.has("properties")) {
            Set<String> required = new HashSet<>();
            schema.path("required").forEach(item -> required.add(item.asText()));
            required.forEach(field -> {
                if (!value.has(field)) {
                    throw new IllegalArgumentException(path + "." + field + " is required");
                }
            });
            JsonNode properties = schema.path("properties");
            Iterator<String> names = value.fieldNames();
            while (names.hasNext()) {
                String field = names.next();
                if (!properties.has(field) && !schema.path("additionalProperties").asBoolean(true)) {
                    throw new IllegalArgumentException(path + "." + field + " is not allowed");
                }
                if (properties.has(field)) {
                    validateAt(properties.get(field), value.get(field), path + "." + field, false);
                }
            }
        } else if (value.isArray() && schema.has("items")) {
            for (int index = 0; index < value.size(); index++) {
                validateAt(schema.get("items"), value.get(index), path + "[" + index + "]", false);
            }
        }
    }

    private void validateBounds(JsonNode schema, JsonNode value, String path) {
        if (value.isNumber()) {
            if (schema.has("minimum") && value.decimalValue().compareTo(schema.path("minimum").decimalValue()) < 0) {
                throw new IllegalArgumentException(path + " is below minimum " + schema.path("minimum"));
            }
            if (schema.has("maximum") && value.decimalValue().compareTo(schema.path("maximum").decimalValue()) > 0) {
                throw new IllegalArgumentException(path + " exceeds maximum " + schema.path("maximum"));
            }
        }
        if (value.isTextual() && schema.has("maxLength")
            && value.textValue().length() > schema.path("maxLength").asInt()) {
            throw new IllegalArgumentException(path + " exceeds maxLength " + schema.path("maxLength").asInt());
        }
        if (value.isArray() && schema.has("maxItems")
            && value.size() > schema.path("maxItems").asInt()) {
            throw new IllegalArgumentException(path + " exceeds maxItems " + schema.path("maxItems").asInt());
        }
        if (value.isArray() && schema.has("minItems")
            && value.size() < schema.path("minItems").asInt()) {
            throw new IllegalArgumentException(path + " is below minItems " + schema.path("minItems").asInt());
        }
    }

    private boolean matches(JsonNode type, JsonNode value) {
        if (type == null || type.isMissingNode() || type.isNull()) {
            return true;
        }
        if (type.isArray()) {
            for (JsonNode candidate : type) {
                if (matchesSingle(candidate.asText(), value)) {
                    return true;
                }
            }
            return false;
        }
        return matchesSingle(type.asText(), value);
    }

    private boolean matchesSingle(String type, JsonNode value) {
        if (value == null || value.isNull()) {
            return "null".equals(type);
        }
        return switch (type) {
            case "object" -> value.isObject();
            case "array" -> value.isArray();
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            default -> true;
        };
    }

    private String typeDescription(JsonNode type) {
        if (type != null && type.isArray()) {
            return type.toString();
        }
        return type == null ? "any value" : type.asText();
    }
}
