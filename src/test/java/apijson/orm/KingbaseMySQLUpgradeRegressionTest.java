package apijson.orm;

import apijson.RequestMethod;
import java.lang.reflect.Proxy;
import java.math.BigDecimal;
import java.sql.*;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

/** Upgrade counterexamples: precision-safe reads and generated CTE parameter ordering. */
public class KingbaseMySQLUpgradeRegressionTest {
	@BeforeClass public static void parser() { KingbaseCompatibilityTest.installJsonParser(); }

	@Test public void decimalGetterFallbackIsLimitedToMysqlNumericFailures() throws Exception {
		for (String db : Arrays.asList("KINGBASE_MYSQL", "MYSQL", "KINGBASE_ORACLE", "KINGBASE_SQLSERVER")) {
			for (int type : new int[]{Types.DECIMAL, Types.NUMERIC, Types.VARCHAR}) {
				List<String> calls = new ArrayList<>();
				SQLException original = new SQLException("Bad value for type BigDecimal", "22003");
				ResultSet rs = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{ResultSet.class}, (p,m,a) -> {
					calls.add(m.getName());
					if (m.getName().equals("getObject")) throw original;
					if (m.getName().equals("getBigDecimal")) return new BigDecimal("2.5000");
					return null;
				});
				ResultSetMetaData md = metadata(type);
				AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> e = new AbstractSQLExecutor<Long,Map<String,Object>,List<Object>>(){};
				AbstractSQLConfig<Long,Map<String,Object>,List<Object>> c = KingbaseSemanticsRegressionTest.config(db);
				if (db.equals("KINGBASE_MYSQL") && type != Types.VARCHAR) {
					assertEquals("2.5000", e.getValue(c,rs,md,0,new LinkedHashMap<>(),1,"v",null,null));
					assertEquals(Arrays.asList("getObject","getBigDecimal"),calls);
				} else {
					assertSame(original, assertThrows(SQLException.class, () -> e.getValue(c,rs,md,0,new LinkedHashMap<>(),1,"v",null,null)));
					assertEquals(Collections.singletonList("getObject"),calls);
				}
			}
		}
	}

	@Test public void failedFallbackKeepsOriginalErrorAndSuccessfulReadsDoNotChange() throws Exception {
		SQLException original = new SQLException("numeric metadata", "22003"), fallback = new SQLException("typed read", "22003");
		AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> e = new AbstractSQLExecutor<Long,Map<String,Object>,List<Object>>(){};
		AbstractSQLConfig<Long,Map<String,Object>,List<Object>> c = KingbaseSemanticsRegressionTest.config("KINGBASE_MYSQL");
		ResultSet broken = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)-> {
			if(m.getName().equals("getObject"))throw original;
			if(m.getName().equals("getBigDecimal"))throw fallback;
			return null;
		});
		assertSame(original,assertThrows(SQLException.class,()->e.getValue(c,broken,metadata(Types.DECIMAL),0,new LinkedHashMap<>(),1,"v",null,null)));
		assertArrayEquals(new Throwable[]{fallback},original.getSuppressed());
		ResultSet normal = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)-> {
			assertEquals("getObject",m.getName());return new BigDecimal("1.2300");
		});
		assertEquals("1.2300",e.getValue(c,normal,metadata(Types.DECIMAL),0,new LinkedHashMap<>(),1,"v",null,null));
		SQLException connectionFailure=new SQLException("connection lost","08006");
		ResultSet disconnected=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)-> {
			assertEquals("getObject",m.getName());throw connectionFailure;
		});
		assertSame(connectionFailure,assertThrows(SQLException.class,()->e.getValue(c,disconnected,metadata(Types.DECIMAL),0,new LinkedHashMap<>(),1,"v",null,null)));
	}

	private ResultSetMetaData metadata(int type) {
		return (ResultSetMetaData) Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSetMetaData.class},(p,m,a)-> {
			if(m.getName().equals("getColumnType"))return type;
			if(m.getName().equals("getColumnTypeName"))return type==Types.NUMERIC?"numeric":type==Types.DECIMAL?"decimal":"varchar";
			return null;
		});
	}

	@Test public void zeroDateBypassesDriverNormalizationOnlyInMysqlMode() throws Exception {
		for(int type:new int[]{Types.DATE,Types.TIMESTAMP}) {
			String literal=type==Types.DATE?"0000-00-00":"0000-00-00 00:00:00.000000";
			ResultSet rs=(ResultSet)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},(p,m,a)-> {
				assertEquals("getString",m.getName());return literal;
			});
			AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> e=new AbstractSQLExecutor<Long,Map<String,Object>,List<Object>>(){};
			assertEquals(literal,e.getValue(KingbaseSemanticsRegressionTest.config("KINGBASE_MYSQL"),rs,metadata(type),0,new LinkedHashMap<>(),1,"v",null,null));
		}
	}

	static AbstractSQLConfig<Long,Map<String,Object>,List<Object>> cte(boolean sameTable) {
		AbstractSQLConfig<Long,Map<String,Object>,List<Object>> c=KingbaseSemanticsRegressionTest.config("KINGBASE_MYSQL");
		c.setTable("users");c.setSchema("");c.setColumn(Arrays.asList("id","concat(name,'!'):label"));c.setOrder("id+");
		for(int i=0;i<2;i++) {
			String table=sameTable?"users":"items";
			AbstractSQLConfig<Long,Map<String,Object>,List<Object>> sub=KingbaseSemanticsRegressionTest.config("KINGBASE_MYSQL");
			sub.setTable(table);sub.setSchema("");sub.setColumn(Collections.singletonList("id"));sub.putWhere("id{}",Arrays.asList(11,22),false);
			if(i==1)sub.putWhere("id<",22,false);
			Subquery<Long,Map<String,Object>,List<Object>> query=new Subquery<>();query.setConfig(sub);query.setKey("sub"+i);query.setFrom(table);
			c.putWhere(i==0?"id{}":"id!{}",query,false);
		}
		return c;
	}

	@Test
	public void groupedHeadCountsFilteredGroupsWithoutLosingCteBindings() throws Exception {
		boolean previous = AbstractSQLConfig.ENABLE_WITH_AS;
		try {
			AbstractSQLConfig.ENABLE_WITH_AS = true;
			AbstractSQLConfig<Long, Map<String, Object>, List<Object>> config = cte(false);
			config.setMethod(RequestMethod.HEAD);
			config.setGroup("id");
			config.setHaving(Collections.<String, Object>singletonMap("minimum", "count(id)>1"));
			config.setCount(2);
			config.setPage(1);
			String sql = config.gainSQL(true);
			assertTrue(sql, sql.startsWith("WITH "));
			assertTrue(sql, sql.contains("SELECT count(*) AS `count` FROM (SELECT"));
			assertTrue(sql, sql.contains("GROUP BY `id` HAVING (count(`id`)>1)"));
			assertFalse(sql, sql.contains("LIMIT"));
			assertEquals(5, KingbaseMySQLFunctions.parameterCount(sql));
			assertEquals(Arrays.asList(11, 22, 11, 22, 22), config.getPreparedValueList());
			assertEquals(sql, config.gainSQL(true));
			assertEquals(Arrays.asList(11, 22, 11, 22, 22), config.getPreparedValueList());
		} finally {
			AbstractSQLConfig.ENABLE_WITH_AS = previous;
		}
	}

	@Test public void cteBindingsFollowSqlOrderAndSurviveRepeatedGeneration() throws Exception {
		boolean previous=AbstractSQLConfig.ENABLE_WITH_AS;
		try {
			AbstractSQLConfig.ENABLE_WITH_AS=true;
			for(boolean same:new boolean[]{false,true}) {
				AbstractSQLConfig<Long,Map<String,Object>,List<Object>> c=cte(same);
				String first=c.gainSQL(true);
				assertTrue(first,first.startsWith("WITH "));
				assertEquals(6,KingbaseMySQLFunctions.parameterCount(first));
				assertEquals(Arrays.asList(11,22,11,22,22,"!"),c.getPreparedValueList());
				assertEquals(first,c.gainSQL(true));
				assertEquals(Arrays.asList(11,22,11,22,22,"!"),c.getPreparedValueList());
				AbstractSQLConfig.ENABLE_WITH_AS=false;
				String inline=c.gainSQL(true);assertFalse(inline,inline.startsWith("WITH "));
				assertEquals(Arrays.asList("!",11,22,11,22,22),c.getPreparedValueList());
				AbstractSQLConfig.ENABLE_WITH_AS=true;
				assertEquals(first,c.gainSQL(true));
				assertEquals(Arrays.asList(11,22,11,22,22,"!"),c.getPreparedValueList());
			}
		} finally {AbstractSQLConfig.ENABLE_WITH_AS=previous;}
	}
}
