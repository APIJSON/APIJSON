package apijson.orm;

import apijson.RequestMethod;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.lang.reflect.Proxy;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** SQL, binding and error snapshots recorded against 8.2.0 plus independent upstream updates.
 * These offline contracts do not substitute for native-server response regression tests. */
public class NativeDatabaseBaselineTest {
	interface Operation {
		Object run(Config c) throws Exception;
	}

	static class Config extends AbstractSQLConfig<Long, Map<String, Object>, List<Object>> {
		final String version;

		Config(String db, String version) {
			super(RequestMethod.GET, "User");
			this.version = version;
			setDatabase(db);
			setSchema("sys");
			setPrepared(true);
			setPreparedValueList(new ArrayList<>());
		}

		public String gainDBVersion() {
			return version;
		}

		public String gainDBUri() {
			throw new AssertionError("Native SQL generation must not open a connection");
		}

		public String gainDBAccount() {
			return "unused";
		}

		public String gainDBPassword() {
			return "unused";
		}

		String page(String sql) {
			return gainOraclePageSQL(sql);
		}

		String having(String value) throws Exception {
			return gainHavingItem(getQuote(), getTable(), null, "h", value, false);
		}

		@Override
		protected void onJoinComplexRelation(
				String sql,
				String quote,
				Join<Long, Map<String, Object>, List<Object>> join,
				String table,
				List<Join.On> onList,
				Join.On on) {}

		String regexJoin(boolean ignoreCase) {
			return relationJoin(ignoreCase ? "*~" : "~");
		}

		String relationJoin(String relation) {
			Join<Long, Map<String, Object>, List<Object>> join = new Join<>();
			Join.On on = new Join.On();
			on.setKey("pattern");
			on.setTargetTable("User");
			on.setTargetKey("name");
			on.setRelateType(relation);
			return concatJoinOn("", getQuote(), join, "Comment", Arrays.asList(on));
		}
	}

	static void record(
			List<String> rows, String db, String version, String name, Operation operation) {
		Config c = new Config(db, version);
		String value;
		try {
			value = String.valueOf(operation.run(c));
		} catch (Exception e) {
			value = "THROWS:" + e.getClass().getName() + ":" + e.getMessage();
		}
		rows.add(
				db
						+ "|"
						+ version
						+ "|"
						+ name
						+ "|"
						+ value.replace("\r", "").replace("\n", "\\n")
						+ "|parameters="
						+ String.valueOf(c.getPreparedValueList()));
	}

