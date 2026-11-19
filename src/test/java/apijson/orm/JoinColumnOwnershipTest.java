package apijson.orm;

import apijson.*;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import static org.junit.Assert.*;

public class JoinColumnOwnershipTest {
	static class Config extends AbstractSQLConfig<Long, Map<String, Object>, List<Object>> {
		final String dialect;
        Config(String db, String table, String... columns) {
			super(RequestMethod.GET, table);
			dialect=db;setDatabase(db.startsWith("KINGBASE_") ? DATABASE_KINGBASE_AUTO : db);
			setColumn(columns.length == 0 ? null : Arrays.asList(columns));
		}

		@Override
		protected Object gainKingbaseModeCacheKey() {
			return Arrays.asList(getClass(), dialect);
		}

		@Override
		protected Connection openKingbaseModeConnection() throws SQLException {
			KingbaseModeResolverTest.Config stub = new KingbaseModeResolverTest.Config();
			stub.mode = dialect.substring("KINGBASE_".length());
			return stub.openKingbaseModeConnection();
		}

		public String gainDBVersion() {
			return "26.0";
		}

		public String gainDBUri() {
			return "jdbc:test";
		}

		public String gainDBAccount() {
			return "test";
		}

		public String gainDBPassword() {
			return "test";
		}

		@Override
		public String gainSQL(boolean prepared) {
			return "SELECT " + getTable();
		}
	}

	static Join<Long, Map<String, Object>, List<Object>> join(
			String db, String table, String... cols) {
		Join<Long, Map<String, Object>, List<Object>> j = new Join<>();
		j.setTable(table);
		j.setJoinType("<");
		j.setJoinConfig(new Config(db, table, cols));
		j.setCacheConfig(new Config(db, table, cols));
		return j;
	}

	static Object defaultValue(Class<?> c) {
		if (c == boolean.class) return false;
		if (c == int.class) return 0;
		if (c == long.class) return 0L;
		return null;
	}

	static Map<String, Object> run(Config c, String[] labels, String[] tables, Object[][] values)
			throws Exception {
		KingbaseCompatibilityTest.installJsonParser();
		final int[] row = {-1};
		ResultSetMetaData md =
				(ResultSetMetaData)
						Proxy.newProxyInstance(
								JoinColumnOwnershipTest.class.getClassLoader(),
								new Class[] {ResultSetMetaData.class},
								(p, m, a) -> {
									if (m.getName().equals("getColumnCount")) return labels.length;
									if (m.getName().equals("getColumnLabel")
											|| m.getName().equals("getColumnName"))
										return labels[(int) a[0] - 1];
									if (m.getName().equals("getTableName"))
										return tables[(int) a[0] - 1];
									return defaultValue(m.getReturnType());
								});
		ResultSet rs =
				(ResultSet)
						Proxy.newProxyInstance(
								JoinColumnOwnershipTest.class.getClassLoader(),
								new Class[] {ResultSet.class},
								(p, m, a) -> {
									if (m.getName().equals("next")) return ++row[0] < values.length;
									if (m.getName().equals("getMetaData")) return md;
									if (m.getName().equals("getObject"))
										return values[row[0]][(int) a[0] - 1];
									return defaultValue(m.getReturnType());
								});
		AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> e =
				new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {
					@Override
					public ResultSet executeQuery(
							SQLConfig<Long, Map<String, Object>, List<Object>> cfg, String sql) {
						return rs;
					}

					@Override
					protected Object getValue(
							SQLConfig<Long, Map<String, Object>, List<Object>> cfg,
							ResultSet r,
							ResultSetMetaData meta,
							int row,
							Map<String, Object> table,
							int col,
							String label,
							Map<String, Map<String, Object>> children,
							Map<String, String> keys)
							throws Exception {
						return r.getObject(col);
					}
				};
		return e.execute(c, false);
	}

