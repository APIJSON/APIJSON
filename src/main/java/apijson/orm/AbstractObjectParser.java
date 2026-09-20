/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import apijson.*;
import apijson.orm.AbstractFunctionParser.FunctionBean;
import apijson.orm.exception.ConflictException;
import apijson.orm.exception.CommonException;
import apijson.orm.exception.NotExistException;
import apijson.orm.exception.UnsupportedDataTypeException;

import java.rmi.ServerException;
import java.util.*;
import java.util.Map.Entry;

import static apijson.JSON.*;
import static apijson.JSONMap.KEY_COMBINE;
import static apijson.JSONMap.KEY_DROP;
import static apijson.JSONMap.KEY_TRY;
import static apijson.JSONRequest.*;
import static apijson.RequestMethod.POST;
import static apijson.RequestMethod.PUT;
import static apijson.orm.SQLConfig.TYPE_ITEM;
import static apijson.RequestMethod.GET;

/**简化Parser，getObject和getArray(getArrayConfig)都能用
 * @author Lemon
 */
public abstract class AbstractObjectParser<T, M extends Map<String, Object>, L extends List<Object>>
		implements ObjectParser<T, M, L> {
	private static final String TAG = "AbstractObjectParser";

	@NotNull
	protected AbstractParser<T, M, L> parser;
	@Override
	public AbstractParser<T, M, L> getParser() {
		return parser;
	}
	@Override
	public AbstractObjectParser<T, M, L> setParser(Parser<T, M, L> parser) {
		this.parser = (AbstractParser<T, M, L>) parser;
		return this;
	}

	protected M request;//不用final是为了recycle
	protected String parentPath;//不用final是为了recycle
	protected SQLConfig<T, M, L> arrayConfig;//不用final是为了recycle
	protected boolean isSubquery;

	protected final int type;
	protected final String arrayTable;
	protected final List<Join<T, M, L>> joinList;
	protected final boolean isTable;
	protected final boolean isArrayMainTable;

	protected final boolean tri;
	/**
	 * TODO Parser内要不因为 非 TYPE_ITEM_CHILD_0 的Table 为空导致后续中断。
	 */
	protected final boolean drop;
	private List<String> stringKeyList;
	private List<String> trimKeyList;

	/**for single object
	 */
	public AbstractObjectParser(@NotNull M request, String parentPath, SQLConfig<T, M, L> arrayConfig
			, boolean isSubquery, boolean isTable, boolean isArrayMainTable) throws Exception {
		if (request == null) {
			throw new IllegalArgumentException(TAG + ".ObjectParser<T, M, L>  request == null!!!");
		}
		this.request = request;
		this.parentPath = parentPath;

		this.arrayConfig = arrayConfig;
		this.isSubquery = isSubquery;

		this.type = arrayConfig == null ? 0 : arrayConfig.getType();
		this.arrayTable = arrayConfig == null ? null : arrayConfig.getTable();
		this.joinList = arrayConfig == null ? null : arrayConfig.getJoinList();

		this.isTable = isTable; // apijson.JSONMap.isTableKey(table);
		this.isArrayMainTable = isArrayMainTable; // isSubquery == false && this.isTable && this.type == SQLConfig.TYPE_ITEM_CHILD_0 && RequestMethod.isGetMethod(method, true);
		//		this.isReuse = isReuse; // isArrayMainTable && arrayConfig != null && arrayConfig.getPosition() > 0;

		this.objectCount = 0;
		this.arrayCount = 0;

		boolean isEmpty = request.isEmpty();//empty有效 User:{}
		if (isEmpty) {
			this.tri = false;
			this.drop = false;
		}
		else {
			this.tri = getBooleanValue(request, KEY_TRY);
			this.drop = getBooleanValue(request, KEY_DROP);

			request.remove(KEY_TRY);
			request.remove(KEY_DROP);
		}

        if (isTable) {
            String raw = getString(request, JSONMap.KEY_RAW);
            String[] rks = StringUtil.split(raw);
            rawKeyList = rks == null || rks.length <= 0 ? null : Arrays.asList(rks);

			String str = getString(request, KEY_STRING);
			String[] sks = StringUtil.split(str);
			stringKeyList = sks == null || sks.length <= 0 ? null : Arrays.asList(sks);
			request.remove(KEY_STRING);

			String trim = getString(request, KEY_TRIM);
			String[] trims = StringUtil.split(trim);
			trimKeyList = trims == null || trims.length <= 0 ? null : Arrays.asList(trims);
			request.remove(KEY_TRIM);
		}
    }

	@Override
	public String getParentPath() {
		return parentPath;
	}

	@Override
	public AbstractObjectParser<T, M, L> setParentPath(String parentPath) {
		this.parentPath = parentPath;
		return this;
	}

	protected M cache;
	@Override
	public M getCache() {
		return cache;
	}

	@Override
	public AbstractObjectParser<T, M, L> setCache(M cache) {
		this.cache = cache;
		return this;
	}

	protected int position;
	public int getPosition() {
		return position;
	}
	public AbstractObjectParser<T, M, L> setPosition(int position) {
		this.position = position;
		return this;
	}


	private boolean invalidate = false;
	public void invalidate() {
		invalidate = true;
	}
	public boolean isInvalidate() {
		return invalidate;
	}

	private boolean breakParse = false;
	public void breakParse() {
		breakParse = true;
	}
	public boolean isBreakParse() {
		return breakParse || isInvalidate();
	}

	protected String name;
	protected String table;
	protected String alias;
	protected boolean isReuse;
	protected String path;

	protected M response;
	protected M sqlRequest;
	protected M sqlResponse;
	/**
	 * 自定义关键词
	 */
	protected Map<String, Object> customMap;
	/**
	 * 远程函数
	 * {"-":{ "key-()":value }, "0":{ "key()":value }, "+":{ "key+()":value } }
	 * - : 在executeSQL前解析
	 * 0 : 在executeSQL后、onChildParse前解析
	 * + : 在onChildParse后解析
	 */
	protected Map<String, Map<String, String>> functionMap;
	/**
	 * 子对象
	 */
	protected Map<String, M> childMap;

	private int objectCount;
	private int arrayCount;

    private List<String> rawKeyList;
	/**解析成员
	 * response重新赋值
	 * @return null or this
	 * @throws Exception
	 */
	@Override
	public AbstractObjectParser<T, M, L> parse(String name, boolean isReuse) throws Exception {
		if (isInvalidate() == false) {
			this.isReuse = isReuse;
			this.name = name;
			this.path = AbstractParser.getAbsPath(parentPath, name);

			apijson.orm.Entry<String, String> tentry = Pair.parseEntry(name, true);
			this.table = tentry.getKey();
			this.alias = tentry.getValue();

			Log.d(TAG, "AbstractObjectParser<T, M, L>  parentPath = " + parentPath + "; name = " + name + "; table = " + table + "; alias = " + alias);
			Log.d(TAG, "AbstractObjectParser<T, M, L>  type = " + type + "; isTable = " + isTable + "; isArrayMainTable = " + isArrayMainTable);
			Log.d(TAG, "AbstractObjectParser<T, M, L>  isEmpty = " + request.isEmpty() + "; tri = " + tri + "; drop = " + drop);

			breakParse = false;

			response = JSON.createJSONObject(); // must init
			sqlResponse = null; // must init

			if (isReuse == false) {
				sqlRequest = JSON.createJSONObject(); // must init

				customMap = null; // must init
				functionMap = null; // must init
				childMap = null; // must init

				Set<Entry<String, Object>> set = request.isEmpty() ? null : new LinkedHashSet<>(request.entrySet());
				if (set != null && set.isEmpty() == false) { // 判断换取少几个变量的初始化是否值得？
					if (isTable) { // 非Table下必须保证原有顺序！否则 count,page 会丢, total@:"/[]/total" 会在[]:{}前执行！
						customMap = new LinkedHashMap<String, Object>();
						childMap = new LinkedHashMap<String, M>();
					}
					functionMap = new LinkedHashMap<String, Map<String, String>>();//必须执行

					// 条件 <<<<<<<<<<<<<<<<<<<
					List<String> whereList = null;
					if (method == PUT) { // 这里只有PUTArray需要处理  || method == DELETE) {
						String[] combine = StringUtil.split(getString(request, KEY_COMBINE));
						if (combine != null) {
							String w;
							for (int i = 0; i < combine.length; i++) { // 去除 &,|,! 前缀
								w = combine[i];
								if (w != null && (w.startsWith("&") || w.startsWith("|") || w.startsWith("!"))) {
									combine[i] = w.substring(1);
								}
							}
						}
						// Arrays.asList() 返回值不支持 add 方法！
						whereList = new ArrayList<String>(Arrays.asList(combine != null ? combine : new String[]{}));
						whereList.add(JSONMap.KEY_ID);
						whereList.add(JSONMap.KEY_ID_IN);
						//						whereList.add(apijson.JSONMap.KEY_USER_ID);
						//						whereList.add(apijson.JSONMap.KEY_USER_ID_IN);
					}
					// 条件>>>>>>>>>>>>>>>>>>>

					int index = 0;
                    // hasOtherKeyNotFun = false;
					M viceItem = null;

					for (Entry<String, Object> entry : set) {
						if (isBreakParse()) {
							break;
						}

						// key 可能为 JSONList，需要进行手动转换（fastjson 为低版本时允许自动转换，如 1.2.21）
						// 例如 request json为 "{[]:{"page": 2, "table1":{}}}"
						Object field = entry == null ? null : entry.getKey();
						String key = field instanceof Map ? toJSONString(field) : field.toString();
						Object value = key == null ? null : entry.getValue();
						if (value == null) {
							continue;
						}

                        // 处理url crud, 将crud 转换为真实method
                        RequestMethod _method = this.parser.getRealMethod(method, key, value);
						// 没有执行校验流程的情况,比如url head, sql@子查询, sql@ method=GET

						Object obj = key.endsWith("@") ? request.get(key) : null;
						if (obj instanceof Map<?, ?>) {
							((Map<String, Object>) obj).put(JSONMap.KEY_METHOD, GET);
						}

						try {
                            boolean startsWithAt = key.startsWith("@");
                            // if (startsWithAt || (key.endsWith("()") == false)) {
                            //     hasOtherKeyNotFun = true;
                            // }

							boolean isTrim = (trimKeyList != null && trimKeyList.contains(key));
							boolean toStr = isTrim || (stringKeyList != null && stringKeyList.contains(key));
							if (toStr) {
								value = JSON.toJSONString(value);
							}
							if (isTrim && value != null) {
								value = StringUtil.trim(value);
							}

							if (startsWithAt || key.endsWith("@") || (key.endsWith("<>") && value instanceof Map<?, ?>)) {
								if (onParse(key, value) == false) {
									invalidate();
								}
							}
							else if (value instanceof Map<?, ?>) {  // JSONRequest，往下一级提取
								if (childMap != null) {  // 添加到childMap，最后再解析
									childMap.put(key, (M) value);
								}
								else {  // 直接解析并替换原来的，[]:{} 内必须直接解析，否则会因为丢掉count等属性，并且total@:"/[]/total"必须在[]:{} 后！
									Object cache = index <= 0 || type != TYPE_ITEM || viceItem == null ? null : JSON.get(viceItem, key);
									Object result = onChildParse(index, key, (M) value, cache);
									if (index <= 0 && type == TYPE_ITEM) {
										M mainItem = (M) result;
										viceItem = result == null ? null : (M) mainItem.remove(AbstractSQLExecutor.KEY_VICE_ITEM);
									}

									response.put(key, result);
									index ++;
								}
							}
							else if ((_method == POST || _method == PUT) && value instanceof List<?>
									&& JSONMap.isTableArray(key)) {  // L，批量新增或修改，往下一级提取
								onTableArrayParse(key, (L) value);
							}
							else if (_method == PUT && value instanceof List<?> && (whereList == null || whereList.contains(key) == false)
									&& StringUtil.isName(key.replaceFirst("[+-]$", ""))) {  // PUT L
								onPUTArrayParse(key, (L) value);
							}
							else {  // L 或其它 Object，直接填充
								if (onParse(key, value) == false) {
									invalidate();
								}
							}
						} catch (Exception e) {
							if (tri == false) {
								throw CommonException.wrap(e, sqlConfig);  // 不忽略错误，抛异常
							}
							invalidate();  // 忽略错误，还原request
						}
					}

				}

				if (isTable) {
					// parser.onVerifyRole 已处理 globalRole

					String db = parser.getGlobalDatabase();
					if (db != null) {
						sqlRequest.putIfAbsent(JSONMap.KEY_DATABASE, db);
					}

					String ds = parser.getGlobalDatasource();
					if (ds != null) {
						sqlRequest.putIfAbsent(JSONMap.KEY_DATASOURCE, ds);
					}

					String ns = parser.getGlobalNamespace();
					if (ns != null) {
						sqlRequest.putIfAbsent(JSONMap.KEY_NAMESPACE, ns);
					}

					String cl = parser.getGlobalCatalog();
					if (cl != null) {
						sqlRequest.putIfAbsent(JSONMap.KEY_CATALOG, cl);
					}

					String sch = parser.getGlobalSchema();
					if (sch != null) {
						sqlRequest.putIfAbsent(JSONMap.KEY_SCHEMA, sch);
					}

					if (isSubquery == false) { // 解决 SQL 语法报错，子查询不能 EXPLAIN
						Boolean exp = parser.getGlobalExplain();
						if (exp != null) {
							sqlRequest.putIfAbsent(JSONMap.KEY_EXPLAIN, exp);
						}

						String cache = parser.getGlobalCache();
						if (cache != null) {
							sqlRequest.putIfAbsent(JSONMap.KEY_CACHE, cache);
						}
					}
				}
			}

			if (isTable) {  // 非Table内的函数会被滞后在onChildParse后调用
				onFunctionResponse("-");
			}

		}

		if (isInvalidate()) {
			recycle();
			return null;
		}

		return this;
	}




    //private boolean hasOtherKeyNotFun = false;

    /**解析普通成员
	 * @param key
	 * @param value
	 * @return whether parse succeed
	 */
	@Override
	public boolean onParse(@NotNull String key, @NotNull Object value) throws Exception {
		if (key.endsWith("@")) {  // StringUtil.isPath((String) value)) {
			// [] 内主表 position > 0 时，用来生成 SQLConfig<T, M, L> 的键值对全都忽略，不解析
			if (value instanceof Map<?, ?>) {  // key{}@ getRealKey, SQL 子查询对象，JSONRequest -> SQLConfig.getSQL
				String replaceKey = key.substring(0, key.length() - 1);

				M subquery = (M) value;
				String range = getString(subquery, KEY_SUBQUERY_RANGE);
				if (range != null && SUBQUERY_RANGE_ALL.equals(range) == false && SUBQUERY_RANGE_ANY.equals(range) == false) {
					throw new IllegalArgumentException("子查询 " + path + "/" + key + ":{ range:value } 中 value 只能为 ["
                            + SUBQUERY_RANGE_ALL + ", " + SUBQUERY_RANGE_ANY + "] 中的一个！");
				}

				L arr = parser.onArrayParse(subquery, path, key, true, null);

				M obj = arr == null || arr.isEmpty() ? null : JSON.get(arr, 0);
				if (obj == null) {
					throw new Exception("服务器内部错误，解析子查询 " + path + "/" + key + ":{ } 为 Subquery 对象失败！");
				}

				String from = getString(subquery, apijson.JSONRequest.KEY_SUBQUERY_FROM);
				boolean isEmpty = StringUtil.isEmpty(from);
				M arrObj = isEmpty ? null : JSON.get(obj, from);
				if (isEmpty) {
					Set<Entry<String, Object>> set = obj.entrySet();
					for (Entry<String, Object> e : set) {
						String k = e == null ? null : e.getKey();
						Object v = k == null ? null : e.getValue();
						if (v instanceof Map<?, ?> && JSONMap.isTableKey(k)) {
							from = k;
							arrObj = (M) v;
							break;
						}
					}
				}

				if (arrObj == null) {
					throw new IllegalArgumentException("子查询 " + path + "/"
                            + key + ":{ from:value } 中 value 对应的主表对象 " + from + ":{} 不存在！");
				}

				SQLConfig<T, M, L> cfg = (SQLConfig) arrObj.get(AbstractParser.KEY_CONFIG);
				if (cfg == null) {
					throw new NotExistException(TAG + ".onParse  cfg == null");
				}

				Subquery s = new Subquery();
				s.setPath(path);
				s.setOriginKey(key);
				s.setOriginValue(subquery);

				s.setFrom(from);
				s.setRange(range);
				s.setKey(replaceKey);
				s.setConfig(cfg);

				key = replaceKey;
				value = s; //(range == null || range.isEmpty() ? "" : "range") + "(" + cfg.getSQL(false) + ") ";

				parser.putQueryResult(AbstractParser.getAbsPath(path, key), s); //字符串引用保证不了安全性 parser.getSQL(cfg));
			}
			else if (value instanceof String) { // //key{}@ getRealKey, 引用赋值路径
				String replaceKey = key.substring(0, key.length() - 1);

				// System.out.println("getObject  key.endsWith(@) >> parseRelation = " + parseRelation);
				String targetPath = AbstractParser.getValuePath(type == TYPE_ITEM ? path : parentPath, (String) value);

				// 先尝试获取，尽量保留缺省依赖路径，这样就不需要担心路径改变
				Object target = onReferenceParse(targetPath);
				Log.i(TAG, "onParse targetPath = " + targetPath + "; target = " + target);

				if (target == null) { // String#equals(null)会出错
					Log.d(TAG, "onParse  target == null  >>  return true;");

					if (method == POST || method == PUT) {
						throw new UnsupportedOperationException("引用赋值 " + path + "/" + key + ": " + targetPath
								+ " 解析失败，POST 和 PUT 接口必须保证每个对象执行都成功！");
					}

					if (Log.DEBUG) {
						parser.putWarnIfNeed(AbstractParser.KEY_REF, path + "/" + key + ": " + targetPath + " 引用赋值获取路径对应的值为 null！请检查路径是否错误！");
					}

					// 非查询关键词 @key 不影响查询，直接跳过
					if (isTable && (key.startsWith("@") == false || JSONMap.TABLE_KEY_LIST.contains(key))) {
						Log.e(TAG, "onParse  isTable && (key.startsWith(@) == false"
								+ " || apijson.JSONMap.TABLE_KEY_LIST.contains(key)) >>  return null;");
						// FIXME getCache() != null 时 return true，解决 RIGHT/OUTER/FOREIGN JOIN 主表无数据导致副表数据也不返回
						return false; // 获取不到就不用再做无效的 query 了。不考虑 Table:{Table:{}} 嵌套
					}

					Log.d(TAG, "onParse  isTable(table) == false >> return true;");
					return true; // 舍去，对Table无影响
				}

//				if (target instanceof Map) { // target 可能是从 requestObject 里取出的 {}
//					if (isTable || targetPath.endsWith("[]/" + JSONResponse.KEY_INFO) == false) {
//						Log.d(TAG, "onParse  target instanceof Map  >>  return false;");
//						return false; // FIXME 这个判断现在来看是否还有必要？为啥不允许为 JSONRequest ？以前可能因为防止二次遍历再解析，现在只有一次遍历
//					}
//				}
//
//				// FIXME 这个判断现在来看是否还有必要？为啥不允许为 JSONRequest ？以前可能因为防止二次遍历再解析，现在只有一次遍历
//				if (targetPath.equals(target)) { // 必须 valuePath 和保证 getValueByPath 传进去的一致！
//					Log.d(TAG, "onParse  targetPath.equals(target)  >>");
//
//					//非查询关键词 @key 不影响查询，直接跳过
//					if (isTable && (key.startsWith("@") == false || apijson.JSONMap.TABLE_KEY_LIST.contains(key))) {
//						Log.e(TAG, "onParse  isTable && (key.startsWith(@) == false"
//								+ " || apijson.JSONMap.TABLE_KEY_LIST.contains(key)) >>  return null;");
//						return false;//获取不到就不用再做无效的query了。不考虑 Table:{Table:{}}嵌套
//					} else {
//						Log.d(TAG, "onParse  isTable(table) == false >> return true;");
//						return true;//舍去，对Table无影响
//					}
//				}

				// 直接替换原来的 key@: path 为 key: target
				Log.i(TAG, "onParse    >>  key = replaceKey; value = target;");
				key = replaceKey;
				value = target;
				Log.d(TAG, "onParse key = " + key + "; value = " + value);
			}
			else {
				throw new IllegalArgumentException(path + "/" + key + ":value 中 value 必须为 依赖路径String 或 SQL子查询JSONObject ！");
			}
		}

		if (key.endsWith("()")) {
			if (value instanceof String == false) {
				throw new IllegalArgumentException(path + "/" + key + ":value 中 value 必须为函数String！");
			}

			String k = key.substring(0, key.length() - 2);

			String type; //远程函数比较少用，一般一个Table:{}内用到也就一两个，所以这里用 "-","0","+" 更直观，转用 -1,0,1 对性能提升不大。
			boolean isMinus = k.endsWith("-");
            boolean isPlus = isMinus == false && k.endsWith("+");
			if (isMinus) { //不能封装到functionMap后批量执行，否则会导致非Table内的 key-():function() 在onChildParse后执行！
				type = "-";
				k = k.substring(0, k.length() - 1);
			}
			else if (isPlus) {
				type = "+";
				k = k.substring(0, k.length() - 1);
			}
			else {
				type = "0";
			}

            if (isPlus == false && isTable == false) {
                parseFunction(key, k, (String) value, this.type == TYPE_ITEM ? path : parentPath, name, request, isMinus);
            }
            else {
				//远程函数比较少用，一般一个Table:{}内用到也就一两个，所以这里循环里new出来对性能影响不大。
				Map<String, String> map = functionMap.get(type);
				if (map == null) {
					map = new LinkedHashMap<>();
				}
				map.put(k, (String) value);

				functionMap.put(type, map);
			}
		}
		else if (isTable && key.startsWith("@") && JSONMap.TABLE_KEY_LIST.contains(key) == false) {
            customMap.put(key, value);
		}
		else {
			sqlRequest.put(key, value);
		}

		return true;
	}



	/**
	 * @param index
	 * @param key
	 * @param value
	 * @param cache
	 * @return
	 * @throws Exception
	 */
	@Override
	public Object onChildParse(int index, String key, M value, Object cache) throws Exception {
		boolean isFirst = index <= 0;
		boolean isMain = isFirst && type == TYPE_ITEM;

		Object child;
		boolean isEmpty;

		if (JSONMap.isArrayKey(key)) { // APIJSON Array
			if (isMain) {
				throw new IllegalArgumentException(parentPath + "/" + key + ":{} 不合法！"
						+ "数组 []:{} 中第一个 key:{} 必须是主表 TableKey:{} ！不能为 arrayKey[]:{} ！");
			}

			if (arrayConfig == null || arrayConfig.getPosition() == 0) {
				arrayCount ++;
				int maxArrayCount = parser.getMaxArrayCount();
				if (arrayCount > maxArrayCount) {
					throw new IllegalArgumentException(path + " 内截至 " + key + ":{} 时数组对象 key[]:{} "
                            + "的数量达到 " + arrayCount + " 已超限，必须在 0-" + maxArrayCount + " 内 !");
				}
			}

			String query = getString(value, KEY_QUERY);
			child = parser.onArrayParse(value, path, key, isSubquery, cache instanceof List<?> ? (L) cache : null);
			isEmpty = child == null || ((List<?>) child).isEmpty();

			if ("2".equals(query) || "ALL".equals(query)) { // 不判断 isEmpty，因为分页数据可能只是某页没有
				String totalKey = JSONResponse.formatArrayKey(key) + "Total";
				String infoKey = JSONResponse.formatArrayKey(key) + "Info";
				if ((request.containsKey(totalKey) || request.containsKey(infoKey)
						|| request.containsKey(totalKey + "@") || request.containsKey(infoKey + "@")) == false) {
					// onParse("total@", "/" + key + "/total");
					// onParse(infoKey + "@", "/" + key + "/info");
					// 替换为以下性能更好、对流程干扰最小的方式：

					String keyPath = AbstractParser.getValuePath(type == TYPE_ITEM ? path : parentPath, "/" + key);
					String totalPath = keyPath + "/total";
					String infoPath = keyPath + "/info";
					response.put(totalKey, onReferenceParse(totalPath));
					response.put(infoKey, onReferenceParse(infoPath));
				}
			}
		}
		else { //APIJSON Object
			boolean isTableKey = JSONMap.isTableKey(Pair.parseEntry(key, true).getKey());
			if (type == TYPE_ITEM && isTableKey == false) {
				throw new IllegalArgumentException(parentPath + "/" + key + ":{} 不合法！"
						+ "数组 []:{} 中每个 key:{} 都必须是表 TableKey:{} 或 数组 arrayKey[]:{} ！");
			}

			if ( //避免使用 "test":{"Test":{}} 绕过限制，实现查询爆炸   isTableKey &&
					(arrayConfig == null || arrayConfig.getPosition() == 0)) {
				objectCount ++;
				int maxObjectCount = parser.getMaxObjectCount();
				if (objectCount > maxObjectCount) {  //TODO 这里判断是批量新增/修改，然后上限为 maxUpdateCount
					throw new IllegalArgumentException(path + " 内截至 " + key + ":{} 时对象"
							+ " key:{} 的数量达到 " + objectCount + " 已超限，必须在 0-" + maxObjectCount + " 内 !");
				}
			}

			child = parser.onObjectParse(value, path, key, isMain ? arrayConfig.setType(SQLConfig.TYPE_ITEM_CHILD_0) : null
					, isSubquery, cache instanceof Map<?, ?> ? (M) cache : null);

			isEmpty = child == null || ((Map<?, ?>) child).isEmpty();
			if (isFirst && isEmpty) {
				invalidate();
			}
		}
//		Log.i(TAG, "onChildParse  ObjectParser.onParse  key = " + key + "; child = " + child);

		return isEmpty ? null : child; // 只添加! isChildEmpty的值，可能数据库返回数据不够count
	}


	//TODO 改用 MySQL json_add,json_remove,json_contains 等函数！不过就没有具体报错了，或许可以新增功能符，或者直接调 SQL 函数

	/**PUT key:[]
	 * @param key
	 * @param array
	 * @throws Exception
	 */
	@Override
	public void onPUTArrayParse(@NotNull String key, @NotNull L array) throws Exception {
		if (isTable == false || array.isEmpty()) {
			sqlRequest.put(key, array);
			Log.e(TAG, "onPUTArrayParse  isTable == false || array == null || array.isEmpty() >> return;");
			return;
		}

		int putType = 0;
		if (key.endsWith("+")) {//add
			putType = 1;
		} else if (key.endsWith("-")) {//remove
			putType = 2;
		} else {//replace
			sqlRequest.put(key, array);
			return;
		}
		String realKey = AbstractSQLConfig.gainRealKey(method, key, false, false);

		//GET > add all 或 remove all > PUT > remove key

		//GET <<<<<<<<<<<<<<<<<<<<<<<<<
		M rq = JSON.createJSONObject();
		rq.put(JSONMap.KEY_ID, request.get(JSONMap.KEY_ID));
		rq.put(JSONMap.KEY_COLUMN, realKey);
		M rp = parseResponse(RequestMethod.GET, table, null, rq, null, false);
		//GET >>>>>>>>>>>>>>>>>>>>>>>>>


		//add all 或 remove all <<<<<<<<<<<<<<<<<<<<<<<<<
		Object target = rp == null ? null : rp.get(realKey);
		if (target instanceof String) {
			try {
				target = JSON.parse(target);
			} catch (Throwable e) {
				if (Log.DEBUG) {
					Log.e(TAG, "try {\n" +
							"\t\t\t\ttarget = parseJSON((String) target);\n" +
							"\t\t\t}\n" +
							"\t\t\tcatch (Throwable e) = " + e.getMessage());
				}
			}
		}

		if (apijson.JSON.isBoolOrNumOrStr(target)) {
			throw new NullPointerException("PUT " + path + ", " + realKey + " 类型为 " + target.getClass().getSimpleName() + "，"
					+ "不支持 Boolean, String, Number 等类型字段使用 'key+': [] 或 'key-': [] ！"
					+ "对应字段在数据库的值必须为 JSONArray, JSONObject 中的一种！"
					+ "值为 JSONRequest 类型时传参必须是 'key+': [{'key': value, 'key2': value2}] 或 'key-': ['key', 'key2'] ！"
			);
		}

		boolean isAdd = putType == 1;

		Collection<Object> targetArray = target instanceof Collection ? (Collection<Object>) target : null;
		Map<String, ?> targetObj = target instanceof Map ? (Map<String, Object>) target : null;

		if (targetArray == null && targetObj == null) {
			if (isAdd == false) {
				throw new NullPointerException("PUT " + path + ", " + realKey + (target == null ? " 值为 null，不支持移除！"
						: " 类型为 " + target.getClass().getSimpleName() + "，不支持这样移除！")
						+ "对应字段在数据库的值必须为 JSONArray, JSONObject 中的一种，且 key- 移除时，本身的值不能为 null！"
						+ "值为 JSONRequest 类型时传参必须是 'key+': [{'key': value, 'key2': value2}] 或 'key-': ['key', 'key2'] ！"
				);
			}

			targetArray = JSON.createJSONArray();
		}

		for (int i = 0; i < array.size(); i++) {
			Object obj = array.get(i);
			if (obj == null) {
				continue;
			}

			if (isAdd) {
				if (targetArray != null) {
					if (targetArray.contains(obj)) {
						throw new ConflictException("PUT " + path + ", " + key + "/" + i + " 已存在！");
					}
					targetArray.add(obj);
				} else {
					if (obj != null && obj instanceof Map == false) {
						throw new ConflictException("PUT " + path + ", " + key + "/" + i + " 必须为 JSONRequest {} ！");
					}
					targetObj.putAll((Map) obj);
				}
			} else {
				if (targetArray != null) {
					if (targetArray.contains(obj) == false) {
						throw new NullPointerException("PUT " + path + ", " + key + "/" + i + " 不存在！");
					}
					targetArray.remove(obj);
				} else {
					if (obj instanceof String == false) {
						throw new ConflictException("PUT " + path + ", " + key + "/" + i + " 必须为 String 类型 ！");
					}
					if (targetObj.containsKey(obj) == false) {
						throw new NullPointerException("PUT " + path + ", " + key + "/" + i + " 不存在！");
					}
					targetObj.remove(obj);
				}
			}
		}

		//add all 或 remove all >>>>>>>>>>>>>>>>>>>>>>>>>

		//PUT <<<<<<<<<<<<<<<<<<<<<<<<<
		sqlRequest.put(realKey, targetArray != null ? targetArray : JSON.toJSONString(targetObj)); // FIXME, SerializerFeature.WriteMapNullValue));
		//PUT >>>>>>>>>>>>>>>>>>>>>>>>>

	}


	@Override
	public void onTableArrayParse(String key, L valueArray) throws Exception {
		String childKey = key.substring(0, key.length() - JSONMap.KEY_ARRAY.length());

		int allCount = 0;
		L ids = JSON.createJSONArray();

		int version = parser.getVersion();
		int maxUpdateCount = parser.getMaxUpdateCount();

        SQLConfig<T, M, L> cfg = null; // 不能污染当前的配置 getSQLConfig();
        if (cfg == null) { // TODO 每次都创建成本比较高，是否新增 defaultInstance 或者 configInstance 用来专门 getIdKey 等？
            cfg = parser.createSQLConfig();
        }

		String idKey = cfg.getIdKey(); //Table[]: [{}] arrayConfig 为 null
		boolean isNeedVerifyContent = parser.isNeedVerifyContent();

        cfg.setTable(childKey); // Request 表 structure 中配置 "ALLOW_PARTIAL_UPDATE_FAILED": "Table[],key[],key:alias[]" 自动配置
        boolean allowPartialFailed = cfg.allowPartialUpdateFailed();
        L failedIds = allowPartialFailed ? JSON.createJSONArray() : null;

        int firstFailIndex = -1;
        M firstFailReq = null;
        Throwable firstFailThrow = null;
		for (int i = 0; i < valueArray.size(); i++) { //只要有一条失败，则抛出异常，全部失败
			//TODO 改成一条多 VALUES 的 SQL 性能更高，报错也更会更好处理，更人性化
			M item;
			try {
				item = JSON.get(valueArray, i);
                if (item == null) {
                    throw new NullPointerException();
                }
			}
			catch (Exception e) {
				throw new UnsupportedDataTypeException(
                        "批量新增/修改失败！" + key + "/" + i + ":value 中value不合法！类型必须是 OBJECT ，结构为 {} !"
                );
			}

            Object id = item.get(idKey);
			M req = JSON.createJSONObject(childKey, item);

            M result = null;
            try {
                if (isNeedVerifyContent) {
                    req = parser.parseCorrectRequest(method, childKey, version, "", req, maxUpdateCount, parser);
                }
                //parser.getMaxSQLCount() ? 可能恶意调用接口，把数据库拖死
                result = (M) onChildParse(0, "" + i, req, null);
            }
            catch (Exception e) {
                if (allowPartialFailed == false) {
                    throw e;
                }

                if (firstFailThrow == null) {
                    firstFailThrow = e;
                    firstFailReq = JSON.get(valueArray, i); // item
                }
            }

            result = result == null ? null : JSON.get(result, childKey);

            boolean success = JSONResponse.isSuccess(result);
            int count = result == null ? 0 : getIntValue(result, JSONResponse.KEY_COUNT);
            if (id == null && result != null) {
                id = result.get(idKey);
            }

            if (success == false || count != 1) { //如果 code = 200 但 count != 1，不能算成功，掩盖了错误不好排查问题
                if (allowPartialFailed) {
                    failedIds.add(id);
                    if (firstFailIndex < 0) {
                        firstFailIndex = i;
                    }
                }
                else {
                    throw new ServerException(
                            "批量新增/修改失败！" + key + "/" + i + "：" + (success ? "成功但 count != 1 ！"
                                    : (result == null ? "null" : getString(result, JSONResponse.KEY_MSG))
                    ));
                }
            }

			allCount += 1; // 加了 allowPartialFailed 后 count 可能为 0  allCount += count;
			ids.add(id);
		}

        int failedCount = failedIds == null ? 0 : failedIds.size();
        if (failedCount > 0 && failedCount >= allCount) {
            throw new ServerException("批量新增/修改 " + key + ":[] 中 " + allCount + " 个子项全部失败！"
                    + "第 " + firstFailIndex + " 项失败原因：" + (firstFailThrow == null ? "" : firstFailThrow.getMessage()));
        }

        M allResult = getParser().newSuccessResult();
        if (failedCount > 0) {
            allResult.put("failedCount", failedCount);
            allResult.put("failedIdList", failedIds);

            M failObj = JSON.createJSONObject();
            failObj.put("index", firstFailIndex);
            failObj.put(childKey, firstFailReq);

            if (firstFailThrow instanceof CommonException && firstFailThrow.getCause() != null) {
                firstFailThrow = firstFailThrow.getCause();
            }
            M obj = firstFailThrow == null ? failObj : getParser().extendErrorResult(failObj, firstFailThrow, parser.isRoot());
            if (Log.DEBUG && firstFailThrow != null) {
                obj.put("trace:throw", firstFailThrow.getClass().getName());
                obj.put("trace:stack", firstFailThrow.getStackTrace());
            }

            allResult.put("firstFailed", obj);
        }
        allResult.put(JSONResponse.KEY_COUNT, allCount);
        allResult.put(idKey + "[]", ids);

		response.put(childKey, allResult); //不按原样返回，避免数据量过大
	}


	@Override
	public M parseResponse(RequestMethod method, String table, String alias
            , M request, List<Join<T, M, L>> joinList, boolean isProcedure) throws Exception {
		SQLConfig<T, M, L> config = newSQLConfig(method, table, alias, request, joinList, isProcedure)
				.setParser(getParser())
				.setObjectParser(this);
		return parseResponse(config, isProcedure);
	}
	@Override
	public M parseResponse(SQLConfig<T, M, L> config, boolean isProcedure) throws Exception {
		parser = getParser();
		if (parser.getSQLExecutor() == null) {
			parser.createSQLExecutor();
		}
		if (config.gainParser() == null) {
			config.setParser(parser);
		}
		return parser.getSQLExecutor().execute(config, isProcedure);
	}


	@Override
	public SQLConfig<T, M, L> newSQLConfig(boolean isProcedure) throws Exception {
		String raw = Log.DEBUG == false || sqlRequest == null ? null : getString(sqlRequest, JSONMap.KEY_RAW);
		String[] keys = raw == null ? null : StringUtil.split(raw);
		if (keys != null && keys.length > 0) {
			boolean allow = AbstractSQLConfig.ALLOW_MISSING_KEY_4_COMBINE;

			for (String key : keys) {
				if (sqlRequest.get(key) != null) {
					continue;
				}

				String msg = "@raw:value 的 value 中 " + key + " 不合法！对应的 "
						+ key + ": value 在当前对象 " + name + " 不存在或 value = null，无法有效转为原始 SQL 片段！";

				if (allow == false) {
					throw new UnsupportedOperationException(msg);
				}

				if (parser instanceof AbstractParser) {
					((AbstractParser) parser).putWarnIfNeed(JSONMap.KEY_RAW, msg);
				}
				break;
			}
		}

		return newSQLConfig(method, table, alias, sqlRequest, joinList, isProcedure)
				.setParser(parser)
				.setObjectParser(this);
	}

	/**SQL 配置，for single object
	 * @return {@link #setSQLConfig(int, int, int)}
	 * @throws Exception
	 */
	@Override
	public AbstractObjectParser<T, M, L> setSQLConfig() throws Exception {
		return setSQLConfig(RequestMethod.isQueryMethod(method) ? 1 : 0, 0, 0);
	}

	@Override
	public AbstractObjectParser<T, M, L> setSQLConfig(int count, int page, int position) throws Exception {
		if (isTable == false || isReuse) {
			return setPosition(position);
		}

		if (sqlConfig == null) {
			try {
				sqlConfig = newSQLConfig(false);
			}
			catch (Exception e) {
				if (e instanceof NotExistException || (e instanceof CommonException && e.getCause() instanceof NotExistException)) {
					return this;
				}
				throw e;
			}
		}
		sqlConfig.setCount(sqlConfig.getCount() <= 0 ? count : sqlConfig.getCount()).setPage(page).setPosition(position);

		parser.onVerifyRole(sqlConfig);

		return this;
	}




	protected SQLConfig<T, M, L> sqlConfig = null;//array item复用
	/**SQL查询，for array item
	 * @return this
	 * @throws Exception
	 */
	@Override
	public AbstractObjectParser<T, M, L> executeSQL() throws Exception {
		//执行SQL操作数据库
		if (isTable == false) {//提高性能
			sqlResponse = JSON.createJSONObject();
			sqlResponse.putAll(sqlRequest);
		}
		else {
            try {
                sqlResponse = onSQLExecute();
            }
            catch (Exception e) {
                if (e instanceof NotExistException || (e instanceof CommonException && e.getCause() instanceof NotExistException)) {
                    //				Log.e(TAG, "getObject  try { response = getSQLObject(config2); } catch (Exception e) {");
                    //				if (e instanceof NotExistException) {//非严重异常，有时候只是数据不存在
                    //					//						e.printStackTrace();
                    sqlResponse = null;//内部吃掉异常，put到最外层
                    //						requestObject.put(JSONResponse.KEY_MSG
                    //								, StringUtil.getString(requestObject.get(JSONResponse.KEY_MSG)
                    //										+ "; query " + path + " cath NotExistException:"
                    //										+ newErrorResult(e).getString(JSONResponse.KEY_MSG)));
                    //				} else {
                    //					throw e;
                    //				}
                }
                else {
                    throw e;
                }
            }
        }

        if (drop) {//丢弃Table，只为了向下提供条件
            sqlResponse = null;
        }

        return this;
	}

	/**
	 * @return response
	 * @throws Exception
	 */
	@Override
	public M response() throws Exception {
		if (sqlResponse == null || sqlResponse.isEmpty()) {
			if (isTable) {//Table自身都获取不到值，则里面的Child都无意义，不需要再解析
				return null;  // response;
			}
		} else {
			response.putAll(sqlResponse);
		}


		//把isTable时取出去的custom重新添加回来
		if (customMap != null) {
			response.putAll(customMap);
		}


		onFunctionResponse("0");

		onChildResponse();

		onFunctionResponse("+");

		onComplete();

		return response;
	}


	@Override
	public void onFunctionResponse(String type) throws Exception {
		Map<String, String> map = functionMap == null ? null : functionMap.get(type);

		//解析函数function
		Set<Entry<String, String>> functionSet = map == null ? null : map.entrySet();
		if (functionSet != null && functionSet.isEmpty() == false) {
            boolean isMinus = "-".equals(type);
			M json = isMinus ? sqlRequest : response; // key-():function 是实时执行，而不是在这里批量执行

			for (Entry<String, String> entry : functionSet) {
                parseFunction(entry.getKey(), entry.getKey(), entry.getValue(), this.type == TYPE_ITEM ? path : parentPath, name, json, isMinus);
			}
		}
	}


	//public void parseFunction(String key, String value, String parentPath, String currentName, JSONRequest currentObject) throws Exception {
    //    parseFunction(key, value, parentPath, currentName, currentObject, false);
    //}
	public void parseFunction(String rawKey, String key, String value, String parentPath
            , String currentName, M currentObject, boolean isMinus) throws Exception {
		Object result;
        boolean containRaw = rawKeyList != null && rawKeyList.contains(rawKey);

        boolean isProcedure = key.startsWith("@");
		if (isProcedure) {
			FunctionBean fb = AbstractFunctionParser.parseFunction(value, currentObject, true, containRaw);

			SQLConfig<T, M, L> config = newSQLConfig(true);
            String sch = fb.getSchema();
            if (StringUtil.isNotEmpty(sch, true)) {
                config.setSchema(sch);
            }
			config.setProcedure(fb.toFunctionCallString(true));
			result = parseResponse(config, true);

			key = key.substring(1);
		}
		else {
			result = parser.onFunctionParse(key, value, parentPath, currentName, currentObject, containRaw);
		}

		String k = AbstractSQLConfig.gainRealKey(method, key, false, false);

        if (isProcedure == false && isMinus) {
            if (result != null) {
                sqlRequest.put(k, result);
            } else {
                sqlRequest.remove(k);
            }
        }

        if (result != null) {
            response.put(k, result);
        } else {
            response.remove(k);
        }

        parser.putQueryResult(AbstractParser.getAbsPath(path, k), result);
	}

	@Override
	public void onChildResponse() throws Exception {
		//把isTable时取出去child解析后重新添加回来
		Set<Entry<String, M>> set = childMap == null ? null : childMap.entrySet();
		if (set != null) {
			int index = 0;
			for (Entry<String, M> entry : set) {
				Object child = entry == null ? null : onChildParse(index, entry.getKey(), entry.getValue(), null);
				if (child == null
						|| (child instanceof Map<?, ?> && ((M) child).isEmpty())
						|| (child instanceof List<?> && ((L) child).isEmpty())
						) {
					continue;
				}

				response.put(entry.getKey(), child);
				index ++;
			}
		}
	}



	@Override
	public Object onReferenceParse(@NotNull String path) {
		return parser.getValueByPath(path);
	}

	@SuppressWarnings("unchecked")
	@Override
	public M onSQLExecute() throws Exception {
		int position = getPosition();

		M result = getCache();
		if (result != null) {
			parser.putQueryResult(path, result);
		}
		else if (isArrayMainTable && position > 0) {  // 数组主表使用专门的缓存数据
			result = parser.getArrayMainCacheItem(parentPath.substring(0, parentPath.lastIndexOf("[]") + 2), position);
		}
		else {
			result = parser.executeSQL(sqlConfig, isSubquery);

			boolean isSimpleArray = false;
            // 提取并缓存数组主表的列表数据
            List<M> rawList = result == null ? null : (List<M>) result.remove(AbstractSQLExecutor.KEY_RAW_LIST);

            if (isArrayMainTable && position == 0 && rawList != null) {

                isSimpleArray = (functionMap == null || functionMap.isEmpty())
                        && (customMap == null || customMap.isEmpty())
                        && (childMap == null || childMap.isEmpty())
                        && (table.equals(arrayTable));

                // APP JOIN 副表时副表返回了这个字段   rawList = (List<JSONRequest>) result.remove(AbstractSQLExecutor.KEY_RAW_LIST);
                String arrayPath = parentPath.substring(0, parentPath.lastIndexOf("[]") + 2);

                if (isSimpleArray) {
					parser.putQueryResult(arrayPath, rawList); // 从数组外部引用该数组内值需要
				} else {
                    long startTime = System.currentTimeMillis();

                    for (int i = 1; i < rawList.size(); i++) {  // 从 1 开始，0 已经处理过
                        M obj = rawList.get(i);

                        if (obj != null) {
							// obj.remove(AbstractSQLExecutor.KEY_VICE_ITEM);
							parser.putQueryResult(arrayPath + "/" + i + "/" + name, obj);  // 解决获取关联数据时requestObject里不存在需要的关联数据
                        }
                    }

                    long endTime = System.currentTimeMillis();  // 3ms - 8ms
                    Log.e(TAG, "\n onSQLExecute <<<<<<<<<<<<<<<<<<<<<<<<<<<<"
                            + "\n for (int i = 1; i < list.size(); i++)  startTime = " + startTime
                            + "; endTime = " + endTime + "; duration = " + (endTime - startTime)
                            + "\n >>>>>>>>>>>>>>>>>>>>>>>>>>>>>\n ");
                }

                parser.putArrayMainCache(arrayPath, rawList);
            }

            if (isSubquery == false && result != null) {
                parser.putQueryResult(path, result);  // 解决获取关联数据时requestObject里不存在需要的关联数据

                if (isSimpleArray) { // FIXME 改为从缓存获取，而不是 result 查
                    result.put(AbstractSQLExecutor.KEY_RAW_LIST, rawList);
                }
            }
        }

		return result;
	}


	/**
	 * response has the final value after parse (and query if isTable)
	 */
	@Override
	public void onComplete() {
	}


	/**回收内存
	 */
	@Override
	public void recycle() {
		//后面还可能用到，要还原
		if (tri) {//避免返回未传的字段
			request.put(KEY_TRY, tri);
		}
		if (drop) {
			request.put(KEY_DROP, drop);
		}
		if (stringKeyList != null) { // 避免被全局关键词覆盖 && ! stringKeyList.isEmpty()) {
			request.put(KEY_STRING, StringUtil.get(stringKeyList.toArray()));
		}
		if (trimKeyList != null) { // 避免被全局关键词覆盖 && ! trimKeyList.isEmpty()) {
			request.put(KEY_TRIM, StringUtil.get(trimKeyList.toArray()));
		}

		method = null;
		parentPath = null;
		arrayConfig = null;

		//		if (response != null) {
		//			response.clear();//有效果?
		//			response = null;
		//		}

		request = null;
		response = null;
		sqlRequest = null;
		sqlResponse = null;

		functionMap = null;
		customMap = null;
		childMap = null;
	}



	protected RequestMethod method;
	@Override
	public AbstractObjectParser<T, M, L> setMethod(RequestMethod method) {
		if (this.method != method) {
			this.method = method;
			sqlConfig = null;
			//TODO ?			sqlResponse = null;
		}
		return this;
	}
	@Override
	public RequestMethod getMethod() {
		return method;
	}


	@Override
	public boolean isTable() {
		return isTable;
	}
	@Override
	public String getPath() {
		return path;
	}
	@Override
	public String getTable() {
		return table;
	}
	@Override
	public String getAlias() {
		return alias;
	}

	@Override
	public SQLConfig<T, M, L> getArrayConfig() {
		return arrayConfig;
	}

	@Override
	public SQLConfig<T, M, L> getSQLConfig() {
		return sqlConfig;
	}

	@Override
	public M getResponse() {
		return response;
	}
	@Override
	public M getSQLRequest() {
		return sqlRequest;
	}
	@Override
	public M getSQLResponse() {
		return sqlResponse;
	}

	@Override
	public Map<String, Object> getCustomMap() {
		return customMap;
	}
	@Override
	public Map<String, Map<String, String>> getFunctionMap() {
		return functionMap;
	}
	@Override
	public Map<String, M> getChildMap() {
		return childMap;
	}

}
