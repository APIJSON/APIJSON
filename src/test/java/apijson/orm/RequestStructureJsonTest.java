package apijson.orm;

import apijson.RequestMethod;
import apijson.Log;
import org.junit.Test;
import java.util.*;
import java.sql.Types;
import javax.sql.rowset.serial.SerialClob;
import static org.junit.Assert.*;

public class RequestStructureJsonTest {
	@Test
	public void readsClobRulesWithoutChangingOrdinaryClobSemantics() throws Exception {
		KingbaseCompatibilityTest.installJsonParser();
		boolean debug = Log.DEBUG;
		try {
			Log.DEBUG = true;
			for (String table : Arrays.asList("Request", "Response")) {
				TestParser parser = new TestParser();
				Map<String, Object> structure =
						parser.getStructure(table, "GETS", "__json_clob_regression__", 1);
				assertEquals(Boolean.TRUE, structure.get("enabled"));
				assertEquals(
						"{\"enabled\":true}",
						parser.executor.mapResultValue(
								parser.createSQLConfig(),
								new SerialClob("{\"enabled\":true}".toCharArray()),
								Types.CLOB,
								"clob",
								"description"));
			}
		} finally {
			Log.DEBUG = debug;
		}
	}

	@Test
	public void readsCachedClobRulesAndPreservesObjectRules() throws Exception {
		KingbaseCompatibilityTest.installJsonParser();
		String tag = "__cached_json_clob_regression__";
		String key = AbstractVerifier.getCacheKeyForRequest("GETS", tag);
		SortedMap<Integer, Map<String, Object>> versions = new TreeMap<>();
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("structure", "{\"enabled\":true}");
		versions.put(1, row);
		SortedMap<Integer, Map<String, Object>> previous =
				AbstractVerifier.REQUEST_MAP.put(key, versions);
		try {
			TestParser parser = new TestParser();
			assertEquals(
					Boolean.TRUE, parser.getStructure("Request", "GETS", tag, 1).get("enabled"));
			// Native string-backed rules retain the upstream contract in this adaptation.
			for (String database : Arrays.asList("MYSQL", "ORACLE", "SQLSERVER", "KINGBASE")) {
				assertThrows(
						ClassCastException.class,
						() -> new TestParser(database).getStructure("Request", "GETS", tag, 1));
			}
			Map<String, Object> rule = new LinkedHashMap<>();
			rule.put("enabled", true);
			row.put("structure", rule);
			assertSame(rule, parser.getStructure("Request", "GETS", tag, 1));
			for (String database : Arrays.asList("MYSQL", "ORACLE", "SQLSERVER", "KINGBASE")) {
				assertSame(rule, new TestParser(database).getStructure("Request", "GETS", tag, 1));
			}
		} finally {
			if (previous == null) AbstractVerifier.REQUEST_MAP.remove(key);
			else AbstractVerifier.REQUEST_MAP.put(key, previous);
		}
	}

	static class TestParser extends AbstractParser<Long, Map<String, Object>, List<Object>> {
		final String database;
		final AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> executor =
				new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {
					@Override
					public Map<String, Object> execute(
							SQLConfig<Long, Map<String, Object>, List<Object>> config,
							boolean unknown)
							throws Exception {
						Map<String, Object> row = new LinkedHashMap<>();
						row.put(
								"structure",
								mapResultValue(
										config,
										new SerialClob("{\"enabled\":true}".toCharArray()),
										Types.CLOB,
										"clob",
										"structure"));
						return row;
					}
				};

		TestParser() {
			this(SQLConfig.DATABASE_KINGBASE_ORACLE);
		}

		TestParser(String database) {
			super(RequestMethod.GETS, false);
			this.database = database;
		}

		@Override
		public SQLConfig<Long, Map<String, Object>, List<Object>> createSQLConfig() {
			return new AbstractSQLConfig<Long, Map<String, Object>, List<Object>>(
					RequestMethod.GET, "Request") {
				@Override
				public String gainSQLDatabase() {
					return database;
				}

				@Override
				public String gainDBVersion() {
					return "9";
				}

				@Override
				public String gainDBUri() {
					return "jdbc:kingbase8://localhost/test";
				}

				@Override
				public String gainDBAccount() {
					return "test";
				}

				@Override
				public String gainDBPassword() {
					return "test";
				}
			}.setDatabase(database);
		}

		@Override
		public SQLExecutor<Long, Map<String, Object>, List<Object>> createSQLExecutor() {
			return executor;
		}

		@Override
		public Parser<Long, Map<String, Object>, List<Object>> createParser() {
			return new TestParser(database);
		}

		@Override
		public Verifier<Long, Map<String, Object>, List<Object>> createVerifier() {
			return null;
		}

		@Override
		public FunctionParser<Long, Map<String, Object>, List<Object>> createFunctionParser() {
			return null;
		}

		@Override
		public Object onFunctionParse(
				String key,
				String function,
				String parentPath,
				String currentName,
				Map<String, Object> object,
				boolean raw) {
			return null;
		}

		@Override
		public ObjectParser<Long, Map<String, Object>, List<Object>> createObjectParser(
				Map<String, Object> request,
				String path,
				SQLConfig<Long, Map<String, Object>, List<Object>> array,
				boolean sub,
				boolean table,
				boolean main) {
			return null;
		}
	}
}
