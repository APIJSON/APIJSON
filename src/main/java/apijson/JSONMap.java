/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson;

import java.util.*;


/**use this class instead of com.alibaba.fastjson.JSONMap
 * @author Lemon
 * @see #put
 * @see #puts
 * @see #putsAll
 */
//default class JSONMap<M, L> extends LinkedHashMap<String, Object> {
public interface JSONMap<M extends Map<String, Object>, L extends List<Object>> extends Map<String, Object> {
	static final String TAG = "JSONMap";

	// 只能是 static public Map<String, Object> map = new LinkedHashMap<>();

	///**ordered
	// */
	//default JSONMap() {
	//	super();
	//}
	///**transfer Object to JSONMap
	// * @param object
	// * @see {@link #JSONMap(Object)}
	// */
	//default JSONMap(Object object) {
	//	this();
	//	if (object instanceof Map) {
	//		@SuppressWarnings("unchecked")
	//		Map<String, Object> map = (Map<String, Object>) object;
	//		putAll(map);
	//	} else if (object != null) {
	//		String json = JSON.toJSONString(object);
	//		if (json != null) {
	//			Map<String, Object> map = JSON.parseObject(json);
	//			if (map != null) {
	//				putAll(map);
	//			}
	//		}
	//	}
	//}
	///**parse JSONMap<M, L> with JSON String
	// * @param json
	// * @see {@link #JSONMap(String)}
	// */
	//default JSONMap(String json) {
	//	this();
	//	Map<String, Object> map = JSON.parseObject(json);
	//	if (map != null) {
	//		putAll(map);
	//	}
	//}
	///**transfer com.alibaba.fastjson.JSONMap<M, L> to JSONMap
	// * @param object
	// * @see {@link #putsAll(Map<? extends String, ? extends Object>)}
	// */
	//default JSONMap(Map<String, Object> object) {
	//	this();
	//	putsAll(object);
	//}

	//public static JSONMap<M, L> valueOf(Object obj) {
	//	JSONMap<M, L> req = new JSONMap() {};
	//	Map<String, Object> m = JSON.parseObject(obj);
	//	if (m != null && ! m.isEmpty()) {
	//		req.map.putAll(m);
	//	}
	//	return req;
	//}

	//judge <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<
	String KEY_ARRAY = "[]";

	/**判断是否为Array的key
	 * @param key
	 * @return
	 */
	public static boolean isArrayKey(String key) {
		return key != null && key.endsWith(KEY_ARRAY);
	}
	/**判断是否为对应Table的key
	 * @param key
	 * @return
	 */
	public static boolean isTableKey(String key) {
		return StringUtil.isBigName(key);
	}
	/**判断是否为对应Table数组的 key
	 * @param key
	 * @return
	 */
	public static boolean isTableArray(String key) {
		return isArrayKey(key) && isTableKey(key.substring(0, key.length() - KEY_ARRAY.length()));
	}
	//judge >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>


	//JSONObject内关键词 key <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


	public static String KEY_ID = "id";
	public static String KEY_ID_IN = KEY_ID + "{}";
	public static String KEY_USER_ID = "userId";
	public static String KEY_USER_ID_IN = KEY_USER_ID + "{}";

	default String getIdKey() {
		return KEY_ID;
	}
	default String getIdInKey() {
		return KEY_ID_IN;
	}
	default String getUserIdKey() {
		return KEY_USER_ID;
	}
	default String getUserIdInKey() {
		return KEY_USER_ID_IN;
	}

	/**set "id":id in Table layer
	 * @param id
	 * @return
	 */
	default JSONMap<M, L> setId(Long id) {
		return puts(getIdKey(), id);
	}
	/**set "id{}":[] in Table layer
	 * @param list
	 * @return
	 */
	default JSONMap<M, L> setIdIn(List<Object> list) {
		return puts(getIdInKey(), list);
	}

