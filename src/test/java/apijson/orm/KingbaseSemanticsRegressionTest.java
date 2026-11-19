package apijson.orm;

import apijson.RequestMethod;
import java.math.BigDecimal;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regression cases for the five compatibility review findings. */
public class KingbaseSemanticsRegressionTest {
	@BeforeClass
	public static void parser() {
		KingbaseCompatibilityTest.installJsonParser();
	}

	static AbstractSQLConfig<Long, Map<String, Object>, List<Object>> config(String database) {
		AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c =
				new AbstractSQLConfig<Long, Map<String, Object>, List<Object>>(
						RequestMethod.GET, "User") {
					public String gainDBUri() {
						return "jdbc:test";
					}

					public String gainDBAccount() {
						return "test";
					}

					public String gainDBPassword() {
						return "unused";
					}

					public String gainDBVersion() {
						return "16.0";
					}

					@Override
					public String gainSQLDatabase() {
						return getDatabase();
					}
				};
		c.setDatabase(database);
		return c;
	}

	@Test
	public void appJoinPreservesStringIdentityAndPrefersExactMatches() {
		AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> e =
				new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {};
		SQLConfig<Long, Map<String, Object>, List<Object>> c = config("KINGBASE_MYSQL");
		assertEquals("1", e.getAppJoinCacheKeyValue(c, "1", Arrays.<Object>asList("01", "1")));
		assertEquals(
				"1", e.getAppJoinCacheKeyValue(c, "1", Collections.<Object>singletonList("01")));
		assertEquals(
				"01", e.getAppJoinCacheKeyValue(c, "01", Collections.<Object>singletonList(1L)));
		assertEquals(
				"1e0", e.getAppJoinCacheKeyValue(c, "1e0", Collections.<Object>singletonList("1")));
		Long numeric = 1L;
		assertSame(
				numeric,
				e.getAppJoinCacheKeyValue(
						c, new BigDecimal("1.000"), Collections.<Object>singletonList(numeric)));
		assertSame(
				numeric,
				e.getAppJoinCacheKeyValue(c, "1", Collections.<Object>singletonList(numeric)));
		BigDecimal exact = new BigDecimal("1.0");
		assertSame(exact, e.getAppJoinCacheKeyValue(c, exact, Arrays.<Object>asList(1L, exact)));
		assertEquals(
				exact,
				e.getAppJoinCacheKeyValue(
						config("MYSQL"), exact, Collections.<Object>singletonList(1L)));
	}

	private AbstractSQLConfig<Long, Map<String, Object>, List<Object>> aliasQuery(String order) {
		AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = config("KINGBASE_MYSQL");
		c.setColumn(Arrays.asList("id:n", "name"));
		Map<String, Object> having = new LinkedHashMap<>();
		having.put("h", "n>1");
		c.setHaving(having);
		c.setOrder(order);
		c.setCount(2);
		c.setPage(1);
		return c;
	}

	@Test
	public void aliasHavingUsesARealComparisonAndOuterPaginationOrder() throws Exception {
		for (String order : Arrays.asList("id+", "n+", "name-,id+")) {
			AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = aliasQuery(order);
			c.putWhere("name", "alice", false);
			String sql = c.gainSQL(true);
			assertTrue(sql, sql.contains("WHERE (`n` > 1)"));
			assertEquals(sql, sql.indexOf("ORDER BY"), sql.lastIndexOf("ORDER BY"));
			assertTrue(sql, sql.indexOf("ORDER BY") > sql.indexOf("_apijson_having"));
			assertTrue(sql, sql.endsWith("LIMIT 2 OFFSET 2"));
			assertEquals(Collections.singletonList("alice"), c.getPreparedValueList());
			assertTrue(
					sql,
					sql.contains(
							order.startsWith("name")
									? "ORDER BY `name` DESC, `n` ASC"
									: "ORDER BY `n` ASC"));
			// Reusing the same config must not accumulate parameters or mutate @order.
			assertEquals(sql, c.gainSQL(true));
			assertEquals(order, c.getOrder());
		}
	}

	@Test
	public void aliasHavingArithmeticAndCombineRemainValidated() throws Exception {
		AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = aliasQuery("id-");
		c.setColumn(Arrays.asList("id:n", "age:a"));
		Map<String, Object> having = new LinkedHashMap<>();
		having.put("first", "(n+1)>=2");
		having.put("second", "a>-1e2");
		c.setHaving(having);
		c.setHavingCombine("first & second");
		String sql = c.gainSQL(true);
		assertTrue(sql, sql.contains("(`n` + 1) >= 2"));
		assertTrue(sql, sql.contains("`a` > (-1e2)"));
		assertTrue(sql, c.getPreparedValueList().isEmpty());
		for (String invalid :
				Arrays.asList("n>1;DROP TABLE User", "n>1--", "n>unknown", "n>1 OR 1=1")) {
			c.setHaving(Collections.<String, Object>singletonMap("h", invalid));
			c.setHavingCombine(null);
			assertThrows(IllegalArgumentException.class, () -> c.gainSQL(true));
		}
	}

