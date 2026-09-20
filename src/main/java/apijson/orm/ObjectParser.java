/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import java.util.List;
import java.util.Map;

import apijson.*;

/**简化Parser，getObject和getArray(getArrayConfig)都能用
 * @author Lemon
 */
public interface ObjectParser<T, M extends Map<String, Object>, L extends List<Object>> {

	Parser<T, M, L> getParser();
	ObjectParser<T, M, L> setParser(Parser<T, M, L> parser);

	String getParentPath();
	ObjectParser<T, M, L> setParentPath(String parentPath);

	ObjectParser<T, M, L> setCache(M cache);
	M getCache();


	/**解析成员
	 * response重新赋值
	 * @param name
	 * @param isReuse 
	 * @return null or this
	 * @throws Exception
	 */
	ObjectParser<T, M, L> parse(String name, boolean isReuse) throws Exception;

	/**调用 parser 的 sqlExecutor 来解析结果
	 * @param method
	 * @param table
	 * @param alias
	 * @param request
	 * @param joinList
	 * @param isProcedure
	 * @return
	 * @throws Exception
	 */
	M parseResponse(RequestMethod method, String table, String alias, M request, List<Join<T, M, L>> joinList, boolean isProcedure) throws Exception;
	/**调用 parser 的 sqlExecutor 来解析结果
	 * @param config
	 * @param isProcedure
	 * @return
	 * @throws Exception
	 */
	M parseResponse(SQLConfig<T, M, L> config, boolean isProcedure) throws Exception;



	/**解析普通成员
	 * @param key
	 * @param value
	 * @return whether parse succeed
	 */
	boolean onParse(@NotNull String key, @NotNull Object value) throws Exception;

	/**解析子对象
	 * @param index
	 * @param key
	 * @param value
	 * @param cache SQL 结果缓存
	 * @return
	 * @throws Exception
	 */
	Object onChildParse(int index, String key, M value, Object cache) throws Exception;
	
	/**解析赋值引用
	 * @param path
	 * @return
	 */
	Object onReferenceParse(@NotNull String path);

	//TODO 改用 MySQL json_add,json_remove,json_contains 等函数！ 
	/**修改数组 PUT key:[]
	 * @param key
	 * @param array
	 * @throws Exception
	 */
	void onPUTArrayParse(@NotNull String key, @NotNull L array) throws Exception;

	/**批量新增或修改 POST or PUT  Table[]:[{}]
	 * @param key
	 * @param array
	 * @throws Exception
	 */
	void onTableArrayParse(@NotNull String key, @NotNull L array) throws Exception;

	/**SQL 配置，for single object
	 * @return {@link #setSQLConfig(int, int, int)}
	 * @throws Exception
	 */
	ObjectParser<T, M, L> setSQLConfig() throws Exception;

	/**SQL 配置
	 * @return 
	 * @throws Exception
	 */
	ObjectParser<T, M, L> setSQLConfig(int count, int page, int position) throws Exception;
	
	
	/**执行 SQL
	 * @return 
	 * @throws Exception
	 */
	ObjectParser<T, M, L> executeSQL() throws Exception;

	
	/**
	 * @return
	 * @throws Exception
	 */
	M onSQLExecute() throws Exception;
	
	
	/**
	 * @return response
	 * @throws Exception
	 */
	M response() throws Exception;

	void onFunctionResponse(String type) throws Exception;

	void onChildResponse() throws Exception;
	

	SQLConfig<T, M, L> newSQLConfig(boolean isProcedure) throws Exception;
	SQLConfig<T, M, L> newSQLConfig(RequestMethod method, String table, String alias, M request, List<Join<T, M, L>> joinList, boolean isProcedure) throws Exception;
	
	/**
	 * response has the final value after parse (and query if isTableKey)
	 */
	void onComplete();


	/**回收内存
	 */
	void recycle();


	ObjectParser<T, M, L> setMethod(RequestMethod method);
	RequestMethod getMethod();


	boolean isTable();
	String getPath();
	String getTable();
	String getAlias();
	SQLConfig<T, M, L> getArrayConfig();

	SQLConfig<T, M, L> getSQLConfig();
	M getResponse();
	M getSQLRequest();
	M getSQLResponse();

	Map<String, Object> getCustomMap();
	Map<String, Map<String, String>> getFunctionMap();
	Map<String, M> getChildMap();

}
