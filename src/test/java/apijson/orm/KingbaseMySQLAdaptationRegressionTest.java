package apijson.orm;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/** Counterexamples for projection ownership, predicate placement and typed parameters. */
public class KingbaseMySQLAdaptationRegressionTest {
	@BeforeClass public static void parser() { KingbaseCompatibilityTest.installJsonParser(); }
	private AbstractSQLConfig<Long, Map<String,Object>, List<Object>> config(String db) {
		return KingbaseSemanticsRegressionTest.config(db);
	}

	@Test public void kingbaseMysqlUnknownResultsExecuteTheAlreadyPreparedCall() throws Exception {
		ResultSet result = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{ResultSet.class}, (p, m, a) -> null);
		AbstractSQLExecutor<Long, Map<String,Object>, List<Object>> executor =
				new AbstractSQLExecutor<Long, Map<String,Object>, List<Object>>() {};
		for (String db : Arrays.asList("KINGBASE_MYSQL", "MYSQL", "KINGBASE_ORACLE", "KINGBASE_SQLSERVER")) {
			List<String> calls = new ArrayList<>();
			PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[]{PreparedStatement.class}, (p, m, a) -> {
						if (m.getName().equals("execute")) {
							calls.add(a == null || a.length == 0 ? "prepared" : (String) a[0]);
							return true;
						}
						return m.getName().equals("getResultSet") ? result : null;
					});
			assertSame(result, executor.executeUnknownType(config(db), statement, "CALL test_proc(1)"));
			assertEquals(Collections.singletonList(db.equals("KINGBASE_MYSQL") ? "prepared" : "CALL test_proc(1)"), calls);
		}
	}

	@Test public void scalarHavingMovesOnlyWhenBothProjectionAndPredicateAreScalar() throws Exception {
		AbstractSQLConfig<Long, Map<String,Object>, List<Object>> c = config("KINGBASE_MYSQL");
		c.setColumn(Arrays.asList("id", "name"));
		c.setHaving(Collections.<String,Object>singletonMap("h", "length(name)>0"));
		String scalar = c.gainSQL(true);
		assertTrue(scalar, scalar.contains(" WHERE "));
		assertTrue(scalar, scalar.contains("octet_length(`name`)>0"));
		assertFalse(scalar, scalar.contains(" HAVING "));
		c.setColumn(Collections.singletonList("count(id):n"));
		assertTrue(c.gainSQL(true).contains(" HAVING "));
		c.setColumn(Collections.singletonList("id"));
		c.setHaving(Collections.<String,Object>singletonMap("h", "sum(id)>0"));
		assertTrue(c.gainSQL(true).contains(" HAVING "));
		assertTrue(KingbaseMySQLExpressions.hasAggregateOrWindow("SUM(id)", AbstractSQLConfig.SQL_AGGREGATE_FUNCTION_MAP.keySet()));
		assertFalse(KingbaseMySQLExpressions.hasAggregateOrWindow("concat('sum(id)')", AbstractSQLConfig.SQL_AGGREGATE_FUNCTION_MAP.keySet()));
		c.setDatabase("MYSQL");c.setHaving(Collections.<String,Object>singletonMap("h", "length(name)>0"));
		String nativeSql = c.gainSQL(true);
		assertTrue(nativeSql, nativeSql.contains(" HAVING "));
		assertTrue(nativeSql, nativeSql.contains("length(`name`)>0"));
		assertFalse(nativeSql, nativeSql.contains("octet_length("));
	}

	@Test public void hiddenSortColumnsDoNotLeakAndBindingsSurviveReuse() throws Exception {
		AbstractSQLConfig<Long, Map<String,Object>, List<Object>> c = config("KINGBASE_MYSQL");
		c.setColumn(Arrays.asList("id:n", "concat('x'):label"));
		c.setHaving(Collections.<String,Object>singletonMap("h", "n>1"));
		c.setOrder("name-,id+");c.setCount(2);c.setPage(1);c.putWhere("sex", 1, false);
		String sql = c.gainSQL(true);
		assertTrue(sql, sql.startsWith("SELECT `n`, `label` FROM ("));
		assertTrue(sql, sql.contains("ORDER BY `_apijson_order_0` DESC, `n` ASC"));
		assertTrue(sql, sql.endsWith("LIMIT 2 OFFSET 2"));
		assertEquals(Arrays.asList("x",1), c.getPreparedValueList());
		assertEquals(sql,c.gainSQL(true));assertEquals(Arrays.asList("x",1), c.getPreparedValueList());
		c.setDistinct(true);
		assertThrows(UnsupportedOperationException.class, () -> c.gainSQL(true));
	}

	@Test public void joinedMainGroupingUsesSourcesAndSkipsAggregates() throws Exception {
		AbstractSQLConfig<Long, Map<String,Object>, List<Object>> main = config("KINGBASE_MYSQL"), vice = config("KINGBASE_MYSQL");
		main.setColumn(Arrays.asList("id:uid", "length(name):n", "sum(sex):s"));
		vice.setGroup("id");
		Join<Long, Map<String,Object>, List<Object>> join = new Join<>();
		join.setJoinType("&");join.setTable("v");join.setJoinConfig(vice);
		main.setJoinList(Collections.singletonList(join));main.setPrepared(true);
		String group = main.gainGroupString(true);
		assertTrue(group,group.contains("`id`"));assertTrue(group,group.contains(", 2,"));
		assertFalse(group,group.contains("id:uid"));assertFalse(group,group.contains("sum("));
	}

	@Test public void mysqlJoinValuesUseTheOwningProjectionEvenForIdenticalAliases() throws Exception {
		AbstractSQLConfig<Long, Map<String,Object>, List<Object>> main=config("KINGBASE_MYSQL"), vice=config("KINGBASE_MYSQL");
		main.setColumn(Collections.singletonList("sum(id):v"));main.gainSQL(true);
		vice.setColumn(Collections.singletonList("count(id):v"));vice.gainSQL(true);
		Join<Long, Map<String,Object>, List<Object>> join=new Join<>();join.setJoinConfig(vice);
		ResultSet rs=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)-> m.getName().equals("getObject") ? 3L : null);
		ResultSetMetaData md=(ResultSetMetaData)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSetMetaData.class},(p,m,a)->{
			if (m.getName().equals("getColumnName")||m.getName().equals("getColumnLabel")) return "v";
			if (m.getName().equals("getColumnType")) return Types.BIGINT;
			if (m.getName().equals("getColumnTypeName")) return "bigint";
			if (m.getName().equals("getColumnCount")) return 1;
			return m.getReturnType()==boolean.class ? false : m.getReturnType()==int.class ? 0 : null;
		});
		AbstractSQLExecutor<Long, Map<String,Object>, List<Object>> e=new AbstractSQLExecutor<Long, Map<String,Object>, List<Object>>(){};
		assertEquals(3L,e.onPutColumn(main,rs,md,0,new LinkedHashMap<>(),1,join,null,null).get("v"));
		assertEquals("3",e.onPutColumn(main,rs,md,0,new LinkedHashMap<>(),1,null,null,null).get("v"));
	}

	@Test public void typedFunctionArgumentsKeepTheirValuesAndNativeSql() throws Exception {
		for (String db:Arrays.asList("KINGBASE_MYSQL","MYSQL")) {
			AbstractSQLConfig<Long, Map<String,Object>, List<Object>> c=config(db);
			c.setColumn(Arrays.asList("field('b','a','b','c'):n", "json_set('{}','$.a',1):j"));
			String sql=c.gainSQL(true);
			assertEquals(Arrays.asList("b","a","b","c","{}","$.a"),c.getPreparedValueList());
			assertEquals(sql,c.gainSQL(true));
			assertEquals("KINGBASE_MYSQL".equals(db),sql.contains("CAST(? AS text)"));
		}
	}

	@Test public void pathAndRegexTranslationPreserveEscapesAndValidation() {
		assertEquals(Arrays.asList("lax $[1]","lax $[last-0]"),KingbaseMySQLJsonPath.steps("$[0001][last-00]"));
		assertEquals(Collections.singletonList("lax $[0]"),KingbaseMySQLJsonPath.steps("$[000]"));
		assertThrows(IllegalArgumentException.class,()->KingbaseMySQLJsonPath.steps("$[01;select]"));
		assertEquals("\\yAlice\\y",KingbaseMySQLExpressions.regex("\\bAlice\\b"));
		assertEquals("A\\Ylice",KingbaseMySQLExpressions.regex("A\\Blice"));
		assertEquals("[\\b]\\\\b",KingbaseMySQLExpressions.regex("[\\b]\\\\b"));
		assertNull(KingbaseMySQLExpressions.regex(null));
	}

	@Test public void roundTypesOnlyItsSingleNumericStringBinding() throws Exception {
		AbstractSQLConfig<Long,Map<String,Object>,List<Object>> c=config("KINGBASE_MYSQL");
		c.setColumn(Arrays.asList("round('2.5'):v", "round(id):n", "round('bad'):bad"));
		String sql=c.gainSQL(true);
		assertTrue(sql,sql.contains("round(CAST(? AS DOUBLE)) AS `v`"));
		assertTrue(sql,sql.contains("round(`id`) AS `n`"));
		assertTrue(sql,sql.contains("round(?) AS `bad`"));
		assertEquals(Arrays.asList("2.5","bad"),c.getPreparedValueList());
		c.setDatabase("MYSQL");assertFalse(c.gainSQL(true).contains("CAST(? AS DOUBLE)"));
		c.setDatabase("KINGBASE_MYSQL");c.setColumn(Collections.singletonList("sec_to_time('-3600.123456'):v"));
		assertTrue(c.gainSQL(true).contains("sec_to_time(CAST(? AS pg_catalog.numeric))"));
		assertEquals(Collections.singletonList("-3600.123456"),c.getPreparedValueList());
	}

	@Test public void unaliasedFunctionsKeepNativeLabelsWhileJsonQuoteRemainsSqlText() throws Exception {
		AbstractSQLConfig<Long, Map<String,Object>, List<Object>> c=config("KINGBASE_MYSQL");
		c.setColumn(Arrays.asList("length(name)","concat('a:b')", "json_quote('x'):quoted"));
		String sql=c.gainSQL(true);
		assertTrue(sql,sql.contains("octet_length(`name`)"));
		assertTrue(sql,sql.contains("AS `length(``name``)`"));
		assertTrue(sql,sql.contains("AS `concat('a:b')`"));
		assertTrue(sql,sql.contains("CAST(json_quote(?) AS text) AS `quoted`"));
		assertEquals(-1,KingbaseMySQLExpressions.aliasIndex("concat('a:b')"));
		c.setColumn(Collections.singletonList("concat('?')"));
		assertTrue(c.gainSQL(false).contains("AS `concat('?')`"));
		assertTrue(KingbaseMySQLExpressions.hasVolatileFunction("rand()"));
		assertFalse(KingbaseMySQLExpressions.hasVolatileFunction("concat('rand()')"));
	}
}
