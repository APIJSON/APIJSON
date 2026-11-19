package apijson.orm;

import apijson.RequestMethod;
import apijson.JSONParser;
import java.math.*;
import java.sql.*;
import java.util.*;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Upgrade acceptance on connection-local tables; no business data or persistent settings. */
public class KingbaseMySQLUpgradeLiveTest {
	@BeforeClass public static void parser() throws Exception {
		KingbaseCompatibilityTest.installJsonParser();
		if(System.getenv("KINGBASE_MYSQL_URL")==null)return;
		// The ORM delegates JSON parsing to the application's installed adapter.
		// Live runs supply Fastjson2 on their test classpath, without a core dependency.
		final Class<?> codec=Class.forName("com.alibaba.fastjson2.JSON");
		apijson.JSON.DEFAULT_JSON_PARSER=new JSONParser<Map<String,Object>,List<Object>>() {
			public Map<String,Object> createJSONObject(){return new LinkedHashMap<>();}
			public List<Object> createJSONArray(){return new ArrayList<>();}
			public Object parse(Object value){try{return value instanceof String?codec.getMethod("parse",String.class).invoke(null,value):value;}catch(Exception e){throw new IllegalArgumentException(e);}}
			@SuppressWarnings("unchecked") public Map<String,Object> parseObject(Object value){return (Map<String,Object>)parse(value);}
			public <T>T parseObject(Object value,Class<T> type){return type.cast(parse(value));}
			@SuppressWarnings("unchecked") public List<Object> parseArray(Object value){return (List<Object>)parse(value);}
			@SuppressWarnings("unchecked") public <T>List<T> parseArray(Object value,Class<T> type){return (List<T>)parse(value);}
			public String toJSONString(Object value,boolean format){try{return (String)codec.getMethod("toJSONString",Object.class).invoke(null,value);}catch(Exception e){throw new IllegalArgumentException(e);}}
		};
	}
	private Connection connect() throws SQLException {
		String url=System.getenv("KINGBASE_MYSQL_URL");
		assumeTrue("Set KINGBASE_MYSQL_URL for upgrade acceptance",url!=null&&!url.isEmpty());
		Connection c=DriverManager.getConnection(url,System.getenv("KINGBASE_USERNAME"),System.getenv("KINGBASE_PASSWORD"));
		assertEquals("KINGBASE_MYSQL",new KingbaseModeResolver().detect(c));
		c.setAutoCommit(false);return c;
	}
	private void sql(Connection c,String text) throws SQLException {
		try(Statement s=c.createStatement()){s.setQueryTimeout(10);s.execute(text);}
	}
	private AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> executor(Connection actualConnection) {
		return new AbstractSQLExecutor<Long,Map<String,Object>,List<Object>>() {
			@Override public Connection getConnection(SQLConfig<Long,Map<String,Object>,List<Object>> c){return actualConnection;}
		};
	}
	private AbstractSQLConfig<Long,Map<String,Object>,List<Object>> config(String table,RequestMethod method) {
		AbstractSQLConfig<Long,Map<String,Object>,List<Object>> c=KingbaseSemanticsRegressionTest.config("KINGBASE_MYSQL");
		c.setTable(table);c.setSchema("");c.setMethod(method);c.setCount(0);c.setPrepared(true);return c;
	}
	private int insert(Connection connection,String table,int id,Object value,String cast) throws Exception {
		AbstractSQLConfig<Long,Map<String,Object>,List<Object>> c=config(table,RequestMethod.POST);
		c.setColumn(Arrays.asList("id","v"));c.setValues(Collections.singletonList(Arrays.asList((Object)id,value)));
		if(cast!=null)c.setCast(Collections.singletonMap("v",cast));
		return executor(connection).executeUpdate(c,null);
	}
	private Object read(Connection connection,String table,int id) throws Exception {
		AbstractSQLConfig<Long,Map<String,Object>,List<Object>> c=config(table,RequestMethod.GET);
		c.setColumn(Collections.singletonList("v"));c.putWhere("id",id,false);
		AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> e=executor(connection);
		try(ResultSet r=e.executeQuery(c,null)) {
			assertTrue(table+" id="+id,r.next());
			Object value=e.getValue(c,r,r.getMetaData(),0,new LinkedHashMap<>(),1,"v",null,null);
			assertFalse(r.next());return value;
		}
	}
	private List<Object> sqlValues(Connection connection,String text,Object...args) throws Exception {
		AbstractSQLConfig<Long,Map<String,Object>,List<Object>> c=config("fixture",RequestMethod.GET);
		c.setPreparedValueList(new ArrayList<>(Arrays.asList(args)));
		AbstractSQLExecutor<Long,Map<String,Object>,List<Object>> e=executor(connection);List<Object> values=new ArrayList<>();
		try(PreparedStatement p=e.getStatement(c,text);ResultSet r=p.executeQuery()) {
			while(r.next())values.add(e.getValue(c,r,r.getMetaData(),0,new LinkedHashMap<>(),1,"v",null,null));
		}
		return values;
	}
	private void expectRejected(Connection c,Action action) throws Exception {
		Savepoint savepoint=c.setSavepoint();
		try {assertThrows(SQLException.class,()->action.run());} finally {c.rollback(savepoint);c.releaseSavepoint(savepoint);}
	}
	private interface Action {void run() throws Exception;}
	private void passed(String id) {System.out.println("UPGRADE_CHECK "+id+" PASS");}
	private void currentRelease(Connection c) throws SQLException {
		try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT version()")) {
			r.next();assumeTrue("New-release type feature",r.getString(1).contains("R003C018"));
		}
	}

	@Test public void unsignedTypesUseOrmBindingsForWritesPredicatesUpdatesAndDeletes() throws Exception {
		try(Connection c=connect()) {
			currentRelease(c);
			try {
				String[][] types={{"TINYINT UNSIGNED","255"},{"SMALLINT UNSIGNED","65535"},{"MEDIUMINT UNSIGNED","16777215"},{"INT UNSIGNED","4294967295"},{"BIGINT UNSIGNED","18446744073709551615"}};
				for(int i=0;i<types.length;i++) {
					String table="kb_upgrade_unsigned_"+i,type=types[i][0];BigInteger maximum=new BigInteger(types[i][1]);
					sql(c,"CREATE TEMPORARY TABLE "+table+"(id INT PRIMARY KEY,v "+type+")");
					assertEquals(1,insert(c,table,1,BigInteger.ZERO,null));
					assertEquals(1,insert(c,table,2,maximum,null));
					assertEquals(1,insert(c,table,3,null,null));
					assertEquals("0",String.valueOf(read(c,table,1)));
					assertEquals(maximum.toString(),String.valueOf(read(c,table,2)));assertNull(read(c,table,3));
					assertEquals(1,insert(c,table,6,maximum.toString(),type));assertEquals(maximum.toString(),String.valueOf(read(c,table,6)));
					AbstractSQLConfig<Long,Map<String,Object>,List<Object>> find=config(table,RequestMethod.GET);
					find.setColumn(Collections.singletonList("id"));find.putWhere("v",maximum,false);
					find.setOrder("id+");
					try(ResultSet r=executor(c).executeQuery(find,null)){assertTrue(r.next());assertEquals(2,r.getInt(1));assertTrue(r.next());assertEquals(6,r.getInt(1));assertFalse(r.next());}
					AbstractSQLConfig<Long,Map<String,Object>,List<Object>> update=config(table,RequestMethod.PUT);
					update.setContent(Collections.singletonMap("v",maximum));update.putWhere("id",1,false);
					assertEquals(1,executor(c).executeUpdate(update,null));assertEquals(maximum.toString(),String.valueOf(read(c,table,1)));
					AbstractSQLConfig<Long,Map<String,Object>,List<Object>> delete=config(table,RequestMethod.DELETE);delete.putWhere("id",3,false);
					assertEquals(1,executor(c).executeUpdate(delete,null));
					expectRejected(c,()->insert(c,table,4,maximum.add(BigInteger.ONE),null));
					expectRejected(c,()->insert(c,table,5,BigInteger.valueOf(-1),null));
					passed(type+" zero/max/null + predicate/update/delete + overflow");
				}
			}finally{c.rollback();}
		}
	}

	@Test public void characterBinaryAndMemberTypesRoundTripThroughOrmWrites() throws Exception {
		try(Connection c=connect()) {
			try {
				String[][] text={{"VARCHAR(40)","中文😀 O'Reilly"},{"CHAR(8)","abc"},{"TINYTEXT","a\nb"},{"TEXT","中文\ntext"},{"MEDIUMTEXT","medium"},{"LONGTEXT","long"},{"ENUM('a','b')","b"},{"SET('a','b')","a,b"}};
				for(int i=0;i<text.length;i++) {
					String table="kb_upgrade_text_"+i;sql(c,"CREATE TEMPORARY TABLE "+table+"(id INT PRIMARY KEY,v "+text[i][0]+")");
					assertEquals(1,insert(c,table,1,text[i][1],null));assertEquals(text[i][1],read(c,table,1));
					assertEquals(1,insert(c,table,2,null,null));assertNull(read(c,table,2));passed(text[i][0]+" bound write/null/read");
				}
				byte[] bytes={0,1,(byte)128,(byte)255};
				String[] types={"BINARY(4)","VARBINARY(20)","TINYBLOB","BLOB","MEDIUMBLOB","LONGBLOB"};
				for(int i=0;i<types.length;i++) {
					String table="kb_upgrade_binary_"+i;sql(c,"CREATE TEMPORARY TABLE "+table+"(id INT PRIMARY KEY,v "+types[i]+")");
					assertEquals(1,insert(c,table,1,bytes,null));assertEquals(Base64.getEncoder().encodeToString(bytes),read(c,table,1));
					assertEquals(1,insert(c,table,2,null,null));assertNull(read(c,table,2));passed(types[i]+" byte binding/null/read");
				}
				String table="kb_upgrade_large";sql(c,"CREATE TEMPORARY TABLE "+table+"(id INT PRIMARY KEY,v LONGTEXT)");
				char[] chars=new char[131072];Arrays.fill(chars,'中');String large=new String(chars);
				assertEquals(1,insert(c,table,1,large,null));assertEquals(large,read(c,table,1));passed("LONGTEXT 128K Unicode characters");
			}finally{c.rollback();}
		}
	}

	@Test public void explicitAndDefaultByteCharacterSemanticsReachTheDatabaseUnchanged() throws Exception {
		try(Connection c=connect()) {
			try {
				for(String unit:Arrays.asList("byte","char")) {
					sql(c,"SET LOCAL nls_length_semantics='"+unit+"'");
					String table="kb_upgrade_length_"+unit;sql(c,"CREATE TEMPORARY TABLE "+table+"(id INT PRIMARY KEY,v VARCHAR(4))");
					String value=unit.equals("byte")?"中":"一二三四";assertEquals(1,insert(c,table,1,value,null));assertEquals(value,read(c,table,1));
					if(unit.equals("byte"))expectRejected(c,()->insert(c,table,2,"中文",null));
					else {
						assertEquals(1,insert(c,table,2,"一二三四五",null));
						sql(c,"INSERT INTO "+table+" VALUES(3,'一二三四五')");
						assertEquals("一二三四",read(c,table,2));assertEquals(read(c,table,3),read(c,table,2));
					}
					passed("NLS_LENGTH_SEMANTICS="+unit);
				}
				for(String unit:Arrays.asList("BYTE","CHAR")) {
					String table="kb_upgrade_explicit_"+unit.toLowerCase(Locale.ROOT);sql(c,"CREATE TEMPORARY TABLE "+table+"(id INT PRIMARY KEY,v VARCHAR(4 "+unit+"))");
					String value=unit.equals("BYTE")?"中":"一二三四";assertEquals(1,insert(c,table,1,value,null));assertEquals(value,read(c,table,1));passed("explicit VARCHAR(4 "+unit+")");
				}
			}finally{c.rollback();}
		}
	}

	@Test public void jsonJsonbAndXmlPhysicalColumnsUseTypedOrmBindings() throws Exception {
		try(Connection c=connect()) {
			try {
				for(String type:Arrays.asList("JSON","JSONB","XML")) {
					String table="kb_upgrade_"+type.toLowerCase(Locale.ROOT);sql(c,"CREATE TEMPORARY TABLE "+table+"(id INT PRIMARY KEY,v "+type+")");
					String value=type.equals("XML")?"<root>中文</root>":"{\"a\":[1,false,null],\"n\":9007199254740993}";
					assertEquals(1,insert(c,table,1,value,type));Object result=read(c,table,1);
					if(type.equals("XML"))assertEquals(value,result);
					else {assertTrue(type+" result="+result+" java="+(result==null?null:result.getClass()),result instanceof Map);assertEquals("9007199254740993",((Map<?,?>)result).get("n").toString());assertEquals(Arrays.asList(1,false,null),((Map<?,?>)result).get("a"));}
					assertEquals(1,insert(c,table,2,null,type));assertNull(read(c,table,2));passed(type+" physical column cast/bind/read/null");
				}
			}finally{c.rollback();}
		}
	}

	@Test public void temporalAndBitValuesRoundTripWithoutDriverRepresentationLoss() throws Exception {
		try(Connection c=connect()) {
			try {
				String[][] values={{"DATE","2024-02-29"},{"TIME(6)","-30:34:56.123456"},{"DATETIME(6)","2024-01-31 12:34:56.123456"},{"TIMESTAMP(6)","2024-01-31 12:34:56.123456"},{"YEAR","2026"},{"BIT(64)","18446744073709551615"}};
				for(int i=0;i<values.length;i++) {
					String table="kb_upgrade_temporal_"+i,type=values[i][0],value=values[i][1];sql(c,"CREATE TEMPORARY TABLE "+table+"(id INT PRIMARY KEY,v "+type+")");
					assertEquals(1,insert(c,table,1,value,type));assertEquals(value,String.valueOf(read(c,table,1)));
					assertEquals(1,insert(c,table,2,null,type));assertNull(read(c,table,2));passed(type+" @cast bound write/read/null");
				}
			}finally{c.rollback();}
		}
	}

	@Test public void generatedCtesBindBeforeProjectionAndRemainStableAcrossExecutions() throws Exception {
		boolean previous=AbstractSQLConfig.ENABLE_WITH_AS;
		try(Connection c=connect()) {
			try {
				sql(c,"CREATE TEMPORARY TABLE users(id INT PRIMARY KEY,name VARCHAR(20))");sql(c,"CREATE TEMPORARY TABLE items(id INT PRIMARY KEY)");
				sql(c,"INSERT INTO users VALUES(11,'first'),(22,'second')");sql(c,"INSERT INTO items VALUES(11),(22)");
				AbstractSQLConfig.ENABLE_WITH_AS=true;
				for(boolean same:new boolean[]{false,true}) {
					AbstractSQLConfig<Long,Map<String,Object>,List<Object>> cfg=KingbaseMySQLUpgradeRegressionTest.cte(same);cfg.setSchema("");
					for(int repeat=0;repeat<3;repeat++)try(ResultSet r=executor(c).executeQuery(cfg,null)) {
						assertTrue(r.next());assertEquals(22,r.getInt(1));assertEquals("second!",r.getString(2));assertFalse(r.next());
					}
					passed("CTE sameTable="+same+" 3 repeated executions");
				}
			}finally{c.rollback();}
		}finally{AbstractSQLConfig.ENABLE_WITH_AS=previous;}
	}

	@Test public void zeroDatesFollowSessionPolicyAndKeepTheirLiteralValueOnRead() throws Exception {
		try(Connection c=connect()) {
			currentRelease(c);
			try {
				sql(c,"SET LOCAL sql_mode=ANSI_QUOTES");
				for(String type:Arrays.asList("DATE","DATETIME(6)","TIMESTAMP(6)")) {
					String table="kb_upgrade_zero_"+type.replaceAll("[^A-Za-z]", "").toLowerCase(Locale.ROOT);
					sql(c,"CREATE TEMPORARY TABLE "+table+"(id INT PRIMARY KEY,v "+type+")");
					String input=type.equals("DATE")?"0000-00-00":"0000-00-00 00:00:00.000000";
					assertEquals(1,insert(c,table,1,input,type));assertEquals(input,read(c,table,1));
					sql(c,"SET LOCAL sql_mode=ANSI_QUOTES,NO_ZERO_DATE");
					expectRejected(c,()->insert(c,table,2,input,type));
					assertEquals(input,read(c,table,1));
					sql(c,"SET LOCAL sql_mode=ANSI_QUOTES");passed(type+" zero date on/off + exact read");
				}
			}finally{c.rollback();}
		}
	}

	@Test public void upgradedExpressionsAndSessionControlledFunctionsKeepValues() throws Exception {
		try(Connection c=connect()) {
			currentRelease(c);
			try {
				assertEquals(Collections.singletonList(true),sqlValues(c,"SELECT json_overlaps(CAST(? AS JSON),CAST(? AS JSON)) AS v","[1]","[1.0]"));
				assertEquals(Collections.singletonList(false),sqlValues(c,"SELECT json_overlaps(CAST(? AS JSON),CAST(? AS JSON)) AS v","[[1]]","[1]"));passed("JSON_OVERLAPS scalar/nested + Boolean contract");
				assertEquals(Collections.singletonList("2"),sqlValues(c,"SELECT CAST(? AS JSON)->>'$.a' AS v","{\"a\":2}"));
				assertEquals(Collections.singletonList(true),sqlValues(c,"SELECT CAST(? AS JSON)->>'$.a' = '2' AS v","{\"a\":2}"));
				assertEquals(Collections.singletonList(3.0),sqlValues(c,"SELECT CAST(? AS JSON)->>'$.a' + 1 AS v","{\"a\":2}"));passed("JSON arrows extraction/comparison/arithmetic precedence");
				assertEquals(Collections.singletonList("1"),sqlValues(c,"SELECT CASE WHEN 1=1 THEN 1 ELSE '2' END AS v"));
				assertEquals("1.25",sqlValues(c,"SELECT CASE WHEN 1=1 THEN CAST(1.25 AS DECIMAL(12,2)) ELSE 2 END AS v").get(0).toString());passed("CASE mixed string/numeric and decimal branches");
				assertEquals(Arrays.asList("bbb","aa","c"),sqlValues(c,"SELECT name AS v FROM (SELECT 'aa' AS name UNION ALL SELECT 'bbb' UNION ALL SELECT 'c') t ORDER BY length(name) DESC"));passed("ORDER BY scalar function");
				sql(c,"SET LOCAL mysql_format_compatible=on");
				AbstractSQLConfig<Long,Map<String,Object>,List<Object>> cfg=config("fixture",RequestMethod.GET);
				cfg.setColumn(Collections.singletonList("format('1234.5',2):v"));
				String generated=cfg.gainSQL(true).replace(cfg.gainTablePath(),"(SELECT 1 AS id) fixture");
				assertEquals(Collections.singletonList("1,234.50"),sqlValues(c,generated,cfg.getPreparedValueList().toArray()));passed("FORMAT compatibility setting + ORM-generated call");
				sql(c,"SET LOCAL mysql_convert_compatible=on");
				assertEquals(Collections.singletonList("中文"),sqlValues(c,"SELECT CONVERT(CAST(? AS varchar) USING UTF8) AS v","中文"));passed("CONVERT USING UTF8 with required setting (backend SQL)");
			}finally{c.rollback();}
		}
	}

	@Test public void configuredUpsertAndTimestampSessionChangesUseExistingOrmPaths() throws Exception {
		String table="kb_upgrade_upsert";Object previous=AbstractSQLConfig.UPSERT_TABLE_MAP.get(table);
		try(Connection c=connect()) {
			try {
				sql(c,"CREATE TEMPORARY TABLE "+table+"(id INT PRIMARY KEY,v VARCHAR(20))");
				AbstractSQLConfig.UPSERT_TABLE_MAP.put(table,"v = VALUES(v)");
				assertEquals(1,insert(c,table,1,"first",null));assertTrue(insert(c,table,1,"second",null)>0);
				assertEquals("second",read(c,table,1));
				assertEquals(Collections.singletonList(1L),sqlValues(c,"SELECT count(*) AS v FROM "+table));passed("configured ON DUPLICATE KEY UPDATE");
				sql(c,"CREATE TEMPORARY TABLE kb_upgrade_timezone(id INT PRIMARY KEY,v TIMESTAMP(6))");
				String value="2024-01-31 12:34:56.123456";assertEquals(1,insert(c,"kb_upgrade_timezone",1,value,"TIMESTAMP(6)"));
				for(String zone:Arrays.asList("UTC","Asia/Shanghai")) {
					sql(c,"SET LOCAL TIME ZONE '"+zone+"'");String server;
					try(Statement s=c.createStatement();ResultSet r=s.executeQuery("SELECT v FROM kb_upgrade_timezone WHERE id=1")){r.next();server=r.getString(1);}
					assertEquals(server,read(c,"kb_upgrade_timezone",1));passed("TIMESTAMP ORM read agrees with server in "+zone);
				}
			}finally{c.rollback();}
		}finally{if(previous==null)AbstractSQLConfig.UPSERT_TABLE_MAP.remove(table);else AbstractSQLConfig.UPSERT_TABLE_MAP.put(table,previous);}
	}
}
