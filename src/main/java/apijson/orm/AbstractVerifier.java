/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import static apijson.JSON.*;
import static apijson.JSONMap.*;
import static apijson.RequestMethod.DELETE;
import static apijson.RequestMethod.GET;
import static apijson.RequestMethod.GETS;
import static apijson.RequestMethod.HEAD;
import static apijson.RequestMethod.HEADS;
import static apijson.RequestMethod.POST;
import static apijson.RequestMethod.PUT;
import static apijson.orm.AbstractFunctionParser.ENABLE_SCRIPT_FUNCTION;
import static apijson.orm.Operation.*;
//import static apijson.orm.Operation.CODE;

import java.net.URL;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import apijson.*;

import apijson.orm.AbstractSQLConfig.IdCallback;
import apijson.orm.exception.ConflictException;
import apijson.orm.exception.NotLoggedInException;
import apijson.orm.exception.UnsupportedDataTypeException;
import apijson.orm.model.Access;
import apijson.orm.model.Column;
import apijson.orm.model.Document;
import apijson.orm.model.ExtendedProperty;
import apijson.orm.model.Function;
import apijson.orm.model.Script;
import apijson.orm.model.PgAttribute;
import apijson.orm.model.PgClass;
import apijson.orm.model.Request;
import apijson.orm.model.SysColumn;
import apijson.orm.model.SysTable;
import apijson.orm.model.Table;
import apijson.orm.model.AllTable;
import apijson.orm.model.AllColumn;
import apijson.orm.model.AllTableComment;
import apijson.orm.model.AllColumnComment;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**校验器(权限、请求参数、返回结果等)
 * TODO 合并 Structure 的代码
 * @author Lemon
 * @param <T> id 与 userId 的类型，一般为 Long
 */
