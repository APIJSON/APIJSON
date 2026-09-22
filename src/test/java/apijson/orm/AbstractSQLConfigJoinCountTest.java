package apijson.orm;

import apijson.JSON;
import apijson.JSONParser;
import apijson.RequestMethod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 复现 issue #824：PostgreSQL + HEAD(count 分页) + JOIN 时，
 * LEFT JOIN 子查询 SELECT 只含副表关联键，缺 ON 引用的主表字段，PG 报 column not found。
 */
public class AbstractSQLConfigJoinCountTest {
	private static final String MAIN_TABLE = "Inventory";
	private static final String JOIN_TABLE = "Location_info";
	private static final String JOIN_TABLE2 = "Warehose_info";
	private static final String DATABASE = SQLConfig.DATABASE_POSTGRESQL;

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

	/**
	 * 复现 issue #824 的 count 分页 SQL（PostgreSQL 场景）：
	 * SELECT count(*) AS "count" FROM "Inventory" AS "Inventory"
	 * LEFT JOIN ( SELECT * FROM "Location_info" ) AS "Location_info"
	 *   ON "Location_info"."code" = "Inventory"."location_code"
	 * LEFT JOIN ( SELECT * FROM "Warehose_info" ) AS "Warehose_info"
	 *   ON "Warehose_info"."id" = "Location_info"."warehouse_id"
	 *
	 * main 分支已对 PostgreSQL 回退为 SELECT *（isMSQL 门控），子查询不缺列，PG 不再报错。
	 * 本用例断言该行为（回归保护）。
	 */
	@Test
	public void headJoinCountPostgreSQLFallsBackToSelectStar() throws Exception {
		String sql = buildHeadJoinSQL();

		System.out.println("Generated SQL:\n" + sql + "\n");

		// 断言：LEFT JOIN 子查询结构存在
		int leftJoin1 = sql.indexOf("LEFT JOIN ( SELECT");
		assertTrue("应为 LEFT JOIN 子查询结构: " + sql, leftJoin1 >= 0);

		// 副表1子查询部分（PG 应回退 SELECT *，天然包含 code + warehouse_id）
		String sub1 = extractSubquery(sql, "Location_info");
		System.out.println("Subquery 1: " + sub1);
		assertTrue("PG 下 Location_info 子查询应回退 SELECT *（含所有列，天然覆盖 ON 引用）: " + sub1,
				sub1.contains("SELECT *"));

		// 副表2子查询部分
		String sub2 = extractSubquery(sql, "Warehose_info");
		System.out.println("Subquery 2: " + sub2);
		assertTrue("PG 下 Warehose_info 子查询应回退 SELECT *: " + sub2,
				sub2.contains("SELECT *"));

		// ON 条件完整性
		assertTrue("ON 应引用 Location_info.code = Inventory.location_code: " + sql,
				sql.contains("\"Location_info\".\"code\" = \"Inventory\".\"location_code\""));
		assertTrue("ON 应引用 Warehose_info.id = Location_info.warehouse_id: " + sql,
				sql.contains("\"Warehose_info\".\"id\" = \"Location_info\".\"warehouse_id\""));
	}

	/**
	 * 回归：MySQL 下副表子查询只含自身关联键 code/id（isMSQL 门控逻辑）。
	 * 本用例断言当前行为，暴露 MySQL 多 join 交叉引用 bug（第二个 ON 引用
	 * Location_info.warehouse_id，但子查询没有该列 -> Unknown column）。
	 */
	@Test
	public void headJoinCountMySQLOnlySelectsSelfOnKey() throws Exception {
		String sql = buildHeadJoinSQLWithDatabase(SQLConfig.DATABASE_MYSQL);

		System.out.println("MySQL SQL:\n" + sql + "\n");

		String sub1 = extractSubquery(sql, "Location_info");
		assertTrue("MySQL 下 Location_info 子查询应包含 code: " + sub1, sub1.contains("`code`"));
		// 关键：MySQL 下子查询缺 warehouse_id，ON 引用它 → 复现 Unknown column
		assertTrue("MySQL 下 Location_info 子查询应包含 warehouse_id（issue #824 反向场景，被 ON 引用的列）: " + sub1,
				sub1.contains("`warehouse_id`"));
	}

	private static String buildHeadJoinSQL() throws Exception {
		return buildHeadJoinSQLWithDatabase(DATABASE);
	}

