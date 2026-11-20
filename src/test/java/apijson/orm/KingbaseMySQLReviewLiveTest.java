package apijson.orm;

import java.sql.*;
import java.math.BigDecimal;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Optional SELECT-only checks against Kingbase calculations and native MySQL API semantics. */
public class KingbaseMySQLReviewLiveTest {
	@BeforeClass
	public static void parser() { KingbaseCompatibilityTest.installJsonParser(); }

	private Connection connect(boolean kingbase) throws SQLException {
		String prefix = kingbase ? "KINGBASE_MYSQL" : "AUDIT_MYSQL";
		String url = System.getenv(prefix + "_URL");
		assumeTrue("Set " + prefix + "_URL for native/Kingbase comparisons", url != null && !url.trim().isEmpty());
		String user = System.getenv(kingbase ? "KINGBASE_USERNAME" : prefix + "_USERNAME");
		String password = System.getenv(kingbase ? "KINGBASE_PASSWORD" : prefix + "_PASSWORD");
		Connection c = DriverManager.getConnection(url, user, password);
		c.setReadOnly(true);
		return c;
	}

	private List<Map<String, Object>> rows(Connection connection, AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c,
			String sql, List<Object> args) throws Exception {
		AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> executor = new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {};
		List<Map<String, Object>> rows = new ArrayList<>();
		try (PreparedStatement ps = connection.prepareStatement(sql)) {
			ps.setQueryTimeout(10);
			for (int i = 0; i < args.size(); i++) executor.setArgument(c, ps, i, args.get(i));
			try (ResultSet rs = ps.executeQuery()) {
				ResultSetMetaData md = rs.getMetaData();
				while (rs.next()) {
					Map<String, Object> row = new LinkedHashMap<>();
					for (int i = 1; i <= md.getColumnCount(); i++) {
						String label = executor.getKey(c, rs, md, rows.size(), row, i, null, null);
						Object value = executor.getValue(c, rs, md, rows.size(), row, i, label, null, null);
						// Integer and Long are both JSON numbers; strings stay strings.
						if (value instanceof Number) value = new BigDecimal(value.toString()).stripTrailingZeros();
						row.put(label, value);
					}
					rows.add(row);
				}
			}
		}
		return rows;
	}

	private List<Map<String, Object>> project(Connection connection, String db, String expression, String fixture,
			String group, String having) throws Exception {
		AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = KingbaseSemanticsRegressionTest.config(db);
		c.setRaw(Collections.singletonList("@column"));
		c.setColumn(Collections.singletonList(expression));
		c.setGroup(group);
		if (having != null) c.setHaving(Collections.<String, Object>singletonMap("h", having));
		// These SELECT-only fixtures generate SQL directly instead of calling the
		// executor entry point; supply the same actual-session context it uses.
		try (KingbaseMySQLCapabilities.Scope scope = KingbaseMySQLCapabilities.enter(connection, ignored -> connection)) {
			String sql = c.gainSQL(true).replace(c.gainTablePath(), fixture);
			return rows(connection, c, sql, c.getPreparedValueList());
		}
	}

	/** The configured Kingbase session owns numeric precision; compare with plain JDBC. */
	private void assertKingbaseCalculation(Connection kb, String apiExpression, String sqlExpression,
			String fixture, Object... arguments) throws Exception {
		List<Map<String, Object>> actual = project(kb, "KINGBASE_MYSQL", apiExpression, fixture, null, null);
		try (PreparedStatement ps = kb.prepareStatement("SELECT " + sqlExpression + " AS v FROM " + fixture)) {
			ps.setQueryTimeout(10);
			for (int i = 0; i < arguments.length; i++) ps.setObject(i + 1, arguments[i]);
			try (ResultSet rs = ps.executeQuery()) {
				assertTrue(rs.next()); assertEquals(1, actual.size());
				Object value = actual.get(0).values().iterator().next();
				String expected = rs.getString(1);
				int type = rs.getMetaData().getColumnType(1);
				if (expected != null && (type == Types.DECIMAL || type == Types.NUMERIC)) {
					// Preserve the established JSON string contract. Driver metadata may
					// remove trailing zeroes; significant digits must agree with the server.
					assertTrue(apiExpression, value instanceof String);
					assertEquals(apiExpression, new BigDecimal(expected).stripTrailingZeros(),
							new BigDecimal((String) value).stripTrailingZeros());
				} else if (value instanceof Number && expected != null) {
					assertEquals(apiExpression, new BigDecimal(expected).stripTrailingZeros(), value);
				} else {
					assertEquals(apiExpression + " / " + fixture, expected, value);
				}
				assertFalse(rs.next());
			}
		}
	}

