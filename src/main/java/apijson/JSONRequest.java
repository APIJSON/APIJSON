/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static apijson.StringUtil.PATTERN_ALPHA_BIG;

/**wrapper for request
 * @author Lemon
 * @see #puts
 * @see #toArray
 * @use JSONRequest<M, L> request = JSON.createJSONObject(...);
 * <br> request.puts(...);//not a must
 * <br> request.toArray(...);//not a must
 */
public interface JSONRequest<M extends Map<String, Object>, L extends List<Object>> extends JSONMap<M, L> {

	//default JSONRequest() {
	//	super();
	//}
	///**
	// * @param object must be annotated by {@link MethodAccess}
	// * @see	{@link #JSONRequest(String, Object)}
	// */
	//default JSONRequest(Object object) {
	//	this(null, object);
	//}
	///**
	// * @param name
	// * @param object
	// * @see {@link #puts(String, Object)}
	// */
	//default JSONRequest(String name, Object object) {
	//	this();
	//	puts(name, object);
	//}

	//public static JSONRequest<M, L> valueOf(Object obj) {
	//	JSONRequest<M, L> req = new JSONRequest() {};
	//	Map<String, Object> m = JSON.parseObject(obj);
	//	if (m != null && ! m.isEmpty()) {
	//		req.map.putAll(m);
	//	}
	//	return req;
	//}

	public static final String KEY_TAG = "tag";//只在最外层，最外层用JSONRequest
	public static final String KEY_VERSION = "version";//只在最外层，最外层用JSONRequest
	public static final String KEY_FORMAT = "format";//只在最外层，最外层用JSONRequest

	/**set "tag":tag in outermost layer
	 * for write operations
	 * @param tag
	 * @return
	 */
	default JSONRequest<M, L> setTag(String tag) {
		return puts(KEY_TAG, tag);
	}

	/**set "version":version in outermost layer
	 * for target version of request
	 * @param version
	 * @return
	 */
	default JSONRequest<M, L> setVersion(Integer version) {
		return puts(KEY_VERSION, version);
	}

	/**set "format":format in outermost layer
	 * for format APIJSON special keys to normal keys of response
	 * @param format
	 * @return
	 */
	default JSONRequest<M, L> setFormat(Boolean format) {
		return puts(KEY_FORMAT, format);
	}


	//array object <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

	public static final int QUERY_TABLE = 0;
	public static final int QUERY_TOTAL = 1;
	public static final int QUERY_ALL = 2;

	public static final String QUERY_TABLE_STRING = "TABLE";
	public static final String QUERY_TOTAL_STRING = "TOTAL";
	public static final String QUERY_ALL_STRING = "ALL";

	public static final String SUBQUERY_RANGE_ALL = "ALL";
	public static final String SUBQUERY_RANGE_ANY = "ANY";

	public static final String KEY_QUERY = "query";
	public static final String KEY_COMPAT = "compat";
	public static final String KEY_COUNT = "count";
	public static final String KEY_PAGE = "page";
	public static final String KEY_JOIN = "join";
	public static final String KEY_SUBQUERY_RANGE = "range";
	public static final String KEY_SUBQUERY_FROM = "from";

	public static final List<String> ARRAY_KEY_LIST = new ArrayList<>(Arrays.asList(
        KEY_QUERY, KEY_COMPAT ,KEY_COUNT, KEY_PAGE, KEY_JOIN, KEY_SUBQUERY_RANGE, KEY_SUBQUERY_FROM
	));

	/**set what to query in Array layer
	 * @param query what need to query, Table,total,ALL?
	 * @return
	 * @see {@link #QUERY_TABLE}
	 * @see {@link #QUERY_TOTAL}
	 * @see {@link #QUERY_ALL}
	 */
	default JSONRequest<M, L> setQuery(int query) {
		return puts(KEY_QUERY, query);
	}

	/**set maximum count of Tables to query in Array layer
	 * @param count <= 0 || >= max ? max : count
	 * @return
	 */
	default JSONRequest<M, L> setCount(int count) {
		return puts(KEY_COUNT, count);
	}

	/**set page of Tables to query in Array layer
	 * @param page <= 0 ? 0 : page
	 * @return
	 */
	default JSONRequest<M, L> setPage(int page) {
		return puts(KEY_PAGE, page);
	}

