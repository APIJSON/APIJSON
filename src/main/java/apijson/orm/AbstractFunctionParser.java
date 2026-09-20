/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import apijson.*;
import apijson.orm.exception.UnsupportedDataTypeException;
import apijson.orm.script.ScriptExecutor;

import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.*;

import static apijson.orm.AbstractSQLConfig.PATTERN_SCHEMA;

/**可远程调用的函数类
 * @author Lemon
 */
public abstract class AbstractFunctionParser<T, M extends Map<String, Object>, L extends List<Object>>
		implements FunctionParser<T, M, L> {
    private static final String TAG = "AbstractFunctionParser";

    /**是否解析参数 key 的对应的值，不用手动编码 curObj.getString(key)
     */
    public static boolean IS_PARSE_ARG_VALUE = false;

    /**开启支持远程函数
     */
    public static boolean ENABLE_REMOTE_FUNCTION = true;
    /**开启支持远程函数中的 JavaScript/Python/Lua/PHP 等脚本形式。
	 * JDK 8~13 可用自带 Nashorn 这个 js 引擎，注意配置 ClassFilter 防脚本注入攻击；
	 * 其它语言及 JDK 14+ 都必须依赖外部脚本引擎，注意按对应引擎说明方式防脚本注入攻击，最好是沙箱环境。
     */
    public static boolean ENABLE_SCRIPT_FUNCTION = false;

	// <methodName, JSONMap>
	// <isContain, <arguments:"array,key", tag:null, methods:null>>
    public static Map<String, ScriptExecutor<?, ? extends Map<String, Object>, ? extends List<Object>>> SCRIPT_EXECUTOR_MAP;
	public static Map<String, Map<String, Object>> FUNCTION_MAP;

	static {
		FUNCTION_MAP = new HashMap<>();
		SCRIPT_EXECUTOR_MAP = new HashMap<>();
	}

	private Parser<T, M, L> parser;
	private RequestMethod method;
	private String tag;
	private int version;
	private String key;
	private String parentPath;
	private String currentName;
	private M request;
	private M current;

	public AbstractFunctionParser() {
		this(null, null, 0, null);
	}

	public AbstractFunctionParser(RequestMethod method, String tag, int version, @NotNull M request) {
		setMethod(method == null ? RequestMethod.GET : method);
		setTag(tag);
		setVersion(version);
		setRequest(request);
	}

	@NotNull
	@Override
	public Parser<T, M, L> getParser() {
		return parser;
	}

	@Override
	public AbstractFunctionParser<T, M, L> setParser(Parser<T, M, L> parser) {
		this.parser = parser;
		return this;
	}

	@NotNull
	@Override
	public RequestMethod getMethod() {
		return method == null ? RequestMethod.GET : method;
	}

	@Override
	public AbstractFunctionParser<T, M, L> setMethod(RequestMethod method) {
		this.method = method;
		return this;
	}

	@Override
	public String getTag() {
		return tag;
	}

	@Override
	public AbstractFunctionParser<T, M, L> setTag(String tag) {
		this.tag = tag;
		return this;
	}

	@Override
	public int getVersion() {
		return version;
	}

	@Override
	public AbstractFunctionParser<T, M, L> setVersion(int version) {
		this.version = version;
		return this;
	}

	@Override
	public String getKey() {
		return key;
	}

	@Override
	public AbstractFunctionParser<T, M, L> setKey(String key) {
		this.key = key;
		return this;
	}

	@Override
	public String getParentPath() {
		return parentPath;
	}

	@Override
	public AbstractFunctionParser<T, M, L> setParentPath(String parentPath) {
		this.parentPath = parentPath;
		return this;
	}

	@Override
	public String getCurrentName() {
		return currentName;
	}

	@Override
	public AbstractFunctionParser<T, M, L> setCurrentName(String currentName) {
		this.currentName = currentName;
		return this;
	}

	@NotNull
	@Override
	public M getRequest() {
		return request;
	}

	@Override
	public AbstractFunctionParser<T, M, L> setRequest(@NotNull M request) {
		this.request = request;
		return this;
	}

	@NotNull
	@Override
	public M getCurrentObject() {
		return current;
	}

	@Override
	public AbstractFunctionParser<T, M, L> setCurrentObject(@NotNull M current) {
		this.current = current;
		return this;
	}

	/**根据路径取 Boolean 值
	 * @param path
	 * @return
	 */
	public Boolean getArgBool(String path) {
		return getArgVal(path, Boolean.class);
	}

	/**根据路径取 Integer 值
	 * @param path
	 * @return
	 */
	public Integer getArgInt(String path) {
		return getArgVal(path, Integer.class);
	}

	/**根据路径取 Long 值
	 * @param path
	 * @return
	 */
	public Long getArgLong(String path) {
		return getArgVal(path, Long.class);
	}

	/**根据路径取 Float 值
	 * @param path
	 * @return
	 */
	public Float getArgFloat(String path) {
		return getArgVal(path, Float.class);
	}

	/**根据路径取 Double 值
	 * @param path
	 * @return
	 */
	public Double getArgDouble(String path) {
		return getArgVal(path, Double.class);
	}

	/**根据路径取 Number 值
	 * @param path
	 * @return
	 */
	public Number getArgNum(String path) {
		return getArgVal(path, Number.class);
	}

	/**根据路径取 BigDecimal 值
	 * @param path
	 * @return
	 */
	public BigDecimal getArgDecimal(String path) {
		return getArgVal(path, BigDecimal.class);
	}

	/**根据路径取 String 值
	 * @param path
	 * @return
	 */
	public String getArgStr(String path) {
		Object obj = getArgVal(path);
		return JSON.toJSONString(obj);
	}

	/**根据路径取 JSONMap 值
	 * @param path
	 * @return
	 */
	public Map<String, Object> getArgObj(String path) {
		return getArgVal(path, Map.class);
	}

	/**根据路径取 JSONList 值
	 * @param path
	 * @return
	 */
	public List<Object> getArgArr(String path) {
		return getArgVal(path, List.class);
	}

	/**根据路径取 List<T> 值
	 * @param path
	 * @return
	 */
	public <T extends Object> List<T> getArgList(String path) {
		return getArgList(path, null);
	}

	/**根据路径取 List<T> 值
	 * @param path
	 * @return
	 */
	public <T extends Object> List<T> getArgList(String path, Class<T> clazz) {
		String s = getArgStr(path);
		return JSON.parseArray(s, clazz);
	}

	/**根据路径取值
	 * @param path
	 * @return
	 * @param <T>
	 */
	public <T extends Object> T getArgVal(String path) {
		return getArgVal(path, null); // 误判概率很小 false);
	}
	/**根据路径取值
	 * @param path
	 * @param clazz
	 * @return
	 * @param <T>
	 */
	public <T extends Object> T getArgVal(String path, Class<T> clazz) {
		return getArgVal(getCurrentObject(), path, clazz, true);
	}
	/**根据路径取值
	 * @param path
	 * @param clazz
	 * @param tryAll false-仅当前对象，true-本次请求的全局对象以及 Parser<T, M, L> 缓存值
	 * @return
	 * @param <T>
	 */
	public <T extends Object> T getArgVal(@NotNull M req, String path, Class<T> clazz, boolean tryAll) {
		T val = getArgValue(req, path, clazz);
		if (tryAll == false || val != null) {
			return val;
		}

		Parser<?, ?, ?> p = getParser();
		String targetPath = AbstractParser.getValuePath(getParentPath(), path);
		return p == null ? null : (T) p.getValueByPath(targetPath);
	}
	/**根据路径从对象 obj 中取值
	 * @param obj
	 * @param path
	 * @return
	 * @param <T>
	 */
	public static <T extends Object> T getArgVal(Map<String, Object> obj, String path) {
		return getArgValue(obj, path, null);
	}

	public static <T extends Object> T getArgValue(Map<String, Object> obj, String path, Class<T> clazz) {
		Object v = AbstractParser.getValue(obj, StringUtil.splitPath(path));

		if (clazz == null) {
			return (T) v;
		}

		// Simple type conversion
		try {
			if (v == null) {
				return null;
			}
			if (clazz.isInstance(v)) {
				return (T) v;
			}
			if (clazz == String.class) {
				return (T) String.valueOf(v);
			}
			if (clazz == Boolean.class || clazz == boolean.class) {
				return (T) Boolean.valueOf(String.valueOf(v));
			}
			if (clazz == Integer.class || clazz == int.class) {
				return (T) Integer.valueOf(String.valueOf(v));
			}
			if (clazz == Long.class || clazz == long.class) {
				return (T) Long.valueOf(String.valueOf(v));
			}
			if (clazz == Double.class || clazz == double.class) {
				return (T) Double.valueOf(String.valueOf(v));
			}
			if (clazz == Float.class || clazz == float.class) {
				return (T) Float.valueOf(String.valueOf(v));
			}
			if (Map.class.isAssignableFrom(clazz)) {
				if (v instanceof Map) {
					return (T) v;
				}
				return (T) JSON.parseObject(v);
			}
			if (List.class.isAssignableFrom(clazz)) {
				if (v instanceof List) {
					return (T) v;
				}
				return (T) JSON.parseArray(v);
			}
			// Fallback to string conversion
			return (T) v;
		} catch (Exception e) {
			return null;
		}
	}


	/**反射调用
	 * @param function 例如get(object,key)，参数只允许引用，不能直接传值
	 * @param current 不作为第一个参数，就不能远程调用invoke，避免死循环
	 * @return {@link #invoke(String, M, boolean)}
	 */
	@Override
	public Object invoke(@NotNull String function, @NotNull M current) throws Exception {
		return invoke(function, current, false);
	}
	/**反射调用
	 * @param function 例如get(object,key)，参数只允许引用，不能直接传值
	 * @param current 不作为第一个参数，就不能远程调用invoke，避免死循环
	 * @param containRaw 包含原始 SQL 片段
	 * @return {@link #invoke(AbstractFunctionParser, String, M, boolean)}
	 */
	@Override
	public Object invoke(@NotNull String function, @NotNull M current, boolean containRaw) throws Exception {
		if (StringUtil.isEmpty(function, true)) {
			throw new IllegalArgumentException("字符 " + function + " 不合法！");
		}

		return invoke(this, function, current, containRaw);
	}

	/**反射调用
	 * @param parser
	 * @param function 例如get(Map:map,key)，参数只允许引用，不能直接传值
     * @param current
     * @return {@link #invoke(AbstractFunctionParser, String, Class[], Object[])}
	 */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T, M extends Map<String, Object>, L extends List<Object>> Object invoke(
			@NotNull AbstractFunctionParser<T, M, L> parser, @NotNull String function
			, @NotNull Map<String, Object> current, boolean containRaw) throws Exception {
        if (ENABLE_REMOTE_FUNCTION == false) {
            throw new UnsupportedOperationException("AbstractFunctionParser.ENABLE_REMOTE_FUNCTION" +
                    " == false 时不支持远程函数！如需支持则设置 AbstractFunctionParser.ENABLE_REMOTE_FUNCTION = true ！");
        }

		FunctionBean fb = parseFunction(function, current, false, containRaw);

		Map<String, Object> row = FUNCTION_MAP.get(fb.getMethod()); //FIXME  fb.getSchema() + "." + fb.getMethod()
		if (row == null) {
			throw new UnsupportedOperationException("不允许调用远程函数 " + fb.getMethod() + " !");
		}

        String language = (String) row.get("language");
        String lang = "java".equalsIgnoreCase(language) ? null : language;

        if (ENABLE_SCRIPT_FUNCTION == false && lang != null) {
            throw new UnsupportedOperationException("language = " + language + " 不合法！AbstractFunctionParser.ENABLE_SCRIPT_FUNCTION" +
                    " == false 时不支持远程函数中的脚本形式！如需支持则设置 AbstractFunctionParser.ENABLE_SCRIPT_FUNCTION = true ！");
        }

		if (lang != null && SCRIPT_EXECUTOR_MAP.get(lang) == null) {
			throw new ClassNotFoundException("找不到脚本语言 " + lang + " 对应的执行引擎！请先依赖相关库并在后端 APIJSONFunctionParser<T, M, L> 中注册！");
		}

		int version = row.get("version") != null ? Integer.parseInt(row.get("version").toString()) : 0;
		if (parser.getVersion() < version) {
			throw new UnsupportedOperationException("不允许 version = " + parser.getVersion() + " 的请求调用远程函数 " + fb.getMethod() + " ! 必须满足 version >= " + version + " !");
		}
		String tag = (String) row.get("tag");  // TODO 改为 tags，类似 methods 支持多个 tag。或者干脆不要？因为目前非开放请求全都只能后端指定
		if (tag != null && tag.equals(parser.getTag()) == false) {
			throw new UnsupportedOperationException("不允许 tag = " + parser.getTag() + " 的请求调用远程函数 " + fb.getMethod() + " ! 必须满足 tag = " + tag + " !");
		}
		String[] methods = StringUtil.split((String) row.get("methods"));
		List<String> ml = methods == null || methods.length <= 0 ? null : Arrays.asList(methods);
		if (ml != null && ml.contains(parser.getMethod().toString()) == false) {
			throw new UnsupportedOperationException("不允许 method = " + parser.getMethod() + " 的请求调用远程函数 " + fb.getMethod() + " ! 必须满足 method 在 " + Arrays.toString(methods) + "内 !");
		}

		try {
            return invoke(parser, fb.getMethod(), fb.getTypes(), fb.getValues(), (String) row.get("returnType"), current, SCRIPT_EXECUTOR_MAP.get(lang));
		}
        catch (Exception e) {
			if (e instanceof NoSuchMethodException) {
				throw new IllegalArgumentException("字符 " + function + " 对应的远程函数 " + getFunction(fb.getMethod(), fb.getKeys())
                        + " 不在后端 " + parser.getClass().getName() + " 内，也不在父类中！如果需要则先新增对应方法！"
						+ "\n请检查函数名和参数数量是否与已定义的函数一致！"
						+ "\n且必须为 function(key0,key1,...) 这种单函数格式！"
						+ "\nfunction 必须符合 Java 函数命名，key 是用于在 curObj 内取值的键！"
						+ "\n调用时不要有空格！" + (Log.DEBUG ? e.getMessage() : ""));
			}
			if (e instanceof InvocationTargetException) {
				Throwable te = ((InvocationTargetException) e).getTargetException();
				if (StringUtil.isEmpty(te.getMessage(), true) == false) { //到处把函数声明throws Exception改成throws Throwable挺麻烦
					throw te instanceof Exception ? (Exception) te : new Exception(te.getMessage());
				}
				throw new IllegalArgumentException("字符 " + function + " 对应的远程函数传参类型错误！"
						+ "\n请检查 key:value 中value的类型是否满足已定义的函数 " + getFunction(fb.getMethod(), fb.getKeys()) + " 的要求！"
						+ (Log.DEBUG ? e.getMessage() : ""));
			}
			throw e;
		}

	}

	/**反射调用
     * @param parser
     * @param methodName
     * @param parameterTypes
     * @param args
     * @return {@link #invoke(AbstractFunctionParser, String, Class[], Object[])}
     * @throws Exception
     */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T, M extends Map<String, Object>, L extends List<Object>> Object invoke(
			@NotNull AbstractFunctionParser<T, M, L> parser, @NotNull String methodName
            , @NotNull Class<?>[] parameterTypes, @NotNull Object[] args) throws Exception {
        return invoke(parser, methodName, parameterTypes, args, null, null, null);
    }
    /**反射调用
     * @param parser
     * @param methodName
     * @param parameterTypes
     * @param args
     * @param returnType
     * @param current
     * @param scriptExecutor
     * @return
     * @throws Exception
     */
	@SuppressWarnings({"unchecked", "rawtypes"})
	public static <T, M extends Map<String, Object>, L extends List<Object>> Object invoke(
			@NotNull AbstractFunctionParser<T, M, L> parser, @NotNull String methodName
            , @NotNull Class<?>[] parameterTypes, @NotNull Object[] args, String returnType
            , Map<String, Object> current, ScriptExecutor scriptExecutor) throws Exception {
        if (scriptExecutor != null) {
            return invokeScript(parser, methodName, parameterTypes, args, returnType, current, scriptExecutor);
        }

		Class<? extends AbstractFunctionParser> cls = parser.getClass();
        Method m = cls.getMethod(methodName, parameterTypes); // 不用判空，拿不到就会抛异常

        if (Log.DEBUG) {
            String rt = Log.DEBUG && m.getReturnType() != null ? m.getReturnType().getSimpleName() : null;

            if ("void".equals(rt)) {
                rt = null;
            }
            if ("void".equals(returnType)) {
                returnType = null;
            }

            if (rt != returnType && (rt == null || rt.equals(returnType) == false)) {
                throw new WrongMethodTypeException("远程函数 " + methodName + " 的实际返回值类型 " + rt + " 与 Function 表中的配置的 " + returnType + " 不匹配！");
            }
        }

        return m.invoke(parser, args);
	}

    /**Java 调用 JavaScript 函数
     * @param parser
     * @param methodName
     * @param parameterTypes
     * @param args
     * @param returnType
     * @param current
     * @return
     * @throws Exception
     */
	@SuppressWarnings({"unchecked", "rawtypes"})
    public static <T, M extends Map<String, Object>, L extends List<Object>> Object invokeScript(
			@NotNull AbstractFunctionParser<T, M, L> parser, @NotNull String methodName
            , @NotNull Class<?>[] parameterTypes, @NotNull Object[] args, String returnType
			, Map<String, Object> current, ScriptExecutor scriptExecutor) throws Exception {
    	Object result = scriptExecutor.execute(parser, current, methodName, args);
        if (Log.DEBUG && result != null) {
            Class<?> rt = result.getClass(); // 作为远程函数的 js 类型应该只有 JSON 的几种类型
            String fullReturnType = (StringUtil.isSmallName(returnType)
                    ? returnType : (returnType.startsWith("JSON") ? "com.alibaba.fastjson." : "java.lang.") + returnType);

            if ((rt == null && returnType != null) || (rt != null && returnType == null)) {
                throw new WrongMethodTypeException("远程函数 " + methodName + " 的实际返回值类型 "
                        + (rt == null ? null : rt.getName()) + " 与 Function 表中的配置的 " + fullReturnType + " 不匹配！");
            }

            Class<?> cls;
            try {
                cls = Class.forName(fullReturnType);
            }
            catch (Exception e) {
                throw new WrongMethodTypeException("远程函数 " + methodName + " 在 Function 表中的配置的类型 "
                        + returnType + " 对应的 " + fullReturnType + " 错误！在 Java 中 Class.forName 找不到这个类型！");
            }

            if (cls.isAssignableFrom(rt) == false) {
                throw new WrongMethodTypeException("远程函数 " + methodName + " 的实际返回值类型 "
                        + (rt == null ? null : rt.getName()) + " 与 Function 表中的配置的 "
                        + returnType + " 对应的 " + fullReturnType + " 不匹配！");
            }
        }

        Log.d(TAG, "invokeScript " + methodName + "(..) >>  result = " + result);
        return result;
    }


    /**解析函数
     * @param function
     * @param request
     * @param isSQLFunction
     * @return
     * @throws Exception
     */
	@NotNull
	public static FunctionBean parseFunction(@NotNull String function, @NotNull Map<String, Object> request, boolean isSQLFunction) throws Exception {
        return parseFunction(function, request, isSQLFunction, false);
    }
    /**解析函数，自动解析的值类型只支持 Boolean, Number, String, Map, List
     * @param function
     * @param request
     * @param isSQLFunction
     * @param containRaw
     * @return
     * @throws Exception
     */
	public static FunctionBean parseFunction(@NotNull String function, @NotNull Map<String, Object> request, boolean isSQLFunction, boolean containRaw) throws Exception {

		int start = function.indexOf("(");
		int end = function.lastIndexOf(")");
		String method = (start <= 0 || end != function.length() - 1) ? null : function.substring(0, start);

        int dotInd = method == null ? -1 : method.indexOf(".");
        String schema = dotInd < 0 ? null : method.substring(0, dotInd);
        method = dotInd < 0 ? method : method.substring(dotInd + 1);

        if (StringUtil.isName(method) == false) {
			throw new IllegalArgumentException("字符 " + method + " 不合法！函数的名称 function 不能为空且必须符合方法命名规范！"
					+ "总体必须为 function(key0,key1,...) 这种单函数格式！"
					+ "\nfunction必须符合 " + (isSQLFunction ? "SQL 函数/SQL 存储过程" : "Java 函数") + " 命名，key 是用于在 request 内取值的键！");
		}
        if (isSQLFunction != true && schema != null) { // StringUtil.isNotEmpty(schema, false)) {
            throw new IllegalArgumentException("字符 " + schema + " 不合法！远程函数不允许指定类名！"
                    + "且必须为 function(key0,key1,...) 这种单函数格式！"
                    + "\nfunction必须符合 " + (isSQLFunction ? "SQL 函数/SQL 存储过程" : "Java 函数") + " 命名，key 是用于在 request 内取值的键！");
        }
        if (schema != null) { // StringUtil.isName(schema) == false) {
			schema = extractSchema(schema, null);
        }

		String[] keys = StringUtil.split(function.substring(start + 1, end));
		int length = keys == null ? 0 : keys.length;

		Class<?>[] types;
		Object[] values;

		if (isSQLFunction || IS_PARSE_ARG_VALUE) {
			types = new Class<?>[length];
			values = new Object[length];

			//碰到null就挂了！！！Number还得各种转换不灵活！不如直接传request和对应的key到函数里，函数内实现时自己 getLongValue,getJSONObject ...
			Object v;
			for (int i = 0; i < length; i++) {
				v = values[i] = getArgValue(request, keys[i], containRaw); // request.get(keys[i]);
				if (v == null) {
					types[i] = Object.class;
					values[i] = null;
					break;
				}

				if (v instanceof Boolean) {
					types[i] = Boolean.class; //只支持JSON的几种类型
				} // 怎么都有 bug，如果是引用的值，很多情况下无法指定  //  用 1L 指定为 Long ？ 其它的默认按长度分配为 Integer 或 Long？
				//else if (v instanceof Long || v instanceof Integer || v instanceof Short) {
				//	types[i] = Long.class;
				//}
				else if (v instanceof Number) {
					types[i] = Number.class;
				}
				else if (v instanceof String) {
					types[i] = String.class;
				}
				else if (v instanceof Map) { // 泛型兼容？ // JSONMap
					types[i] = Map.class;
					//性能比较差
                    //values[i] = TypeUtils.cast(v, Map.class, ParserConfig.getGlobalInstance());
				}
				else if (v instanceof Collection) { // 泛型兼容？ // JSONList
					types[i] = List.class;
					//性能比较差
					List list = new ArrayList<>((Collection) v);
                    values[i] = list; // TypeUtils.cast(v, List.class, ParserConfig.getGlobalInstance());
				}
				else {
					throw new UnsupportedDataTypeException(keys[i] + ":value 中value不合法！远程函数 key():"
                            + function + " 中的 arg 对应的值类型只能是 [Boolean, Number, String, JSONMap, JSONList] 中的一种！");
				}
			}
		}
		else {
			Class<? extends Map> cls = JSON.createJSONObject().getClass();
			types = new Class<?>[length + 1];
			//types[0] = Object.class; // 泛型擦除 JSON.JSON_OBJECT_CLASS;
			types[0] = cls;

			values = new Object[length + 1];
			values[0] = request;

			for (int i = 0; i < length; i++) {
				types[i + 1] = String.class;
				values[i + 1] = keys[i];
			}
		}

		FunctionBean fb = new FunctionBean();
		fb.setFunction(function);
		fb.setSchema(schema);
		fb.setMethod(method);
		fb.setKeys(keys);
		fb.setTypes(types);
		fb.setValues(values);

		return fb;
	}

	public static void verifySchema(String sch, String table) {
		extractSchema(sch, table);
	}

	public static String extractSchema(String sch, String table) {
		if (StringUtil.isEmpty(sch)) {
			return sch;
		}

		if (table == null) {
			table = "Table";
		}

		int ind = sch.indexOf("`");
		if (ind > 0) {
			throw new IllegalArgumentException(table + ": { @key(): value } 对应存储过程 value 中字符 "
					+ sch + " 不合法！`schema` 当有 ` 包裹时一定是首尾各一个，不能多也不能少！");
		}

		if (ind == 0) {
			sch = sch.substring(1);
			if (sch.indexOf("`") != sch.length() - 1) {
				throw new IllegalArgumentException(table + ": { @key(): value } 对应存储过程 value 中字符 `"
						+ sch + " 不合法！`schema` 当有 ` 包裹时一定是首尾各一个，不能多也不能少！");
			}

			sch = sch.substring(0, sch.length() - 1);
		}

		if (PATTERN_SCHEMA.matcher(sch).matches() == false || sch.contains("--")) {
			throw new IllegalArgumentException(table + ": { @key(): value } 对应存储过程 value 中字符 "
					+ sch + " 不合法！schema.function(arg) 中 schema 必须符合 数据库名/模式名 的命名规则！"
					+ "一般只能传英文字母、数字、下划线！不允许 -- 等可能导致 SQL 注入的符号！");
		}

		return sch;
	}


	/**
	 * @param method
	 * @param keys
	 * @return
	 */
	public static String getFunction(String method, String[] keys) {
		String f = method + "(JSONMap request";

		if (keys != null) {
			for (int i = 0; i < keys.length; i++) {
				f += (", String " + keys[i]);
			}
		}

		f += ")";

		return f;
	}

    public static <T> T getArgValue(@NotNull Map<String, Object> current, String keyOrValue) {
        return getArgValue(current, keyOrValue, false);
    }
    public static <T> T getArgValue(@NotNull Map<String, Object> current, String keyOrValue, boolean containRaw) {
        if (keyOrValue == null) {
            return null;
        }


        if (keyOrValue.endsWith("`") && keyOrValue.substring(1).indexOf("`") == keyOrValue.length() - 2) {
            return (T) current.get(keyOrValue.substring(1, keyOrValue.length() - 1));
        }

        if (keyOrValue.endsWith("'") && keyOrValue.substring(1).indexOf("'") == keyOrValue.length() - 2) {
            return (T) keyOrValue.substring(1, keyOrValue.length() - 1);
        }

        // 传参加上 @raw:"key()" 避免意外情况
        Object val = containRaw ? AbstractSQLConfig.RAW_MAP.get(keyOrValue) : null;
        if (val != null) {
            return (T) ("".equals(val) ? keyOrValue : val);
        }

        if (StringUtil.isName(keyOrValue.startsWith("@") ? keyOrValue.substring(1) : keyOrValue)) {
            return (T) current.get(keyOrValue);
        }

        if ("true".equals(keyOrValue)) {
            return (T) Boolean.TRUE;
        }
        if ("false".equals(keyOrValue)) {
            return (T) Boolean.FALSE;
        }

        // 性能更好，但居然非法格式也不报错
        //try {
        //    val = Boolean.valueOf(keyOrValue); // parseJSON(keyOrValue);
        //    return (T) val;
        //}
        //catch (Throwable e) {
        //    Log.d(TAG, "getArgValue  try {\n" +
        //            "            val = Boolean.valueOf(keyOrValue);" +
        //            "} catch (Throwable e) = " + e.getMessage());
        //}

        try {
            val = Double.valueOf(keyOrValue); // parseJSON(keyOrValue);
            return (T) val;
        }
        catch (Throwable e) {
            Log.d(TAG, "getArgValue  try {\n" +
                    "            val = Double.valueOf(keyOrValue);" +
                    "} catch (Throwable e) = " + e.getMessage());
        }

        return (T) current.get(keyOrValue);
    }

	public static class FunctionBean {
		private String function;
		private String schema;
		private String method;
		private String[] keys;
		private Class<?>[] types;
		private Object[] values;

		public String getFunction() {
			return function;
		}
		public void setFunction(String function) {
			this.function = function;
		}

        public String getSchema() {
            return schema;
        }
        public void setSchema(String schema) {
            this.schema = schema;
        }

        public String getMethod() {
			return method;
		}
		public void setMethod(String method) {
			this.method = method;
		}

		public String[] getKeys() {
			return keys;
		}
		public void setKeys(String[] keys) {
			this.keys = keys;
		}

		public Class<?>[] getTypes() {
			return types;
		}
		public void setTypes(Class<?>[] types) {
			this.types = types;
		}

		public Object[] getValues() {
			return values;
		}
		public void setValues(Object[] values) {
			this.values = values;
		}


		/**
		 * @param useValue
		 * @return
		 */
		public String toFunctionCallString(boolean useValue) {
			return toFunctionCallString(useValue, null);
		}
		/**
		 * @param useValue
		 * @param quote
		 * @return
		 */
		public String toFunctionCallString(boolean useValue, String quote) {
            //String sch = getSchema();
			//String s = (StringUtil.isEmpty(sch) ? "" : sch + ".") + getMethod() + "(";
			String s = getMethod() + "(";

			Object[] args = useValue ? getValues() : getKeys();
			if (args != null && args.length > 0) {
				if (quote == null) {
					quote = "'";
				}

				Object arg;
				for (int i = 0; i < args.length; i++) {
					arg = args[i];
					s += (i <= 0 ? "" : ",") + (arg instanceof Boolean || arg instanceof Number ? arg : quote + arg + quote);
				}
			}

			return s + ")";
		}

	}

	/**
	 * 获取JSON对象
	 * @param <V>  TODO
	 * @param req
	 * @param key
	 * @param clazz
	 * @return
	 * @throws Exception
	 */
	public <V> V getArgVal(@NotNull M req, String key, Class<V> clazz) throws Exception {
		// Convert to JSONMap for backward compatibility, replace with proper implementation later
		return getArgVal(req, key, clazz, false);
	}

	/**
	 * 获取参数值
	 * @param key
	 * @param clazz 如果有clazz就返回对应的类型，否则返回原始类型
	 * @param defaultValue
	 * @return
	 * @throws Exception 
	 */
	public <V> V getArgVal(String key, Class<V> clazz, boolean defaultValue) throws Exception {
		Object obj = parser != null && JSONMap.isArrayKey(key) ? AbstractParser.getValue(request, key.split("\\,")) : request.get(key);
		
		if (clazz == null) {
			return (V) obj;
		}
		
		// Replace TypeUtils with appropriate casting method
		try {
			if (obj == null) {
				return null;
			}
			if (clazz.isInstance(obj)) {
				return (V) obj;
			}
			if (clazz == String.class) {
				return (V) String.valueOf(obj);
			}
			if (clazz == Boolean.class || clazz == boolean.class) {
				return (V) Boolean.valueOf(String.valueOf(obj));
			}
			if (clazz == Integer.class || clazz == int.class) {
				return (V) Integer.valueOf(String.valueOf(obj));
			}
			if (clazz == Long.class || clazz == long.class) {
				return (V) Long.valueOf(String.valueOf(obj));
			}
			if (clazz == Double.class || clazz == double.class) {
				return (V) Double.valueOf(String.valueOf(obj));
			}
			if (clazz == Float.class || clazz == float.class) {
				return (V) Float.valueOf(String.valueOf(obj));
			}
			if (Map.class.isAssignableFrom(clazz)) {
				if (obj instanceof Map) {
					return (V) obj;
				}
				return (V) JSON.parseObject(obj);
			}
			if (List.class.isAssignableFrom(clazz)) {
				if (obj instanceof List) {
					return (V) obj;
				}
				return (V) JSON.parseArray(obj);
			}
			// Fallback to string conversion
			return (V) obj;
		} catch (Exception e) {
			if (defaultValue) {
				return null;
			}
			throw e;
		}
	}

}
