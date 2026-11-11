package apijson.orm;

import apijson.JSON;
import apijson.JSONMap;
import apijson.JSONParser;
import apijson.RequestMethod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertTrue;

/**
 * 复现 issue #648：SQL Server 查询聚合函数出错。
 *
 * 请求 { "Moment": { "@column": "max(date):date" } } 只返回一行，
 * 但 SQL Server/DB2 的 OFFSET FETCH 要求 ORDER BY，gainOrderString 会补上
 * ORDER BY "id"，生成
 *   SELECT max("date") AS "date" FROM "sys"."Moment" ORDER BY "id" OFFSET 0 ROWS FETCH FIRST 10 ROWS ONLY
 * 数据库报 order by 子句列 id 无效，因为该列没有包含在聚合函数或 group by 子句中。
 */
public class AbstractSQLConfigAggregatePagingTest {
	private static final String TABLE = "Moment";
	private static JSONParser<? extends Map<String, Object>, ? extends List<Object>> previousJSONParser;

	@BeforeClass
	public static void installJSONParser() {
		previousJSONParser = JSON.DEFAULT_JSON_PARSER;
		JSON.DEFAULT_JSON_PARSER = new JSONParser<Map<String, Object>, List<Object>>() {
			@Override
			public Map<String, Object> createJSONObject() {
				return new LinkedHashMap<>();
			}

			@Override
			public List<Object> createJSONArray() {
				return new ArrayList<>();
			}

			@Override
			public Object parse(Object json) {
				return json;
			}

			@Override
			@SuppressWarnings("unchecked")
			public Map<String, Object> parseObject(Object json) {
				return (Map<String, Object>) json;
			}

			@Override
			public <T> T parseObject(Object json, Class<T> clazz) {
				return clazz.cast(json);
			}

			@Override
			@SuppressWarnings("unchecked")
			public List<Object> parseArray(Object json) {
				return (List<Object>) json;
			}

			@Override
			@SuppressWarnings("unchecked")
			public <T> List<T> parseArray(Object json, Class<T> clazz) {
				return (List<T>) json;
			}

			@Override
			public String toJSONString(Object obj, boolean format) {
				return String.valueOf(obj);
			}
		};
	}

	@AfterClass
	public static void restoreJSONParser() {
		JSON.DEFAULT_JSON_PARSER = previousJSONParser;
	}

	@Test
	public void skipsOrderByForSQLServerAggregate() throws Exception {
		assertNoOrderBy(sql(SQLConfig.DATABASE_SQLSERVER, "2022", 10));
	}

	@Test
	public void skipsOrderByForSQLServerAggregateWithDefaultPage() throws Exception {
		assertNoOrderBy(sql(SQLConfig.DATABASE_SQLSERVER, "2022", 1));
	}

	@Test
	public void skipsOrderByForDB2Aggregate() throws Exception {
		assertNoOrderBy(sql(SQLConfig.DATABASE_DB2, "11.5", 10));
	}

	@Test
	public void skipsOrderByForKingbaseSQLServerAggregate() throws Exception {
		assertNoOrderBy(sql(SQLConfig.DATABASE_KINGBASE_SQLSERVER, "V8", 10));
	}

	@Test
	public void keepsOrderByForSQLServerNormalColumn() throws Exception {
		String sql = sqlWithColumn(SQLConfig.DATABASE_SQLSERVER, "2022", "date", 10);
		System.out.println("SQL Server plain column: " + flat(sql));

		assertTrue("普通字段分页仍必须补 ORDER BY，否则 OFFSET FETCH 非法: " + sql,
				sql.contains("ORDER BY"));
		assertTrue("普通字段分页仍应带 OFFSET FETCH: " + sql, sql.contains("OFFSET"));
	}

	@Test
	public void keepsOrderByForSQLServerAggregateWithGroup() throws Exception {
		String sql = sqlWithGroup(SQLConfig.DATABASE_SQLSERVER, "2022", 10);
		System.out.println("SQL Server aggregate with @group: " + flat(sql));

		assertTrue("带 GROUP BY 的聚合可以按分组字段排序，应保留 ORDER BY: " + sql, sql.contains("ORDER BY"));
	}

	private static void assertNoOrderBy(String sql) {
		String flat = flat(sql);
		System.out.println("aggregate paging: " + flat);

		assertTrue("聚合函数不带 GROUP BY 时不应补 ORDER BY，否则列不在聚合函数内会报错: " + flat,
				!flat.contains("ORDER BY"));
		assertTrue("聚合函数不带 GROUP BY 时不应再拼 OFFSET FETCH: " + flat, !flat.contains("OFFSET"));
		assertTrue("仍应保留聚合函数本身: " + flat, flat.contains("max("));
	}

	private static String flat(String sql) {
		return sql.replaceAll("[\\r\\n]+", " ");
	}

	private static String sql(String database, String version, int count) throws Exception {
		return sqlWithColumn(database, version, null, count);
	}

	private static String sqlWithColumn(String database, String version, String column, int count)
			throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put(JSONMap.KEY_COLUMN, column == null ? "max(date):date" : column);

		return build(request, database, version, count);
	}

	private static String sqlWithGroup(String database, String version, int count) throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put(JSONMap.KEY_COLUMN, "max(date):date");
		request.put(JSONMap.KEY_GROUP, "userId");

		return build(request, database, version, count);
	}

	private static String build(Map<String, Object> request, String database, String version, int count)
			throws Exception {
		AbstractSQLConfig.Callback<Long, Map<String, Object>, List<Object>> callback =
				new AbstractSQLConfig.SimpleCallback<Long, Map<String, Object>, List<Object>>() {
					@Override
					public SQLConfig<Long, Map<String, Object>, List<Object>> getSQLConfig(RequestMethod method,
							String database, String datasource, String namespace, String catalog, String schema,
							String table) {
						return new AbstractSQLConfig<Long, Map<String, Object>, List<Object>>(method, table) {
							@Override
							public String gainDBVersion() {
								return version;
							}

							@Override
							public String gainDBUri() {
								return "jdbc:test://localhost/test";
							}

							@Override
							public String gainDBAccount() {
								return "test";
							}

							@Override
							public String gainDBPassword() {
								return "test";
							}
						};
					}
				};

		SQLConfig<Long, Map<String, Object>, List<Object>> config =
				AbstractSQLConfig.newSQLConfig(RequestMethod.GET, TABLE, null, request, null, false, callback);
		config.setDatabase(database);
		config.setCount(count);
		config.setPage(0);
		return config.gainSQL(false);
	}
}
