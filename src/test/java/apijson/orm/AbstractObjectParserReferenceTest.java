package apijson.orm;

import apijson.JSON;
import apijson.JSONParser;
import apijson.RequestMethod;
import apijson.orm.exception.CommonException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AbstractObjectParserReferenceTest {
	private static final String REFERENCE_KEY = "id@";
	private static final String TARGET_PATH = "Privacy/id";
	private static final String TRAILING_KEY = "afterReference";
	private static final long RESOLVED_VALUE = 42L;

	private static JSONParser<? extends Map<String, Object>, ? extends List<Object>> previousJSONParser;

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

	@Test
	public void rejectsUnresolvedPostTableReference() throws Exception {
		Fixture fixture = newFixture(RequestMethod.POST, true, null);

		assertUnsupportedReference(fixture);
	}

	@Test
	public void rejectsUnresolvedPutTableReference() throws Exception {
		Fixture fixture = newFixture(RequestMethod.PUT, true, null);

		assertUnsupportedReference(fixture);
	}

	@Test
	public void rejectsUnresolvedPostNonTableReference() throws Exception {
		Fixture fixture = newFixture(RequestMethod.POST, false, null);

		assertUnsupportedReference(fixture);
	}

	@Test
	public void keepsGetTableInvalidationForUnresolvedReference() throws Exception {
		Fixture fixture = newFixture(RequestMethod.GET, true, null);

		assertNull(fixture.objectParser.parse(fixture.name, false));
		assertEquals(TARGET_PATH, fixture.parser.lastPath);
	}

	@Test
	public void keepsDeleteTableInvalidationForUnresolvedReference() throws Exception {
		Fixture fixture = newFixture(RequestMethod.DELETE, true, null);

		assertNull(fixture.objectParser.parse(fixture.name, false));
		assertEquals(TARGET_PATH, fixture.parser.lastPath);
	}

	@Test
	public void keepsGetNonTableSkipForUnresolvedReference() throws Exception {
		Fixture fixture = newFixture(RequestMethod.GET, false, null);

		assertSame(fixture.objectParser, fixture.objectParser.parse(fixture.name, false));
		assertEquals(TARGET_PATH, fixture.parser.lastPath);
		assertFalse(fixture.objectParser.getSQLRequest().containsKey(REFERENCE_KEY));
		assertEquals(Boolean.TRUE, fixture.objectParser.getSQLRequest().get(TRAILING_KEY));
	}

	@Test
	public void resolvesPostReferenceAndContinuesParsing() throws Exception {
		Fixture fixture = newFixture(RequestMethod.POST, true, RESOLVED_VALUE);

		assertSame(fixture.objectParser, fixture.objectParser.parse(fixture.name, false));
		assertEquals(TARGET_PATH, fixture.parser.lastPath);
		assertFalse(fixture.objectParser.getSQLRequest().containsKey(REFERENCE_KEY));
		assertEquals(Long.valueOf(RESOLVED_VALUE), fixture.objectParser.getSQLRequest().get("id"));
		assertEquals(Boolean.TRUE, fixture.objectParser.getSQLRequest().get(TRAILING_KEY));
	}

	@Test
	public void rejectsUnresolvedCrudReferenceWithPostDirective() throws Exception {
		Map<String, Object> user = new LinkedHashMap<>();
		user.put(REFERENCE_KEY, TARGET_PATH);
		user.put(TRAILING_KEY, Boolean.TRUE);

		Map<String, Object> request = new LinkedHashMap<>();
		request.put("User", user);
		request.put("@post", "User");

		ReferenceParser parser = new ReferenceParser(null);
		Map<String, Object> verifiedRequest = parser.verifyCrud(request);
		Map<String, Object> verifiedUser = JSON.get(verifiedRequest, "User");
		assertSame(RequestMethod.POST, verifiedUser.get("@method"));

		try {
			parser.onObjectParse(verifiedUser, null, "User", null, false, null);
			fail("The effective POST method must reject an unresolved reference");
		}
		catch (Exception e) {
			assertUnsupportedOperation(e);
		}

		assertNotNull(parser.lastObjectParser);
		assertSame(RequestMethod.POST, parser.lastObjectParser.getMethod());
		assertEquals(TARGET_PATH, parser.lastPath);
		assertNotNull(parser.lastObjectParser.getSQLRequest());
		assertFalse(parser.lastObjectParser.getSQLRequest().containsKey(TRAILING_KEY));
	}

	private static void assertUnsupportedReference(Fixture fixture) throws Exception {
		try {
			fixture.objectParser.parse(fixture.name, false);
			fail("Unresolved POST/PUT references must be rejected");
		}
		catch (Exception e) {
			assertUnsupportedOperation(e);
		}

		assertEquals(TARGET_PATH, fixture.parser.lastPath);
		assertNotNull(fixture.objectParser.getSQLRequest());
		assertFalse("Entries after the unresolved reference must not be parsed",
				fixture.objectParser.getSQLRequest().containsKey(TRAILING_KEY));
	}

	private static void assertUnsupportedOperation(Exception e) {
		Throwable cause = e instanceof CommonException && e.getCause() != null ? e.getCause() : e;
		assertTrue("Expected UnsupportedOperationException but got " + cause.getClass().getName(),
				cause instanceof UnsupportedOperationException);
	}

	private static Fixture newFixture(RequestMethod method, boolean isTable, Object target) throws Exception {
		Map<String, Object> request = new LinkedHashMap<>();
		request.put(REFERENCE_KEY, TARGET_PATH);
		request.put(TRAILING_KEY, Boolean.TRUE);

		ReferenceParser parser = new ReferenceParser(target);
		TestObjectParser objectParser = new TestObjectParser(request, isTable);
		objectParser.setParser(parser);
		objectParser.setMethod(method);

		return new Fixture(parser, objectParser, isTable ? "User" : "metadata");
	}

	private static final class Fixture {
		private final ReferenceParser parser;
		private final TestObjectParser objectParser;
		private final String name;

		private Fixture(ReferenceParser parser, TestObjectParser objectParser, String name) {
			this.parser = parser;
			this.objectParser = objectParser;
			this.name = name;
		}
	}

	private static final class TestObjectParser
			extends AbstractObjectParser<Long, Map<String, Object>, List<Object>> {
		private TestObjectParser(Map<String, Object> request, boolean isTable) throws Exception {
			super(request, null, null, false, isTable, false);
		}

		@Override
		public SQLConfig<Long, Map<String, Object>, List<Object>> newSQLConfig(RequestMethod method,
				String table, String alias, Map<String, Object> request,
				List<Join<Long, Map<String, Object>, List<Object>>> joinList, boolean isProcedure) {
			return null;
		}
	}

	private static final class ReferenceParser
			extends AbstractParser<Long, Map<String, Object>, List<Object>> {
		private final Object target;
		private String lastPath;
		private TestObjectParser lastObjectParser;

		private ReferenceParser(Object target) {
			super(RequestMethod.CRUD, false);
			this.target = target;
		}

		private Map<String, Object> verifyCrud(Map<String, Object> request) throws Exception {
			return batchVerify(RequestMethod.CRUD, null, 0, null, request, 10, this);
		}

		@Override
		public Object getValueByPath(String valuePath) {
			lastPath = valuePath;
			return target;
		}

		@Override
		protected Map<String, Object> getRequestStructure(RequestMethod method, String tag, int version) {
			return new LinkedHashMap<>();
		}

		@Override
		protected Map<String, Object> objectVerify(RequestMethod method, String tag, int version, String name,
				Map<String, Object> request, int maxUpdateCount,
				SQLCreator<Long, Map<String, Object>, List<Object>> creator, Map<String, Object> object) {
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
				boolean isSubquery, boolean isTable, boolean isArrayMainTable) throws Exception {
			TestObjectParser objectParser = new TestObjectParser(request, isTable);
			objectParser.setParser(this);
			lastObjectParser = objectParser;
			return objectParser;
		}

		@Override
		public Parser<Long, Map<String, Object>, List<Object>> createParser() {
			return new ReferenceParser(target);
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