	static List<String> snapshot() throws Exception {
		AbstractSQLConfigJoinCountTest.installJSONParser();
		try {
			List<String> rows = new ArrayList<>();
			List<String> databases = new ArrayList<>(AbstractSQLConfig.DATABASE_LIST);
			databases.removeIf(db -> db.startsWith("KINGBASE_") || "SQLITE".equals(db));
			databases.add("SQLITE");
			databases.add("CUSTOM_DATABASE");
			for (String db : databases) {
				for (String version : Arrays.asList("11.2", "12.1", "12.2", "19.0")) {
					record(rows, db, version, "select", c -> c.gainSQL(true));
					record(rows, db, version, "explain-page", c -> {
						c.setExplain(true).setCount(5).setPage(2);
						return c.gainSQL(true);
					});
					record(rows, db, version, "having-column", c -> c.having("id>0"));
					record(rows, db, version, "having-complex", c -> c.having("count(id)>1&count(age)<5"));
					record(rows, db, version, "join-contains", c -> c.relationJoin("<>"));
					record(
							rows,
							db,
							version,
							"filter",
							c -> {
								c.putWhere("name", "Alice", false);
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"page",
							c -> {
								c.setCount(5).setPage(2);
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"natural-key-page",
							c -> {
								c.setTable("NaturalKeyTable");
								c.setColumn(Arrays.asList("code", "name"));
								c.setOrder("code+").setCount(5);
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"page-ties",
							c -> {
								c.setOrder("name+").setCount(5);
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"group-page",
							c -> {
								c.setColumn(Arrays.asList("age", "count(id):n"));
								c.setGroup("age").setCount(5);
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"alias",
							c -> {
								c.setColumn(Arrays.asList("id:uid", "name"));
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"json-length",
							c -> {
								c.setColumn(Arrays.asList("json_length(payload):n"));
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"having",
							c -> {
								c.setHaving(
										Collections.<String, Object>singletonMap(
												"h", "json_length(payload)>0"));
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"having-match",
							c -> c.having("match(name)AGAINST('hello')>0"));
					record(
							rows,
							db,
							version,
							"date-format",
							c -> {
								c.setColumn(
										Arrays.asList(
												"date_format(created,'%Y-%m-%d %H:%i:%s'):d"));
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"date-format-column",
							c -> {
								c.setColumn(Arrays.asList("date_format(created,formatColumn):d"));
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"regex-case",
							c -> c.gainRegExpString("name", "name", "A.*", false));
					record(
							rows,
							db,
							version,
							"regex-ignore-case",
							c -> c.gainRegExpString("name", "name", "A.*", true));
					record(
							rows,
							db,
							version,
							"regex-pattern",
							c -> c.gainRegExpString("name", "name", "[ab]+", false));
					record(
							rows,
							db,
							version,
							"json-contains",
							c -> c.gainContainString("payload", "payload", new Object[] {1}, 0));
					record(
							rows,
							db,
							version,
							"json-contains-string",
							c -> c.gainContainString("payload", "payload", new Object[] {"1"}, 0));
					record(
							rows,
							db,
							version,
							"json-contains-boolean",
							c -> c.gainContainString("payload", "payload", new Object[] {true}, 0));
					record(rows, db, version, "join-regex-case", c -> c.regexJoin(false));
					record(rows, db, version, "join-regex-ignore-case", c -> c.regexJoin(true));
					record(
							rows,
							db,
							version,
							"procedure",
							c -> {
								c.setProcedure("getComment(1,2)");
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"insert",
							c -> {
								c.setMethod(RequestMethod.POST);
								c.setColumn(Arrays.asList("id", "name"));
								c.setValues(Arrays.asList(Arrays.<Object>asList(7L, "Alice")));
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"update",
							c -> {
								c.setMethod(RequestMethod.PUT);
								c.setContent(
										Collections.<String, Object>singletonMap("name", "Bob"));
								c.putWhere("id", 7L, false);
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"delete",
							c -> {
								c.setMethod(RequestMethod.DELETE);
								c.putWhere("id", 7L, false);
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"head",
							c -> {
								c.setMethod(RequestMethod.HEAD);
								c.setCount(5);
								return c.gainSQL(true);
							});
					record(
							rows,
							db,
							version,
							"metadata-id",
							c -> {
								c.setTable("Table");
								return c.getIdKey();
							});
					record(
							rows,
							db,
							version,
							"callback-id",
							c ->
									new AbstractSQLConfig.SimpleCallback<
											Long, Map<String, Object>, List<Object>>() {
										public SQLConfig<Long, Map<String, Object>, List<Object>>
												getSQLConfig(
														RequestMethod method,
														String database,
														String datasource,
														String namespace,
														String catalog,
														String schema,
														String table) {
											Config result = new Config(database, version);
											result.setMethod(method);
											result.setTable(table);
											return result;
										}
									}.getIdKey(db, null, null, null, "sys", "Table"));
					record(
							rows,
							db,
							version,
							"group",
							c -> {
								c.setGroup("age");
								return c.gainGroupString(true);
							});
					record(
							rows,
							db,
							version,
							"explicit-page-sql",
							c -> {
								c.setCount(5).setPage(2);
								return c.page("SELECT id FROM t ORDER BY id");
							});
					record(
							rows,
							db,
							version,
							"join-order",
							c -> {
								Config child = new Config(db, version);
								child.setTable("Comment");
								child.setOrder("id+");
								Join<Long, Map<String, Object>, List<Object>> join = new Join<>();
								join.setTable("Comment");
								join.setJoinType("<");
								join.setJoinConfig(child);
								Join.On on = new Join.On();
								on.setKey("userId");
								on.setTargetTable("User");
								on.setTargetKey("id");
								join.setOnList(Arrays.asList(on));
								c.setJoinList(Arrays.asList(join));
								return c.gainJoinString() + ";outer=" + c.gainOrderString(true);
							});
				}
			}
			return rows;
		} finally {
			AbstractSQLConfigJoinCountTest.restoreJSONParser();
		}
	}

	@Test
	public void nativeSqlBindingsAndErrorsMatchIsolatedUpstream() throws Exception {
		InputStream stream = getClass().getResourceAsStream("/native-isolated-upstream.tsv");
		assertNotNull("Missing baseline captured from the isolated upstream reference", stream);
		List<String> expected = new ArrayList<>();
		try (BufferedReader reader =
				new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
			for (String line; (line = reader.readLine()) != null; ) expected.add(line);
		}
		List<String> actual = snapshot();
		assertEquals("Snapshot size changed", expected.size(), actual.size());
		for (int i = 0; i < expected.size(); i++)
			assertEquals("Native contract changed at case " + i, expected.get(i), actual.get(i));
	}

	@Test
	public void nativeStatementExecutionKeepsOriginalJdbcCalls() throws Exception {
		List<String> calls = new ArrayList<>();
		Statement statement =
				(Statement)
						Proxy.newProxyInstance(
								getClass().getClassLoader(),
								new Class<?>[] {Statement.class},
								(p, m, a) -> {
									calls.add(m.getName() + ":" + (a == null ? 0 : a.length));
									if (m.getName().equals("execute")) return false;
									if (m.getName().equals("getResultSet")) return null;
									throw new AssertionError("Unexpected JDBC call " + m.getName());
								});
		new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {}.execute(
				statement, "BEGIN p(); END;");
		assertEquals(Arrays.asList("execute:1", "getResultSet:0"), calls);
	}

	/** Run against the independent reference when explicitly regenerating the baseline. */
	public static void main(String[] args) throws Exception {
		Files.write(Paths.get(args[0]), snapshot(), StandardCharsets.UTF_8);
	}
}
