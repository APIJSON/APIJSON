package apijson.orm;

import apijson.RequestMethod;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class OraclePaginationRegressionTest {
	private Config config(String database, String version) {
		Config c = new Config(version, database);
		c.setDatabase(database.startsWith("KINGBASE_") ? SQLConfig.DATABASE_KINGBASE_AUTO : database);
		c.setSchema("test");
		c.setCount(5);
		c.setPage(2);
		return c;
	}

	@Test
	public void nativeOracleKeepsLegacyPaginationAcrossVersions() throws Exception {
		String query = "SELECT a.*, b.* FROM a JOIN b ON a.id=b.id ORDER BY a.id";
		for (String version : Arrays.asList("12.2", "19.0", "23.26.3.0.0", "26.0")) {
			Config c = config("ORACLE", version);
			assertTrue(c.page(query).contains("ROWNUM \"RN\""));
			assertTrue(c.page(query).endsWith("WHERE \"RN\" > 10"));
			c.putWhere("id", 7L, false);
			String sql = c.gainSQL(true);
			assertTrue(sql, sql.contains("ROWNUM"));
			assertFalse(sql, sql.contains("FETCH NEXT"));
			assertEquals(Arrays.asList(7L), c.getPreparedValueList());
		}
	}

	@Test
	public void headAndUnlimitedQueriesDoNotAcquirePagination() throws Exception {
		Config c = config("ORACLE", "26.0");
		c.setMethod(RequestMethod.HEAD);
		assertEquals("SELECT count(*) FROM a", c.page("SELECT count(*) FROM a"));
		assertFalse(c.gainSQL(true).contains("FETCH"));
		c.setMethod(RequestMethod.GET);
		c.setCount(0);
		assertEquals("SELECT * FROM a", c.page("SELECT * FROM a"));
	}

	@Test
	public void oldOracleAndCompatibilityModesKeepTheirExistingPagination() {
		for (String version : Arrays.asList("11.2", "12.1")) {
			Config c = config("ORACLE", version);
			assertTrue(c.page("SELECT * FROM a").contains("ROWNUM"));
		}
		for (String database : Arrays.asList("KINGBASE_ORACLE", "DAMENG")) {
			Config c = config(database, "26.0");
			assertTrue(database, c.page("SELECT * FROM a").contains("ROWNUM"));
		}
	}

	@Test
	public void otherDatabaseSelectSqlIsIndependentOfOracleVersion() throws Exception {
		for (String database :
				Arrays.asList(
						"MYSQL",
						"POSTGRESQL",
						"SQLSERVER",
						"DB2",
						"KINGBASE_MYSQL",
						"KINGBASE_ORACLE",
						"KINGBASE_SQLSERVER",
						"DAMENG")) {
			Config old = config(database, "11.2"), modern = config(database, "26.0");
			old.setOrder("id+");
			modern.setOrder("id+");
			old.putWhere("id", 7L, false);
			modern.putWhere("id", 7L, false);
			assertEquals(database, old.gainSQL(true), modern.gainSQL(true));
			assertEquals(database, old.getPreparedValueList(), modern.getPreparedValueList());
		}
	}

	@Test
	public void groupedOracleCountsDoNotIncludePagingColumns() throws Exception {
		Config c = config("ORACLE", "26.0");
		c.setMethod(RequestMethod.HEAD);
		c.setGroup("category");
		c.setColumn(Arrays.asList("category"));
		String sql = c.gainSQL(true);
		assertTrue(sql, sql.startsWith("SELECT count(*) FROM ("));
		assertFalse(sql, sql.contains("ROWNUM") || sql.contains("FETCH"));
	}

	private static class Config extends AbstractSQLConfig<Long, Map<String, Object>, List<Object>> {
		private final String version;

		private final String dialect;
        Config(String version, String dialect) {
			super(RequestMethod.GET, "Sample");
			this.version = version; this.dialect=dialect;
		}

		@Override
		protected Object gainKingbaseModeCacheKey() {
			return Arrays.asList(getClass(), dialect);
		}

		@Override
		protected java.sql.Connection openKingbaseModeConnection() throws java.sql.SQLException {
			KingbaseModeResolverTest.Config stub = new KingbaseModeResolverTest.Config();
			stub.mode = dialect.substring("KINGBASE_".length());
			return stub.openKingbaseModeConnection();
		}

		public String gainDBVersion() {
			return version;
		}

		public String gainDBUri() {
			return "jdbc:test";
		}

		public String gainDBAccount() {
			return "test";
		}

		public String gainDBPassword() {
			return "test";
		}

		String page(String sql) {
			return gainOraclePageSQL(sql);
		}
	}
}
