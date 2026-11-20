package apijson.orm;

import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native JDBC and JOIN paths must retain the pre-adaptation behavior. */
public class NativeDatabaseIsolationTest {
	private static List<String> nativeDatabases() {
		List<String> databases = new ArrayList<>(AbstractSQLConfig.DATABASE_LIST);
		databases.removeIf(db -> db.startsWith("KINGBASE_"));
		databases.add("SQLITE");
		databases.add("CUSTOM_DATABASE");
		return databases;
	}

	static class Executor extends AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> {
		Join[] projection(SQLConfig<Long, Map<String, Object>, List<Object>> c, ResultSetMetaData m)
				throws Exception {
			return resolveProjectionJoins(c, m);
		}

		Object read(
				SQLConfig<Long, Map<String, Object>, List<Object>> c,
				ResultSet r,
				ResultSetMetaData m)
				throws Exception {
			return getValue(c, r, m, 0, new LinkedHashMap<>(), 1, "name", null, null);
		}
	}

	@Test
	public void nativeJoinsDoNotEnterKingbaseProjectionMapping() throws Exception {
		for (String db : nativeDatabases()) {
			NativeDatabaseBaselineTest.Config c = new NativeDatabaseBaselineTest.Config(db, "19.0");
			ResultSetMetaData md =
					(ResultSetMetaData)
							Proxy.newProxyInstance(
									getClass().getClassLoader(),
									new Class<?>[] {ResultSetMetaData.class},
									(p, m, a) -> {
										throw new AssertionError(
												"Native JOIN entered new projection mapping: "
														+ m.getName());
									});
			assertNull(db, new Executor().projection(c, md));
		}
	}

	@Test
	public void nativeValuesAreReadBeforeMetadataWithoutTextCoercion() throws Exception {
		for (String db : nativeDatabases()) {
			List<String> calls = new ArrayList<>();
			ResultSet rs =
					(ResultSet)
							Proxy.newProxyInstance(
									getClass().getClassLoader(),
									new Class<?>[] {ResultSet.class},
									(p, m, a) -> {
										calls.add(m.getName());
										if (m.getName().equals("getObject"))
											return "original value";
										throw new AssertionError(
												"Unexpected native read: " + m.getName());
									});
			ResultSetMetaData md =
					(ResultSetMetaData)
							Proxy.newProxyInstance(
									getClass().getClassLoader(),
									new Class<?>[] {ResultSetMetaData.class},
									(p, m, a) -> {
										calls.add(m.getName());
										if (m.getName().equals("getColumnType")) return Types.TIME;
										if (m.getName().equals("getColumnTypeName")) return "time";
										throw new AssertionError(
												"Unexpected native metadata call: " + m.getName());
									});
			assertEquals(
					"original value",
					new Executor().read(new NativeDatabaseBaselineTest.Config(db, "19.0"), rs, md));
			assertEquals(
					db, Arrays.asList("getObject", "getColumnTypeName"), calls);
		}
	}

	@Test
	public void explicitPaginationDoesNotInventAnUnavailableId() throws Exception {
		for (String db : Arrays.asList("SQLSERVER", "KINGBASE_SQLSERVER")) {
			AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c =
					KingbaseSemanticsRegressionTest.config(db);
			c.setTable("NaturalKeyTable");
			c.setColumn(Arrays.asList("code", "name"));
			c.setOrder("code+").setCount(5);
			String sql = c.gainSQL(true);
			assertTrue(sql, sql.contains("ORDER BY \"code\" ASC"));
			assertFalse(sql, sql.contains("\"id\""));
			// Wildcards do not supply metadata proving an id column exists either.
			c.setColumn(null);
			sql = c.gainSQL(true);
			assertFalse(sql, sql.contains("\"id\""));
		}
	}

	@Test
	public void nativePreparedStatementsKeepTheOriginalExecutionHook() throws Exception {
		List<String> calls = new ArrayList<>();
		PreparedStatement statement =
				(PreparedStatement)
						Proxy.newProxyInstance(
								getClass().getClassLoader(),
								new Class<?>[] {PreparedStatement.class},
								(p, m, a) -> {
									calls.add(m.getName() + ":" + (a == null ? 0 : a.length));
									if (m.getName().equals("execute")) return false;
									if (m.getName().equals("getResultSet")) return null;
									throw new AssertionError(m.getName());
								});
		new Executor().execute(statement, "SELECT 1");
		assertEquals(Arrays.asList("execute:1", "getResultSet:0"), calls);
	}
}