public abstract class AbstractVerifier<T, M extends Map<String, Object>, L extends List<Object>>
		implements Verifier<T, M, L>, IdCallback<T> {
	private static final String TAG = "AbstractVerifier";

	/**为 PUT, DELETE 强制要求必须有 id/id{}/id{}@ 条件
	 */
	public static boolean IS_UPDATE_MUST_HAVE_ID_CONDITION = true;
	/**开启校验请求角色权限
	*/
	public static boolean ENABLE_VERIFY_ROLE = true;
	/**开启校验请求传参内容
	*/
	public static boolean ENABLE_VERIFY_CONTENT = true;

	/**未登录，不明身份的用户
	 */
	public static final String UNKNOWN = "UNKNOWN";

	/**已登录的用户
	 */
	public static final String LOGIN = "LOGIN";

	/**联系人，必须已登录
	 */
	public static final String CONTACT = "CONTACT";

	/**圈子成员(CONTACT + OWNER)，必须已登录
	 */
	public static final String CIRCLE = "CIRCLE";

	/**拥有者，必须已登录
	 */
	public static final String OWNER = "OWNER";

	/**管理员，必须已登录
	 */
	public static final String ADMIN = "ADMIN";

//	public static ParserCreator<T, M, L> PARSER_CREATOR;

	public static ScriptEngineManager SCRIPT_ENGINE_MANAGER;
	public static ScriptEngine SCRIPT_ENGINE;

	// 共享 STRUCTURE_MAP 则不能 remove 等做任何变更，否则在并发情况下可能会出错，加锁效率又低，所以这里改为忽略对应的 key
	public static Map<String, Entry<String, Object>> ROLE_MAP;

	public static List<String> OPERATION_KEY_LIST;

	// <TableName, <METHOD, allowRoles>>
	// <User, <GET, [OWNER, ADMIN]>>
	@NotNull
	public static Map<String, Map<RequestMethod, String[]>> SYSTEM_ACCESS_MAP; // TODO 改名为 CONFIG_ACCESS_MAP ？
	@NotNull
	public static Map<String, Map<RequestMethod, String[]>> ACCESS_MAP;
	@NotNull
	public static Map<String, Map<String, Object>> ACCESS_FAKE_DELETE_MAP;

	// <method tag, <version, Request>>
	// <PUT Comment, <1, { "method":"PUT", "tag":"Comment", "structure":{ "MUST":"id"... }... }>>
	@NotNull
	public static Map<String, SortedMap<Integer, Map<String, Object>>> REQUEST_MAP;
	private static String VERIFY_LENGTH_RULE = "(?<first>[>=<]*)(?<second>[0-9]*)";
	private static Pattern VERIFY_LENGTH_PATTERN = Pattern.compile(VERIFY_LENGTH_RULE);

	// 正则匹配的别名快捷方式，例如用 "PHONE" 代替 "^((13[0-9])|(15[^4,\\D])|(18[0-2,5-9])|(17[0-9]))\\d{8}$"
	@NotNull
	public static final Map<String, Pattern> COMPILE_MAP;
	static {
		SCRIPT_ENGINE_MANAGER = new ScriptEngineManager();
		SCRIPT_ENGINE = SCRIPT_ENGINE_MANAGER.getEngineByName("js");

		ROLE_MAP = new LinkedHashMap<>();
		ROLE_MAP.put(UNKNOWN, new Entry<String, Object>());
		ROLE_MAP.put(LOGIN, new Entry<String, Object>("userId>", 0));
		ROLE_MAP.put(CONTACT, new Entry<String, Object>("userId{}", "contactIdList"));
		ROLE_MAP.put(CIRCLE, new Entry<String, Object>("userId-()", "verifyCircle()")); // "userId{}", "circleIdList"));  // 还是 {"userId":"currentUserId", "userId{}": "contactIdList", "@combine": "userId,userId{}" } ?
		ROLE_MAP.put(OWNER, new Entry<String, Object>("userId", "userId"));
		ROLE_MAP.put(ADMIN, new Entry<String, Object>("userId-()", "verifyAdmin()"));

		OPERATION_KEY_LIST = new ArrayList<>();
		OPERATION_KEY_LIST.add(TYPE.name());
		OPERATION_KEY_LIST.add(VERIFY.name());
		OPERATION_KEY_LIST.add(INSERT.name());
		OPERATION_KEY_LIST.add(UPDATE.name());
		OPERATION_KEY_LIST.add(REPLACE.name());
		OPERATION_KEY_LIST.add(EXIST.name());
		OPERATION_KEY_LIST.add(UNIQUE.name());
		OPERATION_KEY_LIST.add(REMOVE.name());
		OPERATION_KEY_LIST.add(MUST.name());
		OPERATION_KEY_LIST.add(REFUSE.name());
		OPERATION_KEY_LIST.add(IF.name());
//		OPERATION_KEY_LIST.add(CODE.name());
		OPERATION_KEY_LIST.add(ALLOW_PARTIAL_UPDATE_FAIL.name());


		SYSTEM_ACCESS_MAP = new HashMap<String, Map<RequestMethod, String[]>>();

		SYSTEM_ACCESS_MAP.put(Access.class.getSimpleName(), getAccessMap(Access.class.getAnnotation(MethodAccess.class)));
		SYSTEM_ACCESS_MAP.put(Function.class.getSimpleName(), getAccessMap(Function.class.getAnnotation(MethodAccess.class)));
		SYSTEM_ACCESS_MAP.put(Script.class.getSimpleName(), getAccessMap(Script.class.getAnnotation(MethodAccess.class)));
		SYSTEM_ACCESS_MAP.put(Request.class.getSimpleName(), getAccessMap(Request.class.getAnnotation(MethodAccess.class)));

		if (Log.DEBUG) {
			SYSTEM_ACCESS_MAP.put(Table.class.getSimpleName(), getAccessMap(Table.class.getAnnotation(MethodAccess.class)));
			SYSTEM_ACCESS_MAP.put(Column.class.getSimpleName(), getAccessMap(Column.class.getAnnotation(MethodAccess.class)));
			SYSTEM_ACCESS_MAP.put(PgAttribute.class.getSimpleName(), getAccessMap(PgAttribute.class.getAnnotation(MethodAccess.class)));
			SYSTEM_ACCESS_MAP.put(PgClass.class.getSimpleName(), getAccessMap(PgClass.class.getAnnotation(MethodAccess.class)));
			SYSTEM_ACCESS_MAP.put(AllTable.class.getSimpleName(), getAccessMap(AllTable.class.getAnnotation(MethodAccess.class)));
			SYSTEM_ACCESS_MAP.put(AllTableComment.class.getSimpleName(), getAccessMap(AllTableComment.class.getAnnotation(MethodAccess.class)));
			SYSTEM_ACCESS_MAP.put(AllColumn.class.getSimpleName(), getAccessMap(AllColumn.class.getAnnotation(MethodAccess.class)));
			SYSTEM_ACCESS_MAP.put(AllColumnComment.class.getSimpleName(), getAccessMap(AllColumnComment.class.getAnnotation(MethodAccess.class)));
			SYSTEM_ACCESS_MAP.put(SysTable.class.getSimpleName(), getAccessMap(SysTable.class.getAnnotation(MethodAccess.class)));
			SYSTEM_ACCESS_MAP.put(SysColumn.class.getSimpleName(), getAccessMap(SysColumn.class.getAnnotation(MethodAccess.class)));
			SYSTEM_ACCESS_MAP.put(ExtendedProperty.class.getSimpleName(), getAccessMap(ExtendedProperty.class.getAnnotation(MethodAccess.class)));

			SYSTEM_ACCESS_MAP.put(Document.class.getSimpleName(), getAccessMap(Document.class.getAnnotation(MethodAccess.class)));
		}

		ACCESS_MAP = new HashMap<>(SYSTEM_ACCESS_MAP);

		ACCESS_FAKE_DELETE_MAP = new LinkedHashMap<>();

		REQUEST_MAP = new HashMap<>(ACCESS_MAP.size()*7);  // 单个与批量增删改

		COMPILE_MAP = new HashMap<String, Pattern>();

	}


	/**获取权限Map，每种操作都只允许对应的角色
	 * @param access
	 * @return
	 */
	public static HashMap<RequestMethod, String[]> getAccessMap(MethodAccess access) {
		if (access == null) {
			return null;
		}

		HashMap<RequestMethod, String[]> map = new HashMap<>();
		map.put(GET, access.GET());
		map.put(HEAD, access.HEAD());
		map.put(GETS, access.GETS());
		map.put(HEADS, access.HEADS());
		map.put(POST, access.POST());
		map.put(PUT, access.PUT());
		map.put(DELETE, access.DELETE());

		return map;
	}


	@Override
	public String getVisitorIdKey(SQLConfig<T, M, L> config) {
		return config == null ? getUserIdKey(null, null, null, null, null, null) : config.getUserIdKey();
	}

	@Override
	public String getIdKey(String database, String datasource, String namespace, String catalog, String schema, String table) {
		return KEY_ID;
	}
	@Override
	public String getUserIdKey(String database, String datasource, String namespace, String catalog, String schema, String table) {
		return KEY_USER_ID;
	}

	@SuppressWarnings("unchecked")
	@Override
	public T newId(RequestMethod method, String database, String datasource, String namespace, String catalog, String schema, String table) {
		return (T) Long.valueOf(System.currentTimeMillis());
	}



	@NotNull
	protected Visitor<T> visitor;
	protected Object visitorId;
	@NotNull
	@Override
	public Visitor<T> getVisitor() {
		return visitor;
	}
	@Override
	public AbstractVerifier<T, M, L> setVisitor(Visitor<T> visitor) {
		this.visitor = visitor;
		this.visitorId = visitor == null ? null : visitor.getId();

		//导致内部调用且放行校验(needVerifyLogin, needVerifyRole)也抛异常
		//		if (visitorId == null) {
		//			throw new NullPointerException(TAG + ".setVisitor visitorId == null !!! 可能导致权限校验失效，引发安全问题！");
		//		}

		return this;
	}


	/**验证权限是否通过
	 * @param config
	 * @return
	 * @throws Exception
	 */
	@Override
	public boolean verifyAccess(SQLConfig<T, M, L> config) throws Exception {
		if (ENABLE_VERIFY_ROLE == false) {
			throw new UnsupportedOperationException("AbstractVerifier.ENABLE_VERIFY_ROLE == false " +
                    "时不支持校验角色权限！如需支持则设置 AbstractVerifier.ENABLE_VERIFY_ROLE = true ！");
		}

		String table = config == null ? null : config.getTable();
		if (table == null) {
			return true;
		}

		String role = config.getRole();
		if (role == null) {
			role = UNKNOWN;
		}
		else {
			if (ROLE_MAP.containsKey(role) == false) {
				Set<String> NAMES = ROLE_MAP.keySet();
				throw new IllegalArgumentException("角色 " + role + " 不存在！" +
                        "只能是[" + StringUtil.get(NAMES.toArray()) + "]中的一种！");
			}

			if (role.equals(UNKNOWN) == false) { //未登录的角色
				verifyLogin();
			}
		}

		RequestMethod method = config.getMethod();
		verifyRole(config, table, method, role);

		return true;
	}

	@Override
	public void verifyRole(SQLConfig<T, M, L> config, String table, RequestMethod method, String role) throws Exception {
		verifyAllowRole(config, table, method, role); //验证允许的角色
		verifyUseRole(config, table, method, role); //验证使用的角色
	}

	/**允许请求使用的所以可能角色
	 * @param config
	 * @param table
	 * @param method
	 * @param role
	 * @return
	 * @throws Exception
	 * @see {@link JSONMap#KEY_ROLE}
	 */
	public void verifyAllowRole(SQLConfig<T, M, L> config, String table, RequestMethod method, String role) throws Exception {
		Log.d(TAG, "verifyAllowRole  table = " + table + "; method = " + method + "; role = " + role);
		if (table == null) {
			table = config == null ? null : config.getTable();
		}

		if (table != null) {
			if (method == null) {
				method = config == null ? GET : config.getMethod();
			}
			if (role == null) {
				role = config == null ? UNKNOWN : config.getRole();
			}

			if (Log.DEBUG == false && SYSTEM_ACCESS_MAP.get(table) != null) {
				throw new IllegalAccessException(table + " 不允许 " + role + " 用户的 " + method.name() + " 请求！");
			}

			Map<RequestMethod, String[]> map = ACCESS_MAP.get(table);

			if (map == null || Arrays.asList(map.get(method)).contains(role) == false) {
				throw new IllegalAccessException(table + " 不允许 " + role + " 用户的 " + method.name() + " 请求！");
			}
		}
	}

	/**校验请求使用的角色，角色不好判断，让访问者发过来角色名，OWNER,CONTACT,ADMIN等
	 * @param config
	 * @param table
	 * @param method
	 * @param role
	 * @return
	 * @throws Exception
	 * @see {@link JSONMap#KEY_ROLE}
	 */
	public void verifyUseRole(@NotNull SQLConfig<T, M, L> config, String table, RequestMethod method, String role) throws Exception {
		Log.d(TAG, "verifyUseRole  table = " + table + "; method = " + method + "; role = " + role);
		Objects.requireNonNull(config);
		//验证角色，假定真实强制匹配<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

		if (table == null) {
			table = config.getTable();
		}
		if (method == null) {
			method = config.getMethod();
		}
		if (role == null) {
			role = config.getRole();
		}

		String visitorIdKey = getVisitorIdKey(config);

		Object requestId;
		switch (role) {
		case LOGIN://verifyRole通过就行
			break;
		case CONTACT:
		case CIRCLE:
			// TODO 做一个缓存contactMap<visitorId, contactArray>，提高[]:{}查询性能， removeAccessInfo时map.remove(visitorId)
			// 不能在 Visitor内null -> [] ! 否则会导致某些查询加上不需要的条件！
			List<Object> list = visitor.getContactIdList() == null
			? new ArrayList<Object>() : new ArrayList<Object>(visitor.getContactIdList());
			if (CIRCLE.equals(role)) {
				list.add(visitorId);
			}

			// key!{}:[] 或 其它没有明确id的条件 等 可以和 key{}:[] 组合。类型错误就报错
			requestId = config.getWhere(visitorIdKey, true); // JSON 里数值不能保证是 Long，可能是 Integer
			@SuppressWarnings("unchecked")
			Collection<Object> requestIdArray = (Collection<Object>) config.getWhere(visitorIdKey + "{}", true); // 不能是 &{}， |{} 不要传，直接 {}
			if (requestId != null) {
				if (requestIdArray == null) {
					requestIdArray = createJSONArray();
				}
				requestIdArray.add(requestId);
			}

			if (requestIdArray == null) { // 可能是 @ 得到 || requestIdArray.isEmpty()) { // 请求未声明 key:id 或 key{}:[...] 条件，自动补全
				config.putWhere(visitorIdKey+"{}", parseArray(list), true); // key{}:[] 有效，SQLConfig<T, M, L> 里 throw NotExistException
			}
			else { // 请求已声明 key:id 或 key{}:[] 条件，直接验证
				for (Object id : requestIdArray) {
					if (id == null) {
						continue;
					}

					if (id instanceof Number) { // 不能准确地判断 Long，可能是 Integer
						if (((Number) id).longValue() <= 0 || list.contains(Long.valueOf("" + id)) == false) { // Integer等转为 Long 才能正确判断，强转崩溃
							throw new IllegalAccessException(visitorIdKey + " = " + id + " 的 " + table
									+ " 不允许 " + role + " 用户的 " + method.name() + " 请求！");
						}
					}
					else if (id instanceof String) {
						if (StringUtil.isEmpty(id) || list.contains(id) == false) {
							throw new IllegalAccessException(visitorIdKey + " = " + id + " 的 " + table
									+ " 不允许 " + role + " 用户的 " + method.name() + " 请求！");
						}
					}
					else {
						throw new UnsupportedDataTypeException(table + ".id 类型错误，类型必须是 Long/String！");
					}
				}
			}
			break;
		case OWNER:
			if (config.getMethod() == POST) {
				List<String> c = config.getColumn();
				List<List<Object>> ovs = config.getValues();
				if ( (c == null || c.isEmpty()) || (ovs == null || ovs.isEmpty()) ) {
					throw new IllegalArgumentException("POST 请求必须在Table内设置要保存的 key:value ！");
				}

				int index = c.indexOf(visitorIdKey);
				if (index >= 0) {
					Object oid;
					for (List<Object> ovl : ovs) {
						oid = ovl == null || index >= ovl.size() ? null : ovl.get(index);
						if (oid == null || StringUtil.get(oid).equals("" + visitorId) == false) {
							throw new IllegalAccessException(visitorIdKey + " = " + oid + " 的 " + table
									+ " 不允许 " + role + " 用户的 " + method.name() + " 请求！");
						}
					}
				}
				else {
					List<String> nc = new ArrayList<>(c);
					nc.add(visitorIdKey);
					config.setColumn(nc);

					List<List<Object>> nvs = new ArrayList<>();
					List<Object> nvl;
					for (List<Object> ovl : ovs) {
						nvl = ovl == null || ovl.isEmpty() ? new ArrayList<>() : new ArrayList<>(ovl);
						nvl.add(visitorId);
						nvs.add(nvl);
					}

					config.setValues(nvs);
				}
			}
			else {
				requestId = config.getWhere(visitorIdKey, true);//JSON里数值不能保证是Long，可能是Integer
				if (requestId != null && StringUtil.get(requestId).equals(StringUtil.get(visitorId)) == false) {
					throw new IllegalAccessException(visitorIdKey + " = " + requestId + " 的 " + table
							+ " 不允许 " + role + " 用户的 " + method.name() + " 请求！");
				}

				config.putWhere(visitorIdKey, visitorId, true);
			}
			break;
		case ADMIN://这里不好做，在特定接口内部判。 可以是  /get/admin + 固定秘钥  Parser#needVerify，之后全局跳过验证
			verifyAdmin();
			break;
		default://unknown，verifyRole通过就行
			break;
		}

		//验证角色，假定真实强制匹配>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>
	}


	/**登录校验
	 */
	@Override
	public void verifyLogin() throws Exception {
		//未登录没有权限操作
		if (visitorId == null) {
			throw new NotLoggedInException("未登录或登录过期，请登录后再操作！");
		}

		if (visitorId instanceof Number) {
			if (((Number) visitorId).longValue() <= 0) {
				throw new NotLoggedInException("未登录或登录过期，请登录后再操作！");
			}
		}
		else if (visitorId instanceof String) {
			if (StringUtil.isEmpty(visitorId, true)) {
				throw new NotLoggedInException("未登录或登录过期，请登录后再操作！");
			}
		}
		else {
			throw new UnsupportedDataTypeException("visitorId 只能是 Long 或 String 类型！");
		}

	}

	@Override
	public void verifyAdmin() throws Exception {
		throw new UnsupportedOperationException("不支持 ADMIN 角色！如果要支持就在子类重写这个方法" +
                "来校验 ADMIN 角色，不通过则 throw IllegalAccessException!");
	}


	/**验证是否重复
	 * FIXME 这个方法实际上没有被使用
	 * @param table
	 * @param key
	 * @param value
	 * @throws Exception
	 */
	@Override
	public void verifyRepeat(String table, String key, Object value) throws Exception {
		verifyRepeat(table, key, value, 0);
	}
	/**验证是否重复
	 * FIXME 这个方法实际上没有被使用，而且与 Structure.verifyRepeat 代码重复度比较高，需要简化
	 * @param table
	 * @param key
	 * @param value
	 * @param exceptId 不包含id
	 * @throws Exception
	 */
	@Override
	public void verifyRepeat(String table, String key, Object value, long exceptId) throws Exception {
		if (key == null || value == null) {
			Log.e(TAG, "verifyRepeat  key == null || value == null >> return;");
			return;
		}
		if (value instanceof JSON) {
			throw new UnsupportedDataTypeException(key + ":value 中value的类型不能为JSON！");
		}

		M tblObj = createJSONObject();
		tblObj.put(key, value);
		if (exceptId > 0) {//允许修改自己的属性为该属性原来的值
			tblObj.put(KEY_ID + "!", exceptId);  // FIXME 这里 id 写死了，不支持自定义
		}

		M req = createJSONObject();
		req.put(table, tblObj);
		Map<String, Object> repeat = createParser().setMethod(HEAD).setNeedVerify(true).parseResponse(req);

		repeat = repeat == null ? null : get(repeat, table);
		if (repeat == null) {
			throw new Exception("服务器内部错误  verifyRepeat  repeat == null");
		}
		if (getIntValue(repeat, JSONResponse.KEY_COUNT) > 0) {
			throw new ConflictException(key + ": " + value + " 已经存在，不能重复！");
		}
	}



	/**从request提取target指定的内容
	* @param method
	* @param name
	* @param target
	* @param request
	* @param maxUpdateCount
	* @param database
	* @param schema
	* @return
	* @throws Exception
	*/
	@Override
	public M verifyRequest(@NotNull RequestMethod method, String name, M target, M request, int maxUpdateCount
			, String database, String datasource, String namespace, String catalog, String schema) throws Exception {
		return verifyRequest(method, name, target, request, maxUpdateCount, database, datasource, namespace, catalog, schema, this, getParser());
	}

	/**从request提取target指定的内容
	 * @param method
	 * @param name
	 * @param target
	 * @param request
	 * @param parser
	 * @return
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M verifyRequest(
			@NotNull final RequestMethod method, final String name, final M target, final M request
			, @NotNull Parser<T, M, L> parser) throws Exception {
		return verifyRequest(method, name, target, request, AbstractParser.MAX_UPDATE_COUNT, parser);
	}
	/**从request提取target指定的内容
	 * @param method
	 * @param name
	 * @param target
	 * @param request
	 * @param maxUpdateCount
	 * @param parser
	 * @return
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M verifyRequest(
			@NotNull final RequestMethod method, final String name, final M target, final M request
            , final int maxUpdateCount, @NotNull Parser<T, M, L> parser) throws Exception {

		return verifyRequest(method, name, target, request, maxUpdateCount, null, null, null, parser);
	}

	/**从request提取target指定的内容
	* @param method
	* @param name
	* @param target
	* @param request
	* @param maxUpdateCount
	* @param database
	* @param schema
	* @param idCallback
	* @param parser
	* @return
	* @param <T>
	* @throws Exception
	*/
	public static <T, M extends Map<String, Object>, L extends List<Object>> M verifyRequest(
			@NotNull RequestMethod method, String name, M target, M request, int maxUpdateCount, String database
			, String schema, IdCallback<T> idCallback, @NotNull Parser<T, M, L> parser) throws Exception {

		return verifyRequest(method, name, target, request, maxUpdateCount, database, null, null, null, schema, idCallback, parser);
	}
	/**从request提取target指定的内容
	 * @param method
	 * @param name
	 * @param target
	 * @param request
	 * @param maxUpdateCount
	 * @param database
	 * @param datasource
	 * @param namespace
	 * @param catalog
	 * @param schema
	 * @param idCallback
	 * @param parser
	 * @return
	 * @param <T>
	 * @param <M>
	 * @param <L>
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M verifyRequest(
			@NotNull final RequestMethod method, final String name, final M target, final M request
            , final int maxUpdateCount, final String database, final String datasource, final String namespace
			, final String catalog, final String schema, final IdCallback<T> idCallback
			, @NotNull Parser<T, M, L> parser) throws Exception {
		if (ENABLE_VERIFY_CONTENT == false) {
			throw new UnsupportedOperationException("AbstractVerifier.ENABLE_VERIFY_CONTENT == false" +
                    " 时不支持校验请求传参内容！如需支持则设置 AbstractVerifier.ENABLE_VERIFY_CONTENT = true ！");
		}

		Log.i(TAG, "verifyRequest  method = " + method  + "; name = " + name
				+ "; target = \n" + toJSONString(target)
				+ "\n request = \n" + toJSONString(request));

		if (target == null || request == null) {// || request.isEmpty()) {
			Log.i(TAG, "verifyRequest  target == null || request == null >> return null;");
			return null;
		}

		//已在 Verifier 中处理
		//		if (get(getString(request, apijson.JSONMap.KEY_ROLE)) == ADMIN) {
		//			throw new IllegalArgumentException("角色设置错误！不允许在写操作Request中传 " + name +
		//					":{ " + apijson.JSONMap.KEY_ROLE + ":admin } ！");
		//		}


		//解析
		return parse(method, name, target, request, database, datasource, namespace, catalog, schema, idCallback, parser, new OnParseCallback<T, M, L>() {

			@Override
			public M onParseJSONObject(String key, M tobj, M robj) throws Exception {
				//				Log.i(TAG, "verifyRequest.parse.onParseJSONObject  key = " + key + "; robj = " + robj);

				if (robj == null) {
					if (tobj != null) {//不允许不传Target中指定的Table
						throw new IllegalArgumentException(method + "请求，请在 " + name + " 内传 " + key + ":{} ！");
					}
				} else if (isTableKey(key)) {
					String db = getString(request, KEY_DATABASE);
					String ds = getString(request, KEY_DATASOURCE);
					String ns = getString(request, KEY_NAMESPACE);
					String cl = getString(request, KEY_CATALOG);
					String sh = getString(request, KEY_SCHEMA);
					if (StringUtil.isEmpty(db, false)) {
						db = database;
					}
					if (StringUtil.isEmpty(ds, false)) {
						ds = datasource;
					}
					if (StringUtil.isEmpty(ns, false)) {
						ns = namespace;
					}
					if (StringUtil.isEmpty(cl, false)) {
						cl = catalog;
					}
					if (StringUtil.isEmpty(sh, false)) {
						sh = schema;
					}

					String idKey = idCallback == null ? null : idCallback.getIdKey(db, ds, ns, cl, sh, key);
					String finalIdKey = StringUtil.isEmpty(idKey, false) ? KEY_ID : idKey;

					if (method == POST) {
						if (robj.containsKey(finalIdKey)) {
							throw new IllegalArgumentException(method + "请求，" + name + "/" + key + " 不能传 " + finalIdKey + " ！");
						}
					} else {
						Boolean atLeastOne = tobj == null ? null : getBoolean(tobj, Operation.IS_ID_CONDITION_MUST.name());
						if (Boolean.TRUE.equals(atLeastOne) || RequestMethod.isUpdateMethod(method)) {
							verifyId(method.name(), name, key, robj, finalIdKey, maxUpdateCount, atLeastOne != null ? atLeastOne : IS_UPDATE_MUST_HAVE_ID_CONDITION);

							String userIdKey = idCallback == null ? null : idCallback.getUserIdKey(db, ds, ns, cl, sh, key);
							String finalUserIdKey = StringUtil.isEmpty(userIdKey, false) ? KEY_USER_ID : userIdKey;
							verifyId(method.name(), name, key, robj, finalUserIdKey, maxUpdateCount, false);
						}
					}
				}

				return verifyRequest(method, key, tobj, robj, maxUpdateCount, database, datasource, namespace, catalog, schema, idCallback, parser);
			}

			@Override
			protected L onParseJSONArray(String key, L tarray, L rarray) throws Exception {
				if ((method == POST || method == PUT) && isArrayKey(key)) {
					if (rarray == null || rarray.isEmpty()) {
						throw new IllegalArgumentException(method + "请求，请在 " + name + " 内传 " + key + ":[{ ... }] "
								+ "，批量新增 Table[]:value 中 value 必须是包含表对象的非空数组！其中每个子项 { ... } 都是"
								+ " tag:" + key.substring(0, key.length() - 2) + " 对应单个新增的 structure ！");
					}
					if (rarray.size() > maxUpdateCount) {
						throw new IllegalArgumentException(method + "请求，" + name + "/" + key
								+ " 里面的 " + key + ":[{ ... }] 中 [] 的长度不能超过 " + maxUpdateCount + " ！");
					}
				}
				return super.onParseJSONArray(key, tarray, rarray);
			}
		});

	}

	/**
	 * @param method
	 * @param name
	 * @param key
	 * @param robj
	 * @param idKey
	 * @param atLeastOne 至少有一个不为null
	 */
	private static <T, M extends Map<String, Object>, L extends List<Object>> void verifyId(
			@NotNull String method, @NotNull String name, @NotNull String key
			, @NotNull M robj, @NotNull String idKey, final int maxUpdateCount, boolean atLeastOne) throws Exception {
		//单个修改或删除
		Object id = robj.get(idKey); //如果必须传 id ，可在Request表中配置NECESSARY
		if (id != null && id instanceof Number == false && id instanceof String == false) {
			throw new IllegalArgumentException(method + "请求，" + name + "/" + key
					+ " 里面的 " + idKey + ":value 中value的类型只能是 Long 或 String ！");
		}


		//批量修改或删除
		String idInKey = idKey + "{}";
		// id引用, 格式: "id{}@": "sql"
		String idRefInKey = getString(robj, idKey + "{}@");
		L idIn = null;
		try {
			idIn = get(robj, idInKey); //如果必须传 id{} ，可在Request表中配置NECESSARY
		} catch (Exception e) {
			throw new IllegalArgumentException(method + "请求，" + name + "/" + key
					+ " 里面的 " + idInKey + ":value 中value的类型只能是 [Long] ！");
		}
		if (idIn == null) {
			if (atLeastOne && id == null && idRefInKey == null) {
				throw new IllegalArgumentException(method + "请求，" + name + "/" + key
						+ " 里面 " + idKey + "," + idInKey  + "," + (idKey + "{}@") + " 至少传其中一个！");
			}
		} else {
			if (idIn.size() > maxUpdateCount) { //不允许一次操作 maxUpdateCount 条以上记录
				throw new IllegalArgumentException(method + "请求，" + name + "/" + key
						+ " 里面的 " + idInKey + ":[] 中[]的长度不能超过 " + maxUpdateCount + " ！");
			}
			//解决 id{}: ["1' OR 1='1'))--"] 绕过id{}限制
			//new ArrayList<Long>(idIn) 不能检查类型，Java泛型擦除问题，居然能把 ["a"] 赋值进去还不报错
			for (int i = 0; i < idIn.size(); i++) {
				Object o = idIn.get(i);
				if (o == null) {
					throw new IllegalArgumentException(method + "请求，" + name + "/" + key
							+ " 里面的 " + idInKey + ":[] 中所有项都不能为 [ null, <= 0 的数字, 空字符串 \"\" ] 中任何一个 ！");
				}
				if (o instanceof Number) {
					//解决 Windows mysql-5.6.26-winx64 等低于 5.7 的 MySQL 可能 id{}: [0] 生成 id IN(0) 触发 MySQL bug 导致忽略 IN 条件
					//例如 UPDATE `apijson`.`TestRecord` SET `testAccountId` = -1 WHERE ( (`id` IN (0)) AND (`userId`= 82001) )
					if (((Number) o).longValue() <= 0) {
						throw new IllegalArgumentException(method + "请求，" + name + "/" + key
								+ " 里面的 " + idInKey + ":[] 中所有项都不能为 [ null, <= 0 的数字, 空字符串 \"\" ] 中任何一个 ！");
					}
				}
				else if (o instanceof String) {
					if (StringUtil.isEmpty(o, true)) {
						throw new IllegalArgumentException(method + "请求，" + name + "/" + key
								+ " 里面的 " + idInKey + ":[] 中所有项都不能为 [ null, <= 0 的数字, 空字符串 \"\" ] 中任何一个 ！");
					}
				}
				else {
					throw new IllegalArgumentException(method + "请求，" + name + "/" + key
							+ " 里面的 " + idInKey + ":[] 中所有项的类型都只能是 Long 或 String ！");
				}
			}
		}
	}


	/**校验并将response转换为指定的内容和结构
	* @param method
	* @param name
	* @param target
	* @param response
	* @param database
	* @param schema
	* @param parser
	* @param callback
	* @return
	* @throws Exception
	*/
	@Override
	public M verifyResponse(@NotNull final RequestMethod method, final String name, final M target, final M response
			, final String database, final String schema, @NotNull Parser<T, M, L> parser, OnParseCallback<T, M, L> callback) throws Exception {
		return verifyResponse(method, name, target, response, database, schema, this, parser, callback);
	}

	/**校验并将response转换为指定的内容和结构
	* @param method
	* @param name
	* @param target
	* @param response
	* @param parser
	* @param callback
	* @return
	* @throws Exception
	*/
	public static <T, M extends Map<String, Object>, L extends List<Object>> M verifyResponse(@NotNull final RequestMethod method, final String name
			, final M target, final M response, @NotNull Parser<T, M, L> parser, OnParseCallback<T, M, L> callback) throws Exception {
		return verifyResponse(method, name, target, response, null, null, null, parser, callback);
	}
	/**校验并将response转换为指定的内容和结构
	* @param method
	* @param name
	* @param target
	* @param response
	* @param database
	* @param schema
	* @param idKeyCallback
	* @param parser
	* @param callback
	* @return
	* @param <T>
	* @throws Exception
	*/
	public static <T, M extends Map<String, Object>, L extends List<Object>>  M verifyResponse(@NotNull final RequestMethod method
			, final String name, final M target, final M response, final String database, final String schema
			, final IdCallback<T> idKeyCallback, @NotNull Parser<T, M, L> parser, OnParseCallback<T, M, L> callback) throws Exception {

		Log.i(TAG, "verifyResponse  method = " + method  + "; name = " + name
				+ "; target = \n" + toJSONString(target)
				+ "\n response = \n" + toJSONString(response));

		if (target == null || response == null) {// || target.isEmpty() {
			Log.i(TAG, "verifyResponse  target == null || response == null >> return response;");
			return response;
		}

		//解析
		return parse(method, name, target, response, database, schema
                , idKeyCallback, parser, callback != null ? callback : new OnParseCallback<T, M, L>() {
			@Override
			protected M onParseJSONObject(String key, M tobj, M robj) throws Exception {
				return verifyResponse(method, key, tobj, robj, database, schema, idKeyCallback, parser, callback);
			}
		});
	}


	/**对request和response不同的解析用callback返回
	 * @param method
	 * @param name
	 * @param target
	 * @param real
	 * @param parser
	 * @param callback
	 * @return
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M parse(@NotNull final RequestMethod method
			, String name, M target, M real, @NotNull Parser<T, M, L> parser, @NotNull OnParseCallback<T, M, L> callback) throws Exception {
		return parse(method, name, target, real, null, null, null, parser, callback);
	}
	/**对request和response不同的解析用callback返回
	 * @param method
	 * @param name
	 * @param target
	 * @param real
	 * @param database
	 * @param schema
	 * @param idCallback
	 * @param parser
	 * @param callback
	 * @return
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M parse(
			@NotNull final RequestMethod method, String name, M target, M real, final String database, final String schema
            , final IdCallback<T> idCallback, @NotNull Parser<T, M, L> parser, @NotNull OnParseCallback<T, M, L> callback) throws Exception {
		return parse(method, name, target, real, database, null, null, null, schema, idCallback, parser, callback);
	}
	/**对request和response不同的解析用callback返回
	 * @param method
	 * @param name
	 * @param target
	 * @param real
	 * @param database
	 * @param datasource
	 * @param namespace
	 * @param catalog
	 * @param schema
	 * @param idCallback
	 * @param parser
	 * @param callback
	 * @return
	 * @param <T>
	 * @param <M>
	 * @param <L>
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M parse(@NotNull final RequestMethod method
			, String name, M target, M real, String database, String datasource, String namespace, String catalog, String schema
            , final IdCallback<T> idCallback, @NotNull Parser<T, M, L> parser, @NotNull OnParseCallback<T, M, L> callback) throws Exception {
		if (target == null) {
			return null;
		}

		Object _if = target.get(IF.name());
		boolean ifIsStr = _if instanceof String && StringUtil.isNotEmpty(_if, true);
		M ifObj = ifIsStr == false && _if instanceof Map<?,?> ? (M) _if : null;
//				: (_if instanceof String ? new apijson.JSONMap((String) _if, "" /* "throw new Error('')" */ ) : null);
		if (ifObj == null && _if != null && ifIsStr == false) {
//			if (_if instanceof List<?>) {
//			}
			throw new IllegalArgumentException(name + ": { " + IF.name() + ": value } 中 value 类型错误！只允许 String, JSONRequest！");
		}

		// 获取配置<<<<<<<<<<<<<<<<<<<<<<<<<<<<
		M type = get(target, TYPE.name());
		M verify = get(target, VERIFY.name());
		M insert = get(target, INSERT.name());
		M update = get(target, UPDATE.name());
		M replace = get(target, REPLACE.name());

		String exist = StringUtil.get(getString(target, EXIST.name()));
		String unique = StringUtil.get(getString(target, UNIQUE.name()));
		String remove = StringUtil.get(getString(target, REMOVE.name()));
		String must = StringUtil.get(getString(target, MUST.name()));
		String refuse = StringUtil.get(getString(target, REFUSE.name()));

