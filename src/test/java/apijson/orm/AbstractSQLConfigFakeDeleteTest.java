package apijson.orm;

import apijson.JSON;
import apijson.JSONParser;
import apijson.RequestMethod;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AbstractSQLConfigFakeDeleteTest {
	private static final String TABLE = "User";
	private static final Map<String, Map<String, Object>> EAGERLY_INITIALIZED_MAP =
			AbstractVerifier.ACCESS_FAKE_DELETE_MAP;

	private static JSONParser<? extends Map<String, Object>, ? extends List<Object>> previousJSONParser;
	private Map<String, Map<String, Object>> previousFakeDeleteMap;

	@BeforeClass
	public static void installJSONParser() {
		previousJSONParser = JSON.DEFAULT_JSON_PARSER;
		JSON.DEFAULT_JSON_PARSER = new JSONParser<Map<String, Object>, List<Object>>() {
			@Override
			public Map<String, Object> createJSONObject() {
				return new LinkedHashMap<>();
			}

			@Override
			public List<Object> createJSONArray() {
				return new ArrayList<>();
			}

			@Override
			public Object parse(Object json) {
				return json;
			}

			@Override
			@SuppressWarnings("unchecked")
			public Map<String, Object> parseObject(Object json) {
				return (Map<String, Object>) json;
			}

			@Override
			public <T> T parseObject(Object json, Class<T> clazz) {
				return clazz.cast(json);
			}

			@Override
			@SuppressWarnings("unchecked")
			public List<Object> parseArray(Object json) {
				return (List<Object>) json;
			}

			@Override
			@SuppressWarnings("unchecked")
			public <T> List<T> parseArray(Object json, Class<T> clazz) {
				return (List<T>) json;
			}

			@Override
			public String toJSONString(Object obj, boolean format) {
				return String.valueOf(obj);
			}
		};
	}

	@AfterClass
	public static void restoreJSONParser() {
		JSON.DEFAULT_JSON_PARSER = previousJSONParser;
	}

	@Before
	public void resetFakeDeleteMap() {
		previousFakeDeleteMap = AbstractVerifier.ACCESS_FAKE_DELETE_MAP;
		AbstractVerifier.ACCESS_FAKE_DELETE_MAP = new LinkedHashMap<>();
	}

	@After
	public void restoreFakeDeleteMap() {
		AbstractVerifier.ACCESS_FAKE_DELETE_MAP = previousFakeDeleteMap;
	}

	@Test
	public void eagerlyInitializesFakeDeleteMap() {
		assertNotNull(EAGERLY_INITIALIZED_MAP);
	}

	@Test
	public void keepsGetWhenTableHasNoFakeDeleteConfig() throws Exception {
		SQLConfig<Long, Map<String, Object>, List<Object>> config = newConfig(RequestMethod.GET, true);

		assertEquals(RequestMethod.GET, config.getMethod());
	}

	@Test
	public void rejectsDeleteWhenTableConfigIsMissing() throws Exception {
		assertInvalidConfig(null);
	}

	@Test
	public void rejectsDeleteWhenTableConfigIsEmpty() throws Exception {
		assertInvalidConfig(new LinkedHashMap<String, Object>());
	}

	@Test
	public void rejectsDeleteWhenDeletedKeyIsBlank() throws Exception {
		Map<String, Object> tableConfig = new LinkedHashMap<>();
		tableConfig.put(AbstractSQLConfig.KEY_DELETED_KEY, "   ");

		assertInvalidConfig(tableConfig);
	}

	@Test
	public void rejectsDeleteWhenDeletedKeyIsNotAString() throws Exception {
		Map<String, Object> tableConfig = new LinkedHashMap<>();
		tableConfig.put(AbstractSQLConfig.KEY_DELETED_KEY, 123);

		assertInvalidConfig(tableConfig);
	}

	@Test
	public void rewritesConfiguredDeleteAsPut() throws Exception {
		Map<String, Object> tableConfig = new LinkedHashMap<>();
		tableConfig.put(AbstractSQLConfig.KEY_DELETED_KEY, "deletedFlag");
		tableConfig.put(AbstractSQLConfig.KEY_DELETED_VALUE, 1);
		AbstractVerifier.ACCESS_FAKE_DELETE_MAP.put(TABLE, tableConfig);

		SQLConfig<Long, Map<String, Object>, List<Object>> config = newConfig(RequestMethod.DELETE, true);

		assertEquals(RequestMethod.PUT, config.getMethod());
		assertNotNull(config.getContent());
		assertEquals(Integer.valueOf(1), config.getContent().get("deletedFlag"));
	}

	@Test
	public void keepsDeleteWhenFakeDeleteIsDisabled() throws Exception {
		SQLConfig<Long, Map<String, Object>, List<Object>> config = newConfig(RequestMethod.DELETE, false);

		assertEquals(RequestMethod.DELETE, config.getMethod());
	}

	private static void assertInvalidConfig(Map<String, Object> tableConfig) throws Exception {
		if (tableConfig != null) {
			AbstractVerifier.ACCESS_FAKE_DELETE_MAP.put(TABLE, tableConfig);
		}

		try {
			SQLConfig<Long, Map<String, Object>, List<Object>> config =
					newConfig(RequestMethod.DELETE, true);
			fail("Invalid fake-delete config must throw instead of returning " + config.getMethod());
		}
		catch (IllegalArgumentException e) {
			assertNotNull(e.getMessage());
			assertTrue(e.getMessage().contains(TABLE));
			assertTrue(e.getMessage().contains(AbstractSQLConfig.KEY_DELETED_KEY));
			assertTrue(e.getMessage().contains("String"));
		}
	}

	private static SQLConfig<Long, Map<String, Object>, List<Object>> newConfig(
			RequestMethod method, final boolean fakeDelete) throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("id", 1L);

		AbstractSQLConfig.Callback<Long, Map<String, Object>, List<Object>> callback =
				new AbstractSQLConfig.SimpleCallback<Long, Map<String, Object>, List<Object>>() {
					@Override
					public SQLConfig<Long, Map<String, Object>, List<Object>> getSQLConfig(RequestMethod method,
							String database, String datasource, String namespace, String catalog, String schema,
							String table) {
						return new AbstractSQLConfig<Long, Map<String, Object>, List<Object>>(method, table) {
							@Override
							public boolean isFakeDelete() {
								return fakeDelete;
							}

							@Override
							public String gainDBVersion() {
								return "8.0.0";
							}

							@Override
							public String gainDBUri() {
								return "jdbc:mysql://localhost/test";
							}

							@Override
							public String gainDBAccount() {
								return "test";
							}

							@Override
							public String gainDBPassword() {
								return "test";
							}
						};
					}
				};

		return AbstractSQLConfig.newSQLConfig(method, TABLE, null, request, null, false, callback);
	}
}
