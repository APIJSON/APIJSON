package apijson.orm;

import java.sql.*;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Explicit MySQL-mode live regression; settings and fixtures stay in this session. */
public class KingbaseMySQLCapabilitiesLiveTest {

	@BeforeClass public static void parser() { KingbaseCompatibilityTest.installJsonParser(); }

	private Connection connect() throws SQLException {
		String url = System.getenv("KINGBASE_MYSQL_URL");
		assumeTrue("Set KINGBASE_MYSQL_URL for live MySQL-mode capability checks", url != null && !url.isEmpty());
		Connection c = DriverManager.getConnection(url, System.getenv("KINGBASE_USERNAME"), System.getenv("KINGBASE_PASSWORD"));
		assertEquals("KINGBASE_MYSQL", new KingbaseModeResolver().detect(c));
		return c;
	}

	private AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> executor(Connection actualConnection) {
		return new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {
			@Override public Connection getConnection(SQLConfig<Long, Map<String, Object>, List<Object>> config) {
				return actualConnection;
			}
		};
	}

	private KingbaseMySQLCapabilitiesTest.Config date(String expression, String value) {
		KingbaseMySQLCapabilitiesTest.Config c = new KingbaseMySQLCapabilitiesTest.Config("KINGBASE_MYSQL", "fixture") {
			@Override public String gainTablePath() { return "(SELECT " + value + " AS d) AS fixture"; }
		};
		c.setColumn(Collections.singletonList(expression)); return c;
	}

	private String scalar(PreparedStatement statement) throws SQLException {
		try (ResultSet result = statement.executeQuery()) {
			assertTrue(result.next()); String value = result.getString(1); assertFalse(result.next()); return value;
		}
	}

	@Test public void realSessionOnOffChangesPreparedSqlAndPreservesNegativeArguments() throws Exception {
		try (Connection connection = connect()) {
			connection.setAutoCommit(false);
			AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> e = executor(connection);
			KingbaseMySQLCapabilitiesTest.Config c = date("date_sub(d,INTERVAL -1 DAY):v", "CAST('2026-01-31' AS DATE)");
			try {
				for (String setting : Arrays.asList("on", "off", "on")) {
					try (Statement s = connection.createStatement()) { s.execute("SET LOCAL mysql_interval_style=" + setting); }
					try (PreparedStatement statement = e.getStatement(c)) {
						assertEquals("2026-02-01", scalar(statement));
						assertTrue(c.lastSql, c.lastSql.contains("on".equals(setting) ? "INTERVAL ? DAY" : "INTERVAL '-1 DAY'"));
						assertEquals("on".equals(setting) ? 1 : 0, c.getPreparedValueList().size());
					}
				}
			} finally { connection.rollback(); }
		}
	}

	@Test public void nonPreparedAndUnaliasedDateExecutionUseTheActualSession() throws Exception {
		try (Connection connection = connect()) {
			connection.setAutoCommit(false);
			AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> e = executor(connection);
			try {
				for (String setting : Arrays.asList("on", "off")) {
					try (Statement s = connection.createStatement()) { s.execute("SET LOCAL mysql_interval_style=" + setting); }
					KingbaseMySQLCapabilitiesTest.Config c = date("date_add(d,INTERVAL 1 MONTH)", "CAST('2024-01-31' AS DATE)");
					c.setPrepared(false);
					try (ResultSet result = e.executeQuery(c)) {
						assertTrue(result.next()); assertEquals("2024-02-29", result.getString(1));
						assertEquals("date_add(`d`,INTERVAL 1 MONTH)", result.getMetaData().getColumnLabel(1));
					}
				}
			} finally { connection.rollback(); }
		}
	}

	@Test public void crossJoinEquivalentSyntaxExecutesWithoutAProbeOrTransactionChange() throws Exception {
		try (Connection connection = connect()) {
			connection.setAutoCommit(false);
			try {
				try (Statement s = connection.createStatement()) {
					s.execute("CREATE TEMPORARY TABLE m(id INT)");
					s.execute("CREATE TEMPORARY TABLE v(id INT)");
					s.executeUpdate("INSERT INTO m VALUES(1),(2)");
					s.executeUpdate("INSERT INTO v VALUES(1)");
				}
				KingbaseMySQLCapabilitiesTest.Config cfg = KingbaseMySQLCapabilitiesTest.cross();
				cfg.setKeyPrefix(true);
				try (PreparedStatement statement = executor(connection).getStatement(cfg);
						ResultSet result = statement.executeQuery()) {
					assertTrue(cfg.lastSql, cfg.lastSql.contains(" INNER JOIN ("));
					assertTrue(result.next()); assertEquals(1, result.getInt(1)); assertFalse(result.next());
				}
				assertFalse(connection.getAutoCommit());
				try (Statement s = connection.createStatement(); ResultSet result = s.executeQuery("SELECT 99")) {
					assertTrue(result.next()); assertEquals(99, result.getInt(1));
				}
			} finally { connection.rollback(); }
		}
	}
}
