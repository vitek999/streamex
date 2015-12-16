/*
 * Copyright 2015 Tagir Valeev
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package one.util.streamex;

import static one.util.streamex.TestHelpers.*;
import static org.junit.Assert.*;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import one.util.streamex.EntryStream;
import one.util.streamex.IntStreamEx;
import one.util.streamex.MoreCollectors;
import one.util.streamex.StreamEx;
import one.util.streamex.StreamExTest.Point;

import org.junit.Test;

public class EntryStreamTest {
    @Test
    public void testCreate() {
        assertEquals(0, EntryStream.empty().count());
        assertEquals(0, EntryStream.empty().count());
        Map<String, Integer> data = createMap();
        assertEquals(data, EntryStream.of(data).toMap());
        assertEquals(data, EntryStream.of(data.entrySet().stream()).toMap());
        Map<String, Integer> expected = new HashMap<>();
        expected.put("aaa", 3);
        expected.put("bbb", 3);
        expected.put("c", 1);
        assertEquals(expected, StreamEx.of("aaa", "bbb", "c").mapToEntry(String::length).toMap());
        assertEquals(expected, StreamEx.of("aaa", "bbb", "c").mapToEntry(s -> s, String::length).toMap());
        assertEquals(Collections.singletonMap("foo", 1), EntryStream.of("foo", 1).toMap());
        assertEquals("a->b:c->d", EntryStream.of("a", "b", "c", "d").join("->").joining(":"));
        assertEquals(createMap(), EntryStream.of("a", 1, "bb", 22, "ccc", 33).toMap());

        assertEquals(
            expected,
            StreamEx.of(Collections.singletonMap("aaa", 3), Collections.singletonMap("bbb", 3),
                Collections.singletonMap("c", 1), Collections.emptyMap()).flatMapToEntry(m -> m).toMap());

        EntryStream<String, Integer> stream = EntryStream.of(data);
        assertSame(stream.stream, EntryStream.of(stream).stream);
        assertSame(stream.stream, EntryStream.of(StreamEx.of(EntryStream.of(stream))).stream);

        assertEquals(Collections.singletonMap("aaa", 3),
            EntryStream.of(Collections.singletonMap("aaa", 3).entrySet().spliterator()).toMap());
        assertEquals(Collections.singletonMap("aaa", 3),
            EntryStream.of(Collections.singletonMap("aaa", 3).entrySet().iterator()).toMap());
    }
    
    @Test
    public void testSequential() {
        EntryStream<String, Integer> stream = EntryStream.of(createMap());
        assertFalse(stream.isParallel());
        stream = stream.parallel();
        assertTrue(stream.isParallel());
        stream = stream.sequential();
        assertFalse(stream.isParallel());
    }

    @Test
    public void testZip() {
        Map<String, Integer> expected = new HashMap<>();
        expected.put("aaa", 3);
        expected.put("bbb", 3);
        expected.put("c", 1);
        assertEquals(expected, EntryStream.zip(Arrays.asList("aaa", "bbb", "c"), Arrays.asList(3, 3, 1)).toMap());
        assertEquals(expected, EntryStream.zip(new String[] { "aaa", "bbb", "c" }, new Integer[] { 3, 3, 1 }).toMap());
    }

    @Test
    public void testWithIndex() {
        Map<Integer, String> map = EntryStream.of(Arrays.asList("a", "bbb", "cc")).toMap();
        assertEquals(3, map.size());
        assertEquals("a", map.get(0));
        assertEquals("bbb", map.get(1));
        assertEquals("cc", map.get(2));
        Map<Integer, String> map2 = EntryStream.of(new String[] { "a", "bbb", "cc" }).toMap();
        assertEquals(map, map2);

        Map<Integer, List<String>> grouping = EntryStream.of(new String[] { "a", "bbb", "cc", null, null })
                .append(EntryStream.of(new String[] { "bb", "bbb", "c", null, "e" })).distinct().grouping();
        assertEquals(Arrays.asList("a", "bb"), grouping.get(0));
        assertEquals(Arrays.asList("bbb"), grouping.get(1));
        assertEquals(Arrays.asList("cc", "c"), grouping.get(2));
        assertEquals(Collections.singletonList(null), grouping.get(3));
        assertEquals(Arrays.asList(null, "e"), grouping.get(4));

        assertEquals("0=a,1=bbb,2=cc",
            EntryStream.of(new String[] { "a", "bbb", "cc" }).map(Object::toString).joining(","));
    }
    
    @Test(expected = UnsupportedOperationException.class)
    public void testWithIndexModify() {
        EntryStream.of(Collections.singletonList("1")).forEach(entry -> entry.setValue("2"));
    }

    @Test
    public void testMap() {
        assertEquals(Arrays.asList("1a", "22bb", "33ccc"),
            EntryStream.of(createMap()).map(entry -> entry.getValue() + entry.getKey()).toList());
    }

    @Test
    public void testMapKeyValue() {
        assertEquals(Arrays.asList("1a", "22bb", "33ccc"), EntryStream.of(createMap()).mapKeyValue((k, v) -> v + k)
                .toList());
    }

    @Test
    public void testFilter() {
        assertEquals(Collections.singletonMap("a", 1), EntryStream.of(createMap()).filterKeys(s -> s.length() < 2)
                .toMap());
        assertEquals(Collections.singletonMap("bb", 22), EntryStream.of(createMap()).filterValues(v -> v % 2 == 0)
                .toMap());
        assertEquals(Collections.singletonMap("ccc", 33),
            EntryStream.of(createMap()).filterKeyValue((str, num) -> !str.equals("a") && num != 22).toMap());
    }

    @Test
    public void testPeek() {
        List<String> keys = new ArrayList<>();
        assertEquals(createMap(), EntryStream.of(createMap()).peekKeys(keys::add).toMap());
        assertEquals(Arrays.asList("a", "bb", "ccc"), keys);

        List<Integer> values = new ArrayList<>();
        assertEquals(createMap(), EntryStream.of(createMap()).peekValues(values::add).toMap());
        assertEquals(Arrays.asList(1, 22, 33), values);

        Map<String, Integer> map = new LinkedHashMap<>();
        assertEquals(createMap(), EntryStream.of(createMap()).peekKeyValue(map::put).toMap());
        assertEquals(createMap(), map);
    }

    @Test
    public void testRemove() {
        Map<String, List<Integer>> data = new HashMap<>();
        data.put("aaa", Collections.emptyList());
        data.put("bbb", Collections.singletonList(1));
        assertEquals(Arrays.asList("bbb"), EntryStream.of(data).removeValues(List::isEmpty).keys().toList());
        assertEquals(Arrays.asList("aaa"), EntryStream.of(data).removeKeys(Pattern.compile("bbb").asPredicate()).keys()
                .toList());
    }

    @Test
    public void testLimit() {
        assertEquals(Collections.singletonMap("a", 1), EntryStream.of(createMap()).limit(1).toMap());
    }

    @Test
    public void testKeys() {
        assertEquals(new HashSet<>(Arrays.asList("a", "bb", "ccc")), EntryStream.of(createMap()).keys().toSet());
    }

    @Test
    public void testValues() {
        assertEquals(new HashSet<>(Arrays.asList(1, 22, 33)), EntryStream.of(createMap()).values().toSet());
    }

    @Test
    public void testMapKeys() {
        Map<Integer, Integer> expected = new HashMap<>();
        expected.put(1, 1);
        expected.put(2, 22);
        expected.put(3, 33);
        Map<Integer, Integer> result = EntryStream.of(createMap()).mapKeys(String::length).toMap();
        assertEquals(expected, result);
    }

    @Test
    public void testMapValues() {
        Map<String, String> expected = new HashMap<>();
        expected.put("a", "1");
        expected.put("bb", "22");
        expected.put("ccc", "33");
        Map<String, String> result = EntryStream.of(createMap()).mapValues(String::valueOf).toMap();
        assertEquals(expected, result);
    }

    @Test
    public void testMapToValue() {
        Map<String, Integer> expected = new HashMap<>();
        expected.put("a", 2);
        expected.put("bb", 24);
        expected.put("ccc", 36);
        Map<String, Integer> result = EntryStream.of(createMap()).mapToValue((str, num) -> str.length() + num).toMap();
        assertEquals(expected, result);
    }

    @Test
    public void testMapToKey() {
        Map<String, Integer> expected = new HashMap<>();
        expected.put("a:1", 1);
        expected.put("bb:22", 22);
        expected.put("ccc:33", 33);
        Map<String, Integer> result = EntryStream.of(createMap()).mapToKey((str, num) -> str + ":" + num).toMap();
        assertEquals(expected, result);
    }

    @Test
    public void testAppend() {
        assertEquals(Arrays.asList(22, 33, 5, 22, 33), EntryStream.of(createMap()).append("dddd", 5)
                .append(createMap()).filterKeys(k -> k.length() > 1).values().toList());
        assertEquals(EntryStream.of(createMap()).toList(), EntryStream.empty().append("a", 1, "bb", 22, "ccc", 33)
                .toList());
        assertEquals("bb:22,a:1,ccc:33", EntryStream.of("bb", 22).append("a", 1, "ccc", 33).join(":").joining(","));
    }

    @Test
    public void testPrepend() {
        assertEquals(Arrays.asList(5, 22, 33, 22, 33),
            EntryStream.of(createMap()).prepend(createMap()).prepend("dddd", 5).filterKeys(k -> k.length() > 1)
                    .values().toList());
        assertEquals("a:1,ccc:33,bb:22", EntryStream.of("bb", 22).prepend("a", 1, "ccc", 33).join(":").joining(","));
        assertEquals("a:1,ccc:33,dddd:40,bb:22",
            EntryStream.of("bb", 22).prepend("a", 1, "ccc", 33, "dddd", 40).join(":").joining(","));
    }

    @Test
    public void testToMap() {
        Map<String, Integer> base = IntStreamEx.range(100).mapToEntry(String::valueOf, Integer::valueOf).toMap();
        TreeMap<String, Integer> result = EntryStream.of(base).toCustomMap(TreeMap::new);
        assertEquals(base, result);
        result = EntryStream.of(base).parallel().toCustomMap(TreeMap::new);
        assertEquals(base, result);

        Map<Integer, String> expected = new HashMap<>();
        expected.put(3, "aaa");
        expected.put(2, "bbdd");
        Function<StreamExSupplier<String>, EntryStream<Integer, String>> fn = supplier -> supplier.get().mapToEntry(
            String::length, Function.identity());
        for(StreamExSupplier<String> supplier : streamEx(() -> StreamEx.of("aaa", "bb", "dd"))) {
            HashMap<Integer, String> customMap = fn.apply(supplier).toCustomMap(String::concat, HashMap::new);
            assertEquals(supplier.toString(), expected, customMap);
            Map<Integer, String> map = fn.apply(supplier).toMap(String::concat);
            assertEquals(supplier.toString(), expected, map);
            SortedMap<Integer, String> sortedMap = fn.apply(supplier).toSortedMap(String::concat);
            assertEquals(supplier.toString(), expected, sortedMap);
            
            checkIllegalStateException(supplier.toString(), () -> fn.apply(supplier).toMap(), "2", "dd", "bb");
            checkIllegalStateException(supplier.toString(), () -> fn.apply(supplier).toSortedMap(), "2", "dd", "bb");
            checkIllegalStateException(supplier.toString(), () -> fn.apply(supplier).toCustomMap(HashMap::new), "2", "dd", "bb");
        }

        assertEquals(createMap(), EntryStream.of(createMap()).parallel().toMap());
        assertTrue(EntryStream.of(createMap()).parallel().toMap() instanceof ConcurrentMap);
        SortedMap<String, Integer> sortedMap2 = EntryStream.of(createMap()).toSortedMap();
        assertEquals(createMap(), sortedMap2);
        assertFalse(sortedMap2 instanceof ConcurrentMap);
        sortedMap2 = EntryStream.of(createMap()).parallel().toSortedMap();
        assertEquals(createMap(), sortedMap2);
        assertTrue(sortedMap2 instanceof ConcurrentMap);
    }

    @Test
    public void testFlatMap() {
        assertEquals(Arrays.asList((int) 'a', (int) 'b', (int) 'b', (int) 'c', (int) 'c', (int) 'c'),
            EntryStream.of(createMap()).flatMap(entry -> entry.getKey().chars().boxed()).toList());
        assertEquals(Arrays.asList("a", "b", "b", "c", "c", "c"),
            EntryStream.of(createMap()).flatCollection(entry -> Arrays.asList(entry.getKey().split(""))).toList());
        assertEquals(Arrays.asList("a", 1, "bb", 22, "ccc", 33),
            EntryStream.of(createMap()).flatMapKeyValue((str, num) -> Stream.of(str, num)).toList());
    }

    @Test
    public void testFlatMapKeys() {
        Map<String, List<Integer>> data = new HashMap<>();
        data.put("aaa", Arrays.asList(1, 2, 3));
        data.put("bb", Arrays.asList(2, 3, 4));
        Map<Integer, List<String>> result = EntryStream.of(data).invert().flatMapKeys(List::stream).grouping();
        Map<Integer, List<String>> expected = new HashMap<>();
        expected.put(1, Arrays.asList("aaa"));
        expected.put(2, Arrays.asList("aaa", "bb"));
        expected.put(3, Arrays.asList("aaa", "bb"));
        expected.put(4, Arrays.asList("bb"));
        assertEquals(expected, result);
        assertEquals(0, EntryStream.<Stream<String>, String>of(null, "a").flatMapKeys(Function.identity()).count());
    }

    @Test
    public void testFlatMapValues() {
        Map<String, List<Integer>> data1 = new HashMap<>();
        data1.put("aaa", Arrays.asList(1, 2, 3));
        data1.put("bb", Arrays.asList(4, 5, 6));
        Map<String, List<Integer>> data2 = new HashMap<>();
        data2.put("aaa", Arrays.asList(10));
        data2.put("bb", Arrays.asList(20));
        data2.put("cc", null);
        Map<String, List<Integer>> result = StreamEx.of(data1, data2, null).flatMapToEntry(m -> m)
                .flatMapValues(l -> l == null ? null : l.stream()).grouping();
        Map<String, List<Integer>> expected = new HashMap<>();
        expected.put("aaa", Arrays.asList(1, 2, 3, 10));
        expected.put("bb", Arrays.asList(4, 5, 6, 20));
        assertEquals(expected, result);

        // Find the key which contains the biggest value in the list
        assertEquals("bb",
            EntryStream.of(data1).flatMapValues(List::stream).maxByInt(Entry::getValue).map(Entry::getKey).orElse(null));
    }

    @Test
    public void testGrouping() {
        Map<String, Integer> data = new LinkedHashMap<>();
        data.put("ab", 1);
        data.put("ac", 2);
        data.put("ba", 3);
        data.put("bc", 4);
        Map<String, List<Integer>> expected = new LinkedHashMap<>();
        expected.put("a", Arrays.asList(1, 2));
        expected.put("b", Arrays.asList(3, 4));
        Supplier<EntryStream<String, Integer>> s = () -> EntryStream.of(data).mapKeys(k -> k.substring(0, 1));
        Map<String, List<Integer>> result = s.get().grouping();
        assertEquals(expected, result);
        TreeMap<String, List<Integer>> resultTree = s.get().grouping(TreeMap::new);
        assertEquals(expected, resultTree);
        result = s.get().parallel().grouping();
        assertEquals(expected, result);
        resultTree = s.get().parallel().grouping(TreeMap::new);
        assertEquals(expected, resultTree);

        for (StreamExSupplier<Integer> supplier : streamEx(() -> IntStreamEx.range(1000).boxed())) {
            assertEquals(supplier.toString(), EntryStream.of(0, 500, 1, 500).toMap(),
                supplier.get().mapToEntry(i -> i / 500, i -> i).grouping(MoreCollectors.countingInt()));
            ConcurrentSkipListMap<Integer, Integer> map = supplier.get().mapToEntry(i -> i / 500, i -> i)
                    .grouping(ConcurrentSkipListMap::new, MoreCollectors.countingInt());
            assertEquals(supplier.toString(), EntryStream.of(0, 500, 1, 500).toMap(), map);
        }
    }

    @Test
    public void testGroupingTo() {
        Map<String, Integer> data = new LinkedHashMap<>();
        data.put("ab", 1);
        data.put("ac", 2);
        data.put("ba", 3);
        data.put("bc", 4);
        Map<String, List<Integer>> expected = new LinkedHashMap<>();
        expected.put("a", Arrays.asList(1, 2));
        expected.put("b", Arrays.asList(3, 4));
        Supplier<EntryStream<String, Integer>> s = () -> EntryStream.of(data).mapKeys(k -> k.substring(0, 1));
        Map<String, List<Integer>> result = s.get().groupingTo(LinkedList::new);
        assertEquals(expected, result);
        assertTrue(result.get("a") instanceof LinkedList);
        result = s.get().parallel().groupingTo(LinkedList::new);
        assertEquals(expected, result);
        assertTrue(result.get("a") instanceof LinkedList);
        SortedMap<String, List<Integer>> resultTree = s.get().groupingTo(TreeMap::new, LinkedList::new);
        assertTrue(result.get("a") instanceof LinkedList);
        assertEquals(expected, resultTree);
        resultTree = s.get().parallel().groupingTo(TreeMap::new, LinkedList::new);
        assertTrue(result.get("a") instanceof LinkedList);
        assertEquals(expected, resultTree);
        resultTree = s.get().parallel().groupingTo(ConcurrentSkipListMap::new, LinkedList::new);
        assertTrue(result.get("a") instanceof LinkedList);
        assertEquals(expected, resultTree);
    }

    @Test
    public void testSorting() {
        Map<String, Integer> data = createMap();
        LinkedHashMap<String, Integer> result = EntryStream.of(data).reverseSorted(Entry.comparingByValue())
                .toCustomMap(LinkedHashMap::new);
        assertEquals("{ccc=33, bb=22, a=1}", result.toString());
    }

    @Test
    public void testDistinct() {
        Map<String, List<Integer>> expected = new LinkedHashMap<>();
        expected.put("aaa", Arrays.asList(3));
        expected.put("bbb", Arrays.asList(3, 3));
        expected.put("cc", Arrays.asList(2));
        assertEquals(expected, StreamEx.of("aaa", "bbb", "bbb", "cc").mapToEntry(String::length).grouping());

        Map<String, List<Integer>> expectedDistinct = new LinkedHashMap<>();
        expectedDistinct.put("aaa", Arrays.asList(3));
        expectedDistinct.put("bbb", Arrays.asList(3));
        expectedDistinct.put("cc", Arrays.asList(2));
        assertEquals(expectedDistinct, StreamEx.of("aaa", "bbb", "bbb", "cc").mapToEntry(String::length).distinct()
                .grouping());
    }

    @Test
    public void testNonNull() {
        Map<String, String> input = new LinkedHashMap<>();
        input.put("a", "b");
        input.put("b", null);
        input.put(null, "c");
        assertEquals(Arrays.asList("b", null), EntryStream.of(input).nonNullKeys().values().toList());
        assertEquals(Arrays.asList("a", null), EntryStream.of(input).nonNullValues().keys().toList());
        assertEquals(Collections.singletonMap("a", "b"), EntryStream.of(input).nonNullValues().nonNullKeys().toMap());
    }

    @Test
    public void testSelect() {
        Map<Object, Object> map = new LinkedHashMap<>();
        map.put("a", 1);
        map.put("b", "2");
        map.put(3, "c");
        assertEquals(Collections.singletonMap("a", 1), EntryStream.of(map).selectValues(Integer.class).toMap());
        assertEquals(Collections.singletonMap(3, "c"), EntryStream.of(map).selectKeys(Integer.class).toMap());

        // Weird way to create a map from the array. Don't do this in production
        // code!
        Object[] interleavingArray = { "a", 1, "bb", 22, "ccc", 33 };
        Map<String, Integer> result = EntryStream
                .of(StreamEx.of(interleavingArray).pairMap(SimpleEntry<Object, Object>::new)).selectKeys(String.class)
                .selectValues(Integer.class).toMap();
        assertEquals(createMap(), result);
    }

    @Test
    public void testInvert() {
        Map<Integer, String> result = EntryStream.of(createMap()).invert().toMap();
        Map<Integer, String> expected = new LinkedHashMap<>();
        expected.put(1, "a");
        expected.put(22, "bb");
        expected.put(33, "ccc");
        assertEquals(expected, result);
    }

    @Test(expected = IllegalStateException.class)
    public void testCollision() {
        StreamEx.of("aa", "aa").mapToEntry(String::length).toCustomMap(LinkedHashMap::new);
    }

    @Test
    public void testForKeyValue() {
        Map<String, Integer> output = new HashMap<>();
        EntryStream.of(createMap()).forKeyValue(output::put);
        assertEquals(output, createMap());
    }

    @Test
    public void testJoin() {
        assertEquals("a = 1; bb = 22; ccc = 33", EntryStream.of(createMap()).join(" = ").joining("; "));
        assertEquals("{[a = 1]; [bb = 22]; [ccc = 33]}",
            EntryStream.of(createMap()).join(" = ", "[", "]").joining("; ", "{", "}"));
    }

    private Map<String, Integer> createMap() {
        Map<String, Integer> data = new LinkedHashMap<>();
        data.put("a", 1);
        data.put("bb", 22);
        data.put("ccc", 33);
        return data;
    }

    @Test
    public void testOfPairs() {
        Random r = new Random(1);
        Point[] pts = StreamEx.generate(() -> new Point(r.nextDouble(), r.nextDouble())).limit(100)
                .toArray(Point[]::new);
        double expected = StreamEx.of(pts).cross(pts).mapKeyValue(Point::distance).mapToDouble(Double::doubleValue)
                .max().getAsDouble();
        assertEquals(expected, EntryStream.ofPairs(pts).mapKeyValue(Point::distance).mapToDouble(Double::doubleValue)
                .max().getAsDouble(), 0.0);
        assertEquals(expected,
            EntryStream.ofPairs(pts).parallel().mapKeyValue(Point::distance).mapToDouble(Double::doubleValue).max()
                    .getAsDouble(), 0.0);
    }

    @Test
    public void testDistinctKeysValues() {
        Supplier<EntryStream<Integer, String>> s = () -> EntryStream.of(1, "a", 1, "b", 2, "b").append(2, "c", 1, "c",
            3, "c");
        assertEquals("1=a,2=b,3=c", s.get().distinctKeys().join("=").joining(","));
        assertEquals("1=a,2=b,3=c", s.get().parallel().distinctKeys().join("=").joining(","));
        assertEquals("1=a,1=b,2=c", s.get().distinctValues().join("=").joining(","));
        assertEquals("1=a,1=b,2=c", s.get().parallel().distinctValues().join("=").joining(","));
    }
}