	@Test
	public void dateFunctionsPreserveValuesLabelsAndTypes() throws Exception {
		String[][] cases = {
			{"date_add(d,INTERVAL 1 MONTH):v", "CAST('2026-01-31' AS DATE)"},
			{"date_add(d,INTERVAL 1 MONTH)", "CAST('2026-01-31' AS DATE)"},
			{"date_sub(d,INTERVAL 1 DAY):v", "CAST('2024-03-01' AS DATE)"},
			{"date_add(d,INTERVAL 1 HOUR):v", "CAST('2026-01-31' AS DATE)"},
			{"date_add(d,INTERVAL 1 QUARTER):v", "CAST('2026-01-31' AS DATE)"},
			{"date_add(d,INTERVAL 1 DAY):v", "CAST('2026-01-31 12:34:56.123456' AS DATETIME(6))"},
			{"date_sub(d,INTERVAL -1 DAY):v", "CAST('2026-01-31 12:34:56.123456' AS DATETIME(6))"},
			{"date_sub(d,INTERVAL -1 DAY)", "CAST('2026-01-31' AS DATE)"},
			{"date_add(d,INTERVAL 1 DAY):v", "concat('2026-01-31')"},
			{"date_add(d,INTERVAL 1 DAY):v", "concat('2026-01-31 12:34:56.123456')"},
			{"date_add('2026-01-31',INTERVAL 1 DAY):v", "1"},
			{"date_add('2026-01-31',INTERVAL 1 DAY)", "1"},
			{"date_add('not-a-date',INTERVAL 1 DAY):v", "1"},
			{"date_add(d,INTERVAL 1 DAY):v", "CAST(NULL AS DATE)"}
		};
		try (Connection mysql = connect(false); Connection kb = connect(true)) {
			for (String[] test : cases) {
				String fixture = "(SELECT " + test[1] + " AS d) AS fixture";
				assertEquals(Arrays.toString(test), project(mysql, "MYSQL", test[0], fixture, null, null),
						project(kb, "KINGBASE_MYSQL", test[0], fixture, null, null));
			}
		}
	}

	@Test
	public void jsonContainmentPreservesPathsAndScalarCandidates() throws Exception {
		Object[][] cases = {
			{"[1,2]", "", 1}, {"1", "$", 1}, {"\"Alice\"", "$", "Alice"},
			{"{\"items\":[1,2]}", "$.items", 1},
			{"{\"_items\":[1]}", "$._items", 1},
			{"{\"last\":[1]}", "$.last", 1},
			{"{\"items\":[{\"a.b\":1}]}", "$.items[0].\"a.b\"", 1},
			{"{\"items\":[1,2]}", "$.items[last-1]", 1},
			{"{\"items\":[1,2]}", "$.items[9]", 1},
			{"{\"items\":[1]}", "$.missing", 1},
			{"[{\"items\":[1]}]", "$.items", 1},
			{"1", "$[0]", 1}, {"1", "$[1]", 1},
			{"null", "$", null}, {null, "$", 1}
			,{"[[1]]", "$", 1}, {"[[[1]],2]", "$", 1}, {"[[null]]", "$", null}
			,{"[{\"a\":1}]", "$", 1}, {"[[\"Alice\"]]", "$", "Alice"}
			,{"[1,2]", "$[01]", 2}, {"[1,2]", "$[last-00]", 2}
		};
		try (Connection mysql = connect(false); Connection kb = connect(true)) {
			for (Object[] test : cases) {
				List<List<Map<String, Object>>> results = new ArrayList<>();
				for (String db : Arrays.asList("MYSQL", "KINGBASE_MYSQL")) {
					AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = KingbaseSemanticsRegressionTest.config(db);
					c.setPrepared(true);
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("path", test[1]); item.put("value", test[2]);
					String predicate = c.gainContainString("payload", "payload", new Object[]{item}, Logic.TYPE_OR);
					List<Object> args = new ArrayList<>(); args.add(test[0]); args.addAll(c.getPreparedValueList());
					results.add(rows("MYSQL".equals(db) ? mysql : kb, c,
							"SELECT 1 AS id FROM (SELECT CAST(? AS JSON) AS payload) fixture WHERE " + predicate, args));
				}
				assertEquals(Arrays.toString(test), results.get(0), results.get(1));
			}
		}
	}

