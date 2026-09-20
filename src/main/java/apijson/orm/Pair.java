/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import apijson.StringUtil;

/**key:value
 * @author Lemon
 */
public class Pair extends Entry<String, String> {

	private static final Map<String, Class<?>> CLASS_MAP;
	static {
		CLASS_MAP = new HashMap<String, Class<?>>();
		CLASS_MAP.put(boolean.class.getSimpleName(), boolean.class);
		CLASS_MAP.put(int.class.getSimpleName(), int.class);
		CLASS_MAP.put(long.class.getSimpleName(), long.class);
		CLASS_MAP.put(float.class.getSimpleName(), float.class);
		CLASS_MAP.put(double.class.getSimpleName(), double.class);
		CLASS_MAP.put(Boolean.class.getSimpleName(), Boolean.class);
		CLASS_MAP.put(Integer.class.getSimpleName(), Integer.class);
		CLASS_MAP.put(Long.class.getSimpleName(), Long.class);
		CLASS_MAP.put(Float.class.getSimpleName(), Float.class);
		CLASS_MAP.put(Double.class.getSimpleName(), Double.class);

		CLASS_MAP.put(Object.class.getSimpleName(), Object.class);
		CLASS_MAP.put(String.class.getSimpleName(), String.class);
		CLASS_MAP.put(Collection.class.getSimpleName(), Collection.class);//不允许指定<T>
		CLASS_MAP.put(Map.class.getSimpleName(), Map.class);//不允许指定<T>
//		CLASS_MAP.put(JSONMap.class.getSimpleName(), JSONMap.class);//必须有，Map中没有getLongValue等方法
//		CLASS_MAP.put(JSONList.class.getSimpleName(), JSONList.class);//必须有，Collection中没有getJSONObject等方法
	}


	public Pair() {
		super();
	}

	public boolean isEmpty(boolean trim) {
		return StringUtil.isNotEmpty(key, trim) == false && StringUtil.isNotEmpty(value, trim) == false;
	}

	/**
	 * @param <K>
	 * @param pair
	 * @return
	 */
	public static <K, V> boolean isCorrect(Entry<K, V> pair) {
		return pair != null && StringUtil.isNotEmpty(pair.getValue(), true);
	}

	/**
	 * @return
	 */
	public String toPairString() {
		return toPairString(getKey(), getValue());
	}
	/**
	 * @param typeKey
	 * @param valueKey
	 * @return
	 */
	public static String toPairString(String typeKey, String valueKey) {
		return (typeKey == null ? "" : typeKey + ":") + valueKey;
	}
	/**
	 * @param type
	 * @param value
	 * @return
	 */
	public static String toPairString(Class<?> type, Object value) {
		return toPairString(type == null ? null : type.getSimpleName(), StringUtil.get(value));
	}

	/**
	 * isRightValueDefault = false;
	 * "key":null不应该出现？因为FastJSON内默认不存null
	 * @param pair leftKey:rightValue
	 * @return {@link #parseEntry(String, boolean)}
	 */
	public static Entry<String, String> parseEntry(String pair) {
		return parseEntry(pair, false);
	}
	/**
	 * isRightValueDefault = false;
	 * "key":null不应该出现？因为FastJSON内默认不存null
	 * @param pair leftKey:rightValue
	 * @param isRightValueDefault 右边值缺省，当pair不包含 : 时默认整个pair为leftKey；false-相反
	 * @return {@link #parseEntry(String, boolean, String)}
	 */
	public static Entry<String, String> parseEntry(String pair, boolean isRightValueDefault) {
		return parseEntry(pair, isRightValueDefault, null);
	}
	/**
	 * "key":null不应该出现？因为FastJSON内默认不存null
	 * @param pair leftKey:rightValue
	 * @param isRightValueDefault 右边值缺省，当pair不包含 : 时默认整个pair为leftKey；false-相反
	 * @param defaultValue 缺省值
	 * @return @NonNull
	 */
	public static Entry<String, String> parseEntry(String pair, boolean isRightValueDefault, String defaultValue) {
		pair = StringUtil.get(pair);//让客户端去掉所有空格 getNoBlankString(pair);
		Entry<String, String> entry = new Entry<String, String>();
		if (pair.isEmpty() == false) {
			int index = pair.indexOf(":");
			if (index < 0) {
				entry.setKey(isRightValueDefault ? pair : defaultValue);
				entry.setValue(isRightValueDefault ? defaultValue : pair);
			} else {
				entry.setKey(pair.substring(0, index));
				entry.setValue(pair.substring(index + 1, pair.length()));
			}

		}
		return entry;
	}
	/**
	 * @param pair
	 * @return
	 */
	public static Entry<String, String> parseVariableEntry(String pair) {
		return parseEntry(pair, false, Object.class.getSimpleName());
	}
	/**
	 * @param pair
	 * @param valueMap
	 * @return
	 */
	public static Entry<Class<?>, Object> parseVariableEntry(String pair, Map<String, Object> valueMap) {
		pair = StringUtil.get(pair);//让客户端去掉所有空格 getNoBlankString(pair);
		Entry<Class<?>, Object> entry = new Entry<Class<?>, Object>();
		if (pair.isEmpty() == false) {
			int index = pair.contains(":") ? pair.indexOf(":") : -1;

			entry.setKey(CLASS_MAP.get(index < 0 ? Object.class.getSimpleName() : pair.substring(0, index)));
			entry.setValue(valueMap == null ? null : valueMap.get(pair.substring(index + 1, pair.length())));
		}
		return entry;
	}
}
