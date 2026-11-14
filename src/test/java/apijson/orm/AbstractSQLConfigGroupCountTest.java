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
 * 复现 issue #624：分组后再分页，total 拿到的是组内数量而不是分组后的总条数。
 *
 * HEAD 请求把 SELECT 改成 count(*) 后仍然保留 GROUP BY，
 * 生成 SELECT count(*) AS count FROM Vehicle GROUP BY vid,sdate，
 * 数据库返回的是每一组的数量（多行），而不是分组后的组数。
 *
 * 正确做法与 Oracle 一致：SELECT count(*) FROM ( SELECT count(*) ... GROUP BY ... ) 。
 */
public class AbstractSQLConfigGroupCountTest {
	private static final String TABLE = "Vehicle";
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
	public void countsGroupsInsteadOfRowsInEachGroupForMySQL() throws Exception {
		assertGroupedCountSelectsOuterCount(SQLConfig.DATABASE_MYSQL, "8.0.32");
	}

	@Test
	public void countsGroupsInsteadOfRowsInEachGroupForPostgreSQL() throws Exception {
		assertGroupedCountSelectsOuterCount(SQLConfig.DATABASE_POSTGRESQL, "14.7");
	}

	@Test
	public void countsGroupsInsteadOfRowsInEachGroupForSQLServer() throws Exception {
		assertGroupedCountSelectsOuterCount(SQLConfig.DATABASE_SQLSERVER, "2022");
	}

	@Test
	public void countsGroupsInsteadOfRowsInEachGroupForDB2() throws Exception {
		assertGroupedCountSelectsOuterCount(SQLConfig.DATABASE_DB2, "11.5");
	}

	@Test
	public void keepsPlainCountWhenThereIsNoGroup() throws Exception {
		String sql = buildHeadSQL(SQLConfig.DATABASE_MYSQL, "8.0.32", null);
		System.out.println("HEAD without @group: " + oneLine(sql));

		assertTrue("没有 @group 时不应包一层子查询: " + sql, !sql.contains("FROM ("));
		assertTrue("没有 @group 时应直接 count(*): " + sql, sql.contains("count(*)"));
		assertTrue("没有 @group 时不应出现 GROUP BY: " + sql, !sql.contains("GROUP BY"));
	}

	@Test
	public void keepsGroupByForGetRequests() throws Exception {
		String sql = buildSQL(RequestMethod.GET, SQLConfig.DATABASE_MYSQL, "8.0.32");
		System.out.println("GET with @group: " + oneLine(sql));

		assertTrue("GET 应保留分组字段: " + sql, sql.contains("vid"));
		assertTrue("GET 应保留 GROUP BY: " + sql, sql.contains("GROUP BY"));
		assertTrue("GET 不应改成 count(*): " + sql, !sql.contains("count(*)"));
	}

	private static void assertGroupedCountSelectsOuterCount(String database, String version)
			throws Exception {
		String sql = buildSQL(RequestMethod.HEAD, database, version);
		System.out.println("HEAD with @group on " + database + ": " + oneLine(sql));

		String flat = oneLine(sql).trim();

		assertTrue("分组后的 HEAD 应统计组数，需要外层 count(*): " + flat, flat.startsWith("SELECT count(*)"));
		assertTrue("分组后的 HEAD 应把原语句包成子查询: " + flat, flat.contains(" FROM (SELECT"));
		assertTrue("子查询应保留 GROUP BY: " + flat, flat.contains("GROUP BY"));
		// 外层不能有 GROUP BY，否则统计的仍然是组内数量
		assertTrue("外层 count(*) 之后不能再出现 GROUP BY: " + flat, flat.endsWith(") AS \"Vehicle\"")
				|| flat.endsWith(") AS `Vehicle`"));
	}

	private static String oneLine(String sql) {
		return sql.replaceAll("[\\r\\n]+", " ");
	}

	private static String buildSQL(RequestMethod method, String database, String version)
			throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put(JSONMap.KEY_GROUP, "vid,sdate");
		request.put(JSONMap.KEY_COLUMN, "vid;sdate;max(cnt):maxcnt");
		request.put(JSONMap.KEY_ORDER, "maxcnt-");

		SQLConfig<Long, Map<String, Object>, List<Object>> config =
				newSQLConfig(method, database, version, request);
		config.setCount(5);
		config.setPage(0);

		return config.gainSQL(false);
	}

	private static String buildHeadSQL(String database, String version, String group) throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		if (group != null) {
			request.put(JSONMap.KEY_GROUP, group);
		}

		SQLConfig<Long, Map<String, Object>, List<Object>> config =
				newSQLConfig(RequestMethod.HEAD, database, version, request);
		return config.gainSQL(false);
	}

	private static SQLConfig<Long, Map<String, Object>, List<Object>> newSQLConfig(RequestMethod method,
			String database, String version, Map<String, Object> request) throws Exception {
		AbstractSQLConfig.Callback<Long, Map<String, Object>, List<Object>> callback =
				new AbstractSQLConfig.SimpleCallback<Long, Map<String, Object>, List<Object>>() {
					@Override
					public SQLConfig<Long, Map<String, Object>, List<Object>> getSQLConfig(RequestMethod method,
							String database, String datasource, String namespace, String catalog, String schema,
							String table) {
						return newConfig(method, table, version);
					}
				};
		SQLConfig<Long, Map<String, Object>, List<Object>> config =
				AbstractSQLConfig.newSQLConfig(method, TABLE, null, request, null, false, callback);
		config.setDatabase(database);
		return config;
	}

	private static SQLConfig<Long, Map<String, Object>, List<Object>> newConfig(RequestMethod method,
			String table, final String version) {
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
}