	@Test
	public void kingbaseOracleWildcardMainStaysMainWithoutTableMetadata() throws Exception {
		Config c = new Config("KINGBASE_ORACLE", "Comment");
		c.setJoinList(Arrays.asList(join("KINGBASE_ORACLE", "User", "id", "name")));
		Map<String, Object> r =
				run(
						c,
						new String[] {"id", "userId", "content", "id", "name"},
						new String[] {"", "", "", "", ""},
						new Object[][] {
							{1L, 7L, "comment", 7L, "user"}, {2L, 8L, "second", 8L, "other"}
						});
		assertEquals(1L, r.get("id"));
		assertEquals("comment", r.get("content"));
		Map v = (Map) r.get(AbstractSQLExecutor.KEY_VICE_ITEM);
		assertEquals(7L, ((Map) v.get("User")).get("id"));
		List rows = (List) r.get(AbstractSQLExecutor.KEY_RAW_LIST);
		assertEquals(2L, ((Map) rows.get(1)).get("id"));
	}

	@Test
	public void nullFirstViceRowCannotShiftFollowingWildcardJoin() throws Exception {
		for (String db : Arrays.asList("KINGBASE_ORACLE", "KINGBASE_MYSQL", "KINGBASE_SQLSERVER")) {
			Config c = new Config(db, "Moment", "id", "userId", "content");
			c.setJoinList(Arrays.asList(join(db, "User", "id", "name"), join(db, "Comment")));
			Map<String, Object> r =
					run(
							c,
							new String[] {
								"id", "userId", "content", "id", "name", "id", "toId", "content"
							},
							new String[] {"", "", "", "", "", "", "", ""},
							new Object[][] {
								{12L, 7L, "moment", null, null, 162L, 0L, "comment"},
								{15L, 8L, "next", 8L, "user", 164L, 0L, "second"}
							});
			Map v = (Map) r.get(AbstractSQLExecutor.KEY_VICE_ITEM);
			Map user = (Map) v.get("User"), comment = (Map) v.get("Comment");
			assertFalse(user.toString(), user.containsKey("toId"));
			assertNull(user.get("id"));
			assertEquals(162L, comment.get("id"));
			assertEquals("comment", comment.get("content"));
			List rows = (List) r.get(AbstractSQLExecutor.KEY_RAW_LIST);
			Map second = (Map) ((Map) rows.get(1)).get(AbstractSQLExecutor.KEY_VICE_ITEM);
			assertEquals(8L, ((Map) second.get("User")).get("id"));
			assertEquals(164L, ((Map) second.get("Comment")).get("id"));
		}
	}

	@Test
	public void aliasesAndPaginationDoNotChangeExplicitBoundaries() throws Exception {
		Config c = new Config("KINGBASE_ORACLE", "Moment", "id:mid", "content:body");
		c.setCount(2);
		c.setJoinList(Arrays.asList(join("KINGBASE_ORACLE", "User", "id:uid", "name:label")));
		Map<String, Object> r =
				run(
						c,
						new String[] {"mid", "body", "uid", "label", "RN"},
						new String[] {"", "", "", "", ""},
						new Object[][] {{12L, "moment", 7L, "user", 1L}});
		assertEquals(12L, r.get("mid"));
		assertFalse(r.containsKey("uid"));
		assertFalse(r.containsKey("RN"));
		Map user = (Map) ((Map) r.get(AbstractSQLExecutor.KEY_VICE_ITEM)).get("User");
		assertEquals(7L, user.get("uid"));
		assertFalse(user.containsKey("RN"));
	}

	@Test
	public void explicitNullColumnDoesNotStealNextTablesId() throws Exception {
		Config c = new Config("KINGBASE_ORACLE", "Moment", "id", "content");
		c.setJoinList(
				Arrays.asList(
						join("KINGBASE_ORACLE", "User", "name"),
						join("KINGBASE_ORACLE", "Comment", "id", "content")));
		Map<String, Object> r =
				run(
						c,
						new String[] {"id", "content", "name", "id", "content"},
						new String[] {"", "", "", "", ""},
						new Object[][] {{12L, "moment", null, 162L, "comment"}});
		Map v = (Map) r.get(AbstractSQLExecutor.KEY_VICE_ITEM);
		assertTrue(((Map) v.get("User")).isEmpty());
		assertEquals(162L, ((Map) v.get("Comment")).get("id"));
	}
}