	/**set joins of Main Table and it's Vice Tables in Array layer
	 * @param joins "@/User/id@", "&/User/id@,>/Comment/momentId@" ...
	 * @return
	 */
	default JSONRequest<M, L> setJoin(String... joins) {
		return setJson(this, StringUtil.get(joins));
	}

	public static <M extends Map<String, Object>> M setJson(M m, String... joins) {
		m.put(KEY_JOIN, StringUtil.get(joins));
		return m;
	}

	/**set range for Subquery
	 * @param range
	 * @return
	 * @see {@link #SUBQUERY_RANGE_ALL}
	 * @see {@link #SUBQUERY_RANGE_ANY}
	 */
	default JSONRequest<M, L> setSubqueryRange(String range) {
		return puts(KEY_SUBQUERY_RANGE, range);
	}

	/**set from for Subquery
	 * @param from
	 * @return
	 */
	default JSONRequest<M, L> setSubqueryFrom(String from) {
		return puts(KEY_SUBQUERY_FROM, from);
	}

	//array object >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	/**create a parent JSONMap named KEY_ARRAY
	 * @param count
	 * @param page
	 * @return {@link #toArray(int, int)}
	 */
	default M toArray(int count, int page) {
		return toArray(count, page, null);
	}

	/**create a parent JSONMap named name+KEY_ARRAY.
	 * @param count
	 * @param page
	 * @param name
	 * @return {name+KEY_ARRAY : this}. if needs to be put, use {@link #putsAll(Map<? extends String, ? extends Object>)} instead
	 */
	default M toArray(int count, int page, String name) {
		return JSON.createJSONObject(StringUtil.get(name) + KEY_ARRAY, this.setCount(count).setPage(page));
	}


	@Override
	default JSONRequest<M, L> putsAll(Map<? extends String, ? extends Object> map) {
		putAll(map);
		return this;
	}

	@Override
	default JSONRequest<M, L> puts(Object value) {
		put(value);
		return this;
	}

	@Override
	default JSONRequest<M, L> puts(String key, Object value) {
		put(key, value);
		return this;
	}


	/**ABCdEfg => upper ? A-B-CD-EFG : a-b-cd-efg
	 * @param key
	 * @return
	 */
	public static String recoverHyphen(@NotNull String key, Boolean upper) {
		return recoverDivider(key, "-", upper);
	}

	/**ABCdEfg => upper ? A_B_CD_EFG : a_b_cd_efg
	 * @param key
	 * @return
	 */
	public static String recoverUnderline(@NotNull String key, Boolean upper) {
		return recoverDivider(key, "_", upper);
	}

	/**ABCdEfg => upper ? A$B$CD$EFG : a$b$cd$efg
	 * @param key
	 * @return
	 */
	public static String recoverDollar(@NotNull String key, Boolean upper) {
		return recoverDivider(key, "$", upper);
	}

	/**ABCdEfg => upper ? A.B.CD.EFG : a.b.cd.efg
	 * @param key
	 * @return
	 */
	public static String recoverDot(@NotNull String key, Boolean upper) {
		return recoverDivider(key, ".", upper);
	}

	/**ABCdEfg => upper ? A_B_CD_EFG : a/b/cd/efg
	 * @param key
	 * @return
	 */
	public static String recoverDivider(@NotNull String key, Boolean upper) {
		return recoverDivider(key, "/", upper);
	}

	/**驼峰格式转为带分隔符的全大写或全小写格式
	 * @param key
	 * @param divider
	 * @param upper
	 * @return
	 */
	public static String recoverDivider(@NotNull String key, @NotNull String divider, Boolean upper) {
		StringBuilder name = new StringBuilder();
		char[] cs = key.toCharArray();
		int len = key.length();
		for (int i = 0; i < len; i++) {
			String s = key.substring(i, i + 1);
			if (i > 0 && PATTERN_ALPHA_BIG.matcher(s).matches()) {
				name.append(divider);
			}
			if (upper != null) {
				s = upper ? s.toUpperCase() : s.toLowerCase();
			}
			name.append(s);
		}
		return name.toString();
	}


}