	/**set "userId":userId in Table layer
	 * @param id
	 * @return
	 */
	default JSONMap<M, L> setUserId(Long id) {
		return puts(getUserIdKey(), id);
	}
	/**set "userId{}":[] in Table layer
	 * @param list
	 * @return
	 */
	default JSONMap<M, L> setUserIdIn(List<Object> list) {
		return puts(getUserIdInKey(), list);
	}


	int CACHE_ALL = 0;
	int CACHE_ROM = 1;
	int CACHE_RAM = 2;

	String CACHE_ALL_STRING = "ALL";
	String CACHE_ROM_STRING = "ROM";
	String CACHE_RAM_STRING = "RAM";


	//@key关键字都放这个类 <<<<<<<<<<<<<<<<<<<<<<
	String KEY_TRY = "@try"; // 尝试，忽略异常
	String KEY_CATCH = "@catch"; // TODO 捕捉到异常后，处理方式  null-不处理；DEFAULT-返回默认值；ORIGIN-返回请求里的原始值
	String KEY_DROP = "@drop"; // 丢弃，不返回，TODO 应该通过 fastjson 的 ignore 之类的机制来处理，避免导致下面的对象也不返回
	//	String KEY_KEEP = "@keep"; // 一定会返回，为 null 或 空对象时，会使用默认值(非空)，解决其它对象因为不关联的第一个对为空导致也不返回
	String KEY_DEFAULT = "@default"; // TODO 自定义默认值 { "@default":true }，@default 可完全替代 @keep
	String KEY_NULL = "@null"; // 值为 null 的键值对 "@null":"tag,pictureList"，允许 is NULL 条件判断， SET tag = NULL 修改值为 NULL 等
	String KEY_CAST = "@cast"; // 类型转换 cast(date AS DATE)

	String KEY_ROLE = "@role"; // 角色，拥有对某些数据的某些操作的权限
	String KEY_DATABASE = "@database"; // 数据库类型，默认为MySQL
	String KEY_DATASOURCE = "@datasource"; // 数据源
	String KEY_NAMESPACE = "@namespace"; // 命名空间，Table 在非默认 namespace 内时需要声明
	String KEY_CATALOG = "@catalog"; // 目录，Table 在非默认 catalog 内时需要声明
	String KEY_SCHEMA = "@schema"; // 数据库，Table 在非默认 schema 内时需要声明
	String KEY_EXPLAIN = "@explain"; // 分析 true/false
	String KEY_CACHE = "@cache"; // 缓存 RAM/ROM/ALL
	String KEY_COLUMN = "@column"; // 查询的 Table 字段或 SQL 函数
	String KEY_FROM = "@from"; // FROM语句
	String KEY_COMBINE = "@combine"; // 条件组合，每个条件 key 前面可以放 &,|,! 逻辑关系  "id!{},&sex,!name&$"
	String KEY_GROUP = "@group"; // 分组方式
	String KEY_HAVING = "@having"; // 聚合函数条件，一般和 @group 一起用
	String KEY_HAVING_AND = "@having&"; // 聚合函数条件，一般和 @group 一起用
	String KEY_SAMPLE = "@sample"; // 取样方式
	String KEY_LATEST = "@latest"; // 最近方式
	String KEY_PARTITION = "@partition"; // 分区方式
	String KEY_FILL = "@fill"; // 填充方式
	String KEY_ORDER = "@order"; // 排序方式
	String KEY_KEY = "@key"; // key 映射，year:left(date,4);name_tag:(name,tag)
	String KEY_RAW = "@raw"; // 自定义原始 SQL 片段
	String KEY_JSON = "@json"; // 把字段转为 JSON 输出
	String KEY_STRING = "@string"; // 把字段转为 String 输入
	String KEY_TRIM = "@trim"; // 去除首位空格等空白字符
	String KEY_METHOD = "@method"; // json 对象配置操作方法
	String KEY_GET = "@get"; // json 对象配置操作方法
	String KEY_GETS = "@gets"; // json 对象配置操作方法
	String KEY_HEAD = "@head"; // json 对象配置操作方法
	String KEY_HEADS = "@heads"; // json 对象配置操作方法
	String KEY_POST = "@post"; // json 对象配置操作方法
	String KEY_PUT = "@put"; // json 对象配置操作方法
	String KEY_DELETE = "@delete"; // json 对象配置操作方法