	@Test
	public void unavailableWrapperOrderIsProjectedInternallyWithoutChangingTheResponse() throws Exception {
		AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = aliasQuery("age+");
		String sql = c.gainSQL(true);
		assertTrue(sql, sql.startsWith("SELECT `n`, `name` FROM ("));
		assertTrue(sql, sql.contains("`age` AS `_apijson_order_0`"));
		assertTrue(sql, sql.contains("ORDER BY `_apijson_order_0` ASC"));
		assertEquals(sql, c.gainSQL(true));
	}

	@Test
	public void nativeMysqlHavingAndOrderKeepTheirExistingSql() throws Exception {
		AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = aliasQuery("id+");
		c.setDatabase("MYSQL");
		c.setHaving(Collections.<String, Object>singletonMap("h", "count(id)>1"));
		String sql = c.gainSQL(true);
		assertTrue(sql, sql.contains(" HAVING (count(`id`)>1)"));
		assertTrue(sql, sql.contains("ORDER BY `id` ASC"));
		assertFalse(sql, sql.contains("_apijson_having"));
	}

	@Test
	public void regexAdaptationKeepsNativeSqlServerUnchanged() {
		AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = config("SQLSERVER");
		c.setPrepared(true);
		assertEquals("\"name\" REGEXP BINARY ?", c.gainRegExpString("name", "name", "A.*", false));
		assertEquals("\"name\" REGEXP ?", c.gainRegExpString("name", "name", "A.*", true));
		assertEquals(Arrays.asList("A.*", "A.*"), c.getPreparedValueList());
		assertTrue(
				config("KINGBASE_SQLSERVER")
						.gainRegExpString("name", "name", "A.*", false)
						.contains(" ~ "));
		assertTrue(
				config("KINGBASE_SQLSERVER")
						.gainRegExpString("name", "name", "A.*", true)
						.contains(" ~* "));
	}

	@Test
	public void dateFormatsTranslateMinutesSecondsFractionsAndLiteralText() throws Exception {
		for (String db : Collections.singletonList("KINGBASE_SQLSERVER")) {
			boolean kb = db.startsWith("KINGBASE");
			AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = config(db);
			c.setColumn(
					Collections.singletonList("date_format(created,'%Y-%m-%dT%H:%i:%s.%f %%')"));
			String sql = c.gainSQL(true);
			String expected =
					kb
							? "YYYY\"-\"MM\"-\"DD\"T\"HH24\":\"MI\":\"SS\".\"US\" %\""
							: "yyyy\"-\"MM\"-\"dd\"T\"HH\":\"mm\":\"ss\".\"ffffff\" %\"";
			assertEquals(Collections.singletonList(expected), c.getPreparedValueList());
			assertFalse(sql, sql.contains("REPLACE("));
			assertTrue(sql, sql.contains(kb ? "to_char(" : "FORMAT("));
			assertEquals(
					kb ? "HH12\":\"MI\":\"SS\" \"AM" : "hh\":\"mm\":\"ss\" \"tt",
					SQLServerDateFormat.translate("%I:%i:%S %p", kb));
			assertEquals("\"\"", SQLServerDateFormat.translate("", kb));
			assertEquals(
					"\"yyyy is literal %Y\"",
					SQLServerDateFormat.translate("yyyy is literal %%Y", kb));
			c.setColumn(Collections.singletonList("date_format(created,'%Q')"));
			assertThrows(UnsupportedOperationException.class, () -> c.gainSQL(true));
			c.setColumn(Collections.singletonList("date_format(created,formatColumn)"));
			assertThrows(UnsupportedOperationException.class, () -> c.gainSQL(true));
		}
		assertThrows(
				IllegalArgumentException.class, () -> SQLServerDateFormat.translate("%", false));
		AbstractSQLConfig<Long, Map<String, Object>, List<Object>> nativeMysql = config("MYSQL");
		nativeMysql.setColumn(
				Collections.singletonList("date_format(created,'%Y-%m-%d %H:%i:%s')"));
		assertTrue(nativeMysql.gainSQL(true).contains("date_format(`created`,?)"));
		assertEquals(
				Collections.singletonList("%Y-%m-%d %H:%i:%s"), nativeMysql.getPreparedValueList());
	}

	@Test
	public void jsonLengthPreservesObjectsScalarsAndOptionalPathContract() throws Exception {
		for (String db : Arrays.asList("MYSQL", "KINGBASE_MYSQL")) {
			AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = config(db);
			c.setColumn(
					Arrays.asList(
							"json_length(payload):n", "json_length(payload,'$.items'):items"));
			String sql = c.gainSQL(true);
			assertTrue(sql, sql.contains("json_length(`payload`) AS `n`"));
			assertTrue(sql, sql.contains("json_length(`payload`,?) AS `items`"));
			assertFalse(sql, sql.contains("json_array_length"));
			assertEquals(Collections.singletonList("$.items"), c.getPreparedValueList());
			assertEquals("json_length(`payload`)", c.gainKey("payload{"));
		}
	}
}
