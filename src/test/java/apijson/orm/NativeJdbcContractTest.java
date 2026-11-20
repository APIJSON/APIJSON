package apijson.orm;

import apijson.JSON;
import apijson.JSONParser;
import apijson.RequestMethod;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.Month;
import java.time.DayOfWeek;
import java.util.*;
import javax.sql.rowset.serial.SerialBlob;
import javax.sql.rowset.serial.SerialClob;
import org.junit.Test;
import org.junit.Before;
import org.junit.After;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import static org.junit.Assert.*;

/** Executed unchanged against 8.2.0, the isolated upstream reference and this branch. */
@RunWith(Parameterized.class)
public class NativeJdbcContractTest {
    private static final List<String> DATABASES = Arrays.asList(
            "MYSQL", "ORACLE", "SQLSERVER", "POSTGRESQL", "DB2", "KINGBASE", "SQLITE", "CUSTOM_DATABASE");

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> routes() {
        return Arrays.asList(new Object[][]{{"direct"}, {"default-auto"}, {"explicit-auto"}});
    }

    private final String routing;
    private String previousDatabase;

    public NativeJdbcContractTest(String routing) { this.routing = routing; }

    @Before public void useAutoDefault() {
        previousDatabase = AbstractSQLConfig.DEFAULT_DATABASE;
        AbstractSQLConfig.DEFAULT_DATABASE = "KINGBASE_AUTO";
    }

    @After public void restoreDefault() { AbstractSQLConfig.DEFAULT_DATABASE = previousDatabase; }

    static class NativeConfig extends NativeDatabaseBaselineTest.Config {
        private final String route;
        private final String routing;

        NativeConfig(String database, String version, String routing) {
            super("direct".equals(routing) ? database : "explicit-auto".equals(routing) ? "KINGBASE_AUTO" : null, version);
            this.route = database;
            this.routing = routing;
        }

        @Override public String gainSQLDatabase() {
            return "direct".equals(routing) ? super.gainSQLDatabase() : route;
        }
    }

