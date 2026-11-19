package apijson.orm;

import apijson.RequestMethod;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.Assume;
import java.io.*;
import java.lang.reflect.Proxy;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.rowset.serial.SerialClob;
import static org.junit.Assert.*;

/** V9R1C10 metadata contracts, independent of a live database. */
public class KingbaseTypeMappingTest {
	private final AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> executor =
			new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {};
	private static final String[] MODES = {"MYSQL", "ORACLE", "SQLSERVER"};

	@BeforeClass
	public static void parser() {
		KingbaseCompatibilityTest.installJsonParser();
	}

	private AbstractSQLConfig<Long, Map<String, Object>, List<Object>> config(String mode) {
		return new AbstractSQLConfig<Long, Map<String, Object>, List<Object>>(
				RequestMethod.GET, "types") {
			@Override
			public String gainDBVersion() {
				return "V009R001C010";
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

			@Override
			public boolean isKingBaseMySQL() {
				return "MYSQL".equals(mode);
			}

			@Override
			public boolean isKingBaseOracle() {
				return "ORACLE".equals(mode);
			}

			@Override
			public boolean isKingBaseSQLServer() {
				return "SQLSERVER".equals(mode);
			}
		};
	}

	private Object map(String mode, Object value, int jdbc, String type) throws Exception {
		return executor.mapResultValue(config(mode), value, jdbc, type, "value");
	}

	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, handler);
	}

	private ResultSet cursor(AtomicBoolean closed, boolean fail) {
		AtomicInteger position = new AtomicInteger();
		ResultSetMetaData md =
				proxy(
						ResultSetMetaData.class,
						(p, m, a) -> {
							if (m.getName().equals("getColumnCount")) return 2;
							if (m.getName().equals("getColumnLabel"))
								return (int) a[0] == 1 ? "amount" : "enabled";
							if (m.getName().equals("getColumnType"))
								return (int) a[0] == 1 ? Types.NUMERIC : Types.BIT;
							if (m.getName().equals("getColumnTypeName"))
								return (int) a[0] == 1 ? "numeric" : "bool";
							throw new UnsupportedOperationException(m.getName());
						});
		return proxy(
				ResultSet.class,
				(p, m, a) -> {
					if (m.getName().equals("getMetaData")) return md;
					if (m.getName().equals("next")) return position.getAndIncrement() == 0;
					if (m.getName().equals("close")) {
						closed.set(true);
						return null;
					}
					if (m.getName().equals("getObject")) {
						if (fail) throw new SQLException("read failed");
						return (int) a[0] == 1 ? new BigDecimal("1234567890.123456789") : true;
					}
					throw new UnsupportedOperationException(m.getName());
				});
	}

	@Test
	public void materializesCursorsAndClosesOnSuccessAndFailure() throws Exception {
		for (String mode : MODES) {
			AtomicBoolean closed = new AtomicBoolean();
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("amount", "1234567890.123456789");
			row.put("enabled", true);
			assertEquals(
					Collections.singletonList(row),
					map(mode, cursor(closed, false), Types.REF_CURSOR, "refcursor"));
			assertTrue(closed.get());
			closed.set(false);
			try {
				map(mode, cursor(closed, true), Types.REF_CURSOR, "refcursor");
				fail("Expected read failure");
			} catch (SQLException expected) {
				assertEquals("read failed", expected.getMessage());
			}
			assertTrue(closed.get());
		}
	}

	@Test
	public void mapsArraysUsingElementMetadataAndFreesThem() throws Exception {
		for (String mode : MODES) {
			AtomicBoolean freed = new AtomicBoolean();
			java.sql.Array array =
					proxy(
							java.sql.Array.class,
							(p, m, a) -> {
								if (m.getName().equals("getArray"))
									return new String[] {"t", "f", null};
								if (m.getName().equals("getBaseType")) return Types.BIT;
								if (m.getName().equals("getBaseTypeName")) return "bool";
								if (m.getName().equals("free")) {
									freed.set(true);
									return null;
								}
								throw new UnsupportedOperationException(m.getName());
							});
			assertEquals(Arrays.asList(true, false, null), map(mode, array, Types.ARRAY, "_bool"));
			assertTrue(freed.get());
			assertEquals(
					Arrays.asList(Arrays.asList(true, false)),
					map(mode, new String[][] {{"t", "f"}}, Types.ARRAY, "bool[]"));
			assertEquals(
					Arrays.asList("AQI=", "AwQ="),
					map(mode, new byte[][] {{1, 2}, {3, 4}}, Types.ARRAY, "_bytea"));
			assertEquals("10101", map(mode, "10101", Types.BIT, "varbit"));
		}
	}

	@Test
	public void mapsStructRowIdXmlAndTimeWithoutLeakingJdbcObjects() throws Exception {
		for (String mode : MODES) {
			Struct struct =
					proxy(
							Struct.class,
							(p, m, a) -> {
								if (m.getName().equals("getAttributes"))
									return new Object[] {
										new BigDecimal("1.234"), new byte[] {1, 2}, null
									};
								throw new UnsupportedOperationException(m.getName());
							});
			assertEquals(
					Arrays.asList("1.234", "AQI=", null),
					map(mode, struct, Types.STRUCT, "address"));
			RowId id = proxy(RowId.class, (p, m, a) -> "(1,2)");
			assertEquals("(1,2)", map(mode, id, Types.ROWID, "rowid"));
			SQLXML xml = proxy(SQLXML.class, (p, m, a) -> "<root>中文</root>");
			assertEquals("<root>中文</root>", map(mode, xml, Types.SQLXML, "xml"));
			assertEquals("12:34:56", map(mode, Time.valueOf("12:34:56"), Types.TIME, "time"));
		}
		assertEquals(
				"2026-01-01",
				map("MYSQL", java.sql.Date.valueOf("2026-01-01"), Types.DATE, "year"));
	}

	@Test
	public void bindsJdbcSpecialValuesInAllModesWithoutMetadataProbe() throws Exception {
		for (String mode : MODES) {
			List<String> calls = new ArrayList<>();
			PreparedStatement statement =
					proxy(
							PreparedStatement.class,
							(p, m, a) -> {
								if (!m.getName().startsWith("set"))
									throw new AssertionError(m.getName());
								calls.add(m.getName());
								assertEquals(1, a[0]);
								return null;
							});
			executor.setArgument(
					config(mode),
					statement,
					0,
					new javax.sql.rowset.serial.SerialBlob(new byte[] {1}));
			executor.setArgument(config(mode), statement, 0, new SerialClob("text".toCharArray()));
			executor.setArgument(config(mode), statement, 0, proxy(NClob.class, (p, m, a) -> null));
			executor.setArgument(
					config(mode), statement, 0, proxy(SQLXML.class, (p, m, a) -> null));
			executor.setArgument(config(mode), statement, 0, proxy(RowId.class, (p, m, a) -> null));
			assertEquals(
					Arrays.asList("setBlob", "setClob", "setNClob", "setSQLXML", "setRowId"),
					calls);
		}
	}

	@Test
	public void freesArraysEvenWhenReadingFails() throws Exception {
		for (String mode : MODES) {
			AtomicBoolean freed = new AtomicBoolean();
			java.sql.Array array =
					proxy(
							java.sql.Array.class,
							(p, m, a) -> {
								if (m.getName().equals("free")) {
									freed.set(true);
									return null;
								}
								throw new SQLException("array read failed");
							});
			try {
				map(mode, array, Types.ARRAY, "_numeric");
				fail("Expected read failure");
			} catch (SQLException expected) {
				assertEquals("array read failed", expected.getMessage());
			}
			assertTrue(freed.get());
		}
	}

	@Test
	public void bindsBitAndNationalCharNullsUsingServerTypeInference() throws Exception {
		for (String mode : new String[] {"ORACLE", "SQLSERVER"}) {
			for (int jdbc : new int[] {Types.BIT, Types.NCHAR}) {
				List<Object[]> calls = new ArrayList<>();
				ParameterMetaData metadata =
						proxy(
								ParameterMetaData.class,
								(p, m, a) -> {
									if (m.getName().equals("getParameterType")) return jdbc;
									if (m.getName().equals("getParameterTypeName"))
										return jdbc == Types.BIT ? "bool" : "nchar";
									throw new UnsupportedOperationException(m.getName());
								});
				PreparedStatement statement =
						proxy(
								PreparedStatement.class,
								(p, m, a) -> {
									if (m.getName().equals("getParameterMetaData")) return metadata;
									calls.add(new Object[] {m.getName(), a});
									return null;
								});
				executor.setArgument(config(mode), statement, 0, null);
				assertEquals("setNull", calls.get(0)[0]);
				assertEquals(Types.OTHER, ((Object[]) calls.get(0)[1])[1]);
				if (jdbc == Types.BIT) {
					executor.setArgument(config(mode), statement, 0, false);
					assertEquals("setObject", calls.get(1)[0]);
					assertArrayEquals(
							new Object[] {1, "0", Types.OTHER}, (Object[]) calls.get(1)[1]);
				}
			}
		}
	}

	@Test
	public void oracleUuidBindsAsOtherInsteadOfVarchar() throws Exception {
		List<Object[]> calls = new ArrayList<>();
		PreparedStatement statement =
				proxy(
						PreparedStatement.class,
						(p, m, a) -> {
							if (m.getName().equals("getParameterMetaData")) return null;
							calls.add(a);
							assertEquals("setObject", m.getName());
							return null;
						});
		UUID id = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
		executor.setArgument(config("ORACLE"), statement, 0, id);
		assertArrayEquals(new Object[] {1, id.toString(), Types.OTHER}, calls.get(0));
	}

	@Test
	public void realDriverArraysBindTextWithoutBinaryArrayCorruption() throws Exception {
		requireDriver();
		Class<?> base = Class.forName("com.kingbase8.core.BaseConnection");
		for (String mode : MODES) {
			java.sql.Array array =
					(java.sql.Array)
							Class.forName("com.kingbase8.jdbc.KbArray")
									.getConstructor(base, int.class, String.class)
									.newInstance(null, 1000, "{t,f,NULL}");
			List<Object[]> calls = new ArrayList<>();
			PreparedStatement statement =
					proxy(
							PreparedStatement.class,
							(p, m, a) -> {
								if (m.getName().equals("getParameterMetaData")) return null;
								assertEquals("setObject", m.getName());
								calls.add(a);
								return null;
							});
			executor.setArgument(config(mode), statement, 0, array);
			assertArrayEquals(new Object[] {1, "{t,f,NULL}", Types.OTHER}, calls.get(0));
		}
	}

	private void requireDriver() {
		try {
			Class.forName("com.kingbase8.Driver");
		} catch (ClassNotFoundException e) {
			Assume.assumeNoException("Run with -Pkingbase-driver-tests", e);
		}
	}

	private Object vendor(String name, String value) throws Exception {
		return Class.forName(name).getConstructor(String.class).newInstance(value);
	}

	private Object jsonObject(String type, String value) throws Exception {
		Object object = Class.forName("com.kingbase8.util.KBobject").newInstance();
		object.getClass().getMethod("setType", String.class).invoke(object, type);
		object.getClass().getMethod("setValue", String.class).invoke(object, value);
		return object;
	}

	@Test
	public void coversJdbcMetadataFixtureWithActualJdbcJavaClasses() throws Exception {
		requireDriver();
		int count = 0;
		try (BufferedReader reader =
				new BufferedReader(
						new InputStreamReader(
								getClass().getResourceAsStream("/kingbase-v9r1c10-types.tsv"),
								StandardCharsets.UTF_8))) {
			reader.readLine();
			String line;
			while ((line = reader.readLine()) != null) {
				String[] r = line.split("\t");
				int jdbc = Types.class.getField(r[2].substring(6)).getInt(null);
				Object value, expected;
				switch (r[3]) {
					case "java.lang.Integer":
						value = 123;
						expected = value;
						break;
					case "java.lang.Short":
						value = (short) 123;
						expected = value;
						break;
					case "java.lang.Long":
						value = 4294967295L;
						expected = value;
						break;
					case "java.lang.Float":
						value = 1.25F;
						expected = value;
						break;
					case "java.lang.Double":
						value = 1.25D;
						expected = value;
						break;
					case "java.math.BigInteger":
						value = new BigInteger("18446744073709551615");
						expected = value.toString();
						break;
					case "java.math.BigDecimal":
						value = new BigDecimal("1234567890.123456789");
						expected = value.toString();
						break;
					case "java.lang.String":
						value = "中文 text";
						expected = value;
						break;
					case "java.lang.Boolean":
						value = true;
						expected = true;
						break;
					case "byte[]":
					case "[B":
						value = new byte[] {0, 1, -1};
						expected = "AAH/";
						break;
					case "java.sql.Date":
						value = java.sql.Date.valueOf("2026-01-01");
						expected = value.toString();
						break;
					case "java.sql.Time":
						value = Time.valueOf("12:34:56");
						expected = value.toString();
						break;
					case "java.sql.Timestamp":
						value = Timestamp.valueOf("2026-01-01 12:34:56.123456789");
						expected =
								"timestamptz".equals(r[1])
										? ((Timestamp) value).toInstant().toString()
										: value.toString();
						break;
					case "java.sql.Clob":
						value = new SerialClob("中文 text".toCharArray());
						expected = "中文 text";
						break;
					case "java.sql.NClob":
						value = proxy(NClob.class, (p, m, a) -> new StringReader("中文 text"));
						expected = "中文 text";
						break;
					case "java.sql.ResultSet":
						value = cursor(new AtomicBoolean(), false);
						Map<String, Object> row = new LinkedHashMap<>();
						row.put("amount", "1234567890.123456789");
						row.put("enabled", true);
						expected = Collections.singletonList(row);
						break;
					case "java.util.UUID":
						value = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
						expected = value.toString();
						break;
					case "com.kingbase8.util.KBobject":
						value = jsonObject("json", "{\"enabled\":true}");
						expected = Collections.singletonMap("enabled", true);
						break;
					case "com.kingbase8.geometric.KBpoint":
						value = vendor(r[3], "(1,2)");
						expected = value.toString();
						break;
					case "com.kingbase8.jdbc.YMInterval":
						value = vendor(r[3], "1-2");
						expected = value.toString();
						break;
					case "com.kingbase8.jdbc.DSInterval":
						value = vendor(r[3], "1 02:03:04.123456");
						expected = value.toString();
						break;
					default:
						throw new AssertionError("No sample for " + line);
				}
				assertEquals(line, expected, map(r[0], value, jdbc, r[1]));
				assertNull(line, map(r[0], null, jdbc, r[1]));
				count++;
			}
		}
		assertEquals(83, count);
	}

	@Test
	public void realDriverSpecialTypesStayJsonSafeInAllModes() throws Exception {
		requireDriver();
		String[][] cases = {
			{"com.kingbase8.jdbc.YMInterval", "1-2", "yminterval"},
			{"com.kingbase8.jdbc.DSInterval", "1 02:03:04.123456", "dsinterval"},
			{"com.kingbase8.geometric.KBpoint", "(1,2)", "point"},
			{"com.kingbase8.geometric.KBbox", "(1,2),(3,4)", "box"},
			{"com.kingbase8.geometric.KBcircle", "<(1,2),3>", "circle"},
			{"com.kingbase8.geometric.KBlseg", "[(1,2),(3,4)]", "lseg"},
			{"com.kingbase8.geometric.KBpath", "[(1,2),(3,4)]", "path"},
			{"com.kingbase8.geometric.KBpolygon", "((1,2),(3,4),(5,6))", "polygon"},
			{"com.kingbase8.geometric.KBline", "{1,2,3}", "line"},
			{"com.kingbase8.util.KBInterval", "1 year 2 mons 3 days 04:05:06", "interval"}
		};
		for (String mode : MODES) {
			for (String[] c : cases) {
				Object value = vendor(c[0], c[1]);
				assertEquals(c[0], value.toString(), map(mode, value, Types.OTHER, c[2]));
			}
			for (String type : new String[] {"json", "jsonb", "sys.jsonb"}) {
				assertEquals(
						Collections.singletonMap("enabled", true),
						map(mode, jsonObject(type, "{\"enabled\":true}"), Types.OTHER, type));
				assertNull(map(mode, jsonObject(type, null), Types.OTHER, type));
			}
			Struct nested =
					proxy(
							Struct.class,
							(p, m, a) ->
									new Object[] {
										jsonObject("jsonb", "{\"enabled\":true}"),
										new BigDecimal("1.25")
									});
			assertEquals(
					Arrays.asList(Collections.singletonMap("enabled", true), "1.25"),
					map(mode, nested, Types.STRUCT, "record"));
			for (String type :
					new String[] {"inet", "cidr", "macaddr", "tsvector", "tsquery", "int4range"}) {
				assertEquals(
						"opaque text",
						map(mode, jsonObject(type, "opaque text"), Types.OTHER, type));
			}
		}
	}
}