	@Test
	public void longUnaliasedProjectionsKeepBothFullLabels() throws Exception {
		try (Connection mysql = connect(false); Connection kb = connect(true)) {
			for (String prefix : Arrays.asList("abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789",
					"时间时间时间时间时间时间时间时间时间时间时间时间")) {
				String first = "concat('" + prefix + "A')";
				String second = "concat('" + prefix + "B')";
				Map<String, Object> expected = new LinkedHashMap<>();
				expected.put(first, prefix + "A");
				expected.put(second, prefix + "B");
				for (String db : Arrays.asList("MYSQL", "KINGBASE_MYSQL")) {
					AbstractSQLConfig<Long, Map<String, Object>, List<Object>> config = KingbaseSemanticsRegressionTest.config(db);
					config.setColumn(Arrays.asList(first, second));
					Connection connection = "MYSQL".equals(db) ? mysql : kb;
					for (boolean prepared : Arrays.asList(false, true)) {
						String sql = config.gainSQL(prepared).replace(config.gainTablePath(), "(SELECT 1 AS id) AS fixture");
						assertEquals(db + " prepared=" + prepared, Collections.singletonList(expected),
								rows(connection, config, sql, config.getPreparedValueList()));
					}
				}
			}
		}
	}

	@Test
	public void timeColumnsPreserveFractionsSignsAndDurations() throws Exception {
		try (Connection mysql = connect(false); Connection kb = connect(true)) {
			for (String value : Arrays.asList("00:00:00.000000", "12:34:56.123456", "23:59:59.999999", "-00:30:00.000000", "30:34:56.000000", null)) {
				String literal = value == null ? "NULL" : "'" + value + "'";
				String fixture = "(SELECT CAST(" + literal + " AS TIME(6)) AS d) AS fixture";
				List<Map<String, Object>> actual = project(kb, "KINGBASE_MYSQL", "d:v", fixture, null, null);
				assertEquals(value, 1, actual.size());
				assertEquals(value, value, actual.get(0).get("v"));
				String nativeResult;
				try { nativeResult = project(mysql, "MYSQL", "d:v", fixture, null, null).toString(); }
				catch (SQLException e) { nativeResult = "SQLState=" + e.getSQLState(); }
				// Observation only: driver differences must not redefine the
				// Kingbase assertion above, whose expected value is the input.
				System.out.println("TIME input=" + value + "; native=" + nativeResult + "; Kingbase=" + actual);
			}
			assertEquals("12:34:57.123456", project(kb, "KINGBASE_MYSQL", "date_add(d,INTERVAL 1 SECOND):v",
					"(SELECT CAST('12:34:56.123456' AS TIME(6)) AS d) AS fixture", null, null).get(0).get("v"));
		}
	}

	@Test
	public void intervalResultsPreserveClockTextFractionsAndLongNegativeDurations() throws Exception {
		try (Connection kb = connect(true)) {
			for (String[] test : new String[][] {
				{"3600.123456", "01:00:00.123456"},
				{"-3600.123456", "-01:00:00.123456"},
				{"100000", "27:46:40.000000"}
			}) {
				List<Map<String,Object>> result = project(kb, "KINGBASE_MYSQL", "sec_to_time(" + test[0] + "):v",
						"(SELECT 1 AS d) fixture", null, null);
				assertEquals(test[1], result.get(0).get("v"));
			}
		}
	}