    interface Call { Object invoke(String name, Object[] args) throws Throwable; }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, Call call) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
                (p, m, a) -> call.invoke(m.getName(), a));
    }

    static class Executor extends AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> {
        Object read(SQLConfig<Long, Map<String, Object>, List<Object>> config, Object value,
                String type, List<String> calls) throws Exception {
            ResultSet rs = proxy(ResultSet.class, (n, a) -> {
                calls.add(n);
                if ("getObject".equals(n)) return value;
                throw new AssertionError("Unexpected native result read: " + n);
            });
            ResultSetMetaData md = proxy(ResultSetMetaData.class, (n, a) -> {
                calls.add(n);
                if ("getColumnTypeName".equals(n)) return type;
                throw new AssertionError("Unexpected native metadata read: " + n);
            });
            return getValue(config, rs, md, 0, new LinkedHashMap<>(), 1, "value", null, null);
        }
    }

    @Test public void nonStringValuesKeepTheirOriginalTypesWithoutMetadataReads() throws Exception {
        Object xml = proxy(SQLXML.class, (n, a) -> { throw new AssertionError(n); });
        Object array = proxy(java.sql.Array.class, (n, a) -> { throw new AssertionError(n); });
        List<Object> values = Arrays.asList(null, true, 7, Double.NaN, Float.NaN,
                LocalDate.of(2026, 1, 2), UUID.fromString("123e4567-e89b-12d3-a456-426614174000"),
                new Object[]{1, 2}, new int[]{1, 2}, new byte[]{1, 2}, xml, array);
        for (String db : DATABASES) for (Object value : values) {
            List<String> calls = new ArrayList<>();
            assertSame(db, value, new Executor().read(new NativeConfig(db, "19.0", routing), value, "jsonb", calls));
            assertEquals(db, Collections.singletonList("getObject"), calls);
        }
    }

    @Test public void originalJsonDetectionAndExplicitJsonRunThroughTheParser() throws Exception {
        JSONParser<?, ?> previous = JSON.DEFAULT_JSON_PARSER;
        List<Object> parsed = new ArrayList<>();
        JSON.DEFAULT_JSON_PARSER = proxy(JSONParser.class, (n, a) -> {
            if ("parse".equals(n)) { parsed.add(a[0]); return a[0]; }
            throw new AssertionError(n);
        });
        try {
            for (String db : DATABASES) {
                NativeDatabaseBaselineTest.Config c = new NativeConfig(db, "19.0", routing);
                List<String> calls = new ArrayList<>();
                parsed.clear();
                assertEquals("text", new Executor().read(c, "text", "business_json_status", calls));
                assertEquals(Collections.singletonList("text"), parsed);
                assertEquals(Arrays.asList("getObject", "getColumnTypeName"), calls);
                c.setJson(Collections.singletonList("value"));
                for (Object value : Arrays.asList(null, true, 7, LocalDate.of(2026, 1, 2))) {
                    parsed.clear(); calls.clear();
                    new Executor().read(c, value, "varchar", calls);
                    assertEquals(Collections.singletonList(value), parsed);
                    assertEquals(Collections.singletonList("getObject"), calls);
                }
            }
        } finally { JSON.DEFAULT_JSON_PARSER = previous; }
    }

    @Test public void clobLineHandlingAndCloseErrorsKeepTheOriginalContract() throws Exception {
        JSONParser<?, ?> previous = JSON.DEFAULT_JSON_PARSER;
        JSON.DEFAULT_JSON_PARSER = proxy(JSONParser.class, (n, a) -> a[0]);
        try {
            for (String db : DATABASES) {
                Clob clob = proxy(Clob.class, (n, a) -> {
                    if ("getCharacterStream".equals(n)) return new StringReader("one\ntwo\r\nthree") {
                        @Override public void close() { throw new IllegalStateException("close failed"); }
                    };
                    throw new AssertionError(n);
                });
                assertEquals("onetwothree", new Executor().read(
                        new NativeConfig(db, "19.0", routing), clob, "clob", new ArrayList<>()));
            }
        } finally { JSON.DEFAULT_JSON_PARSER = previous; }
    }

    @Test public void jsonTypeOverrideStillControlsNativeReads() throws Exception {
        Executor e = new Executor() {
            @Override public boolean isJSONType(SQLConfig<Long, Map<String, Object>, List<Object>> c,
                    ResultSetMetaData md, int position, String label) { return false; }
        };
        List<String> calls = new ArrayList<>();
        assertEquals("text", e.read(new NativeConfig("MYSQL", "8.0", routing), "text", "jsonb", calls));
        assertEquals(Collections.singletonList("getObject"), calls);
    }

    @Test public void numericTemporalAndBlobConversionsMatch820() throws Exception {
        JSONParser<?, ?> previous = JSON.DEFAULT_JSON_PARSER;
        JSON.DEFAULT_JSON_PARSER = proxy(JSONParser.class, (n, a) -> a[0]);
        Object[][] cases = {
            {new BigInteger("42"), "42"}, {new BigDecimal("1.2300"), "1.2300"},
            {9007199254740991L, 9007199254740991L}, {9007199254740992L, "9007199254740992"},
            {Timestamp.valueOf("2026-01-02 03:04:05"), "2026-01-02 03:04:05.0"},
            {java.sql.Date.valueOf("2026-01-02"), "2026-01-02"}, {Time.valueOf("03:04:05"), "03:04:05"},
            {LocalDateTime.of(2026, 1, 2, 3, 4), "2026-01-02T03:04"},
            {Year.of(2026), 2026}, {Month.JANUARY, 1}, {DayOfWeek.FRIDAY, 5},
            {new SerialBlob("text".getBytes("UTF-8")), "text"}
        };
        try {
            for (String db : DATABASES) for (Object[] item : cases) {
                List<String> calls = new ArrayList<>();
                assertEquals(db, item[1], new Executor().read(
                        new NativeConfig(db, "19.0", routing), item[0], "unused", calls));
                assertEquals(Collections.singletonList("getObject"), calls);
            }
        } finally { JSON.DEFAULT_JSON_PARSER = previous; }
    }

    @Test public void sqlGenerationPrecedesConnectionsAndRetainsPreparationOptions() throws Exception {
        for (String db : DATABASES) for (RequestMethod method : Arrays.asList(RequestMethod.GET, RequestMethod.POST)) {
            List<String> calls = new ArrayList<>();
            NativeDatabaseBaselineTest.Config c = new NativeConfig(db, "19.0", routing) {
                @Override public String gainSQL(boolean prepared) { calls.add("sql"); return "SELECT 1"; }
            };
            c.setMethod(method);
            PreparedStatement st = proxy(PreparedStatement.class, (n, a) -> { throw new AssertionError(n); });
            Connection conn = proxy(Connection.class, (n, a) -> {
                assertEquals("prepareStatement", n);
                if (method == RequestMethod.POST) {
                    if ("ORACLE".equals(db)) assertArrayEquals(new String[]{"id"}, (String[]) a[1]);
                    else assertEquals(Statement.RETURN_GENERATED_KEYS, a[1]);
                } else if (Arrays.asList("MYSQL", "ORACLE", "SQLSERVER", "POSTGRESQL", "DB2").contains(db)) {
                    assertEquals(ResultSet.TYPE_SCROLL_SENSITIVE, a[1]);
                    assertEquals(ResultSet.CONCUR_UPDATABLE, a[2]);
                } else assertEquals(1, a.length);
                calls.add("prepare"); return st;
            });
            Executor e = new Executor() {
                @Override public Connection getConnection(SQLConfig<Long, Map<String, Object>, List<Object>> config) {
                    calls.add("connection"); return conn;
                }
            };
            assertSame(st, e.getStatement(c));
            assertEquals(Arrays.asList("sql", "connection", "prepare"), calls);
        }
    }

    @Test public void invalidSqlFailsBeforeOpeningTheNativeConnection() throws Exception {
        for (String db : DATABASES) {
            SQLException expected = new SQLException("invalid SQL");
            NativeDatabaseBaselineTest.Config c = new NativeConfig(db, "19.0", routing) {
                @Override public String gainSQL(boolean prepared) throws Exception { throw expected; }
            };
            Executor e = new Executor() {
                @Override public Connection getConnection(SQLConfig<Long, Map<String, Object>, List<Object>> config) {
                    throw new AssertionError("SQL errors must precede connection acquisition");
                }
            };
            assertSame(expected, assertThrows(SQLException.class, () -> e.getStatement(c)));
        }
    }

    @Test public void emptyGeneratedSqlIsNotRegeneratedAfterConnecting() throws Exception {
        for (String sql : Arrays.asList(null, "")) {
            List<String> calls = new ArrayList<>();
            NativeDatabaseBaselineTest.Config c = new NativeConfig("MYSQL", "8.0", routing) {
                @Override public String gainSQL(boolean prepared) { calls.add("sql"); return sql; }
            };
            Connection nativeConnection = proxy(Connection.class, (n, a) -> {
                assertEquals("prepareStatement", n); assertEquals(sql, a[0]);
                return proxy(PreparedStatement.class, (method, args) -> null);
            });
            Executor e = new Executor() {
                @Override public Connection getConnection(SQLConfig<Long, Map<String, Object>, List<Object>> config) {
                    calls.add("connection"); return nativeConnection;
                }
            };
            e.getStatement(c);
            assertEquals(Arrays.asList("sql", "connection"), calls);
        }
    }

    @Test public void nativeBindingsDoNotUseKingbaseParameterInference() throws Exception {
        List<Object> values = Arrays.asList(null, true, 7, "text", Collections.singletonMap("a", 1),
                Arrays.asList(1, 2), new byte[]{1, 2}, UUID.randomUUID(), new SerialClob("text".toCharArray()));
        for (String db : DATABASES) for (Object value : values) {
            List<String> calls = new ArrayList<>();
            PreparedStatement st = proxy(PreparedStatement.class, (n, a) -> {
                calls.add(n); assertEquals(1, a[0]); assertEquals(2, a.length);
                if (JSON.isBoolOrNumOrStr(value)) { assertEquals("setObject", n); assertSame(value, a[1]); }
                else { assertEquals("setString", n); assertEquals(value == null ? null : value.toString(), a[1]); }
                return null;
            });
            new Executor().setArgument(new NativeConfig(db, "19.0", routing), st, 0, value);
            assertEquals(1, calls.size());
        }
    }

    @Test public void generatedKeysDoNotAddNativeCloseCallsOrCloseFailures() throws Exception {
        for (String db : DATABASES) {
            List<String> calls = new ArrayList<>();
            ResultSet keys = proxy(ResultSet.class, (n, a) -> {
                calls.add(n);
                if ("next".equals(n)) return true;
                if ("getLong".equals(n)) return 42L;
                throw new AssertionError("Unexpected native key operation: " + n);
            });
            PreparedStatement st = proxy(PreparedStatement.class, (n, a) -> {
                calls.add(n);
                if ("executeUpdate".equals(n)) return 1;
                if ("getGeneratedKeys".equals(n)) return keys;
                throw new AssertionError(n);
            });
            NativeDatabaseBaselineTest.Config c = new NativeConfig(db, "19.0", routing);
            c.setMethod(RequestMethod.POST);
            Executor e = new Executor() {
                @Override public PreparedStatement getStatement(SQLConfig<Long, Map<String, Object>, List<Object>> config) { return st; }
            };
            assertEquals(1, e.executeUpdate(c, null));
            assertEquals(42L, c.getId());
            assertEquals(Arrays.asList("executeUpdate", "getGeneratedKeys", "next", "getLong"), calls);
        }
    }
}
