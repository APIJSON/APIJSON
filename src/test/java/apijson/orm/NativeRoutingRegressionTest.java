package apijson.orm;

import apijson.RequestMethod;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Native configuration extension points must keep their pre-adaptation semantics. */
public class NativeRoutingRegressionTest {
    private List<String> databases() {
        List<String> values = new ArrayList<>(AbstractSQLConfig.DATABASE_LIST);
        values.removeIf(db -> db.startsWith("KINGBASE_"));
        values.add("SQLITE");
        values.add("CUSTOM_DATABASE");
        return values;
    }

    private NativeDatabaseBaselineTest.Config routed(String database) {
        // IoTDB has always used getDatabase() directly, so retain its configured route.
        return new NativeDatabaseBaselineTest.Config("IOTDB".equals(database) ? database : "ROUTING_CONFIGURATION", "19.0") {
            @Override public String gainSQLDatabase() { return database; }
        };
    }

    @Test public void kingbaseClassificationUsesTheResolvedNativeRoute() {
        for (String db : databases()) {
            assertEquals(db, new NativeDatabaseBaselineTest.Config(db, "19.0").isKingBase(), routed(db).isKingBase());
        }
    }

    @Test public void legacyKingbasePredicateKeepsThe820IdentifierContract() {
        List<String> values = databases();
        values.addAll(Arrays.asList(null, "", "kingbase", " KINGBASE ", "KINGBASE_AUTO",
                "KINGBASE_MYSQL", "KINGBASE_ORACLE", "KINGBASE_SQLSERVER"));
        for (String database : values) {
            assertEquals(String.valueOf(database), "KINGBASE".equals(database),
                    AbstractSQLConfig.isKingBase(database));
        }
    }

    private Object operation(NativeDatabaseBaselineTest.Config config, int operation) throws Exception {
        switch (operation) {
            case 0: return config.gainRegExpString("name", "name", "A.*", false);
            case 1: return config.gainContainString("payload", "payload", new Object[]{1}, 0);
            case 2: config.setCount(5).setPage(2); return config.gainSQL(true);
            case 3: config.setExplain(true).setCount(5).setPage(2); return config.gainSQL(true);
            case 4: return config.relationJoin("<>");
            case 5: return config.relationJoin("~");
            case 6: config.setTable("Table"); return config.getIdKey();
            case 7: config.setTable("Column"); return config.getIdKey();
            case 8: config.setTable("PgClass"); return config.getIdKey();
            case 9: config.setTable("PgAttribute"); return config.getIdKey();
            default: throw new AssertionError(operation);
        }
    }

    @Test public void resolvedNativeRoutesKeepSqlParametersAndErrors() throws Exception {
        AbstractSQLConfigJoinCountTest.installJSONParser();
        try {
            for (String db : databases()) for (int operation = 0; operation < 6; operation++) {
                NativeDatabaseBaselineTest.Config direct = new NativeDatabaseBaselineTest.Config(db, "19.0");
                NativeDatabaseBaselineTest.Config routed = routed(db);
                Object expected;
                try { expected = operation(direct, operation); }
                catch (Exception error) { expected = error.getClass().getName() + ":" + error.getMessage(); }
                Object actual;
                try { actual = operation(routed, operation); }
                catch (Exception error) { actual = error.getClass().getName() + ":" + error.getMessage(); }
                assertEquals(db + " operation " + operation, expected, actual);
                assertEquals(db, direct.getPreparedValueList(), routed.getPreparedValueList());
            }
        } finally { AbstractSQLConfigJoinCountTest.restoreJSONParser(); }
    }

    @Test public void nativeKingbasePredicateOverridesKeepLegacySqlBranches() throws Exception {
        AbstractSQLConfigJoinCountTest.installJSONParser();
        try {
            for (int operation = 0; operation < 6; operation++) {
                NativeDatabaseBaselineTest.Config custom = new NativeDatabaseBaselineTest.Config("CUSTOM_DATABASE", "19.0") {
                    @Override public boolean isKingBase() { return true; }
                };
                NativeDatabaseBaselineTest.Config direct = new NativeDatabaseBaselineTest.Config("KINGBASE", "19.0");
                assertEquals("operation " + operation, operation(direct, operation), operation(custom, operation));
                assertEquals(direct.getPreparedValueList(), custom.getPreparedValueList());
            }
        } finally { AbstractSQLConfigJoinCountTest.restoreJSONParser(); }
    }

