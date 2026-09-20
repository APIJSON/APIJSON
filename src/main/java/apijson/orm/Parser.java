/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.List;
import java.util.Map;

import apijson.*;

/**解析器
 * @author Lemon
 */
public interface Parser<T, M extends Map<String, Object>, L extends List<Object>>
		extends ParserCreator<T, M, L>, VerifierCreator<T, M, L>, SQLCreator<T, M, L> {

	@NotNull
	Visitor<T> getVisitor();
	Parser<T, M, L> setVisitor(@NotNull Visitor<T> visitor);

	@NotNull
	RequestMethod getMethod();
	Parser<T, M, L> setMethod(@NotNull RequestMethod method);

	int getVersion();
	Parser<T, M, L> setVersion(int version);
	
	String getTag();
	Parser<T, M, L> setTag(String tag);

	M getRequest();
	Parser<T, M, L> setRequest(M request);

	Parser<T, M, L> setNeedVerify(boolean needVerify);
	
	boolean isNeedVerifyLogin();
	Parser<T, M, L> setNeedVerifyLogin(boolean needVerifyLogin);

	boolean isNeedVerifyRole();
	Parser<T, M, L> setNeedVerifyRole(boolean needVerifyRole);

	boolean isNeedVerifyContent();
	Parser<T, M, L> setNeedVerifyContent(boolean needVerifyContent);

	
	String parse(String request);
	String parse(M request);

	M parseResponse(String request);
	M parseResponse(M request);

	// 没必要性能还差 JSONRequest parseCorrectResponse(String table, JSONRequest response) throws Exception;


	M parseCorrectRequest() throws Exception;
	
	M parseCorrectRequest(RequestMethod method, String tag, int version, String name, M request,
			int maxUpdateCount, SQLCreator<T, M, L> creator) throws Exception;


	Map<String, Object> getStructure(String table, String method, String tag, int version) throws Exception;


	M onObjectParse(M request, String parentPath, String name, SQLConfig<T, M, L> arrayConfig, boolean isSubquery, M cache) throws Exception;

	L onArrayParse(M request, String parentPath, String name, boolean isSubquery, L cache) throws Exception;

	/**解析远程函数
	 * @param key
	 * @param function
	 * @param parentPath
	 * @param currentName
	 * @param currentObject
	 * @return
	 * @throws Exception
	 */
	Object onFunctionParse(String key, String function, String parentPath, String currentName, M currentObject, boolean containRaw) throws Exception;
	
	ObjectParser<T, M, L> createObjectParser(M request, String parentPath, SQLConfig<T, M, L> arrayConfig, boolean isSubquery, boolean isTable, boolean isArrayMainTable) throws Exception;

	int getMinQueryPage();
	int getMaxQueryPage();
	int getDefaultQueryCount();
	int getMaxQueryCount();
	int getMaxUpdateCount();
	int getMaxSQLCount();
	int getMaxObjectCount();
	int getMaxArrayCount();
	int getMaxQueryDepth();
	
	void putQueryResult(String path, Object result);


	Object getValueByPath(String valuePath);


	void onVerifyLogin() throws Exception;
	void onVerifyContent() throws Exception;
	void onVerifyRole(SQLConfig<T, M, L> config) throws Exception;
	
	M executeSQL(SQLConfig<T, M, L> config, boolean isSubquery) throws Exception;
	
	SQLExecutor<T, M, L> getSQLExecutor();
	Verifier<T, M, L> getVerifier();
	
	
	Boolean getGlobalFormat();
	String getGlobalRole();
	String getGlobalDatabase();
	String getGlobalDatasource();
	String getGlobalNamespace();
	String getGlobalCatalog();
	String getGlobalSchema();
	Boolean getGlobalExplain();
	String getGlobalCache();

	
	int getTransactionIsolation();
	void setTransactionIsolation(int transactionIsolation);
	
	void begin(int transactionIsolation);
	void rollback() throws SQLException;
	void rollback(Savepoint savepoint) throws SQLException;
	void commit() throws SQLException;
	void close();

	M newSuccessResult();
	M newErrorResult(Exception e);

}