	List<String> TABLE_KEY_LIST = new ArrayList<>(Arrays.asList(
			KEY_ROLE, 
			KEY_DATABASE, 
			KEY_DATASOURCE,
			KEY_NAMESPACE,
			KEY_CATALOG,
			KEY_SCHEMA,
			KEY_EXPLAIN,
			KEY_CACHE,
			KEY_COLUMN,
			KEY_FROM,
			KEY_NULL,
			KEY_CAST,
			KEY_COMBINE,
			KEY_GROUP,
			KEY_HAVING,
			KEY_HAVING_AND,
			KEY_SAMPLE,
			KEY_LATEST,
			KEY_PARTITION,
			KEY_FILL,
			KEY_ORDER,
			KEY_KEY,
			KEY_RAW,
			KEY_JSON,
			KEY_STRING,
			KEY_TRIM,
			KEY_METHOD,
			KEY_GET,
			KEY_GETS,
			KEY_HEAD,
			KEY_HEADS,
			KEY_POST,
			KEY_PUT,
			KEY_DELETE
	));

	//@key关键字都放这个类 >>>>>>>>>>>>>>>>>>>>>>


	/**set try, ignore exceptions
	 * @param tri
	 * @return this
	 */
	default JSONMap<M, L> setTry(Boolean tri) {
		return puts(KEY_TRY, tri);
	}

	/**set catch
	 * @param isCatch
	 * @return this
	 */
	default JSONMap<M, L> setCatch(String isCatch) {
		return puts(KEY_CATCH, isCatch);
	}
	/**set drop, data dropped will not return
	 * @param drop
	 * @return this
	 */
	default JSONMap<M, L> setDrop(Boolean drop) {
		return puts(KEY_DROP, drop);
	}

	/**set if has default
	 * @param hasDefault
	 * @return this
	 */
	default JSONMap<M, L> setDefault(Boolean hasDefault) {
		return puts(KEY_DEFAULT, hasDefault);
	}


	/**set role of request sender
	 * @param role
	 * @return this
	 */
	default JSONMap<M, L> setRole(String role) {
		return puts(KEY_ROLE, role);
	}
	/**set database where table was puts
	 * @param database
	 * @return this
	 */
	default JSONMap<M, L> setDatabase(String database) {
		return puts(KEY_DATABASE, database);
	}
	/**set datasource where table was puts
	 * @param datasource
	 * @return this
	 */
	default JSONMap<M, L> setDatasource(String datasource) {
		return puts(KEY_DATASOURCE, datasource);
	}
	/**set namespace where table was puts
	 * @param namespace
	 * @return this
	 */
	default JSONMap<M, L> setNamespace(String namespace) {
		return puts(KEY_NAMESPACE, namespace);
	}
	/**set catalog where table was puts
	 * @param catalog
	 * @return this
	 */
	default JSONMap<M, L> setCatalog(String catalog) {
		return puts(KEY_CATALOG, catalog);
	}
	/**set schema where table was puts
	 * @param schema
	 * @return this
	 */
	default JSONMap<M, L> setSchema(String schema) {
		return puts(KEY_SCHEMA, schema);
	}
	/**set if return explain informations
	 * @param explain
	 * @return
	 */
	default JSONMap<M, L> setExplain(Boolean explain) {
		return puts(KEY_EXPLAIN, explain);
	}
	/**set cache type
	 * @param cache
	 * @return
	 * @see {@link #CACHE_ALL}
	 * @see {@link #CACHE_RAM}
	 * @see {@link #CACHE_ROM}
	 */
	default JSONMap<M, L> setCache(Integer cache) {
		return puts(KEY_CACHE, cache);
	}
	/**set cache type
	 * @param cache
	 * @return
	 * @see {@link #CACHE_ALL_STRING}
	 * @see {@link #CACHE_RAM_STRING}
	 * @see {@link #CACHE_ROM_STRING}
	 */
	default JSONMap<M, L> setCache(String cache) {
		return puts(KEY_CACHE, cache);
	}

