package apijson.orm;

import apijson.RequestMethod;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class KingbaseMySQLCapabilitiesTest {

	@BeforeClass public static void parser() { KingbaseCompatibilityTest.installJsonParser(); }

	static class Config extends AbstractSQLConfig<Long, Map<String, Object>, List<Object>> {
		String lastSql;
		Config(String database, String table) {
			super(RequestMethod.GET, table);
			setDatabase(database); setSchema(""); setPrepared(true);
			setRaw(Collections.singletonList("@column"));
		}
		@Override public String gainSQLDatabase() { return getDatabase(); }
		public String gainDBVersion() { return "9.0.0"; }
		public String gainDBUri() { return "jdbc:test"; }
		public String gainDBAccount() { return "test"; }
		public String gainDBPassword() { return "unused"; }
		@Override public String gainSQL(boolean prepared) throws Exception {
			return lastSql = super.gainSQL(prepared);
		}
		@Override protected void onGainCrossJoinString(Join<Long, Map<String, Object>, List<Object>> join) {}
	}

	static Config dates(String database) {
		Config c = new Config(database, "fixture");
		c.setColumn(Arrays.asList("date_add(d,INTERVAL 1 MONTH)",
				"date_sub('2026-01-31',INTERVAL -1 DAY):next"));
		return c;
	}

	static Config cross() {
		Config main = new Config("KINGBASE_MYSQL", "m"), vice = new Config("KINGBASE_MYSQL", "v");
		main.setColumn(Collections.singletonList("id")); vice.setColumn(Collections.singletonList("id"));
		Join<Long, Map<String, Object>, List<Object>> join = new Join<>();
		join.setJoinType("*"); join.setTable("v"); join.setJoinConfig(vice);
		Join.On on = new Join.On(); on.setKey("id"); on.setTargetTable("m"); on.setTargetKey("id");
		join.setOnList(Collections.singletonList(on)); main.setJoinList(Collections.singletonList(join));
		return main;
	}

	/** A session whose settings can change while the same connection is reused. */
	static class Session {
		String interval = "on";
		int shows, statementCloses, resultCloses;
		final List<String> preparedSql = new ArrayList<>();
		final List<Object> arguments = new ArrayList<>();
		final Connection connection = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[]{Connection.class}, (p, m, a) -> {
			switch (m.getName()) {
			case "createStatement": return statement();
			case "prepareStatement":
				preparedSql.add((String) a[0]); arguments.clear();
				return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PreparedStatement.class},
						(ps, method, args) -> {
					if (method.getName().equals("setObject")) {
						while (arguments.size() < (Integer) args[0]) arguments.add(null);
						arguments.set((Integer) args[0] - 1, args[1]); return null;
					}
					if (method.getName().equals("close")) return null;
					throw new AssertionError(method.getName());
				});
			default: throw new AssertionError("Business connection must stay open and unchanged: " + m.getName());
			}
		});

		Statement statement() {
			return (Statement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Statement.class},
					(p, m, a) -> {
				if (m.getName().equals("setQueryTimeout")) { assertEquals(5, a[0]); return null; }
				if (m.getName().equals("close")) { statementCloses++; return null; }
				if (m.getName().equals("executeQuery")) {
					assertEquals("SHOW mysql_interval_style", a[0]);
					shows++;
					final boolean[] row = {false};
					return Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class},
							(rs, method, args) -> {
						if (method.getName().equals("next")) { boolean has = !row[0]; row[0] = true; return has; }
						if (method.getName().equals("getString")) return interval;
						if (method.getName().equals("close")) { resultCloses++; return null; }
						throw new AssertionError(method.getName());
					});
				}
				throw new AssertionError(m.getName());
			});
		}
	}

	@Test public void actualSessionControlsDateSqlAndNegativeBindingsAcrossReuse() throws Exception {
		Session session = new Session(); Config c = dates("KINGBASE_MYSQL");
		AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> executor =
				new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {
			public Connection getConnection(SQLConfig<Long, Map<String, Object>, List<Object>> config) {
				return session.connection;
			}
		};
		try (PreparedStatement ignored = executor.getStatement(c)) {
			assertTrue(c.lastSql, c.lastSql.contains("INTERVAL 1 MONTH"));
			assertTrue(c.lastSql, c.lastSql.contains("INTERVAL ? DAY"));
			assertEquals("2026-01-31", session.arguments.get(0));
			assertEquals("-1", session.arguments.get(1).toString());
			assertTrue(c.lastSql, c.lastSql.contains("AS `date_add(``d``,INTERVAL 1 MONTH)`"));
		}
		session.interval = "off";
		try (PreparedStatement ignored = executor.getStatement(c)) {
			assertTrue(c.lastSql, c.lastSql.contains("INTERVAL '1 MONTH'"));
			assertTrue(c.lastSql, c.lastSql.contains("INTERVAL '-1 DAY'"));
			assertEquals(Collections.singletonList("2026-01-31"), session.arguments);
		}
		assertEquals(2, session.shows);
		assertEquals(2, session.resultCloses); assertEquals(2, session.statementCloses);
	}

	@Test public void oneExecutionSharesSettingsButNeverMixesDataSourcesOrLeaksContext() throws Exception {
		Session on = new Session(), off = new Session(); off.interval = "off";
		Config a = dates("KINGBASE_MYSQL"), b = dates("KINGBASE_MYSQL"); b.setDatasource("off");
		Object owner = new Object();
		try (KingbaseMySQLCapabilities.Scope scope = KingbaseMySQLCapabilities.enter(owner,
				c -> "off".equals(c.getDatasource()) ? off.connection : on.connection)) {
			assertFalse(a.gainSQL(true).contains("INTERVAL '1 MONTH'"));
			try (KingbaseMySQLCapabilities.Scope nested = KingbaseMySQLCapabilities.enter(owner,
					c -> { throw new AssertionError("Nested same-executor scope must reuse its connection provider"); })) {
				assertFalse(dates("KINGBASE_MYSQL").gainSQL(true).contains("INTERVAL '1 MONTH'"));
			}
			assertTrue(b.gainSQL(true).contains("INTERVAL '1 MONTH'"));
		}
		assertEquals(1, on.shows); assertEquals(1, off.shows);
		// Pure SQL generation has no stale execution connection/settings attached.
		assertTrue(a.gainSQL(true).contains("INTERVAL '1 MONTH'"));
	}

	@Test public void crossJoinUsesEquivalentGrammarWithoutAnyConnectionProbe() throws Exception {
		try (KingbaseMySQLCapabilities.Scope scope = KingbaseMySQLCapabilities.enter(this,
				c -> { throw new AssertionError("CROSS JOIN must not inspect or change the business connection"); })) {
			assertTrue(cross().gainSQL(true).contains(" INNER JOIN ("));
			Config cartesian = cross();
			cartesian.getJoinList().get(0).setOnList(Collections.emptyList());
			assertTrue(cartesian.gainSQL(true).contains(" CROSS JOIN ("));
		}
	}

	@Test public void invalidIntervalSettingPropagatesAndTheNextExecutionCanRetry() throws Exception {
		Session session = new Session(); session.interval = "unknown";
		try (KingbaseMySQLCapabilities.Scope scope = KingbaseMySQLCapabilities.enter(this, c -> session.connection)) {
			assertThrows(IllegalStateException.class, () -> dates("KINGBASE_MYSQL").gainSQL(true));
		}
		session.interval = "on";
		try (KingbaseMySQLCapabilities.Scope scope = KingbaseMySQLCapabilities.enter(this, c -> session.connection)) {
			assertFalse(dates("KINGBASE_MYSQL").gainSQL(true).contains("INTERVAL '1 MONTH'"));
		}
		assertTrue(dates("KINGBASE_MYSQL").gainSQL(true).contains("INTERVAL '1 MONTH'"));
	}

	@Test public void nativeAndOtherModesNeverUseTheMysqlCapabilityConnection() throws Exception {
		try (KingbaseMySQLCapabilities.Scope scope = KingbaseMySQLCapabilities.enter(this,
				c -> { throw new AssertionError("No MySQL-mode probe on other routes"); })) {
			for (String db : Arrays.asList("MYSQL", "ORACLE", "SQLSERVER", "KINGBASE_ORACLE", "KINGBASE_SQLSERVER", "KINGBASE")) {
				assertFalse(dates(db).gainSQL(true).contains("INTERVAL '1 MONTH'"));
			}
		}
	}
}
