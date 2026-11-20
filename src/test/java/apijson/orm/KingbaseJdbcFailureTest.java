package apijson.orm;

import apijson.RequestMethod;
import java.io.IOException;
import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class KingbaseJdbcFailureTest {
    @BeforeClass public static void parser() { KingbaseCompatibilityTest.installJsonParser(); }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }

    @Test public void oracleBindingFallsBackWhenParameterMetadataIsMissingOrThrows() throws Exception {
        AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> executor = new AbstractSQLExecutor<Long,Map<String,Object>,List<Object>>() {};
        for (boolean fail : new boolean[]{false,true}) {
            List<Object[]> bindings = new ArrayList<>();
            PreparedStatement statement = proxy(PreparedStatement.class,(p,m,a)-> {
                if (m.getName().equals("getParameterMetaData")) {
                    if (fail) throw new SQLException("metadata unavailable");
                    return null;
                }
                bindings.add(a); return null;
            });
            SQLConfig<Long,Map<String,Object>,List<Object>> config = KingbaseSemanticsRegressionTest.config("KINGBASE_ORACLE");
            executor.setArgument(config,statement,0,null);
            executor.setArgument(config,statement,1,"O'Reilly");
            assertArrayEquals(new Object[]{1,null},bindings.get(0));
            assertArrayEquals(new Object[]{2,"O'Reilly"},bindings.get(1));
        }
    }

    @Test public void bothCursorCreationFailuresPropagateWithoutExecutingSql() throws Exception {
        for (String mode : Arrays.asList("KINGBASE_MYSQL","KINGBASE_ORACLE")) {
            AtomicInteger attempts = new AtomicInteger();
            SQLException fallback = new SQLException("forward-only unavailable","0A000");
            Connection testConnection = proxy(Connection.class,(p,m,a)-> {
                assertEquals("prepareStatement",m.getName());
                assertEquals(3,a.length);
                if (attempts.getAndIncrement()==0) {
                    assertEquals(ResultSet.TYPE_SCROLL_INSENSITIVE,a[1]);
                    throw new SQLFeatureNotSupportedException("scroll unavailable");
                }
                assertEquals(ResultSet.TYPE_FORWARD_ONLY,a[1]);
                throw fallback;
            });
            AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> executor = new AbstractSQLExecutor<Long,Map<String,Object>,List<Object>>() {
                @Override public Connection getConnection(SQLConfig<Long,Map<String,Object>,List<Object>> config) { return testConnection; }
            };
            assertSame(fallback,assertThrows(SQLException.class,()->executor.getStatement(KingbaseSemanticsRegressionTest.config(mode),"SELECT 1")));
            assertEquals(2,attempts.get());
        }
    }

    @Test public void generatedKeyFailureDoesNotReplayTheInsert() throws Exception {
        for (String mode : Arrays.asList("KINGBASE_MYSQL","KINGBASE_ORACLE")) {
            AtomicInteger writes = new AtomicInteger();
            SQLException expected = new SQLException("generated keys unavailable","HY000");
            PreparedStatement statement = proxy(PreparedStatement.class,(p,m,a)-> {
                if (m.getName().equals("executeUpdate")) { writes.incrementAndGet(); return 1; }
                if (m.getName().equals("getGeneratedKeys")) throw expected;
                throw new AssertionError(m.getName());
            });
            AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> executor = executor(statement);
            AbstractSQLConfig<Long,Map<String,Object>,List<Object>> config = KingbaseSemanticsRegressionTest.config(mode);
            config.setMethod(RequestMethod.POST);config.setId(null);
            assertSame(expected,assertThrows(SQLException.class,()->executor.executeUpdate(config,null)));
            assertEquals(1,writes.get());
        }
    }

    private AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> executor(PreparedStatement statement) {
        return new AbstractSQLExecutor<Long,Map<String,Object>,List<Object>>() {
            @Override public PreparedStatement getStatement(SQLConfig<Long,Map<String,Object>,List<Object>> config) { return statement; }
        };
    }

    @Test public void emptyGeneratedKeysKeepTheAffectedCountAndCloseTheResultSet() throws Exception {
        for (String mode : Arrays.asList("KINGBASE_MYSQL","KINGBASE_ORACLE")) {
            AtomicBoolean closed = new AtomicBoolean();AtomicInteger writes = new AtomicInteger();
            ResultSet keys = proxy(ResultSet.class,(p,m,a)-> {
                if (m.getName().equals("next")) return false;
                if (m.getName().equals("close")) { closed.set(true);return null; }
                throw new AssertionError(m.getName());
            });
            PreparedStatement statement = proxy(PreparedStatement.class,(p,m,a)-> {
                if (m.getName().equals("executeUpdate")) { writes.incrementAndGet();return 1; }
                if (m.getName().equals("getGeneratedKeys")) return keys;
                throw new AssertionError(m.getName());
            });
            AbstractSQLConfig<Long,Map<String,Object>,List<Object>> config = KingbaseSemanticsRegressionTest.config(mode);
            config.setMethod(RequestMethod.POST);config.setId(null);
            assertEquals(1,executor(statement).executeUpdate(config,null));
            assertNull(config.getId());assertTrue(closed.get());assertEquals(1,writes.get());
        }
    }

    @Test public void unavailableColumnMetadataStillReadsTheActualValue() throws Exception {
        class Reader extends AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> {
            Object read(SQLConfig<Long,Map<String,Object>,List<Object>> config) throws Exception {
                ResultSetMetaData metadata = proxy(ResultSetMetaData.class,(p,m,a)-> { throw new SQLException("metadata unavailable"); });
                ResultSet result = proxy(ResultSet.class,(p,m,a)-> {
                    assertEquals("getObject",m.getName());return 17;
                });
                return getValue(config,result,metadata,0,new LinkedHashMap<>(),1,"value",null,null);
            }
        }
        for (String mode : Arrays.asList("KINGBASE_MYSQL","KINGBASE_ORACLE"))
            assertEquals(17,new Reader().read(KingbaseSemanticsRegressionTest.config(mode)));
    }

    @Test public void clobReadersCloseAfterSuccessfulAndFailedReads() throws Exception {
        AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> executor = new AbstractSQLExecutor<Long,Map<String,Object>,List<Object>>() {};
        for (String mode : Arrays.asList("KINGBASE_MYSQL","KINGBASE_ORACLE")) {
            for (boolean fail : new boolean[]{false,true}) {
                AtomicBoolean closed = new AtomicBoolean();
                StringReader reader = new StringReader("first\nsecond") {
                    @Override public int read(char[] buffer,int offset,int length) throws IOException {
                        if (fail) throw new IOException("injected CLOB read failure");
                        return super.read(buffer,offset,length);
                    }
                    @Override public void close() { closed.set(true);super.close(); }
                };
                Clob clob = proxy(Clob.class,(p,m,a)-> {
                    assertEquals("getCharacterStream",m.getName());return reader;
                });
                SQLConfig<Long,Map<String,Object>,List<Object>> config = KingbaseSemanticsRegressionTest.config(mode);
                if (fail) assertThrows(IOException.class,()->executor.mapResultValue(config,clob,Types.CLOB,"clob","v"));
                else assertEquals("first\nsecond",executor.mapResultValue(config,clob,Types.CLOB,"clob","v"));
                assertTrue(closed.get());
            }
        }
    }
}