	/**set keys need to be returned
	 * @param keys  key0, key1, key2 ...
	 * @return {@link #setColumn(String)}
	 */
	default JSONMap<M, L> setColumn(String... keys) {
		return setColumn(StringUtil.get(keys, true));
	}
	/**set keys need to be returned
	 * @param keys  "key0,key1,key2..."
	 * @return
	 */
	default JSONMap<M, L> setColumn(String keys) {
		return puts(KEY_COLUMN, keys);
	}

	/**set keys whose value is null
	 * @param keys  key0, key1, key2 ...
	 * @return {@link #setNull(String)}
	 */
	default JSONMap<M, L> setNull(String... keys) {
		return setNull(StringUtil.get(keys, true));
	}
	/**set keys whose value is null
	 * @param keys  "key0,key1,key2..."
	 * @return
	 */
	default JSONMap<M, L> setNull(String keys) {
		return puts(KEY_NULL, keys);
	}
	
	/**set keys and types whose value should be cast to type, cast(value AS DATE)
	 * @param keyTypes  key0:type0, key1:type1, key2:type2 ...
	 * @return {@link #setCast(String)}
	 */
	default JSONMap<M, L> setCast(String... keyTypes) {
		return setCast(StringUtil.get(keyTypes, true));
	}
	/**set keys and types whose value should be cast to type, cast(value AS DATE)
	 * @param keyTypes  "key0:type0,key1:type1,key2:type2..."
	 * @return
	 */
	default JSONMap<M, L> setCast(String keyTypes) {
		return puts(KEY_CAST, keyTypes);
	}

	/**set combination of keys for conditions
	 * @param keys  key0,&key1,|key2,!key3 ...  TODO or key0> | (key1{} & !key2)...
	 * @return {@link #setColumn(String)}
	 */
	default JSONMap<M, L> setCombine(String... keys) {
		return setCombine(StringUtil.get(keys, true));
	}
	/**set combination of keys for conditions
	 * @param keys  key0,&key1,|key2,!key3 ...  TODO or key0> | (key1{} & !key2)...
	 * @return
	 */
	default JSONMap<M, L> setCombine(String keys) {
		return puts(KEY_COMBINE, keys);
	}

	/**set keys for group by
	 * @param keys key0, key1, key2 ...
	 * @return {@link #setGroup(String)}
	 */
	default JSONMap<M, L> setGroup(String... keys) {
		return setGroup(StringUtil.get(keys, true));
	}
	/**set keys for group by
	 * @param keys  "key0,key1,key2..."
	 * @return
	 */
	default JSONMap<M, L> setGroup(String keys) {
		return puts(KEY_GROUP, keys);
	}

	/**set keys for having
	 * @param keys count(key0) > 1, sum(key1) <= 5, function2(key2) ? value2 ...
	 * @return {@link #setHaving(String)}
	 */
	default JSONMap<M, L> setHaving(String... keys) {
		return setHaving(StringUtil.get(keys, true));
	}
	/**set keys for having
	 * @param keys  "key0,key1,key2..."
	 * @return
	 */
	default JSONMap<M, L> setHaving(String keys) {
		return setHaving(keys, false);
	}
	/**set keys for having
	 * @param keys  "key0,key1,key2..."
	 * @return
	 */
	default JSONMap<M, L> setHaving(String keys, boolean isAnd) {
		return puts(isAnd ? KEY_HAVING_AND : KEY_HAVING, keys);
	}

