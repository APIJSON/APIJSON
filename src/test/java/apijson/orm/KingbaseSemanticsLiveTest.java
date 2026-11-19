package apijson.orm;

import java.sql.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Optional read-only server contracts. No schema, data or extensions are changed. */
public class KingbaseSemanticsLiveTest {
	private Connection connect(String prefix) throws SQLException {
		String url = System.getenv(prefix + "_URL");
		assumeTrue(
				"Set " + prefix + "_URL to run server contracts",
				url != null && !url.trim().isEmpty());
		String username = System.getenv(prefix + "_USERNAME");
		String password = System.getenv(prefix + "_PASSWORD");
		if (prefix.startsWith("KINGBASE")) {
			if (username == null) username = System.getenv("KINGBASE_USERNAME");
			if (password == null) password = System.getenv("KINGBASE_PASSWORD");
		}
		if (username == null || password == null)
			throw new IllegalArgumentException("Missing credentials for " + prefix);
		Connection connection = DriverManager.getConnection(url, username, password);
		try {
			if (prefix.startsWith("KINGBASE")) {
				assertEquals(prefix, new KingbaseModeResolver().detect(connection));
			}
			return connection;
		} catch (Exception | AssertionError e) {
			connection.close();
			throw e;
		}
	}

	private Object scalar(Connection connection, String sql, List<Object> arguments)
			throws SQLException {
		try (PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setQueryTimeout(10);
			for (int i = 0; i < arguments.size(); i++) statement.setObject(i + 1, arguments.get(i));
			try (ResultSet result = statement.executeQuery()) {
				assertTrue(result.next());
				Object value = result.getObject(1);
				assertFalse(result.next());
				return value;
			}
		}
	}

	@Test
	public void kingbaseMysqlJsonAndAliasPaginationMatchExpectedValues() throws Exception {
		try (Connection connection = connect("KINGBASE_MYSQL")) {
			String[] documents = {
				"[]", "[1,2]", "{}", "{\"a\":1,\"b\":2}", "123", "\"abc\"", "true", "null", null
			};
			Integer[] lengths = {0, 2, 0, 2, 1, 1, 1, 1, null};
			for (int i = 0; i < documents.length; i++) {
				Object length =
						scalar(
								connection,
								"SELECT json_length(CAST(? AS JSON))",
								Collections.<Object>singletonList(documents[i]));
				assertEquals(
						documents[i],
						lengths[i],
						length == null ? null : ((Number) length).intValue());
			}
			assertEquals(
					2,
					((Number)
									scalar(
											connection,
											"SELECT json_length(CAST(? AS JSON), ?)",
											Arrays.<Object>asList("{\"items\":[1,2]}", "$.items")))
							.intValue());
			assertNull(
					scalar(
							connection,
							"SELECT json_length(CAST(? AS JSON), ?)",
							Arrays.<Object>asList("{}", "$.missing")));

			AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c =
					KingbaseSemanticsRegressionTest.config("KINGBASE_MYSQL");
			c.setColumn(Collections.singletonList("id:n"));
			c.setCount(1);
			c.setPage(1);
			c.setOrder("id+");
			c.setHaving(Collections.<String, Object>singletonMap("h", "n>1"));
			String sql =
					c.gainSQL(true)
							.replace(
									c.gainTablePath(),
									"(SELECT 1 AS id UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4) AS fixture");
			assertEquals(
					3, ((Number) scalar(connection, sql, c.getPreparedValueList())).intValue());
		}
	}

	private void assertDateFormat(Connection connection, String database) throws Exception {
		AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c =
				KingbaseSemanticsRegressionTest.config(database);
		c.setPrepared(true);
		c.setPreparedValueList(new ArrayList<>());
		String expression =
				c.parseSQLExpression(
						"@column", "date_format(created,'%Y-%m-%dT%H:%i:%s.%f %%')", false, false);
		String sql =
				"SELECT "
						+ expression
						+ " FROM (SELECT CAST('2026-01-02 03:04:05.123456' AS datetime2(6)) AS created) AS fixture";
		assertEquals(
				"2026-01-02T03:04:05.123456 %", scalar(connection, sql, c.getPreparedValueList()));
	}

	@Test
	public void kingbaseSqlServerRetainsDateFormatFractions() throws Exception {
		try (Connection connection = connect("KINGBASE_SQLSERVER")) {
			assertDateFormat(connection, "KINGBASE_SQLSERVER");
		}
	}
}
