package apijson.orm;

import java.lang.reflect.Method;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class KingbaseJoinJsonRegressionTest {
    @BeforeClass public static void parser() { KingbaseCompatibilityTest.installJsonParser(); }

    @SuppressWarnings("unchecked")
    private List<Join<Long,Map<String,Object>,List<Object>>> joins(
            String global, String arrayDatabase, String tableDatabase) throws Exception {
        AbstractParser<Long,Map<String,Object>,List<Object>> parser = new AbstractParser<Long,Map<String,Object>,List<Object>>() {
            public Object onFunctionParse(String function, String parentPath, String currentName, String key,
                    Map<String,Object> currentObject, boolean raw) { throw new AssertionError("Unexpected function"); }
            public Parser<Long,Map<String,Object>,List<Object>> createParser() { return this; }
            public FunctionParser<Long,Map<String,Object>,List<Object>> createFunctionParser() { return null; }
            public Verifier<Long,Map<String,Object>,List<Object>> createVerifier() { return null; }
            public SQLConfig<Long,Map<String,Object>,List<Object>> createSQLConfig() { return null; }
            public SQLExecutor<Long,Map<String,Object>,List<Object>> createSQLExecutor() { return null; }
            public ObjectParser<Long,Map<String,Object>,List<Object>> createObjectParser(
                    Map<String,Object> request, String path, SQLConfig<Long,Map<String,Object>,List<Object>> array,
                    boolean subquery, boolean table, boolean main) { return null; }
        };
        parser.setGlobalDatabase(global);
        Map<String,Object> main = new LinkedHashMap<>();
        Map<String,Object> child = new LinkedHashMap<>();
        child.put("id@", "/Primary/owner_id");
        child.put("@json", "payload");
        child.put("@column", "id,payload");
        if (tableDatabase != null) child.put("@database", tableDatabase);
        Map<String,Object> request = new LinkedHashMap<>();
        request.put("Primary", main); request.put("Secondary", child);
        if (arrayDatabase != null) request.put("@database", arrayDatabase);
        Method method = AbstractParser.class.getDeclaredMethod("onJoinParse",Object.class,Map.class);
        method.setAccessible(true);
        return (List<Join<Long,Map<String,Object>,List<Object>>>)method.invoke(parser,"</Secondary/id@",request);
    }

    @Test public void kingbaseJoinRetainsTheViceTablesJsonDeclarationAtEachRouteLevel() throws Exception {
        for (String[] route : new String[][] {
                {"KINGBASE_AUTO",null,null}, {"MYSQL","KINGBASE_AUTO",null},
                {"MYSQL",null,"KINGBASE_AUTO"}}) {
            assertEquals("payload",joins(route[0],route[1],route[2]).get(0).getRequest().get("@json"));
        }
    }

    @Test public void nativeAndLegacyJoinsKeepTheirOriginalRequestCopyRules() throws Exception {
        for (String database : Arrays.asList("MYSQL","ORACLE","POSTGRESQL","SQLSERVER","KINGBASE")) {
            assertFalse(joins(database,null,null).get(0).getRequest().containsKey("@json"));
            assertFalse(joins("KINGBASE_AUTO",null,database).get(0).getRequest().containsKey("@json"));
        }
    }
}
