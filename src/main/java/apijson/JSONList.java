/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/

package apijson;

import java.util.*;

/**
 * Custom JSONList implementation based on ArrayList to replace com.alibaba.fastjson.JSONList
 * Maintains same API as fastjson but uses standard Java List implementation
 * @author Lemon
 */
public interface JSONList<M extends Map<String, Object>, L extends List<Object>> extends List<Object> {
    public static final String TAG = "JSONList";

    ///**
    // * Create an empty JSONList
    // */
    //default JSONList() {
    //    super();
    //}
    //
    //private int initialCapacity = 10;
    ///**
    // * Create a JSONList with initial capacity
    // * @param initialCapacity the initial capacity
    // */
    //default JSONList(int initialCapacity) {
    //    super(initialCapacity);
    //}
    //
    ///**
    // * Create a JSONList from a Collection
    // * @param collection the collection to copy from
    // */
    //default JSONList(Collection<?> collection) {
    //    super(collection);
    //}
    //
    ///**
    // * Create a JSONList from a JSON string
    // * @param json JSON string
    // */
    //default JSONList(String json) {
    //    this();
    //    List<Object> list = JSON.parseArray(json);
    //    if (list != null) {
    //        addAll(list);
    //    }
    //}
    //
    /**
     * Get a JSONMap at the specified index
     * @param index the index
     * @return the JSONMap or null if not a JSONMap
     */
    default M getJSONObject(int index) {
        if (index < 0 || index >= size()) {
            return null;
        }
        
        Object obj = get(index);
        if (obj instanceof Map<?, ?>) {
            return JSON.createJSONObject((Map<? extends String, ?>) obj);
        }

        return null;
    }
    
    /**
     * Get a JSONList at the specified index
     * @param index the index
     * @return the JSONList or null if not a JSONList
     */
    default L getJSONArray(int index) {
        if (index < 0 || index >= size()) {
            return null;
        }
        
        Object obj = get(index);
        if (obj instanceof List<?>) {
            return JSON.createJSONArray((List<?>) obj);
        }

        return null;
    }
    
    /**
     * Get a boolean value at the specified index
     * @param index the index
     * @return the boolean value or false if not found
     */
    default boolean getBooleanValue(int index) {
        if (index < 0 || index >= size()) {
            return false;
        }
        
        Object obj = get(index);
        if (obj instanceof Boolean) {
            return (Boolean) obj;
        } else if (obj instanceof Number) {
            return ((Number) obj).intValue() != 0;
        } else if (obj instanceof String) {
            return Boolean.parseBoolean((String) obj);
        }

        return false;
    }
    
    /**
     * Get an integer value at the specified index
     * @param index the index
     * @return the integer value or 0 if not found
     */
    default int getIntValue(int index) {
        if (index < 0 || index >= size()) {
            return 0;
        }
        
        Object obj = get(index);
        if (obj instanceof Number) {
            return ((Number) obj).intValue();
        } else if (obj instanceof String) {
            try {
                return Integer.parseInt((String) obj);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return 0;
    }
    
    /**
     * Get a long value at the specified index
     * @param index the index
     * @return the long value or 0 if not found
     */
    default long getLongValue(int index) {
        if (index < 0 || index >= size()) {
            return 0L;
        }
        
        Object obj = get(index);
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        } else if (obj instanceof String) {
            try {
                return Long.parseLong((String) obj);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return 0L;
    }
    
    /**
     * Get a double value at the specified index
     * @param index the index
     * @return the double value or 0 if not found
     */
    default double getDoubleValue(int index) {
        if (index < 0 || index >= size()) {
            return 0.0;
        }
        
        Object obj = get(index);
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        } else if (obj instanceof String) {
            try {
                return Double.parseDouble((String) obj);
            } catch (NumberFormatException e) {
                // Ignore
            }
        }
        return 0.0;
    }
    
    /**
     * Get a string value at the specified index
     * @param index the index
     * @return the string value or null if not found
     */
    default String getString(int index) {
        if (index < 0 || index >= size()) {
            return null;
        }
        
        Object obj = get(index);
        return obj != null ? obj.toString() : null;
    }

    
    default String toJSONString() {
        return JSON.toJSONString(this);
    }

    //@Override
    //default boolean containsAll(Collection<?> c) {
    //    if (c == null || c.isEmpty()) {
    //        return true;
    //    }
    //    return super.containsAll(c);
    //}
    //
    //@Override
    //default boolean addAll(Collection<?> c) {
    //    if (c == null || c.isEmpty()) {
    //        return true;
    //    }
    //    return super.addAll(c);
    //}
    //
    //@Override
    //default boolean addAll(int index, Collection<?> c) {
    //    if (c == null || c.isEmpty()) {
    //        return true;
    //    }
    //
    //    int sz = size();
    //    if (index < 0 || index >= sz) {
    //        index += sz;
    //    }
    //
    //    return super.addAll(index, c);
    //}
    //
    //@Override
    //default boolean removeAll(Collection<?> c) {
    //    if (c == null || c.isEmpty()) {
    //        return true;
    //    }
    //    return super.removeAll(c);
    //}
    //
    //@Override
    //default boolean retainAll(Collection<?> c) {
    //    if (c == null || c.isEmpty()) {
    //        return true;
    //    }
    //    return super.retainAll(c);
    //}
    //
    //
    //@Override
    //default Object get(int index) {
    //    int sz = size();
    //    if (index < 0 || index >= sz) {
    //        index += sz;
    //    }
    //
    //    return super.get(index);
    //}
    //
    //@Override
    //default Object set(int index, Object element) {
    //    int sz = size();
    //    if (index < 0 || index >= sz) {
    //        index += sz;
    //    }
    //
    //    return super.set(index, element);
    //}
    //
    //@Override
    //default void add(int index, Object element) {
    //    int sz = size();
    //    if (index < 0 || index >= sz) {
    //        index += sz;
    //    }
    //
    //    super.add(index, element);
    //}
    //
    //@Override
    //default Object remove(int index) {
    //    int sz = size();
    //    if (index < 0 && index >= -sz) {
    //        index += sz;
    //    }
    //    if (index < 0 || index >= sz) {
    //        return null;
    //    }
    //
    //    return super.remove(index);
    //}
    //
    //@Override
    //default ListIterator<Object> listIterator(int index) {
    //    int sz = size();
    //    if (index < 0 && index >= -sz) {
    //        index += sz;
    //    }
    //
    //    return super.listIterator(index);
    //}
    //
    //@Override
    //default List<Object> subList(int fromIndex, int toIndex) {
    //    int sz = size();
    //    if (fromIndex < 0 && fromIndex >= -sz) {
    //        fromIndex += sz;
    //    }
    //    if (toIndex < 0 && toIndex >= -sz) {
    //        toIndex += sz;
    //    }
    //
    //    return super.subList(fromIndex, toIndex);
    //}

}