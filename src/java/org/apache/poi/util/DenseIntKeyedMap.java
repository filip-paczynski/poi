package org.apache.poi.util;

import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.collections4.OrderedMap;

/**
 * No null values allowed
 *
 * TODO: size vs totalUsed vs elemntData.length
 * <p>
 * Author: Filip Paczyński, filip.paczynski@concept.biz.pl
 */
public class DenseIntKeyedMap<V> implements IntKeyedMap<V> {

    private final ExpandStrategy expandStrategy;

    @SuppressWarnings("unchecked")
    private final V NULL_MARKER = (V) new Object();

    private final float expandBy;
    private int firstUsed = -1;
    private int lastUsed = -1;
    private int totalUsed = 0;

    int size = 0;

    private transient V[] elementData;

    private static final ExpandStrategy DEFAULT_EXPAND_STRATEGY = ExpandStrategy.CONSTANT;
    private static final float DEFAULT_EXPAND_BY = 32;

    public static <V> DenseIntKeyedMap<V> empty() {
        return new DenseIntKeyedMap<>(DEFAULT_EXPAND_STRATEGY, DEFAULT_EXPAND_BY);
    }

    /**
     * Constructor for view-like map
     */
    private DenseIntKeyedMap(DenseIntKeyedMap<V> src, int fromIncl, int toExcl) {
        this.elementData = src.elementData;
        this.lastUsed = findLastUsed();
        this.firstUsed = findFirstUsed();
        this.size = findUsedCount(fromIncl, toExcl);
        this.expandBy = src.expandBy;
        this.expandStrategy = src.expandStrategy;
    }

    private DenseIntKeyedMap() {
        throw new UnsupportedOperationException();
    }


    protected DenseIntKeyedMap(ExpandStrategy expandStrategy, float expandBy) {
        this.expandStrategy = expandStrategy;
        this.expandBy = expandBy;
    }

    private void expandIfNeeded(int reqIdx) {
        if (size <= reqIdx) {
            final int newSize = expandStrategy.compute(size, expandBy, reqIdx);

            @SuppressWarnings("unchecked")
            V[] newElements = (V[]) new Object[newSize];
            System.arraycopy(elementData, 0, newElements, 0, newSize);
            this.elementData = newElements;
        }
    }

    @Override
    public int size() {
        return totalUsed;
    }

    @Override
    public boolean isEmpty() {
        return totalUsed == 0;
    }

    /**
     * @deprecated Use {@link #containsKey(int)} when possible, to avoid autoboxing
     */
    @Deprecated
    @Override
    public boolean containsKey(Object key) {
        return containsKey(((Integer) key).intValue());
    }

    public boolean containsKey(int idx) {
        return (idx >= 0 && idx < size ) && elementData[idx] != null;
    }

