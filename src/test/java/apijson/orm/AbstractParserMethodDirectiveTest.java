package apijson.orm;

import apijson.JSON;
import apijson.JSONParser;
import apijson.RequestMethod;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AbstractParserMethodDirectiveTest {
	private static JSONParser<? extends Map<String, Object>, ? extends List<Object>> previousJSONParser;

	@BeforeClass
	public static void setUpJSONParser() {
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

	@Test
	public void dispatchesMethodDirectivesBeforeBusinessObjectsRegardlessOfOrder() throws Exception {
		TestParser parser = new TestParser();
		Map<String, Object> request = new LinkedHashMap<>();

		Map<String, Object> delete = new LinkedHashMap<>();
		delete.put("id", 1L);
		request.put("Moment", delete);
		request.put("@delete", "Moment");

		List<Object> comments = new ArrayList<>();
		comments.add(new LinkedHashMap<String, Object>());
		request.put("Comment:new[]", comments);
		request.put("@post", "Comment:new[]");

		parser.run(request);

		assertSame(RequestMethod.DELETE, parser.methods.get("Moment"));
		assertSame(RequestMethod.POST, parser.methods.get("Comment:new[]"));
	}

	@Test
	public void keepsObjectMethodPriorityOverGlobalDirective() throws Exception {
		TestParser parser = new TestParser();
		Map<String, Object> request = new LinkedHashMap<>();
		Map<String, Object> moment = new LinkedHashMap<>();
		moment.put("@method", RequestMethod.PUT.name());
		moment.put("id", 1L);
		request.put("Moment", moment);
		request.put("@delete", "Moment");

		parser.run(request);

		assertSame(RequestMethod.PUT, parser.methods.get("Moment"));
	}

	@Test
	public void rejectsArrayMethodDirectiveValues() throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		List<Object> directive = new ArrayList<>();
		directive.add("Moment");
		request.put("@delete", directive);
		request.put("Moment", new LinkedHashMap<String, Object>());

		try {
			new TestParser().run(request);
			fail("List-valued method directive should be rejected");
		}
		catch (IllegalArgumentException e) {
			assertTrue(e.getMessage().contains("String"));
			assertTrue(e.getMessage().contains("Map"));
		}
	}

	private static final class TestParser extends AbstractParser<Long, Map<String, Object>, List<Object>> {
		private final Map<String, RequestMethod> methods = new LinkedHashMap<>();

		private TestParser() {
			super(RequestMethod.CRUD, false);
		}

		private Map<String, Object> run(Map<String, Object> request) throws Exception {
			return batchVerify(RequestMethod.CRUD, null, 0, null, request, 10, this);
		}

		@Override
		protected Map<String, Object> getRequestStructure(RequestMethod method, String tag, int version) {
			return new LinkedHashMap<>();
		}

		@Override
		protected Map<String, Object> objectVerify(RequestMethod method, String tag, int version, String name,
				Map<String, Object> request, int maxUpdateCount,
				SQLCreator<Long, Map<String, Object>, List<Object>> creator, Map<String, Object> object) {
			methods.put(request.keySet().iterator().next(), method);
			return request;
		}

		@Override
		public Object onFunctionParse(String key, String function, String parentPath, String currentName,
				Map<String, Object> currentObject, boolean containRaw) {
			return null;
		}

		@Override
		public ObjectParser<Long, Map<String, Object>, List<Object>> createObjectParser(
				Map<String, Object> request, String parentPath,
				SQLConfig<Long, Map<String, Object>, List<Object>> arrayConfig,
				boolean isSubquery, boolean isTable, boolean isArrayMainTable) {
			return null;
		}

		@Override
		public Parser<Long, Map<String, Object>, List<Object>> createParser() {
			return new TestParser();
		}

		@Override
		public FunctionParser<Long, Map<String, Object>, List<Object>> createFunctionParser() {
			return null;
		}

		@Override
		public Verifier<Long, Map<String, Object>, List<Object>> createVerifier() {
			return null;
		}

		@Override
		public SQLConfig<Long, Map<String, Object>, List<Object>> createSQLConfig() {
			return null;
		}

		@Override
		public SQLExecutor<Long, Map<String, Object>, List<Object>> createSQLExecutor() {
			return null;
		}
	}
}
