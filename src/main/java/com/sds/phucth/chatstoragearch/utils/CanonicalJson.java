package com.sds.phucth.chatstoragearch.utils;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.TreeMap;

public final class CanonicalJson {
    private static final ObjectMapper M = new ObjectMapper()
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);

    public static byte[] toCanonicalBytes(Object value) {
        try {
            JsonNode sorted = sort(M.valueToTree(value));
            return M.writeValueAsBytes(sorted);
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static JsonNode sort(JsonNode node) {
        if (node.isObject()) {
            ObjectNode obj = (ObjectNode) node;
            ObjectNode out = M.createObjectNode();
            TreeMap<String, JsonNode> map = new TreeMap<>();
            obj.fields().forEachRemaining(e -> map.put(e.getKey(), sort(e.getValue())));
            map.forEach(out::set);
            return out;
        } else if (node.isArray()) {
            ArrayNode arr = (ArrayNode) node;
            ArrayNode out = M.createArrayNode();
            for (JsonNode n: arr){
                out.add(sort(n));
            }
            return out;
        } else {
            return node;
        }
    }
}