	@Test
	public void groupedHavingAndSumKeepNativeResponseTypes() throws Exception {
		try (Connection mysql = connect(false); Connection kb = connect(true)) {
			String fixture = "(SELECT 1 AS id UNION ALL SELECT 2) AS fixture";
			assertEquals(project(mysql, "MYSQL", "id:id", fixture, "id", "count(id)>0"),
					project(kb, "KINGBASE_MYSQL", "id:id", fixture, "id", "count(id)>0"));
			for (String expr : Arrays.asList("sum(id):total", "sum(id)", "sum(DISTINCT id):total", "count(id):total",
					"sum(id)+1:total", "sum(id)+1", "sum(id)*2:total", "sum(id)+1.25:total", "sum(id)/2:total")) {
				if (expr.contains("/")) {
					assertKingbaseCalculation(kb, expr, expr.substring(0, expr.indexOf(':')), fixture);
				} else {
					assertEquals(expr, project(mysql, "MYSQL", expr, fixture, null, null),
							project(kb, "KINGBASE_MYSQL", expr, fixture, null, null));
				}
			}
			fixture = "(SELECT CAST(1.25 AS DOUBLE) AS id UNION ALL SELECT CAST(2.5 AS DOUBLE)) AS fixture";
			assertEquals(project(mysql, "MYSQL", "sum(id):total", fixture, null, null),
					project(kb, "KINGBASE_MYSQL", "sum(id):total", fixture, null, null));
			for (String type : Arrays.asList("DECIMAL(20,0)", "DECIMAL(12,2)", "DECIMAL(12,6)", "DOUBLE")) {
				fixture="(SELECT CAST(1.25 AS "+type+") AS id UNION ALL SELECT CAST(2.34 AS "+type+")) AS fixture";
				for(String expression:Arrays.asList("sum(id)/2:total","sum(id)/2","sum(DISTINCT id)/2.5:total","sum(id)/0:total")) {
					int alias = expression.indexOf(':');
					assertKingbaseCalculation(kb, expression, alias < 0 ? expression : expression.substring(0, alias), fixture);
				}
			}
		}
	}

	private static class JoinConfig extends NativeDatabaseBaselineTest.Config {
		JoinConfig() { super("KINGBASE_MYSQL", "9.0");setTable("a"); }
		@Override public String gainSQLDatabase() { return getDatabase(); }
		@Override protected void onJoinNotRelation(String sql,String quote,Join<Long,Map<String,Object>,List<Object>> join,
				String table,List<Join.On> ons,Join.On on) {}
		String predicate(String relation,String target,boolean not) {
			Join.On on=new Join.On();on.setKey("name");on.setTargetTable("a");on.setTargetKey(target);on.setRelateType(relation);
			if(not)on.setLogic(new Logic(Logic.TYPE_NOT));
			return concatJoinOn("",getQuote(),new Join<>(),"b",Collections.singletonList(on));
		}
	}

	@Test
	public void regexJoinsPreserveNegationCaseAndBothNullOperands() throws Exception {
		try(Connection mysql=connect(false);Connection kb=connect(true)) {
			for(String[] input:new String[][]{{"^A","Alice"},{"^a","Alice"},{"^A","Bob"},{null,"Alice"},{"^A",null},{null,null}}) {
				for(boolean ignore:new boolean[]{false,true})for(boolean not:new boolean[]{false,true}) {
					JoinConfig c=new JoinConfig();
					String expected="SELECT b.name AS v FROM (SELECT CAST(? AS CHAR) AS pattern) a INNER JOIN (SELECT CAST(? AS CHAR) AS name) b ON "
							+(not?"NOT ":"")+"regexp_like(b.name,a.pattern,"+(ignore?"'i'":"'c'")+")";
					String actual="SELECT b.name AS v FROM (SELECT CAST(? AS varchar) AS pattern) a INNER JOIN (SELECT CAST(? AS varchar) AS name) b"
							+c.predicate(ignore?"*~":"~","pattern",not);
					List<Object> args=Arrays.<Object>asList(input[0],input[1]);
					assertEquals(Arrays.toString(input)+" ignore="+ignore+" not="+not,
							rows(mysql,KingbaseSemanticsRegressionTest.config("MYSQL"),expected,args),rows(kb,c,actual,args));
				}
			}
		}
	}

	@Test
	public void jsonJoinsEncodeTypedScalarsAndEscapedStrings() throws Exception {
		Object[][] tests={{"[\"Alice\"]","Alice","varchar"},{"[\"A\\\"\\\\B\"]","A\"\\B","varchar"},
				{"[1]",1,"integer"},{"[\"1\"]",1,"integer"},{"[1]","1","varchar"},
				{"[true]",true,"boolean"},{"[null]",null,"varchar"},{null,"Alice","varchar"}};
		try(Connection mysql=connect(false);Connection kb=connect(true)) {
			for(Object[] test:tests)for(boolean not:new boolean[]{false,true}) {
				JoinConfig c=new JoinConfig();
				String actual="SELECT 1 AS id FROM (SELECT CAST(? AS JSON) AS payload) a INNER JOIN (SELECT CAST(? AS "+test[2]+") AS name) b"
						+c.predicate("{}","payload",not);
				boolean string=test[1] instanceof String;
				String condition="a.payload IS NOT NULL AND json_contains(a.payload,"+(string?"json_quote(?)":"?")+")";
				String expected="SELECT 1 AS id FROM (SELECT CAST(? AS JSON) AS payload) a WHERE "+(not?"NOT ("+condition+")":condition);
				Object candidate=test[1]==null?null:test[1].toString();
				assertEquals(Arrays.toString(test)+" not="+not,
						rows(mysql,KingbaseSemanticsRegressionTest.config("MYSQL"),expected,Arrays.asList(test[0],candidate)),
						rows(kb,c,actual,Arrays.asList(test[0],test[1])));
			}
		}
	}