	/**set keys for sample by
	 * @param keys  key0, key1, key2 ...
	 * @return {@link #setSample(String)}
	 */
	default JSONMap<M, L> setSample(String... keys) {
		return setSample(StringUtil.get(keys, true));
	}
	/**set keys for sample by
	 * @param keys  "key0,key1,key2..."
	 * @return
	 */
	default JSONMap<M, L> setSample(String keys) {
		return puts(KEY_SAMPLE, keys);
	}

	/**set keys for latest on
	 * @param keys  key0, key1, key2 ...
	 * @return {@link #setLatest(String)}
	 */
	default JSONMap<M, L> setLatest(String... keys) {
		return setLatest(StringUtil.get(keys, true));
	}
	/**set keys for latest on
	 * @param keys  "key0,key1,key2..."
	 * @return
	 */
	default JSONMap<M, L> setLatest(String keys) {
		return puts(KEY_LATEST, keys);
	}

	/**set keys for partition by
	 * @param keys  key0, key1, key2 ...
	 * @return {@link #setPartition(String)}
	 */
	default JSONMap<M, L> setPartition(String... keys) {
		return setPartition(StringUtil.get(keys, true));
	}
	/**set keys for partition by
	 * @param keys  key0, key1, key2 ...
	 * @return
	 */
	default JSONMap<M, L> setPartition(String keys) {
		return puts(KEY_PARTITION, keys);
	}

	/**set keys for fill(key): fill(null), fill(linear), fill(prev)
	 * @param keys  key0, key1, key2 ...
	 * @return {@link #setFill(String)}
	 */
	default JSONMap<M, L> setFill(String... keys) {
		return setFill(StringUtil.get(keys, true));
	}
	/**set keys for fill(key): fill(null), fill(linear), fill(prev)
	 * @param keys  key0, key1, key2 ...
	 * @return
	 */
	default JSONMap<M, L> setFill(String keys) {
		return puts(KEY_FILL, keys);
	}

	/**set keys for order by
	 * @param keys  key0, key1+, key2- ...
	 * @return {@link #setOrder(String)}
	 */
	default JSONMap<M, L> setOrder(String... keys) {
		return setOrder(StringUtil.get(keys, true));
	}
	/**set keys for order by
	 * @param keys  "key0,key1+,key2-..."
	 * @return
	 */
	default JSONMap<M, L> setOrder(String keys) {
		return puts(KEY_ORDER, keys);
	}

	/**set key map
	 * @param keyMap  "name_tag:(name,tag);year:left(date,1,5)..."
	 * @return
	 */
	default JSONMap<M, L> setKey(String keyMap) {
		return puts(KEY_KEY, keyMap);
	}

	/**set keys to raw
	 * @param keys  "key0,key1,key2..."
	 * @return
	 */
	default JSONMap<M, L> setRaw(String keys) {
		return puts(KEY_RAW, keys);
	}

	/**set keys to cast to json
	 * @param keys  "key0,key1,key2..."
	 * @return
	 */
	default JSONMap<M, L> setJson(String keys) {
		return puts(KEY_JSON, keys);
	}

	/**set keys to cast to string
	 * @param keys  "key0,key1,key2..."
	 * @return
	 */
	default JSONMap<M, L> setString(String keys) {
		return puts(KEY_STRING, keys);
	}

	/**set keys to cast to string
	 * @param keys  "key0,key1,key2..."
	 * @return
	 */
	default JSONMap<M, L> setTrim(String keys) {
		return puts(KEY_TRIM, keys);
	}

	//JSONObject内关键词 key >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>



	//Request <<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<


