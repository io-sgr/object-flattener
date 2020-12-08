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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.type.MapType;
import com.google.common.collect.ImmutableMap;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ObjectFlattenerTest {

    @Test
    public void testFlattenNestedArray() throws IOException {
        final Map<String, Object> obj = new LinkedHashMap<>();
        obj.put("key_1", "text");
        obj.put("key_2", null);
        obj.put("key_3", Collections.singletonList("sample_value"));
        obj.put("key_4", ImmutableMap.of("4n1", "4nv1"));
        obj.put("key_5", Arrays.asList(
                ImmutableMap.of("foo", "bar"),
                null,
                ImmutableMap.of("k1", "v1", "k2", Arrays.asList("1", "2", "3"))
                )
        );
        obj.put("key_6", Arrays.asList(
                Arrays.asList("n1", "n2"),
                Arrays.asList(ImmutableMap.of("n1", "nv1"), ImmutableMap.of("n2", "nv2")),
                Arrays.asList("n5", "n6", "n7"))
        );

        final Map<String, Object> map = ImmutableMap.of("prefix", obj, "other", "some_other");
        final Map<String, String> flattened = ObjectFlattener.flatten(map);

        final JsonNode node = ObjectFlattener.unflatten(flattened, "prefix");
        final MapType mapType = ObjectFlattener.OBJECT_MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, Object.class);
        final Map<String, Object> obj2 = ObjectFlattener.OBJECT_MAPPER.readValue(node.traverse(), mapType);
        assertEquals(obj.get("key_1"), obj2.get("key_1"));
        assertEquals(obj.get("key_2"), obj2.get("key_2"));
        assertEquals(obj.get("key_3"), obj2.get("key_3"));
        assertEquals(obj.get("key_4"), obj2.get("key_4"));

        final List<?> list5 = ((List<?>) obj.get("key_5"));
        final List<?> list5new = (List<?>) obj2.get("key_5");
        for (int i = 0; i < list5.size(); i++) {
            if (list5.get(i) == null) {
                assertNull(list5new.get(i));
                continue;
            }
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) list5.get(i)).entrySet()) {
                if (entry.getValue() instanceof List) {
                    assertArrayEquals(((List<?>) entry.getValue()).toArray(), ((List<?>) ((Map<?, ?>) list5new.get(i)).get(entry.getKey())).toArray());
                } else {
                    assertEquals(entry.getValue(), ((Map<?, ?>) list5new.get(i)).get(entry.getKey()));
                }
            }
        }

        final List<?> list6 = ((List<?>) obj.get("key_6"));
        final List<?> list6new = (List<?>) obj2.get("key_6");
        for (int i = 0; i < list6.size(); i++) {
            assertArrayEquals(((List<?>) list6.get(i)).toArray(), ((List<?>) list6new.get(i)).toArray());
        }
    }

}