    @Test public void nativePredicateOverridesKeepQuoteAndExplainPrecedence() throws Exception {
        NativeDatabaseBaselineTest.Config mysqlSyntax = new NativeDatabaseBaselineTest.Config("KINGBASE", "19.0") {
            @Override public boolean isMySQL() { return true; }
        };
        assertEquals("`", mysqlSyntax.getQuote());
        NativeDatabaseBaselineTest.Config sqlServerExplain = new NativeDatabaseBaselineTest.Config("KINGBASE", "19.0") {
            @Override public boolean isSQLServer() { return true; }
        };
        sqlServerExplain.setExplain(true);
        String sql = sqlServerExplain.gainSQL(true);
        assertTrue(sql, sql.startsWith("SET STATISTICS PROFILE ON  "));
    }

    @Test public void autoConfigurationDoesNotOverrideResolvedNativeRoutes() throws Exception {
        String previous = AbstractSQLConfig.DEFAULT_DATABASE;
        AbstractSQLConfigJoinCountTest.installJSONParser();
        try {
            AbstractSQLConfig.DEFAULT_DATABASE = "KINGBASE_AUTO";
            for (String db : databases()) {
                // IoTDB's existing predicate reads getDatabase(), not gainSQLDatabase().
                if ("IOTDB".equals(db)) continue;
                for (String configured : Arrays.asList(null, "KINGBASE_AUTO", "ROUTING_CONFIGURATION", db)) {
                    for (int operation = 0; operation < 10; operation++) {
                        NativeDatabaseBaselineTest.Config direct = new NativeDatabaseBaselineTest.Config(db, "19.0");
                        NativeDatabaseBaselineTest.Config routed = new NativeDatabaseBaselineTest.Config(configured, "19.0") {
                            @Override public String gainSQLDatabase() { return db; }
                        };
                        assertEquals(db, direct.isKingBase(), routed.isKingBase());
                        Object expected;
                        try { expected = operation(direct, operation); }
                        catch (Exception error) { expected = error.getClass().getName() + ":" + error.getMessage(); }
                        Object actual;
                        try { actual = operation(routed, operation); }
                        catch (Exception error) { actual = error.getClass().getName() + ":" + error.getMessage(); }
                        assertEquals(db + " configured=" + configured + " operation=" + operation, expected, actual);
                        assertEquals(direct.getPreparedValueList(), routed.getPreparedValueList());
                        assertEquals(configured, routed.getDatabase());
                    }
                }
            }
        } finally {
            AbstractSQLConfig.DEFAULT_DATABASE = previous;
            AbstractSQLConfigJoinCountTest.restoreJSONParser();
        }
    }

    @Test public void autoDefaultPreservesNativeRequestIdCallbacks() throws Exception {
        String previous = AbstractSQLConfig.DEFAULT_DATABASE;
        AbstractSQLConfigJoinCountTest.installJSONParser();
        try {
            AbstractSQLConfig.DEFAULT_DATABASE = "KINGBASE_AUTO";
            for (String db : Arrays.asList("MYSQL", "ORACLE", "SQLSERVER", "KINGBASE")) {
                for (String table : Arrays.asList("User", "Table", "Column", "PgClass", "PgAttribute")) {
                    for (String idKey : Arrays.asList("id", "custom_id")) {
                        for (RequestMethod method : Arrays.asList(RequestMethod.GET, RequestMethod.POST)) {
                            List<String> callbackCalls = new ArrayList<>();
                            AbstractSQLConfig.SimpleCallback<Long, Map<String, Object>, List<Object>> callback =
                                    new AbstractSQLConfig.SimpleCallback<Long, Map<String, Object>, List<Object>>() {
                                @Override public SQLConfig<Long, Map<String, Object>, List<Object>> getSQLConfig(
                                        RequestMethod m, String database, String datasource, String namespace,
                                        String catalog, String schema, String t) {
                                    NativeDatabaseBaselineTest.Config c = new NativeDatabaseBaselineTest.Config(database, "19.0") {
                                        @Override public String gainSQLDatabase() { return db; }
                                    };
                                    c.setMethod(m); c.setTable(t);
                                    return c;
                                }
                                @Override public String getIdKey(String database, String datasource, String namespace,
                                        String catalog, String schema, String t) {
                                    assertNull(database);
                                    callbackCalls.add(t);
                                    return "id".equals(idKey)
                                            ? super.getIdKey(database, datasource, namespace, catalog, schema, t) : idKey;
                                }
                            };
                            Map<String, Object> request = new LinkedHashMap<>();
                            request.put(idKey, 7L);
                            SQLConfig<Long, Map<String, Object>, List<Object>> c = AbstractSQLConfig.newSQLConfig(
                                    method, table, null, request, null, false, callback);
                            assertEquals(db + "/" + table + "/" + idKey, 7L, c.getId());
                            assertEquals(Collections.singletonList(table), callbackCalls);
                            assertEquals(7L, request.get(idKey));
                            assertNull(c.getDatabase());
                        }
                    }
                }
            }
        } finally {
            AbstractSQLConfig.DEFAULT_DATABASE = previous;
            AbstractSQLConfigJoinCountTest.restoreJSONParser();
        }
    }
}
