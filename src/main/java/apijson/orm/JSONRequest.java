/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import java.util.*;

import apijson.JSON;
import apijson.Log;
import apijson.StringUtil;

/**JSONRequest for Server to replace apijson.JSONMap,
 * put JSON.parseObject(value) and not encode in public cases
 * @author Lemon
 * @see #put(String, Object)
 */
public class JSONRequest implements apijson.JSONRequest<LinkedHashMap<String, Object>, ArrayList<Object>> {

	protected Map<String, Object> map = new LinkedHashMap<>();
	public JSONRequest() {
		super();
	}
	/**
	 * encode = true
	 * {@link #JSONRequest(String, Object)}
	 * @param object
	 */
	public JSONRequest(Object object) {
		super();
		put(object);
	}
	/**
	 * @param name
	 * @param object
	 */
	public JSONRequest(String name, Object object) {
		super();
		put(name, object);
	}

	///**create a parent JSONMap named KEY_ARRAY
	// * @param count
	// * @param page
	// * @return {@link #toArray(int, int)}
	// */
	//public LinkedHashMap<String, Object> toArray(int count, int page) {
	//	return toArray(count, page, null);
	//}
	//
	///**create a parent JSONMap named name+KEY_ARRAY.
	// * @param count
	// * @param page
	// * @param name
	// * @return {name+KEY_ARRAY : this}. if needs to be put, use {@link #putsAll(Map<? extends String, ? extends Object>)} instead
	// */
	//public LinkedHashMap<String, Object> toArray(int count, int page, String name) {
	//	return new JSONRequest(StringUtil.get(name) + KEY_ARRAY, this.setCount(count).setPage(page));
	//}

	@Override
	public JSONRequest putsAll(Map<? extends String, ? extends Object> m) {
		putAll(m);
		return this;
	}

	/**
	 * @param value
	 * @return {@link #puts(String, Object)}
	 */
	@Override
	public JSONRequest puts(Object value) {
		put(value);
		return this;
	}
	/**
	 * @param key
	 * @param value
	 * @return this
	 * @see {@link #put(String, Object)}
	 */
	@Override
	public JSONRequest puts(String key, Object value) {
		put(key, value);
		return this;
	}


	/**自定义类型必须转为JSONObject或JSONArray，否则RequestParser解析不了
	 */
	@Override
	public Object put(String key, Object value) {
		if (value == null) {//  || key == null
			return null;
		}

        Object target = null;
        try {
            target = JSON.parse(value);
        } catch (Exception e) {
            // nothing
			Log.e(TAG, "JSON.parse failed for key: " + key, e);
        }
        //		if (target == null) { // "tag":"User" 报错
		//			return null;
		//		}
		return map.put(StringUtil.isNotEmpty(key, true) ? key : value.getClass().getSimpleName() //must handle key here
				, target == null ? value : target);
	}


	@Override
	public int size() {
		return map.size();
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public boolean containsKey(Object key) {
		return map.containsKey(key);
	}

	@Override
	public boolean containsValue(Object value) {
		return map.containsValue(value);
	}

	@Override
	public Object get(Object key) {
		return map.get(key);
	}

	@Override
	public Object remove(Object key) {
		return map.remove(key);
	}


	@Override
	public void clear() {
		map.clear();
	}

	@Override
	public Set<String> keySet() {
		return map.keySet();
	}

	@Override
	public Collection<Object> values() {
		return map.values();
	}

	@Override
	public Set<Entry<String, Object>> entrySet() {
		return map.entrySet();
	}

	@Override
	public String toString() {
		return JSON.toJSONString(map);
	}

	public String toJSONString() {
		return JSON.toJSONString(map);
	}

}