	/**
	 * @param key
	 * @param keys  path = keys[0] + "/" + keys[1] + "/" + keys[2] + ...
	 * @return {@link #puts(String, Object)}
	 */
	default JSONMap<M, L> putsPath(String key, String... keys) {
		return puts(key+"@", StringUtil.get(keys, "/"));
	}

	/**
	 * @param key
	 * @param isNull
	 * @return {@link #puts(String, Object)}
	 */
	default JSONMap<M, L> putsNull(String key, boolean isNull) {
		return puts(key+"{}", SQL.isNull(isNull));
	}
	/**
	 * trim = false
	 * @param key
	 * @param isEmpty
	 * @return {@link #putsEmpty(String, boolean, boolean)}
	 */
	default JSONMap<M, L> putsEmpty(String key, boolean isEmpty) {
		return putsEmpty(key, isEmpty, false);
	}
	/**
	 * @param key
	 * @param isEmpty
	 * @return {@link #puts(String, Object)}
	 */
	default JSONMap<M, L> putsEmpty(String key, boolean isEmpty, boolean trim) {
		return puts(key+"{}", SQL.isEmpty(key, isEmpty, trim));
	}
	/**
	 * @param key
	 * @param compare <=0, >5 ...
	 * @return {@link #puts(String, Object)}
	 */
	default JSONMap<M, L> putsLength(String key, String compare) {
		return puts(key+"{}", SQL.length(key) + compare);
	}
	/**
	 * @param key
	 * @param compare <=, > ...
	 * @param value 1, 5, 3.14, -99 ...
	 * @return {@link #puts(String, Object)}
	 */
	default JSONMap<M, L> putsLength(String key, String compare, Object value) {
		return puts(key+"["+(StringUtil.isEmpty(compare) || "=".equals(compare) ? "" : ("!=".equals(compare) ? "!" : compare)), value);
	}
	/**
	 * @param key
	 * @param compare <=0, >5 ...
	 * @return {@link #puts(String, Object)}
	 */
	default JSONMap<M, L> putsJSONLength(String key, String compare) {
		return puts(key+"{}", SQL.json_length(key) + compare);
	}
	/**
	 * @param key
	 * @param compare <=0, >5 ...
	 * @return {@link #puts(String, Object)}
	 */
	default JSONMap<M, L> putsJSONLength(String key, String compare, Object value) {
		return puts(key + "{" + (StringUtil.isEmpty(compare) || "=".equals(compare) ? "" : ("!=".equals(compare) ? "!" : compare)), value);
	}

	/**设置搜索
	 * type = SEARCH_TYPE_CONTAIN_FULL
	 * @param key
	 * @param value
	 * @return {@link #putsSearch(String, String, int)}
	 */
	default JSONMap<M, L> putsSearch(String key, String value) {
		return putsSearch(key, value, SQL.SEARCH_TYPE_CONTAIN_FULL);
	}
	/**设置搜索
	 * @param key
	 * @param value
	 * @param type
	 * @return {@link #puts(String, Object)}
	 */
	default JSONMap<M, L> putsSearch(String key, String value, int type) {
		return puts(key+"$", SQL.search(value, type));
	}

	//Request >>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>

	/**put and return this
	 * @param value  must be annotated by {@link MethodAccess}
	 * @return {@link #puts(String, Object)}
	 */
	default JSONMap<M, L> puts(Object value) {
		put(value);
		return this;
	}
	/**put and return this
	 * @param key
	 * @param value 
	 * @return this
	 */
	default JSONMap<M, L> puts(String key, Object value) {
		put(key, value);
		return this;
	}

	/**put and return value
	 * @param value  must be annotated by {@link MethodAccess}
	 */
	default Object put(Object value) {
		Class<?> clazz = value.getClass(); //should not return null
		if (clazz.getAnnotation(MethodAccess.class) == null) {
			throw new IllegalArgumentException("puts  StringUtil.isEmpty(key, true)" +
					" clazz.getAnnotation(MethodAccess.class) == null" +
					" \n key为空时仅支持 类型被@MethodAccess注解 的value !!!" +
					" \n 如果一定要这么用，请对 " + clazz.getName() + " 注解！" +
					" \n 如果是类似 key[]:{} 结构的请求，建议用 putsAll(...) ！");
		}
		return put(clazz.getSimpleName(), value);
	}