//		Object code = target.get(CODE.name());

		// 移除字段<<<<<<<<<<<<<<<<<<<
		String[] removes = StringUtil.split(remove);
		if (removes != null && removes.length > 0) {
			for (String r : removes) {
				real.remove(r);
			}
		}
		// 移除字段>>>>>>>>>>>>>>>>>>>

		// 判断必要字段是否都有<<<<<<<<<<<<<<<<<<<
		String[] musts = StringUtil.split(must);
		Set<String> mustSet = new HashSet<String>();

		if (musts != null && musts.length > 0) {
			for (String s : musts) {
				if (real.get(s) == null && real.get(s+"@") == null) {  // 可能传null进来，这里还会通过 real.containsKey(s) == false) {
					throw new IllegalArgumentException(method + "请求，"
                            + name + " 里面不能缺少 " + s + " 等[" + must + "]内的任何字段！");
				}

				mustSet.add(s);
			}
		}
		// 判断必要字段是否都有>>>>>>>>>>>>>>>>>>>

		String[] sks = StringUtil.split(getString(real, KEY_STRING));
		String[] trims = StringUtil.split(getString(real, KEY_TRIM));
		List<String> stringKeyList = sks == null || sks.length <= 0 ? null : Arrays.asList(sks);
		List<String> trimKeyList = trims == null || trims.length <= 0 ? null : Arrays.asList(trims);

		Set<String> objKeySet = new HashSet<String>(); // 不能用tableKeySet，仅判断 Table:{} 会导致 key:{ Table:{} } 绕过判断

		// 解析内容<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<<

		Set<Map.Entry<String, Object>> set = new LinkedHashSet<>(target.entrySet());
		if (set.isEmpty() == false) {

			for (Map.Entry<String, Object> entry : set) {
                String key = entry == null ? null : entry.getKey();
				if (key == null || OPERATION_KEY_LIST.contains(key)) {
					continue;
				}
                Object tvalue = entry.getValue();
                Object rvalue = real.get(key);
				if (rvalue != null && stringKeyList != null && stringKeyList.contains(key)) {
					rvalue = toJSONString(rvalue);
				}
				if (rvalue != null && trimKeyList != null && trimKeyList.contains(key)) {
					rvalue = StringUtil.trim(rvalue);
				}

				if (callback.onParse(key, tvalue, rvalue) == false) {
					continue;
				}

				if (tvalue instanceof Map<?, ?>) { // JSONRequest，往下一级提取
					if (rvalue != null && rvalue instanceof Map<?, ?> == false) {
						throw new UnsupportedDataTypeException(key + ":value 的 value 不合法！类型必须是 OBJECT ，结构为 {} !");
					}
					tvalue = callback.onParseJSONObject(key, (M) tvalue, (M) rvalue);

					objKeySet.add(key);
				} else if (tvalue instanceof List<?>) { // L
					if (rvalue != null && rvalue instanceof List<?> == false) {
						throw new UnsupportedDataTypeException(key + ":value 的 value 不合法！类型必须是 ARRAY ，结构为 [] !");
					}
					tvalue = callback.onParseJSONArray(key, (L) tvalue, (L) rvalue);

					if ((method == POST || method == PUT) && isArrayKey(key)) {
						objKeySet.add(key);
					}
				} else { // 其它Object
					tvalue = callback.onParseObject(key, tvalue, rvalue);
				}

				if (tvalue != null) { // 可以在target中加上一些不需要客户端传的键值对
					real.put(key, tvalue);
				}
			}

		}

		// 解析内容>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>



		Set<String> rkset = real.keySet(); // 解析内容并没有改变rkset

		// 解析不允许的字段<<<<<<<<<<<<<<<<<<<
		String[] refuses = StringUtil.split(refuse);
		Set<String> refuseSet = new HashSet<String>();

		if (refuses != null && refuses.length > 0) {
			Set<String> notRefuseSet = new HashSet<String>();

			for (String rfs : refuses) {
				if (rfs == null) {  // StringUtil.isEmpty(rfs, true) {
					continue;
				}

				if (rfs.startsWith("!")) {
					rfs = rfs.substring(1);

					if (notRefuseSet.contains(rfs)) {
						throw new ConflictException(REFUSE.name() + ":value 中出现了重复的 !"
                                + rfs + " ！不允许重复，也不允许一个 key 和取反 !key 同时使用！");
					}
					if (refuseSet.contains(rfs)) {
						throw new ConflictException(REFUSE.name() + ":value 中同时出现了 "
                                + rfs + " 和 !" + rfs + " ！不允许重复，也不允许一个 key 和取反 !key 同时使用！");
					}

					if (rfs.equals("")) { // 所有非 MUST
                        // 对@key放行，@role,@column,自定义@position等， @key:{ "Table":{} } 不会解析内部
						for (String key : rkset) {
							if (key == null || key.startsWith("@") || notRefuseSet.contains(key)
                                    || mustSet.contains(key) || objKeySet.contains(key)) {
								continue;
							}
							// 支持id ref: id{}@
							if (key.endsWith("@") && mustSet.contains(key.substring(0, key.length() - 1))) {
								continue;
							}
							refuseSet.add(key);
						}
					}
					else {  // 排除 !key 后再禁传其它的
						notRefuseSet.add(rfs);
					}
				}
				else {
					if (refuseSet.contains(rfs)) {
						throw new ConflictException(REFUSE.name() + ":value 中出现了重复的 " + rfs + " ！" +
                                "不允许重复，也不允许一个 key 和取反 !key 同时使用！");
					}
					if (notRefuseSet.contains(rfs)) {
						throw new ConflictException(REFUSE.name() + ":value 中同时出现了 " + rfs + " 和 !" + rfs + " ！" +
                                "不允许重复，也不允许一个 key 和取反 !key 同时使用！");
					}

					refuseSet.add(rfs);
				}
			}
		}

		// 解析不允许的字段>>>>>>>>>>>>>>>>>>>

		Set<String> onKeys = new LinkedHashSet<>();

		// 判断不允许传的key<<<<<<<<<<<<<<<<<<<<<<<<<
		for (String rk : rkset) {
			if (rk == null || KEY_STRING.equals(rk) || KEY_TRIM.equals(rk)) {
				// ConcurrentModificationException  real.remove(rk);
				continue;
			}

			if (refuseSet.contains(rk)) { // 不允许的字段
				throw new IllegalArgumentException(method + "请求，" + name
						+ " 里面不允许传 " + rk + " 等" + StringUtil.get(refuseSet) + "内的任何字段！");
			}

			if (KEY_COMBINE.equals(rk)) {
				throw new UnsupportedOperationException(method + " 请求，" + rk + " 不合法！" +
						"非开放请求不允许传 " + KEY_COMBINE + ":value ！");
			}
			if (KEY_KEY.equals(rk)) {
				throw new UnsupportedOperationException(method + " 请求，" + rk + " 不合法！" +
						"非开放请求不允许传 " + KEY_KEY + ":value ！");
			}

			Object rv = real.get(rk);
			if (rv != null && stringKeyList != null && stringKeyList.contains(rk)) {
				rv = toJSONString(rv);
			}
			if (rv != null && trimKeyList != null && trimKeyList.contains(rk)) {
				rv = StringUtil.trim(rv);
			}

			// 不允许传远程函数，只能后端配置
			if (rk.endsWith("()") && rv instanceof String) {
				throw new UnsupportedOperationException(method + " 请求，" + rk + " 不合法！" +
                        "非开放请求不允许传远程函数 key():\"fun()\" ！");
			}

			// 不在target内的 key:{}
			if (rk.startsWith("@") == false && rk.endsWith("@") == false && objKeySet.contains(rk) == false) {
				if (rv instanceof Map<?, ?>) {
					throw new UnsupportedOperationException(method + " 请求，"
                            + name + " 里面不允许传 " + rk + ":{} ！");
				}
				if ((method == POST || method == PUT)
                        && rv instanceof List<?> && isArrayKey(rk)) {
					throw new UnsupportedOperationException(method + " 请求，" + name + " 里面不允许 "
                            + rk + ":[] 等未定义的 Table[]:[{}] 批量操作键值对！");
				}
			}

			// 先让其它操作符完成
//			if (rv != null) { // || nulls.contains(rk)) {
//				onKeys.add(rk);
//			}
		}
		// 判断不允许传的key>>>>>>>>>>>>>>>>>>>>>>>>>



		// 校验与修改Request<<<<<<<<<<<<<<<<<
		// 在tableKeySet校验后操作，避免 导致put/add进去的Table 被当成原Request的内容
		real = operate(TYPE, type, real, parser);
		real = operate(VERIFY, verify, real, parser);
		real = operate(INSERT, insert, real, parser);
		real = operate(UPDATE, update, real, parser);
		real = operate(REPLACE, replace, real, parser);
		// 校验与修改Request>>>>>>>>>>>>>>>>>


		String db = getString(real, KEY_DATABASE);
		String ds = getString(real, KEY_DATASOURCE);
		String ns = getString(real, KEY_NAMESPACE);
		String cl = getString(real, KEY_CATALOG);
		String sh = getString(real, KEY_SCHEMA);
		if (StringUtil.isEmpty(db, false)) {
			db = database;
		}
		if (StringUtil.isEmpty(ds, false)) {
			ds = datasource;
		}
		if (StringUtil.isEmpty(ns, false)) {
			ns = namespace;
		}
		if (StringUtil.isEmpty(cl, false)) {
			cl = catalog;
		}
		if (StringUtil.isEmpty(sh, false)) {
			sh = schema;
		}
		String idKey = idCallback == null ? null : idCallback.getIdKey(db, ds, ns, cl, sh, name);
		String finalIdKey = StringUtil.isEmpty(idKey, false) ? KEY_ID : idKey;

		// TODO 放在operate前？考虑性能、operate修改后再验证的值是否和原来一样
		// 校验存在<<<<<<<<<<<<<<<<<<<
		String[] exists = StringUtil.split(exist);
		if (exists != null && exists.length > 0) {
			long exceptId = getLongValue(real, finalIdKey);
			Map<String,Object> map = new HashMap<>();
			for (String e : exists) {
				map.put(e,real.get(e));
			}
			verifyExist(name, map, exceptId, parser);
		}
		// 校验存在>>>>>>>>>>>>>>>>>>>

		// TODO 放在operate前？考虑性能、operate修改后再验证的值是否和原来一样
		// 校验重复<<<<<<<<<<<<<<<<<<<
		String[] uniques = StringUtil.split(unique);
		if (uniques != null && uniques.length > 0) {
			long exceptId = getLongValue(real, finalIdKey);
			Map<String,Object> map = new HashMap<>();
			for (String u : uniques) {
				map.put(u, real.get(u));
			}
			verifyRepeat(name, map, exceptId, finalIdKey, parser);
		}
		// 校验重复>>>>>>>>>>>>>>>>>>>

		// 校验并配置允许批量增删改部分失败<<<<<<<<<<<<<<<<<<<
		String allowPartialUpdateFail = StringUtil.get(getString(target, ALLOW_PARTIAL_UPDATE_FAIL.name()));
		String[] partialFails = StringUtil.split(allowPartialUpdateFail);
		if (partialFails != null && partialFails.length > 0) {
			for (String key : partialFails) {
                if (isArrayKey(key) == false) {
                    throw new IllegalArgumentException("后端 Request 表中 " + ALLOW_PARTIAL_UPDATE_FAIL.name()
                            + ":value 中 " + key + " 不合法！必须以 [] 结尾！");
                }
                if (target.get(key) instanceof Collection == false) {
                    throw new IllegalArgumentException("后端 Request 表中 " + ALLOW_PARTIAL_UPDATE_FAIL.name()
                            + ":value 中 " + key + " 对应的 " + key + ":[] 不存在！");
                }

                // 可能 Table[] 和 Table:alias[] 冲突  int index = key.indexOf(":");
                // String k = index < 0 ? key.substring(0, key.length() - 2) : key.substring(0, index);
                String k = key.substring(0, key.length() - 2);
                if (k.isEmpty()) {
                    throw new IllegalArgumentException("后端 Request 表中 " + ALLOW_PARTIAL_UPDATE_FAIL.name()
                            + ":value 中 " + key + " 不合法！[] 前必须有名字！");
                }

				AbstractSQLConfig.ALLOW_PARTIAL_UPDATE_FAIL_TABLE_MAP.putIfAbsent(k, "");
			}
		}
		// 校验并配置允许部分批量增删改失败>>>>>>>>>>>>>>>>>>>


		String[] nks = ifObj == null ? null : StringUtil.split(getString(real, KEY_NULL));
		Collection<?> nkl = nks == null || nks.length <= 0 ? new HashSet<>() : Arrays.asList(nks);

		Set<Map.Entry<String, Object>> ifSet = ifObj == null ? null : ifObj.entrySet();
		if (ifIsStr || (ifSet != null && ifSet.isEmpty() == false)) {
			// 没必要限制，都是后端配置的，安全可控，而且可能确实有特殊需求，需要 id, @column 等
//			List<String> condKeys = new ArrayList<>(Arrays.asList(apijson.JSONMap.KEY_ID, apijson.JSONMap.KEY_ID_IN
//					, apijson.JSONMap.KEY_USER_ID, apijson.JSONMap.KEY_USER_ID_IN));
//			condKeys.addAll(apijson.JSONMap.TABLE_KEY_LIST);

			String preCode = "var curObj = " + toJSONString(real) + ";";

			// 未传的 key 在后面 eval 时总是报错 undefined，而且可能有冲突，例如对象里有 "curObj": val 键值对，就会覆盖当前对象定义，还不如都是 curObj.sex 这样取值
//			Set<Map.Entry<String, Object>> rset = real.entrySet();
//			for (Map.Entry<String, Object> entry : rset) {
//				String k = entry == null ? null : entry.getKey();
//				if (StringUtil.isEmpty(k)) {
//					continue;
//				}
//				String vn = JSONResponse.formatOtherKey(k);
//				if (StringUtil.isName(vn) == false) { // 通过 curObj['id@'] 这样取值，写在 IF 配置里
//					continue;
//				}
//
//				Object v = entry.getValue();
//				String vs = v instanceof String ? "\"" + ((String) v).replaceAll("\"", "\\\"") + "\""
//						: (JSON.isBooleanOrNumberOrString(v) ? v.toString() : JSON.format(v));
//				preCode += "\nvar " + vn + " = " + vs + ";";
//			}

			if (ifIsStr) {
				String ifStr = (String) _if;
				int ind = ifStr.indexOf(":");
				String lang = ind < 0 || ind > 20 ? null : ifStr.substring(0, ind);
				boolean isName = StringUtil.isName(lang);
				ScriptEngine engine = getScriptEngine(isName ? lang : null);
				engine.eval(preCode + "\n" + (isName ? ifStr.substring(ind + 1) : ifStr));
			}
			else {
				for (Map.Entry<String, Object> entry : ifSet) {
					String k = entry == null ? null : entry.getKey();
//					if (condKeys.contains(k)) {
//						throw new IllegalArgumentException("Request 表 structure 配置的 " + ON.name()
//								+ ":{ " + k + ":value } 中 key 不合法，不允许传 [" + StringUtil.join(condKeys.toArray(new String[]{})) + "] 中的任何一个 ！");
//					}

					Object v = k == null ? null : entry.getValue();
					if (v instanceof String) {
						int ind = k.indexOf(":");
						String lang = ind < 0 || ind > 20 ? null : k.substring(0, ind);
						boolean isName = StringUtil.isName(lang);
						ScriptEngine engine = getScriptEngine(isName ? lang : null);
						k = isName ? k.substring(ind + 1) : k;

						boolean isElse = StringUtil.isEmpty(k, false); // 其它直接报错，不允许传 StringUtil.isEmpty(k, true) || "ELSE".equals(k);
//						String code = preCode + "\n\n" + (StringUtil.isEmpty(v, false) ? k : (isElse ? v : "if (" + k + ") {\n  " + v + "\n}"));
						String code = preCode + "\n\n" + (isElse ? v : "if (" + k + ") {\n  " + v + "\n}");

//						ScriptExecutor executor = new JavaScriptExecutor();
//						executor.execute(null, real, )

						engine.eval(code);

//						PARSER_CREATOR.createFunctionParser()
//								.setCurrentObject(real)
//								.setKey(k)
//								.setMethod(method)
//								.invoke()
						continue;
					}

					if (v instanceof Map<?, ?> == false) {
						throw new IllegalArgumentException("Request 表 structure 配置的 " + IF.name()
								+ ":{ " + k + ":value } 中 value 不合法，必须是 JSONRequest {} ！");
					}

					if (nkl.contains(k) || real.get(k) != null) {
						real = parse(method, name, (M) v, real, database, datasource, namespace, catalog, schema, idCallback, parser, callback);
					}
				}
			}
		}

		Log.i(TAG, "parse  return real = " + toJSONString(real));
		return real;
	}

	public static ScriptEngine getScriptEngine(String lang) {
		if (ENABLE_SCRIPT_FUNCTION == false) {
			throw new UnsupportedOperationException("AbstractFunctionParser.ENABLE_SCRIPT_FUNCTION" +
					" == false 时不支持执行脚本！如需支持则设置为 true ！");
		}

		boolean isEmpty = StringUtil.isEmpty(lang, true);
		ScriptEngine engine = isEmpty ? SCRIPT_ENGINE : SCRIPT_ENGINE_MANAGER.getEngineByName(lang);

		if (engine == null) {
			throw new NullPointerException("找不到可执行 " + (isEmpty ? "js" : lang) + " 脚本的引擎！engine == null!");
		}

		return engine;
	}


	/**执行操作
	 * @param opt
	 * @param targetChild
	 * @param real
	 * @param parser
	 * @return
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> M operate(Operation opt, M targetChild
            , M real, @NotNull Parser<T, M, L> parser) throws Exception {
		if (targetChild == null) {
			return real;
		}
		if (real == null) {
			throw new IllegalArgumentException("operate  real == null!!!");
		}

		Set<Map.Entry<String, Object>> set = new LinkedHashSet<>(targetChild.entrySet());
		for (Map.Entry<String, Object> e : set) {
			String tk = e == null ? null : e.getKey();
			if (tk == null || OPERATION_KEY_LIST.contains(tk)) {
				continue;
			}

			Object tv = e.getValue();

			if (opt == TYPE) {
				verifyType(tk, tv, real);
			}
			else if (opt == VERIFY) {
				verifyValue(tk, tv, real, parser);
			}
			else if (opt == UPDATE) {
				real.put(tk, tv);
			}
			else {
				if (real.containsKey(tk)) {
					if (opt == REPLACE) {
						real.put(tk, tv);
					}
				}
				else {
					if (opt == INSERT) {
						real.put(tk, tv);
					}
				}
			}
		}

		return real;
	}


	/**验证值类型
	 * @param tk
	 * @param tv {@link Operation}
	 * @param real
	 * @throws Exception
	 */
	public static void verifyType(@NotNull String tk, Object tv, @NotNull Map<String, Object> real)
            throws UnsupportedDataTypeException {
		if (tv instanceof String == false) {
			throw new UnsupportedDataTypeException("服务器内部错误，" + tk + ":value 的value不合法！"
					+ "Request表校验规则中 TYPE:{ key:value } 中的value只能是String类型！");
		}

		verifyType(tk, (String) tv, real.get(tk));
	}
	/**验证值类型
	 * @param tk
	 * @param tv {@link Operation}
	 * @param rv
	 * @throws Exception
	 */
	public static void verifyType(@NotNull String tk, @NotNull String tv, Object rv)
            throws UnsupportedDataTypeException {
		verifyType(tk, tv, rv, false);
	}
	/**验证值类型
	 * @param tk
	 * @param tv {@link Operation}
	 * @param rv
	 * @param isInArray
	 * @throws Exception
	 */
	public static void verifyType(@NotNull String tk, @NotNull String tv, Object rv, boolean isInArray)
            throws UnsupportedDataTypeException {
		if (rv == null) {
			return;
		}

		if (tv.endsWith("[]")) {
			verifyType(tk, "ARRAY", rv);

			for (Object o : (Collection<?>) rv) {
				verifyType(tk, tv.substring(0, tv.length() - 2), o, true);
			}

			return;
		}

		//这里不抽取 enum，因为 enum 不能满足扩展需求，子类需要可以自定义，而且 URL[] 这种也不符合命名要求，得用 constructor + getter + setter
		switch (tv) {
		case "BOOLEAN": //Boolean.parseBoolean(getString(real, tk)); 只会判断null和true
			if (rv instanceof Boolean == false) { //apijson.JSONMap.getBoolean 可转换Number类型
				throw new UnsupportedDataTypeException(tk + ":value 的value不合法！类型必须是 BOOLEAN" + (isInArray ? "[] !" : " !"));
			}
			break;
		case "NUMBER": //整数
			try {
				Long.parseLong(rv.toString()); //1.23会转换为1  real.getLong(tk);
			} catch (Exception e) {
				throw new UnsupportedDataTypeException(tk + ":value 的value不合法！类型必须是 NUMBER" + (isInArray ? "[] !" : " !"));
			}
			break;
		case "DECIMAL": //小数
			try {
				Double.parseDouble(rv.toString());
			} catch (Exception e) {
				throw new UnsupportedDataTypeException(tk + ":value 的value不合法！" +
                        "类型必须是 DECIMAL" + (isInArray ? "[] !" : " !"));
			}
			break;
		case "STRING":
			if (rv instanceof String == false) { //apijson.JSONMap.getString 可转换任何类型
				throw new UnsupportedDataTypeException(tk + ":value 的value不合法！" +
                        "类型必须是 STRING" + (isInArray ? "[] !" : " !"));
			}
			break;
		case "URL": //网址，格式为 http://www.apijson.org, https://www.google.com 等
			try {
				new URL((String) rv);
			} catch (Exception e) {
				throw new UnsupportedDataTypeException(tk + ":value 的value不合法！" +
                        "类型必须是 URL" + (isInArray ? "[] !" : " !"));
			}
			break;
		case "DATE": //日期，格式为 YYYY-MM-DD（例如 2020-02-20）的 STRING
			try {
				LocalDate.parse((String) rv);
			} catch (Exception e) {
				throw new UnsupportedDataTypeException(tk + ":value 的value不合法！" +
                        "类型必须是格式为 YYYY-MM-DD（例如 2020-02-20）的 DATE" + (isInArray ? "[] !" : " !"));
			}
			break;
		case "TIME": //时间，格式为 HH:mm:ss（例如 12:01:30）的 STRING
			try {
				LocalTime.parse((String) rv);
			} catch (Exception e) {
				throw new UnsupportedDataTypeException(tk + ":value 的value不合法！" +
                        "类型必须是格式为 HH:mm:ss（例如 12:01:30）的 TIME" + (isInArray ? "[] !" : " !"));
			}
			break;
		case "DATETIME": //日期+时间，格式为 YYYY-MM-DDTHH:mm:ss（例如 2020-02-20T12:01:30）的 STRING
			try {
				LocalDateTime.parse((String) rv);
			} catch (Exception e) {
				throw new UnsupportedDataTypeException(tk + ":value 的value不合法！类型必须是格式为 " +
                        "YYYY-MM-DDTHH:mm:ss（例如 2020-02-20T12:01:30）的 DATETIME" + (isInArray ? "[] !" : " !"));
			}
			break;
		case "OBJECT":
			if (rv instanceof Map == false) { //apijson.JSONMap.getJSONObject 可转换String类型
				throw new UnsupportedDataTypeException(tk + ":value 的value不合法！" +
                        "类型必须是 OBJECT" + (isInArray ? "[] !" : " !") + " OBJECT 结构为 {} !");
			}
			break;
		case "ARRAY":
			if (rv instanceof Collection == false) { //apijson.JSONMap.getJSONArray 可转换String类型
				throw new UnsupportedDataTypeException(tk + ":value 的value不合法！" +
                        "类型必须是 ARRAY" + (isInArray ? "[] !" : " !") + " ARRAY 结构为 [] !");
			}
			break;
			//目前在业务表中还用不上，单一的类型校验已经够用
			//		case "JSON":
			//			try {
			//				com.alibaba.fastjson.parseJSON(rv.toString());
			//			} catch (Exception e) {
			//				throw new UnsupportedDataTypeException(tk + ":value 的value不合法！类型必须是 JSON ！"
			//						+ "也就是 {Object}, [Array] 或 它们对应的字符串 '{Object}', '[Array]' 4种中的一个 !");
			//			}
			//			break;
		default:
			throw new UnsupportedDataTypeException(
					"服务器内部错误，类型 " + tv + " 不合法！Request表校验规则中 TYPE:{ key:value } 中的 value 必须是"
							+ " [ BOOLEAN, NUMBER, DECIMAL, STRING, URL, DATE, TIME, DATETIME, OBJECT, ARRAY ] 或它们的数组"
							+ " [ BOOLEAN[], NUMBER[], DECIMAL[], STRING[], URL[], DATE[], TIME[], DATETIME[], OBJECT[], ARRAY[] ] 中的一个!");
		}

	}


	/**验证值
	 * @param tk
	 * @param tv
	 * @param real
	 * @param parser
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> void verifyValue(@NotNull String tk
			, @NotNull Object tv, @NotNull M real, @NotNull Parser<T, M, L> parser) throws Exception {
		if (tv == null) {
			throw new IllegalArgumentException("operate  operate == VERIFY " + tk + ":" + tv + " ,  >> tv == null!!!");
		}

		String rk;
		Object rv;
		Logic logic;
		if (tk.endsWith("$")) {  // 模糊搜索
			verifyCondition("$", real, tk, tv, parser);
		}
		else if (tk.endsWith("~")) {  // 正则匹配
			logic = new Logic(tk.substring(0, tk.length() - 1));
			rk = logic.getKey();
			rv = real.get(rk);
			if (rv == null) {
				return;
			}

			L array = AbstractSQLConfig.newJSONArray(tv);

			boolean m;
			boolean isOr = false;
			Pattern reg;
			for (Object r : array) {
				if (r instanceof String == false) {
					throw new UnsupportedDataTypeException(rk + ":" + rv + " 中value只支持 String 或 [String] 类型！");
				}
				reg = COMPILE_MAP.get(r);
				if (reg == null) {
					reg = Pattern.compile((String) r);
				}
				m = reg.matcher("" + rv).matches();
				if (m) {
					if (logic.isNot()) {
						throw new IllegalArgumentException(rk + ":value 中value不合法！必须匹配 " + tk + ":" + tv + " !");
					}
					if (logic.isOr()) {
						isOr = true;
						break;
					}
				} else {
					if (logic.isAnd()) {
						throw new IllegalArgumentException(rk + ":value 中value不合法！必须匹配 " + tk + ":" + tv + " !");
					}
				}
			}

			if (isOr == false && logic.isOr()) {
				throw new IllegalArgumentException(rk + ":value 中value不合法！必须匹配 " + tk + ":" + tv + " !");
			}
		}
		else if (tk.endsWith("{}")) { //rv符合tv条件或在tv内
			if (tv instanceof String) { //TODO  >= 0, < 10
				verifyCondition("{}", real, tk, tv, parser);
			}
			else if (tv instanceof List<?>) {
				logic = new Logic(tk.substring(0, tk.length() - 2));
				rk = logic.getKey();
				rv = real.get(rk);
				if (rv == null) {
					return;
				}

				if (((L) tv).contains(rv) == logic.isNot()) {
					throw new IllegalArgumentException(rk + ":value 中value不合法！必须匹配 " + tk + ":" + tv + " !");
				}
			}
			else {
				throw new UnsupportedDataTypeException("服务器Request表verify配置错误！");
			}
		}
		else if (tk.endsWith("<>")) { //rv包含tv内的值
			logic = new Logic(tk.substring(0, tk.length() - 2));
			rk = logic.getKey();
			rv = real.get(rk);
			if (rv == null) {
				return;
			}

			if (rv instanceof Collection<?> == false) {
				throw new UnsupportedDataTypeException("服务器Request表verify配置错误！");
			}

			L array = AbstractSQLConfig.newJSONArray(tv);

			boolean isOr = false;
			for (Object o : array) {
				if (((L) rv).contains(o)) {
					if (logic.isNot()) {
						throw new IllegalArgumentException(rk + ":value 中value不合法！必须匹配 " + tk + ":" + tv + " !");
					}
					if (logic.isOr()) {
						isOr = true;
						break;
					}
				} else {
					if (logic.isAnd()) {
						throw new IllegalArgumentException(rk + ":value 中value不合法！必须匹配 " + tk + ":" + tv + " !");
					}
				}
			}

			if (isOr == false && logic.isOr()) {
				throw new IllegalArgumentException(rk + ":value 中value不合法！必须匹配 " + tk + ":" + tv + " !");
			}
		}
		else {
			throw new IllegalArgumentException("服务器Request表verify配置错误！");
		}
	}

	/**校验字符串长度
	 * @param rule	规则
	 * @param len	长度
	 * @return
	 * @throws UnsupportedDataTypeException
	 */
	public static boolean verifyLength(String rule, int len) throws UnsupportedDataTypeException {
		String first = null;
		String second = null;
		Matcher matcher = VERIFY_LENGTH_PATTERN.matcher(rule);
		while (matcher.find()) {
			first = StringUtil.isEmpty(first) ? matcher.group("first") : first;
			second = StringUtil.isEmpty(second) ? matcher.group("second") : second;
		}
		// first和second为空表示规则不合法
		if (StringUtil.isEmpty(first) || StringUtil.isEmpty(second)) {
			throw new UnsupportedDataTypeException("服务器Request表verify配置错误！");
		}

		int secondNum = Integer.parseInt(second);
		switch (Objects.requireNonNull(first)) {
			case ">=":
				return len >= secondNum;
			case "<=":
				return len <= secondNum;
			case "!=":
				return len != secondNum;
			case ">":
				return len > secondNum;
			case "<":
				return len < secondNum;
			case "=":
				return len == secondNum;
		}

		// 出现不能识别的符号也认为规则不合法
		throw new UnsupportedDataTypeException("服务器Request表verify配置错误！");
	}

	/**通过数据库执行SQL语句来验证条件
	 * @param funChar
	 * @param real
	 * @param tk
	 * @param tv
	 * @param parser
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> void verifyCondition(
			@NotNull String funChar, @NotNull M real, @NotNull String tk, @NotNull Object tv
			, @NotNull Parser<T, M, L> parser) throws Exception {
		// 不能用Parser, 0 这种不符合 StringUtil.isName !

		boolean isRange = "{}".equals(funChar);

		String k = tk.substring(0, tk.length() - funChar.length());
		Logic logic = new Logic(k);
		String rk = logic.getKey();
		boolean isLen = isRange && k.endsWith("[");
		boolean isJSOnLen = isRange && k.endsWith("{");
		k = isLen || isJSOnLen ? rk.substring(0, rk.length() - 1) : rk;
		Object rv = real.get(k);
		int len = 0;
		if (isLen) {
			len = StringUtil.length(rv, false);
		}
		else if (isJSOnLen) {
			rv = rv instanceof Map ? ((Map<?, ?>) rv) : (rv instanceof Collection ? ((Collection<?>) rv) : JSON.parse(rv));
			len = rv instanceof Map ? ((Map<?, ?>) rv).size() : (rv instanceof Collection ? ((Collection<?>) rv).size() : StringUtil.length(rv, false));
		}

		if (isLen || isJSOnLen) {
			String[] tvs = tv.toString().split(",");
			for (String tvItem : tvs) {
				if (! verifyLength(tvItem, len)) {
					throw new IllegalArgumentException(k + ":value 中value长度不合法！必须匹配 " + tk + ":" + tv + " !");
				}
			}

			return;
		}

		if (rv == null) {
			return;
		}

		if (rv instanceof String && ((String) rv).contains("'")) {  // || key.contains("#") || key.contains("--")) {
			throw new IllegalArgumentException(rk + ":value 中value不合法！value 中不允许有单引号 ' ！");
		}

		SQLConfig<T, M, L> config = parser.createSQLConfig().setMethod(GET).setCount(1).setPage(0);
		config.setTest(true);
		//		config.setTable(Test.class.getSimpleName());
		//		config.setColumn(rv + logic.getChar() + funChar)
		// 字符串可能 SQL 注入，目前的解决方式是加 TYPE 校验类型或者干脆不用 sqlVerify，而是通过远程函数来校验
		config.putWhere(rv + logic.getChar() + funChar, tv, false);
		config.setCount(1);

		SQLExecutor<T, M, L> executor = parser.createSQLExecutor(); // close 后复用导致不好修复的 NPE getSQLExecutor();
		executor.setParser(parser);
		M result;
		try {
			result = executor.execute(config, false);
		} finally {
			executor.close();
		}

		if (result != null && JSONResponse.isExist(result) == false) {
			throw new IllegalArgumentException(rk + ":value 中value不合法！必须匹配 '" + tk + "': '" + tv + "' ！");
		}
	}


	/**验证是否存在
	 * @param table
	 * @param key
	 * @param value
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>>void verifyExist(String table, String key
			, Object value, long exceptId, @NotNull Parser<T, M, L> parser) throws Exception {
		if (key == null || value == null) {
			Log.e(TAG, "verifyExist  key == null || value == null >> return;");
			return;
		}
		if (value instanceof JSON) {
			throw new UnsupportedDataTypeException(key + ":value 中value的类型不能为JSON！");
		}
		Map<String,Object> map = new HashMap<>();
		map.put(key,value);
		verifyExist(table,map,exceptId,parser);
	}

	/**验证是否存在
	 * @param table
	 * @param param
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> void verifyExist(String table
			, Map<String,Object> param, long exceptId, @NotNull Parser<T, M, L> parser) throws Exception {
		if (param.isEmpty()) {
			Log.e(TAG, "verifyExist is empty >> return;");
			return;
		}

		SQLConfig<T, M, L> config = parser.createSQLConfig().setMethod(HEAD).setCount(1).setPage(0);
		config.setTable(table);
		param.forEach((key,value) -> config.putWhere(key, value, false));

		SQLExecutor<T, M, L> executor = parser.createSQLExecutor();
		executor.setParser(parser);
		try {
			M result = executor.execute(config, false);
			if (result == null) {
				throw new Exception("服务器内部错误  verifyExist  result == null");
			}
			if (getIntValue(result, JSONResponse.KEY_COUNT) <= 0) {
				StringBuilder sb = new StringBuilder();
				param.forEach((key,value) -> sb.append("key:").append(key).append(" value:").append(value).append(" "));
				throw new ConflictException(sb + "的数据不存在！如果必要请先创建！");
			}
		} finally {
			executor.close();
		}
	}

	/**验证是否重复
	 * @param table
	 * @param key
	 * @param value
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> void verifyRepeat(String table, String key
			, Object value, @NotNull Parser<T, M, L> parser) throws Exception {
		verifyRepeat(table, key, value, 0, parser);
	}

	/**验证是否重复
	 * @param table
	 * @param key
	 * @param value
	 * @param exceptId 不包含id
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> void verifyRepeat(String table, String key
			, Object value, long exceptId, @NotNull Parser<T, M, L> parser) throws Exception {
		verifyRepeat(table, key, value, exceptId, null, parser);
	}

	/**验证是否重复
	 * TODO 与 AbstractVerifier.verifyRepeat 代码重复，需要简化
	 * @param table
	 * @param key
	 * @param value
	 * @param exceptId 不包含id
	 * @param idKey
	 * @param parser
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>>void verifyRepeat(String table, String key
			, Object value, long exceptId, String idKey, @NotNull Parser<T, M, L> parser) throws Exception {
		if (key == null || value == null) {
			Log.e(TAG, "verifyRepeat  key == null || value == null >> return;");
			return;
		}
		if (value instanceof JSON) {
			throw new UnsupportedDataTypeException(key + ":value 中value的类型不能为JSON！");
		}
		Map<String,Object> map = new HashMap<>();
		map.put(key,value);
		verifyRepeat(table, map, exceptId, idKey, parser);
	}

	/**验证是否重复
	 * TODO 与 AbstractVerifier.verifyRepeat 代码重复，需要简化
	 * @param table
	 * @param param
	 * @param exceptId 不包含id
	 * @param idKey
	 * @param parser
	 * @throws Exception
	 */
	public static <T, M extends Map<String, Object>, L extends List<Object>> void verifyRepeat(String table
			, Map<String,Object> param, long exceptId, String idKey, @NotNull Parser<T, M, L> parser) throws Exception {
		if (param.isEmpty()) {
			Log.e(TAG, "verifyRepeat is empty >> return;");
			return;
		}

		String finalIdKey = StringUtil.isEmpty(idKey, false) ? KEY_ID : idKey;

		SQLConfig<T, M, L> config = parser.createSQLConfig().setMethod(HEAD).setCount(1).setPage(0);
		config.setTable(table);
		if (exceptId > 0) { //允许修改自己的属性为该属性原来的值
			config.putWhere(finalIdKey + "!", exceptId, false);
		}
		param.forEach((key,value) -> config.putWhere(key,value, false));

		SQLExecutor<T, M, L> executor = parser.createSQLExecutor();
		executor.setParser(parser);
		try {
			M result = executor.execute(config, false);
			if (result == null) {
				throw new Exception("服务器内部错误  verifyRepeat  result == null");
			}
			if (getIntValue(result, JSONResponse.KEY_COUNT) > 0) {
				StringBuilder sb = new StringBuilder();
				param.forEach((key,value) -> sb.append("key:").append(key).append(" value:").append(value).append(" "));
				throw new ConflictException(sb + "的数据已经存在，不能重复！");
			}
		} finally {
			executor.close();
		}
	}


	public static String getCacheKeyForRequest(String method, String tag) {
		return method + "/" + tag;
	}

}
