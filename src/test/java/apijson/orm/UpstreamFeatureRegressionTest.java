package apijson.orm;

import apijson.RequestMethod;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

/** Independent upstream changes must survive removal of native Kingbase side effects. */
public class UpstreamFeatureRegressionTest {
    @Test public void upsertKeepsEachNativeDialectAndPreparedValues() throws Exception {
        String table = "IsolationUpsert";
        Object previous = AbstractSQLConfig.UPSERT_TABLE_MAP.get(table);
        try {
            AbstractSQLConfig.UPSERT_TABLE_MAP.put(table, "version = version + 1");
            for (String db : Arrays.asList("MYSQL", "POSTGRESQL", "SQLITE", "ORACLE", "DB2", "SQLSERVER")) {
                NativeDatabaseBaselineTest.Config c = new NativeDatabaseBaselineTest.Config(db, "19.0");
                c.setMethod(RequestMethod.POST); c.setTable(table);
                c.setColumn(Arrays.asList("id", "version"));
                c.setValues(Arrays.asList(Arrays.<Object>asList(7L, 1)));
                String sql = c.gainSQL(true);
                if ("MYSQL".equals(db)) assertTrue(sql, sql.contains(" ON DUPLICATE KEY UPDATE version = version + 1"));
                else if ("POSTGRESQL".equals(db) || "SQLITE".equals(db))
                    assertTrue(sql, sql.contains(" ON CONFLICT(\"id\") DO UPDATE SET version = version + 1"));
                else {
                    assertTrue(sql, sql.startsWith("MERGE INTO "));
                    assertTrue(sql, sql.contains("WHEN MATCHED THEN"));
                    assertTrue(sql, sql.contains("WHEN NOT MATCHED THEN"));
                }
                assertEquals(db, Arrays.<Object>asList(7L, 1), c.getPreparedValueList());
                if (Arrays.asList("ORACLE", "DB2", "SQLSERVER").contains(db)) {
                    c.setValues(Arrays.asList(Arrays.<Object>asList(7L, 1), Arrays.<Object>asList(8L, 1)));
                    assertThrows(IllegalArgumentException.class, () -> c.gainSQL(true));
                }
            }
        } finally {
            if (previous == null) AbstractSQLConfig.UPSERT_TABLE_MAP.remove(table);
            else AbstractSQLConfig.UPSERT_TABLE_MAP.put(table, previous);
        }
    }

    @Test public void havingRetainsUpstreamExpressionParsing() throws Exception {
        for (String db : Arrays.asList("MYSQL", "POSTGRESQL", "ORACLE", "SQLSERVER", "KINGBASE")) {
            NativeDatabaseBaselineTest.Config c = new NativeDatabaseBaselineTest.Config(db, "19.0");
            assertEquals(c.gainKey("id"), c.having("id"));
            assertEquals("count(" + c.gainKey("id") + ")>0", c.having("count(id)>0"));
            // Record the exact bccbf475 contract, including its parameter treatment.
            assertEquals("?", c.having("id>0"));
            assertEquals(Collections.singletonList("id>0"), c.getPreparedValueList());
        }
    }
}