	private static String extractSubquery(String sql, String table) {
		String marker = "JOIN ( ";
		int joinIdx = sql.indexOf(marker);
		while (joinIdx >= 0) {
			int closeIdx = sql.indexOf(" ) ", joinIdx);
			if (closeIdx < 0) {
				break;
			}
			String sub = sql.substring(joinIdx + marker.length(), closeIdx);
			if (sub.contains(table)) {
				return sub;
			}
			joinIdx = sql.indexOf(marker, closeIdx);
		}
		return "";
	}

	private static String buildHeadJoinSQLWithDatabase(String database) throws Exception {
		// 主表
		Map<String, Object> mainRequest = new LinkedHashMap<>();

		// join 副表1：Location_info.code@ = /Inventory/location_code
		// 注意：request 中不能包含 @ 引用键（真实解析器把它们剥离进 onList），
		// 只把 onList 作为唯一关联信息来源（与 AbstractParser.onJoinParse 一致）
		Map<String, Object> joinRequest1 = new LinkedHashMap<>();

		// join 副表2：Warehose_info.id@ = /Location_info/warehouse_id
		Map<String, Object> joinRequest2 = new LinkedHashMap<>();

		// 组装 joinList（on 定义显式传入，与 AbstractParser.onJoinParse 剥离后的 onList 等价）
		List<Join<Long, Map<String, Object>, List<Object>>> joinList = new ArrayList<>();
		joinList.add(newJoin("</Location_info/code@", JOIN_TABLE, "code@", "/Inventory/location_code"));
		joinList.add(newJoin("</Warehose_info/id@", JOIN_TABLE2, "id@", "/Location_info/warehouse_id"));

		AbstractSQLConfig.Callback<Long, Map<String, Object>, List<Object>> callback =
				new AbstractSQLConfig.SimpleCallback<Long, Map<String, Object>, List<Object>>() {
					@Override
					public SQLConfig<Long, Map<String, Object>, List<Object>> getSQLConfig(RequestMethod method,
							String database, String datasource, String namespace, String catalog, String schema,
							String table) {
						return new AbstractSQLConfig<Long, Map<String, Object>, List<Object>>(method, table) {
							@Override
							public String gainDBVersion() {
								return "16.0";
							}

							@Override
							public String gainDBUri() {
								return "jdbc:postgresql://localhost/test";
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
				AbstractSQLConfig.newSQLConfig(RequestMethod.HEAD, MAIN_TABLE, null, mainRequest, null, false, callback);
		config.setDatabase(database);

		config = AbstractSQLConfig.parseJoin(RequestMethod.HEAD, config, joinList, callback);

		return config.gainSQL(false);
	}

	/**手工构建 Join（模拟 AbstractParser.onJoinParse 剥离 @ 引用键后的产物）
	 *  @param path join 路径 </Table/key@
	 *  @param table 副表
	 *  @param originKey 关联键（含 @）
	 *  @param targetPath 引用目标 /TargetTable/targetKey
	 */
	private static Join<Long, Map<String, Object>, List<Object>> newJoin(String path, String table,
			String originKey, String targetPath) throws Exception {
		Join<Long, Map<String, Object>, List<Object>> j = new Join<>();
		j.setPath(path);
		j.setJoinType("<");
		j.setTable(table);
		j.setRequest(new LinkedHashMap<>()); // 真实解析器剥离 @ 引用键，request 为空

		List<Join.On> onList = new ArrayList<>();
		Join.On on = new Join.On();
		// originKey = "code@" -> key = "code"（setKeyAndType 会剥掉 @）
		on.setKeyAndType("<", table, originKey);
		on.setOriginKey(originKey);
		on.setOriginValue(targetPath);

		// 解析 /TargetTable/targetKey
		String path0 = targetPath.startsWith("/") ? targetPath.substring(1) : targetPath;
		int idx = path0.lastIndexOf("/");
		String targetKey = idx < 0 ? path0 : path0.substring(idx + 1);
		String targetTableKey = idx < 0 ? "" : path0.substring(0, idx);
		// targetTableKey 可能带别名 TargetTable:alias
		int cIdx = targetTableKey.indexOf(":");
		String targetTable = cIdx < 0 ? targetTableKey : targetTableKey.substring(0, cIdx);
		String targetAlias = cIdx < 0 ? null : targetTableKey.substring(cIdx + 1);

		on.setTargetTableKey(targetTableKey);
		on.setTargetTable(targetTable);
		on.setTargetAlias(targetAlias);
		on.setTargetKey(targetKey);

		onList.add(on);
		j.setOnList(onList);

		return j;
	}
}
