package apijson.orm;

import apijson.JSON;
import apijson.JSONParser;
import apijson.Log;
import apijson.RequestMethod;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/** Keep the inherited text-call contract; no additional procedure capabilities. */
public class KingbaseProcedureTest {
	private static final List<String> DATABASES = Arrays.asList(
			"KINGBASE_MYSQL", "KINGBASE_ORACLE", "KINGBASE_SQLSERVER",
			"MYSQL", "ORACLE", "SQLSERVER", "POSTGRESQL", "KINGBASE", "CUSTOM_DATABASE");
	private JSONParser<?, ?> previousParser;
	private boolean previousDebug;

	@Before public void setup() {
		previousParser = JSON.DEFAULT_JSON_PARSER;
		previousDebug = Log.DEBUG;
		KingbaseCompatibilityTest.installJsonParser();
		Log.DEBUG = false;
	}
	@After public void restore() {
		JSON.DEFAULT_JSON_PARSER = previousParser;
		Log.DEBUG = previousDebug;
	}

	private static AbstractSQLConfig<Long, Map<String, Object>, List<Object>> config(String database) {
		AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = KingbaseSemanticsRegressionTest.config(database);
		c.setSchema("app");
		return c;
	}

	@Test public void textAndNoArgumentCallsKeepTheirSqlWithoutBoundParameters() throws Exception {
		for (String database : DATABASES) {
			AbstractSQLConfig<Long, Map<String, Object>, List<Object>> c = config(database);
			String quote = c.getQuote();
			for (String invocation : Arrays.asList("p()", "p('text',7,true,NULL)")) {
				c.setProcedure("app." + invocation);
				for (boolean prepared : new boolean[] {true, false}) {
					assertEquals("CALL " + quote + "app" + quote + "." + invocation, c.gainSQL(prepared));
					assertTrue(c.getPreparedValueList().isEmpty());
				}
			}
		}
	}

	@Test public void jsonEntryKeepsTheInheritedTextFormattingAndValidation() throws Exception {
		Map<String, Object> input = new LinkedHashMap<>();
		input.put("a", "O'Reilly"); input.put("b", null); input.put("c", 42);
		for (String database : DATABASES) {
			Call call = new Call(config(database), input);
			call.invoke("p(a,b,c)");
			// Record the baseline's existing formatting, including its null limitation.
			assertEquals("p('O'Reilly','null','null')", call.config.getProcedure());
			assertTrue(call.sql.startsWith("CALL "));
			assertTrue(call.values.isEmpty());
			assertEquals(1, call.configCreations); assertEquals(1, call.executions);
			Call noArguments = new Call(config(database), Collections.emptyMap());
			noArguments.invoke("p()");
			assertEquals("p()", noArguments.config.getProcedure());
			Call invalid = new Call(config(database), input);
			assertThrows(IllegalArgumentException.class, () -> invalid.invoke("invalid name()"));
			assertEquals(0, invalid.configCreations);
		}
	}

	@Test public void processCallsStillReachTheOriginalExecutorExtensionPoint() throws Exception {
		for (String database : DATABASES) {
			SQLException expected = new SQLException("original executor hook");
			PreparedStatement statement = (PreparedStatement) Proxy.newProxyInstance(
					getClass().getClassLoader(), new Class<?>[] {PreparedStatement.class},
					(p, method, args) -> { throw new AssertionError("Procedure bypassed the original execute hook"); });
			AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> executor =
					new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {
				@Override public PreparedStatement getStatement(SQLConfig<Long, Map<String, Object>, List<Object>> c) {
					return statement;
				}
				@Override public ResultSet execute(Statement actual, String sql) throws Exception {
					assertSame(statement, actual);
					assertTrue(sql, sql.startsWith("CALL "));
					throw expected;
				}
			};
			assertSame(expected, assertThrows(SQLException.class,
					() -> executor.execute(config(database).setProcedure("p()"), true)));
		}
	}

	@Test public void statementExecutionDoesNotAdvanceToImplicitOrLaterResults() throws Exception {
		List<String> calls = new ArrayList<>();
		Statement statement = (Statement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] {Statement.class}, (p, method, args) -> {
			calls.add(method.getName() + ":" + (args == null ? 0 : args.length));
			if (method.getName().equals("execute")) return false;
			if (method.getName().equals("getResultSet")) return null;
			throw new AssertionError("Unexpected JDBC call: " + method.getName());
		});
		AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> executor =
				new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {};
		assertNull(executor.execute(statement, "BEGIN p(); END;"));
		assertEquals(Arrays.asList("execute:1", "getResultSet:0"), calls);
	}

	private static final class Call extends AbstractObjectParser<Long, Map<String, Object>, List<Object>> {
		final AbstractSQLConfig<Long, Map<String, Object>, List<Object>> config;
		final Map<String, Object> input;
		int configCreations, executions;
		String sql;
		List<Object> values;
		Call(AbstractSQLConfig<Long, Map<String, Object>, List<Object>> config, Map<String, Object> input) throws Exception {
			super(new LinkedHashMap<>(input), "", null, false, true, false);
			this.config = config; this.input = input;
			response = new LinkedHashMap<>(); setParser(new ParserStub());
		}
		void invoke(String function) throws Exception { parseFunction("@out()", "@out", function, "", "Probe", input, false); }
		@Override public SQLConfig<Long, Map<String, Object>, List<Object>> newSQLConfig(boolean procedure) {
			assertTrue(procedure); configCreations++; return config;
		}
		@Override public Map<String, Object> parseResponse(SQLConfig<Long, Map<String, Object>, List<Object>> c, boolean procedure) throws Exception {
			assertTrue(procedure); executions++; sql = c.gainSQL(true); values = new ArrayList<>(c.getPreparedValueList());
			return new LinkedHashMap<>();
		}
		@Override public SQLConfig<Long, Map<String, Object>, List<Object>> newSQLConfig(RequestMethod m, String t, String a,
				Map<String, Object> r, List<Join<Long, Map<String, Object>, List<Object>>> j, boolean p) { throw new AssertionError(); }
	}

	private static final class ParserStub extends AbstractParser<Long, Map<String, Object>, List<Object>> {
		ParserStub() { super(RequestMethod.GET, false); }
		@Override public Object onFunctionParse(String key, String function, String parentPath, String currentName,
				Map<String, Object> currentObject, boolean raw) { throw new AssertionError("Unexpected Java function call"); }
		@Override public void putQueryResult(String path, Object result) {}
		@Override public Parser<Long, Map<String, Object>, List<Object>> createParser() { return this; }
		@Override public FunctionParser<Long, Map<String, Object>, List<Object>> createFunctionParser() { return null; }
		@Override public Verifier<Long, Map<String, Object>, List<Object>> createVerifier() { return null; }
		@Override public SQLConfig<Long, Map<String, Object>, List<Object>> createSQLConfig() { return null; }
		@Override public SQLExecutor<Long, Map<String, Object>, List<Object>> createSQLExecutor() { return null; }
		@Override public ObjectParser<Long, Map<String, Object>, List<Object>> createObjectParser(Map<String, Object> r,
				String p, SQLConfig<Long, Map<String, Object>, List<Object>> a, boolean s, boolean t, boolean m) { return null; }
	}
}
