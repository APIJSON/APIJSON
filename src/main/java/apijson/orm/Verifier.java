/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import java.util.List;
import java.util.Map;

import apijson.*;

/**校验器(权限、请求参数、返回结果等)
 * @author Lemon
 */
public interface Verifier<T, M extends Map<String, Object>, L extends List<Object>> {


	/**验证权限是否通过
	 * @param config
	 * @return
	 * @throws Exception
	 */
	boolean verifyAccess(SQLConfig<T, M, L> config) throws Exception;


	/**校验请求使用的角色，角色不好判断，让访问者发过来角色名，OWNER,CONTACT,ADMIN等
	 * @param config
	 * @param table
	 * @param method
	 * @param role
	 * @return
	 * @throws Exception 
	 * @see {@link JSONMap#KEY_ROLE}
	 */
	void verifyRole(SQLConfig<T, M, L> config, String table, RequestMethod method, String role) throws Exception;

	/**登录校验
	 * @throws Exception
	 */
	void verifyLogin() throws Exception;
	/**管理员角色校验
	 * @throws Exception
	 */
	void verifyAdmin() throws Exception;



	/**验证是否重复
	 * @param table
	 * @param key
	 * @param value
	 * @throws Exception
	 */
	void verifyRepeat(String table, String key, Object value) throws Exception;
	
	/**验证是否重复
	 * @param table
	 * @param key
	 * @param value
	 * @param exceptId 不包含id
	 * @throws Exception
	 */
	void verifyRepeat(String table, String key, Object value, long exceptId) throws Exception;
	
	/**验证请求参数的数据和结构
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
	 * @return
	 * @throws Exception
	 */
	M verifyRequest(RequestMethod method, String name, M target, M request, int maxUpdateCount
		, String database, String datasource, String namespace, String catalog, String schema
	) throws Exception;

	/**验证返回结果的数据和结构
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
	M verifyResponse(
		RequestMethod method, String name, M target, M response,
		String database, String schema, @NotNull Parser<T, M, L> parser, OnParseCallback<T, M, L> callback
	) throws Exception;


	@NotNull
	Parser<T, M, L> createParser();

	Parser<T, M, L> getParser();
	Verifier<T, M, L> setParser(AbstractParser<T, M, L> parser);

	@NotNull
	Visitor<T> getVisitor();
	Verifier<T, M, L> setVisitor(@NotNull Visitor<T> visitor);
	
	String getVisitorIdKey(SQLConfig<T, M, L> config);

}