	@Test
	public void averagesKeepKingbasePrecisionFloatingTypeDistinctAndNulls() throws Exception {
		String[] fixtures={
				"(SELECT 1 AS d UNION ALL SELECT 2) AS fixture",
				"(SELECT CAST(1.25 AS DECIMAL(12,2)) AS d UNION ALL SELECT CAST(2.34 AS DECIMAL(12,2))) AS fixture",
				"(SELECT CAST(1.25 AS DECIMAL(12,6)) AS d UNION ALL SELECT CAST(2.34 AS DECIMAL(12,6))) AS fixture",
				"(SELECT CAST(1 AS DECIMAL(40,20)) AS d UNION ALL SELECT CAST(0 AS DECIMAL(40,20)) UNION ALL SELECT CAST(0 AS DECIMAL(40,20))) AS fixture",
				"(SELECT CAST(1 AS DECIMAL(50,30)) AS d UNION ALL SELECT CAST(0 AS DECIMAL(50,30)) UNION ALL SELECT CAST(0 AS DECIMAL(50,30))) AS fixture",
				"(SELECT CAST(-1 AS DECIMAL(12,2)) AS d UNION ALL SELECT CAST(0 AS DECIMAL(12,2)) UNION ALL SELECT CAST(0 AS DECIMAL(12,2))) AS fixture",
				"(SELECT CAST(1.25 AS DOUBLE) AS d UNION ALL SELECT CAST(2.34 AS DOUBLE)) AS fixture",
				"(SELECT concat('1.25') AS d UNION ALL SELECT concat('2.34')) AS fixture",
				"(SELECT CAST(NULL AS DECIMAL(12,2)) AS d) AS fixture",
				"(SELECT CAST(1 AS DECIMAL(12,2)) AS d WHERE 1=0) AS fixture"};
		try(Connection kb=connect(true)) {
			for(String fixture:fixtures)for(String expression:Arrays.asList("avg(d):v","avg(d)","avg(DISTINCT d):v","avg(d)+1:v")) {
				int alias = expression.indexOf(':');
				assertKingbaseCalculation(kb, expression, alias < 0 ? expression : expression.substring(0, alias), fixture);
			}
			String fixture="(SELECT 1 AS d) AS fixture";
			assertKingbaseCalculation(kb, "avg('1.25'):v", "avg(CAST(? AS varchar))", fixture, "1.25");
			assertKingbaseCalculation(kb, "avg(d):v", "avg(d)", fixture);
		}
	}

	@Test
	public void upgradedSessionPrecisionControlsTheActualAggregateCalculation() throws Exception {
		try (Connection kb = connect(true)) {
			try (Statement s = kb.createStatement(); ResultSet r = s.executeQuery("SELECT version()")) {
				r.next(); assumeTrue("New-release precision setting", r.getString(1).contains("R003C018"));
			}
			kb.setAutoCommit(false);
			try {
				for (int increment : new int[]{2, 6}) {
					try (Statement s = kb.createStatement()) {
						s.execute("SET LOCAL div_precision_increment=" + increment);
					}
					String fixture = "(SELECT 1 AS d UNION ALL SELECT 0 UNION ALL SELECT 0) AS fixture";
					assertKingbaseCalculation(kb, "avg(d):v", "avg(d)", fixture);
					assertKingbaseCalculation(kb, "sum(d)/3:v", "sum(d)/3", fixture);
				}
			} finally { kb.rollback(); }
		}
	}

	@Test
	public void boundTimeFunctionPreservesFullDurationValues() throws Exception {
		try(Connection kb=connect(true)) {
			for(String input:Arrays.asList("00:00:00.000000","12:34:56.123456","23:59:59.999999","-00:30:00.000000","30:34:56.000000")) {
				assertEquals(input,input,project(kb,"KINGBASE_MYSQL","time('"+input+"'):v","(SELECT 1 AS d) AS fixture",null,null).get(0).get("v"));
			}
		}
	}
}
