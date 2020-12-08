/*
 * Copyright 2020-2020 SgrAlpha
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sgr.flattener.jackson;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Strings.isNullOrEmpty;

import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Strings;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class ObjectFlattener {

    static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    static {
        OBJECT_MAPPER.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        OBJECT_MAPPER.setSerializationInclusion(Include.NON_NULL);
        OBJECT_MAPPER.registerModule(new JavaTimeModule());
        OBJECT_MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    private static final Pattern ARRAY_PART = Pattern.compile("^\\[\\d+]$");

    /**
     * Flatten an object to dot-annotated map.
     *
     * @param obj The object to be flattened.
     * @return A map of dot-annotated attributes from the object.
     */
    @Nonnull
    public static Map<String, String> flatten(@Nonnull final Object obj) {
        return flatten(OBJECT_MAPPER, obj, null);
    }

    /**
     * Flatten an object to dot-annotated map.
     * Similar to {@link ObjectFlattener#flatten(Object)} but generated keys will lead with given prefix.
     *
     * @param obj The object to be flattened.
     * @param prefix The prefix of keys.
     * @return A map of dot-annotated attributes from the object.
     */
    @Nonnull
    public static Map<String, String> flatten(@Nonnull final Object obj, @Nullable String prefix) {
        return flatten(OBJECT_MAPPER, obj, prefix);
    }

    /**
     * Flatten an object to dot-annotated map.
     * Similar to {@link ObjectFlattener#flatten(Object, String)} but support to use provided object mapper.
     *
     * @param objectMapper The object mapper that will be used.
     * @param obj The object to be flattened.
     * @param prefix The prefix of keys.
     * @return A map of dot-annotated attributes from the object.
     */
    @Nonnull
    public static Map<String, String> flatten(@Nonnull final ObjectMapper objectMapper, @Nonnull final Object obj, @Nullable String prefix) {
        checkNotNull(objectMapper, "Missing object mapper!");
        final Map<String, String> result = new LinkedHashMap<>();
        final List<String> names = new LinkedList<>();
        Optional.ofNullable(prefix).map(Strings::emptyToNull).ifPresent(names::add);
        mapAppender(result, objectMapper.valueToTree(obj), names);
        return result;
    }

    private static void mapAppender(@Nonnull final Map<String, String> result, @Nonnull final JsonNode value, @Nonnull final List<String> names) {
        String field = String.join(".", names);
        if (value.isValueNode()) {
            result.put(field, value.asText());
        } else if (value.isArray()) {
            final AtomicInteger i = new AtomicInteger(0);
            value.iterator().forEachRemaining(item -> {
                String name = String.format("%s.[%d]", field, i.getAndIncrement());
                if (item.isNull()) {
                    result.put(name, null);
                    return;
                }
                List<String> list = new LinkedList<>();
                list.add(name);
                mapAppender(result, item, list);
            });
        } else {
            value.fields().forEachRemaining(nested -> {
                List<String> list = new LinkedList<>(names);
                list.add(nested.getKey());
                mapAppender(result, nested.getValue(), list);
            });
        }
    }

    public static <T> T unflatten(@Nonnull final Map<String, String> flattened, @Nonnull final Class<T> clazz, @Nullable final String prefix) {
        return unflatten(flattened, OBJECT_MAPPER, clazz, prefix);
    }

    public static <T> T unflatten(
            @Nonnull final Map<String, String> flattened,
            @Nonnull final ObjectMapper objectMapper, @Nonnull final Class<T> clazz,
            @Nullable final String prefix
    ) {
        try {
            final ObjectNode root = unflatten(flattened, objectMapper, prefix);
            return objectMapper.readValue(root.traverse(), clazz);
        } catch (IOException e) {
            final String message = String.format("Could not convert the flattened map to %s", clazz);
            throw new IllegalArgumentException(message, e);
        }
    }

    public static ObjectNode unflatten(@Nonnull final Map<String, String> flattened) {
        return unflatten(flattened, OBJECT_MAPPER, null);
    }

    public static ObjectNode unflatten(@Nonnull final Map<String, String> flattened, @Nullable final String prefix) {
        return unflatten(flattened, OBJECT_MAPPER, prefix);
    }

    public static ObjectNode unflatten(@Nonnull final Map<String, String> flattened, @Nonnull final ObjectMapper objectMapper, @Nullable final String prefix) {
        checkNotNull(flattened, "Missing flattened map!");
        checkNotNull(objectMapper, "Missing object mapper!");
        final String pref = Optional.ofNullable(prefix).map(Strings::emptyToNull).map(str -> str + ".").orElse("");
        final ObjectNode root = objectMapper.createObjectNode();
        flattened.entrySet().stream()
                .filter(entry -> !isNullOrEmpty(entry.getKey()))
                .filter(entry -> entry.getKey().startsWith(pref) && !entry.getKey().equals("."))
                .forEach(entry -> {
                    final String key = entry.getKey().substring(pref.length());
                    final String value = entry.getValue();
                    final String[] parts = key.split("\\.");
                    fillObject(objectMapper, root, parts, value);
                });
        return root;
    }

    private static void fillObject(
            @Nonnull final ObjectMapper objectMapper, @Nonnull final ObjectNode node,
            @Nonnull final String[] parts, @Nullable final String value
    ) {
        String part = parts[0];
        if (parts.length > 1) {
            String currentPart = part;
            part = parts[1];
            if (ARRAY_PART.matcher(part).find()) {
                if (!node.has(currentPart)) {
                    node.set(currentPart, objectMapper.createArrayNode());
                }
                String[] restParts = new String[parts.length - 1];
                System.arraycopy(parts, 1, restParts, 0, restParts.length);
                fillArray(objectMapper, (ArrayNode) node.get(currentPart), restParts, value);
            } else {
                final ObjectNode nested = objectMapper.createObjectNode();
                node.set(currentPart, nested);
                String[] restParts = new String[parts.length - 1];
                System.arraycopy(parts, 1, restParts, 0, restParts.length);
                fillObject(objectMapper, nested, restParts, value);
            }
            return;
        }
        node.put(part, value);
    }

    private static void fillArray(
            @Nonnull final ObjectMapper objectMapper, @Nonnull final ArrayNode node,
            @Nonnull final String[] parts, @Nullable final String value
    ) {
        String part = parts[0];
        if (parts.length > 1) {
            String currentPart = part;
            int index = Integer.parseInt(currentPart.substring(1, currentPart.length() - 1));
            part = parts[1];
            if (ARRAY_PART.matcher(part).find()) {
                if (!node.has(index)) {
                    node.insert(index, objectMapper.createArrayNode());
                }
                String[] restParts = new String[parts.length - 1];
                System.arraycopy(parts, 1, restParts, 0, restParts.length);
                fillArray(objectMapper, (ArrayNode) node.get(index), restParts, value);
            } else {
                if (!node.has(index)) {
                    node.insert(index, objectMapper.createObjectNode());
                }
                String[] restParts = new String[parts.length - 1];
                System.arraycopy(parts, 1, restParts, 0, restParts.length);
                fillObject(objectMapper, (ObjectNode) node.get(index), restParts, value);
            }
        } else {
            int index = Integer.parseInt(part.substring(1, part.length() - 1));
            node.insert(index, value);
        }
    }

}
