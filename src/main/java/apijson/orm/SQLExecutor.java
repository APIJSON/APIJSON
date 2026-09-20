/*Copyright (C) 2026 the APIJSON group.  All rights reserved.

This source code is licensed under the Apache License Version 2.0.*/


package apijson.orm;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import apijson.*;

/**executor for query(read) or update(write) MySQL database
 * @author Lemon
 */
public interface SQLExecutor<T, M extends Map<String, Object>, L extends List<Object>> {
	Parser<T, M, L> getParser();
	SQLExecutor<T, M, L> setParser(Parser<T, M, L> parser);

	/**保存缓存
	 * @param sql
	 * @param list
	 * @param config
	 */
	void putCache(String sql, List<M> list, SQLConfig<T, M, L> config);

	/**获取缓存
	 * @param sql
	 * @param config
	 * @return
	 */
	List<M> getCache(String sql, SQLConfig<T, M, L> config);

	/**获取缓存
	 * @param sql
	 * @param position
	 * @param config
	 * @return
	 */
	M getCacheItem(String sql, int position, SQLConfig<T, M, L> config);

	/**移除缓存
	 * @param sql
	 * @param config
	 */
	void removeCache(String sql, SQLConfig<T, M, L> config);

	/**执行SQL
	 * @param config
	 * @param unknownType
	 * @return
	 * @throws Exception
	 */
	M execute(@NotNull SQLConfig<T, M, L> config, boolean unknownType) throws Exception;

	//executeQuery和executeUpdate这两个函数因为返回类型不同，所以不好合并
	/**执行查询
	 * @param config
	 * @return
	 * @throws SQLException
	 */
	default ResultSet executeQuery(@NotNull SQLConfig<T, M, L> config) throws Exception {
		return executeQuery(config, null);
	}
	ResultSet executeQuery(@NotNull SQLConfig<T, M, L> config, String sql) throws Exception;

	/**执行增、删、改
	 * @param config
	 * @return
	 * @throws SQLException
	 */
	default int executeUpdate(@NotNull SQLConfig<T, M, L> config) throws Exception {
		return executeUpdate(config, null);
	}
	int executeUpdate(@NotNull SQLConfig<T, M, L> config, String sql) throws Exception;


	/**判断是否为JSON类型
	* @param config
	* @param rsmd
	* @param position
	* @param label
	* @return
	*/
	boolean isJSONType(@NotNull SQLConfig<T, M, L> config, ResultSetMetaData rsmd, int position, String label);

	/**
	 * Convert a JDBC value to a JSON-serializable Java value. Database adapters can
	 * override this hook without replacing the complete result-set traversal.
	 */
	default Object mapResultValue(@NotNull SQLConfig<T, M, L> config, Object value, int jdbcType
			, String typeName, String label) throws Exception {
		return value;
	}


	Connection getConnection(@NotNull SQLConfig<T, M, L> config) throws Exception;
	default Statement getStatement(@NotNull SQLConfig<T, M, L> config) throws Exception {
		return getStatement(config, null);
	}
	Statement getStatement(@NotNull SQLConfig<T, M, L> config, String sql) throws Exception;

	int getTransactionIsolation();
	void setTransactionIsolation(int transactionIsolation);
	/**开始事务
	 * @throws SQLException
	 */
	void begin(int transactionIsolation) throws SQLException;
	/**回滚事务
	 * @throws SQLException
	 */
	void rollback() throws SQLException;
	/**提交事务
	 * @throws SQLException
	 */
	void rollback(Savepoint savepoint) throws SQLException;
	/**提交事务
	 * @throws SQLException
	 */
	void commit() throws SQLException;
	/**关闭连接，释放资源
	 */
	void close();

	ResultSet executeQuery(@NotNull Statement statement, String sql) throws Exception;

	int executeUpdate(@NotNull Statement statement, String sql) throws Exception;

	ResultSet execute(@NotNull Statement statement, String sql) throws Exception;

	int getGeneratedSQLCount();

	int getCachedSQLCount();

	int getExecutedSQLCount();

	long getExecutedSQLDuration();

	long getSqlResultDuration();

}