    @SuppressWarnings("Convert2streamapi")
    @Override
    public boolean containsValue(Object value) {
        if (value == null) {
            value = NULL_MARKER;
        }

        for (Object elem : elementData) {
            if (Objects.equals(elem, value)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @deprecated Use {@link #get(int)} when possible, to avoid autoboxing
     */
    @Override
    public V get(Object key) {
        return get(((Integer) key).intValue());
    }

    public V get(int idx) {
        rangeCheck(idx);
        return elementData[idx];
    }

    /**
     * @deprecated Use {@link #put(int, V)} when possible, to avoid autoboxing
     */
    @Override
    public V put(Integer key, V value) {
        return put(key.intValue(), value);
    }

    public V put(int idx, V value) {
        expandIfNeeded(idx);

        if (value == null) {
            value = NULL_MARKER;
        }

        V prev = elementData[idx];

        if (prev == null) {
            totalUsed++;
        }

        if (firstUsed > idx) {
            firstUsed = idx;
        }
        if (lastUsed < idx) {
            lastUsed = idx;
        }

        elementData[idx] = value;

        return prev;
    }

    public Iterator<V> iterator() {
        return streamValues().iterator();
    }

    public Stream<V> streamValues() {
        return Arrays.stream(elementData).filter(Objects::nonNull);
    }

    public Object[] valuesToArray() {
        return toArrayImpl(size);
    }

    private V[] toArrayImpl(int size) {
        @SuppressWarnings("unchecked")
        V[] copy = (V[]) new Object[size];
        System.arraycopy(elementData, 0, copy, 0, elementData.length);

        return copy;
    }

    public <T> T[] valuesToArray(T[] a) {
        if (a.length <= elementData.length) {
            @SuppressWarnings("unchecked")
            final T[] result = (T[]) valuesToArray();
            return result;
        } else {
            @SuppressWarnings("unchecked")
            final T[] result = (T[]) toArrayImpl(a.length);
            return result;

        }
    }

    @Override
    public void putAll(Map<? extends Integer, ? extends V> m) {
//      int firstIdx = 0;
        int lastIdx = 0;
        if (m instanceof OrderedMap) {
//            @SuppressWarnings("unchecked")
//            Integer first = ((OrderedMap<Integer, ?>) m).firstKey();
//            firstIdx = first;
            @SuppressWarnings("unchecked")
            Integer last = ((OrderedMap<Integer, ?>) m).lastKey();
            lastIdx = last;
        } else if (m instanceof DenseIntKeyedMap<?>) {
            //noinspection ConstantConditions
//            firstIdx = 0;
            //noinspection unchecked
            lastIdx = ((DenseIntKeyedMap<? extends V>) m).lastUsed;
        } else {
//            firstIdx = m.keySet().stream().min(Integer::compareTo)
//                    .map(Integer::intValue).orElse(0);
            lastIdx = m.keySet().stream().max(Integer::compareTo)
                    .map(Integer::intValue).orElse(0);
        }

        expandIfNeeded(lastIdx);

        if (m instanceof DenseIntKeyedMap<?>) {
            @SuppressWarnings("unchecked")
            final DenseIntKeyedMap<V> srcMap = (DenseIntKeyedMap<V>) m;
            V[] srcData = srcMap.elementData;
            System.arraycopy(srcData, srcMap.firstUsed, this.elementData,
                    srcMap.firstUsed, srcMap.lastUsed - srcMap.firstUsed);

            this.lastUsed = srcMap.lastUsed;
            this.size = this.lastUsed + 1;
            this.totalUsed = (int) streamValues()
                    .filter(Objects::nonNull)
                    .count();
        } else {
            for (Entry<? extends Integer, ? extends V> src : m.entrySet()) {
                put(src.getKey().intValue(), src.getValue());
            }
        }

    }

    @Override
    public void clear() {
        @SuppressWarnings("unchecked")
        final V[] newElements = (V[]) new Object[0];
        this.elementData = newElements;
        this.size = 0;
        this.totalUsed = 0;
        this.firstUsed = -1;
        this.lastUsed = -1;
    }

    @Override
    public Set<Integer> keySet() {
        return IntStream.range(0, size).filter(key -> elementData[key] != null)
                .boxed().collect(Collectors.toSet());
    }

    @Override
    public Collection<V> values() {
        return streamValues().collect(Collectors.toList());
    }

    /**
     * @deprecated Causes needles creation of @link {@link java.util.Map.Entry}
     * objects. Use {@link #streamValues()} where possible.
     */
    @Override
    public Set<Entry<Integer, V>> entrySet() {
        return IntStream.range(0, elementData.length)
                .filter(i -> elementData[i] != null)
                .mapToObj(i -> new SimpleImmutableEntry<>(i, elementData[i]))
                .collect(Collectors.toSet());
    }

    /**
     * @deprecated Use {@link #remove(int)} to avoid autoboxing
     */
    @Override
    public V remove(Object key) {
        return remove(((Integer)key).intValue());
    }

    @SuppressWarnings("ReturnOfNull")
    public V remove(int idx) {
        if (idx < size) {
            V prev = elementData[idx];
            elementData[idx] = null;
            if (prev != null) {
                totalUsed--;
                if (lastUsed == idx) {
                    lastUsed = findLastUsed();
                }
                if (idx == size - 1) {
                    size--;
                }

                return prev;
            } else {
                return null;
            }
        }
        return null;
    }

    private int findLastUsed() {
        int result = -1;
        int i;
        for (i = lastUsed; i >= 0; i--) {
            if (elementData[i] != null) {
                result = i;
                break;
            }
        }

        return result;
    }

    private int findFirstUsed() {
        int result = -1;
        int i;
        for (i = 0; i <= size; i++) {
            if (elementData[i] != null) {
                result = i;
                break;
            }
        }

        return result;
    }

    private int findUsedCount(int fromIncl, int toExcl) {
        return (int) Arrays.stream(elementData, fromIncl, toExcl)
                .filter(Objects::nonNull)
                .count();
    }

    @Override
    public Comparator<? super Integer> comparator() {
        return Integer::compareTo;
    }

    @Override
    public SortedMap<Integer, V> subMap(Integer fromKey, Integer toKey) {
        return new DenseIntKeyedMap<>(this, fromKey, toKey);
    }

    @Override
    public SortedMap<Integer, V> headMap(Integer toKey) {
        return new DenseIntKeyedMap<>(this, 0, toKey);
    }

    @Override
    public SortedMap<Integer, V> tailMap(Integer fromKey) {
        return new DenseIntKeyedMap<>(this, fromKey, lastUsed);
    }

    @Override
    public Integer firstKey() {
        return firstUsed;
    }

    @Override
    public Integer lastKey() {
        return lastUsed;
    }

    /**
     * Checks if the given index is in range.  If not, throws an appropriate
     * runtime exception.  This method does *not* check if the index is
     * negative: It is always used immediately prior to an array access,
     * which throws an ArrayIndexOutOfBoundsException if index is negative.
     */
    private void rangeCheck(int index) {
        if (index >= size)
            throw new IndexOutOfBoundsException(outOfBoundsMsg(index));
    }

    /**
     * Constructs an IndexOutOfBoundsException detail message.
     * Of the many possible refactorings of the error handling code,
     * this "outlining" performs best with both server and client VMs.
     */
    private String outOfBoundsMsg(int index) {
        return "Index: " + index + ", Size: " + size;
    }

    enum ExpandStrategy {
        CONSTANT,
        FACTOR
        //
        ;

        int compute(int prevSize, float expandBy, int reqSize) {
            int newSize;
            switch (this) {
            case CONSTANT:
                newSize = prevSize + (int) expandBy;
                break;
            case FACTOR:
                newSize = prevSize + (int) (prevSize * expandBy) + 1;
                break;
            default:
                throw new IllegalArgumentException(
                        String.format("Unknown strategy: %s", this.name()));
            }

            return newSize <= reqSize ?
                    newSize :
                    compute(reqSize, expandBy, reqSize);
        }
    }

}