	/**puts key-value in object into this
	 * @param map
	 * @return this
	 */
	default JSONMap<M, L> putsAll(Map<? extends String, ? extends Object> map) {
		putAll(map);
		return this;
	}


	/**
	 * Get a boolean value from the JSONMap
	 * @param key the key
	 * @return the boolean value or false if not found
	 */
	default boolean getBooleanValue(String key) {
		return JSON.getBooleanValue(this, key);
	}
	
	/**
	 * Get an integer value from the JSONMap
	 * @param key the key
	 * @return the integer value or 0 if not found
	 */
	default int getIntValue(String key) {
		return JSON.getIntValue(this, key);
	}
	
	/**
	 * Get a long value from the JSONMap
	 * @param key the key
	 * @return the long value or 0 if not found
	 */
	default long getLongValue(String key) {
		return JSON.getLongValue(this, key);
	}
	
	/**
	 * Get a double value from the JSONMap
	 * @param key the key
	 * @return the double value or 0 if not found
	 */
	default double getDoubleValue(String key) {
		return JSON.getDoubleValue(this, key);
	}
	
	/**
	 * Get a string value from the JSONMap
	 * @param key the key
	 * @return the string value or null if not found
	 */
	default String getString(String key) {
		Object value = get(key);
		return value != null ? value.toString() : null;
	}
	
	/**
	 * Get a JSONMap<M, L> value from the JSONMap
	 * @param key the key
	 * @return the JSONMap<M, L> value or null if not found
	 */
	default M getJSONObject(String key) {
		Map<String, Object> map = JSON.getMap(this, key);
		return map != null ? JSON.createJSONObject(map) : null;
	}

	/**
	 * Get a JSONList value from the JSONMap
	 * @param key the key
	 * @return the JSONList value or null if not found
	 */
	default L getJSONArray(String key) {
		List<Object> list = JSON.getList(this, key);
		return list != null ? JSON.createJSONArray(list) : null;
	}

	@Override
	default void putAll(Map<? extends String, ? extends Object> map) {
		Set<? extends Map.Entry<? extends String, ?>> set = map == null ? null : map.entrySet();
		if (set != null || set.isEmpty()) {
			return;
		}

		for (Map.Entry<? extends String, ?> entry : set) {
			put(entry.getKey(), entry.getValue());
		}
	}

	default String toJSONString() {
		return JSON.toJSONString(this);
	}

	//@Override
	//default int size() {
	//	return map.size();
	//}
	//
	//@Override
	//default boolean isEmpty() {
	//	return map.isEmpty();
	//}
	//
	//@Override
	//default boolean containsKey(Object key) {
	//	return map.containsKey(key);
	//}
	//
	//@Override
	//default boolean containsValue(Object value) {
	//	return map.containsValue(value);
	//}
	//
	//@Override
	//default Object get(Object key) {
	//	return map.get(key);
	//}
	//
	//@Override
	//default Object put(String key, Object value) {
	//	return map.put(key, value);
	//}
	//
	//@Override
	//default Object remove(Object key) {
	//	return map.remove(key);
	//}


	//@Override
	//default void clear() {
	//	map.clear();
	//}
	//
	//@Override
	//default Set<String> keySet() {
	//	return map.keySet();
	//}
	//
	//@Override
	//default Collection<Object> values() {
	//	return map.values();
	//}
	//
	//@Override
	//default Set<Entry<String, Object>> entrySet() {
	//	return map.entrySet();
	//}

	//@Override
	//default String toString() {
	//	return JSON.toJSONString(this);
	//}

}
