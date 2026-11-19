package apijson.orm;

import apijson.RequestMethod;
import org.junit.Before;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.Assert.*;

public class KingbaseModeResolverTest {
	static class Config extends AbstractSQLConfig<Long, Map<String, Object>, List<Object>> {
		String mode = "mysql";
		boolean empty;
		boolean fail;
		final AtomicInteger opened = new AtomicInteger();
		final AtomicInteger closed = new AtomicInteger();
		final AtomicInteger statementsClosed = new AtomicInteger();
		final AtomicInteger resultsClosed = new AtomicInteger();

		Config() {
			super(RequestMethod.GET, "User");
			setDatabase(DATABASE_KINGBASE_AUTO);
			setSchema("public");
		}

		public String gainDBVersion() {
			return "9.0.0";
		}

		public String gainDBUri() {
			return "jdbc:kingbase8://localhost/test";
		}

		public String gainDBAccount() {
			return "test";
		}

		public String gainDBPassword() {
			return "unused";
		}

		protected Connection openKingbaseModeConnection() throws SQLException {
			opened.incrementAndGet();
			if (fail) {
				throw new SQLException("unavailable");
			}
			AtomicInteger rows = new AtomicInteger();
			ResultSet result =
					(ResultSet)
							Proxy.newProxyInstance(
									getClass().getClassLoader(),
									new Class<?>[] {ResultSet.class},
									(p, m, a) -> {
										switch (m.getName()) {
											case "next":
												return !empty && rows.getAndIncrement() == 0;
											case "getString":
												assertEquals(1, a[0]);
												return mode;
											case "close":
												resultsClosed.incrementAndGet();
												return null;
											default:
												throw new AssertionError(m.getName());
										}
									});
			Statement statement =
					(Statement)
							Proxy.newProxyInstance(
									getClass().getClassLoader(),
									new Class<?>[] {Statement.class},
									(p, m, a) -> {
										switch (m.getName()) {
											case "setQueryTimeout":
												assertEquals(5, a[0]);
												return null;
											case "executeQuery":
												assertEquals("SHOW database_mode", a[0]);
												return result;
											case "close":
												statementsClosed.incrementAndGet();
												return null;
											default:
												throw new AssertionError(m.getName());
										}
									});
			return (Connection)
					Proxy.newProxyInstance(
							getClass().getClassLoader(),
							new Class<?>[] {Connection.class},
							(p, m, a) -> {
								switch (m.getName()) {
									case "createStatement":
										return statement;
									case "close":
										closed.incrementAndGet();
										return null;
									default:
										throw new AssertionError(m.getName());
								}
							});
		}
	}

	@Before
	public void reset() {
		AbstractSQLConfig.KINGBASE_MODE_RESOLVER.clear();
		KingbaseCompatibilityTest.installJsonParser();
	}

	@Test
	public void optionalExpectationIsCheckedEvenOnSharedCacheHit() {
		Config auto = new Config();
		auto.setDatabase("KINGBASE_AUTO");
		auto.mode = "oracle";
		assertEquals("KINGBASE_ORACLE", auto.gainSQLDatabase());
		Config strict =
				new Config() {
					@Override
					protected Object gainKingbaseModeCacheKey() {
						return auto.gainKingbaseModeCacheKey();
					}

					@Override
					protected String gainKingbaseExpectedDatabase() {
						return "KINGBASE_MYSQL";
					}
				};
		strict.setDatabase("KINGBASE_AUTO");
		IllegalStateException error =
				assertThrows(IllegalStateException.class, strict::gainSQLDatabase);
		assertTrue(error.getMessage().contains("expected KINGBASE_MYSQL"));
		assertTrue(error.getMessage().contains("KINGBASE_ORACLE"));
		assertEquals(0, strict.opened.get());
		assertEquals("KINGBASE_ORACLE", auto.gainSQLDatabase());
		assertEquals(1, auto.opened.get());
	}

	@Test
	public void startupApiAndConfigShareCacheAndInvalidation() {
		Config c = new Config();
		c.setDatabase("KINGBASE_AUTO");
		Object key = c.gainKingbaseModeCacheKey();
		assertEquals(
				"KINGBASE_MYSQL",
				AbstractSQLConfig.KINGBASE_MODE_RESOLVER.resolve(
						key, c::openKingbaseModeConnection));
		assertEquals("KINGBASE_MYSQL", c.gainSQLDatabase());
		assertEquals(1, c.opened.get());
		c.mode = "oracle";
		AbstractSQLConfig.KINGBASE_MODE_RESOLVER.invalidateKey(key);
		assertEquals("KINGBASE_ORACLE", c.gainSQLDatabase());
		assertEquals(2, c.opened.get());
	}

	@Test
	public void autoClassificationDetectsOnceAndUsesTheCachedMode() {
		Config c = new Config();
		c.setDatabase("KINGBASE_AUTO");
		assertFalse(c.isKingBase());
		assertTrue(c.isKingBaseCompatibilityMode());
		assertTrue(c.isKingBaseMySQL());
		assertEquals("KINGBASE_MYSQL", c.gainSQLDatabase());
		assertEquals(1, c.opened.get());
		assertEquals(1, c.closed.get());
		assertEquals(1, c.statementsClosed.get());
		assertEquals(1, c.resultsClosed.get());
		assertEquals("KINGBASE_AUTO", c.getDatabase());
		assertTrue(AbstractSQLConfig.DATABASE_LIST.contains("KINGBASE_AUTO"));
	}

	@Test
	public void autoClassificationPropagatesDetectionFailuresAndAllowsRetry() {
		Config c = new Config();
		c.fail = true;
		assertThrows(IllegalStateException.class, c::isKingBase);
		assertThrows(IllegalStateException.class, c::isKingBaseCompatibilityMode);
		assertEquals(2, c.opened.get());
		c.fail = false;
		assertTrue(c.isKingBaseCompatibilityMode());
		assertFalse(c.isKingBase());
		assertEquals(3, c.opened.get());
	}

	@Test
	public void autoUsesAllActualModesWithoutChangingConfiguration() throws Exception {
			for (String actual : Arrays.asList("mysql", "oracle", "sqlserver")) {
				Config detected = new Config();
				detected.mode = " " + actual.toUpperCase(Locale.ROOT) + " ";
				detected.setDatasource(actual);
				Config reference =
						new Config() {
							@Override
							public String gainSQLDatabase() {
								return getDatabase();
							}
						};
				String expected = "KINGBASE_" + actual.toUpperCase(Locale.ROOT);
				reference.setDatabase(expected);
				// Frozen internal dialect for SQL comparison only.
            detected.setCount(10).setPage(1);
				reference.setCount(10).setPage(1);
				assertEquals(expected, detected.gainSQLDatabase());
				assertFalse(detected.isKingBase());
				assertTrue(detected.isKingBaseCompatibilityMode());
					assertEquals(reference.gainSQL(false), detected.gainSQL(false));
					assertEquals(reference.gainSQL(true), detected.gainSQL(true));
					assertEquals("mysql".equals(actual), detected.isKingBaseMySQL());
					assertEquals("oracle".equals(actual), detected.isKingBaseOracle());
					assertEquals("sqlserver".equals(actual), detected.isKingBaseSQLServer());
				assertEquals("KINGBASE_AUTO", detected.getDatabase());
				assertEquals(1, detected.opened.get());
				assertEquals(1, detected.closed.get());
				assertEquals(1, detected.statementsClosed.get());
				assertEquals(1, detected.resultsClosed.get());
				assertEquals(0, reference.opened.get());
        }
    }

    @Test public void internalKingbaseModesAreRejectedAsBackendAndDefaultConfiguration() {
        String previous = AbstractSQLConfig.DEFAULT_DATABASE;
        try {
            for (String dialect : Arrays.asList("KINGBASE_MYSQL", "KINGBASE_ORACLE", "KINGBASE_SQLSERVER")) {
                assertFalse(AbstractSQLConfig.DATABASE_LIST.contains(dialect));
                assertFalse(KingbaseModeResolver.requiresDetection(dialect));
                Config c = new Config(); c.setDatabase(dialect);
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class, c::gainSQLDatabase);
                assertTrue(error.getMessage().contains("KINGBASE_AUTO"));
                AbstractSQLConfig.DEFAULT_DATABASE = dialect; c.setDatabase(null);
                assertThrows(IllegalArgumentException.class, c::gainSQLDatabase);
                assertEquals(0, c.opened.get());
            }
        } finally { AbstractSQLConfig.DEFAULT_DATABASE = previous; }
    }

    @Test public void nativeBackendAndDefaultRoutesKeepTheirOriginalValidationContract() {
        String previous = AbstractSQLConfig.DEFAULT_DATABASE;
        try {
            for (String database : Arrays.asList("UNKNOWN_DB", "mysql", "", " ")) {
                Config c = new Config(); c.setDatabase(database);
                assertEquals(database, c.gainSQLDatabase());
                AbstractSQLConfig.DEFAULT_DATABASE = database; c.setDatabase(null);
                assertEquals(database, c.gainSQLDatabase());
                assertEquals(0, c.opened.get());
            }
        } finally { AbstractSQLConfig.DEFAULT_DATABASE = previous; }
    }

    @Test public void unlistedDatabasesAreRejectedInRequestsBeforeCreatingAConfig() {
        for (String dialect : Arrays.asList("KINGBASE_MYSQL", "KINGBASE_ORACLE", "KINGBASE_SQLSERVER", "UNKNOWN_DB", "mysql")) {
            Map<String, Object> request = new LinkedHashMap<>(); request.put("@database", dialect);
            apijson.orm.exception.UnsupportedDataTypeException error = assertThrows(apijson.orm.exception.UnsupportedDataTypeException.class, () -> AbstractSQLConfig.newSQLConfig(
                RequestMethod.GET, "User", null, request, null, false,
                new AbstractSQLConfig.SimpleCallback<Long, Map<String, Object>, List<Object>>() {
                    public SQLConfig<Long, Map<String, Object>, List<Object>> getSQLConfig(RequestMethod method,
                        String database, String datasource, String namespace, String catalog, String schema, String table) {
                        throw new AssertionError("Unlisted database must be rejected before routing");
                    }
                }));
            assertTrue(error.getMessage().contains("KINGBASE_AUTO"));
        }
    }

    @Test public void backendValidationUsesThePublicAllowlistIncludingRegisteredExtensions() {
        for (String database : AbstractSQLConfig.DATABASE_LIST) {
            AbstractSQLConfig.validateConfiguredDatabase(database);
            if (!"KINGBASE_AUTO".equals(database)) {
                Config c = new Config(); c.setDatabase(database);
                assertEquals(database, c.gainSQLDatabase()); assertEquals(0, c.opened.get());
            }
        }
        for (String invalid : Arrays.asList(null, "", " ", "UNKNOWN_DB")) {
            assertThrows(IllegalArgumentException.class, () -> AbstractSQLConfig.validateConfiguredDatabase(invalid));
        }
        String custom = "REGISTERED_TEST_DB";
        AbstractSQLConfig.DATABASE_LIST.add(custom);
        try {
            Config c = new Config(); c.setDatabase(custom);
            assertEquals(custom, c.gainSQLDatabase()); assertEquals(0, c.opened.get());
        } finally { AbstractSQLConfig.DATABASE_LIST.remove(custom); }
        assertThrows(IllegalArgumentException.class, () -> AbstractSQLConfig.validateConfiguredDatabase(custom));
    }

    @Test public void optionalBackendExpectationAcceptsMatchingModesAndRejectsMismatches() {
        for (String actual : Arrays.asList("mysql", "oracle", "sqlserver")) {
            for (String expected : Arrays.asList("KINGBASE_MYSQL", "KINGBASE_ORACLE", "KINGBASE_SQLSERVER", "invalid")) {
                Config c = new Config() {
                    @Override protected String gainKingbaseExpectedDatabase() { return " " + expected.toLowerCase(Locale.ROOT) + " "; }
                };
                c.mode = actual; c.setDatasource(actual + expected);
                if (expected.equals("KINGBASE_" + actual.toUpperCase(Locale.ROOT))) {
                    assertEquals(expected, c.gainSQLDatabase());
                } else if ("invalid".equals(expected)) {
                    assertThrows(IllegalArgumentException.class, c::gainSQLDatabase);
                } else {
                    assertThrows(IllegalStateException.class, c::gainSQLDatabase);
                    assertThrows(IllegalStateException.class, () -> c.gainSQL(true));
                }
                assertEquals(1, c.opened.get());
			}
		}
	}

	@Test
	public void nonKingbaseAndLegacyModesNeverProbe() {
		for (String db : Arrays.asList("MYSQL", "ORACLE", "SQLSERVER", "KINGBASE")) {
			Config c = new Config();
			c.setDatabase(db);
			c.fail = true;
			assertEquals(db, c.gainSQLDatabase());
			assertEquals("KINGBASE".equals(db), c.isKingBase());
			assertFalse(c.isKingBaseCompatibilityMode());
			assertEquals(0, c.opened.get());
		}
		assertEquals(KingbaseSQLDialect.LEGACY, KingbaseSQLDialect.from("KINGBASE"));
	}

	@Test
	public void defaultCompatibilityModeIsDetectedWithoutChangingConfiguration() {
		String previous = AbstractSQLConfig.DEFAULT_DATABASE;
		try {
			AbstractSQLConfig.DEFAULT_DATABASE = SQLConfig.DATABASE_KINGBASE_AUTO;
			Config c = new Config();
			c.setDatabase(null);
			assertFalse(c.isKingBase());
			assertEquals(1, c.opened.get());
			assertTrue(c.isKingBaseCompatibilityMode());
			assertTrue(c.isKingBaseMySQL());
			assertEquals(1, c.opened.get());
			assertNull(c.getDatabase());
		} finally {
			AbstractSQLConfig.DEFAULT_DATABASE = previous;
		}
	}

	@Test
	public void cacheIsSharedIsolatedAndInvalidatable() {
		Config a = new Config();
		a.setDatabase("KINGBASE_AUTO");
		a.setDatasource("a");
		Config b = new Config();
		b.setDatabase("KINGBASE_AUTO");
		b.setDatasource("b");
		b.mode = "oracle";
		Config copy = new Config();
		copy.setDatabase("KINGBASE_AUTO");
		copy.setDatasource("a");
		copy.fail = true;
		assertEquals("KINGBASE_MYSQL", a.gainSQLDatabase());
		assertEquals("KINGBASE_MYSQL", copy.gainSQLDatabase());
		assertEquals(0, copy.opened.get());
		assertEquals("KINGBASE_ORACLE", b.gainSQLDatabase());
		a.mode = "sqlserver";
		AbstractSQLConfig.KINGBASE_MODE_RESOLVER.invalidate(a);
		assertEquals("KINGBASE_SQLSERVER", a.gainSQLDatabase());
		assertEquals(2, a.opened.get());
		a.setDatasource("b");
		assertEquals("KINGBASE_ORACLE", a.gainSQLDatabase());
	}

	@Test
	public void failuresAreNotCachedAndUnknownModesNeverGuess() {
		Config c = new Config();
		c.fail = true;
		assertThrows(IllegalStateException.class, c::gainSQLDatabase);
		c.fail = false;
		c.mode = "pg";
		assertThrows(IllegalStateException.class, c::gainSQLDatabase);
		assertEquals(1, c.closed.get());
		assertEquals(1, c.resultsClosed.get());
		c.mode = null;
		assertThrows(IllegalStateException.class, c::gainSQLDatabase);
		c.empty = true;
		assertThrows(IllegalStateException.class, c::gainSQLDatabase);
		c.empty = false;
		c.mode = "mysql";
		assertEquals("KINGBASE_MYSQL", c.gainSQLDatabase());
		assertEquals(5, c.opened.get());
	}

	@Test
	public void concurrentRequestsProbeOnlyOnce() throws Exception {
		Config c = new Config();
		ExecutorService pool = Executors.newFixedThreadPool(8);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<String>> results = new ArrayList<>();
			for (int i = 0; i < 16; i++)
				results.add(
						pool.submit(
								() -> {
									start.await();
									return c.gainSQLDatabase();
								}));
			start.countDown();
			for (Future<String> result : results)
				assertEquals("KINGBASE_MYSQL", result.get(5, TimeUnit.SECONDS));
			assertEquals(1, c.opened.get());
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	public void recursiveConnectionRoutingFailsClearly() {
		Config c =
				new Config() {
					@Override
					public String gainDBUri() {
						return isKingBaseMySQL() ? "mysql" : "other";
					}
				};
		IllegalStateException failure =
				assertThrows(IllegalStateException.class, c::gainSQLDatabase);
		assertTrue(failure.getMessage().contains("must not depend"));
		assertEquals(0, c.opened.get());
	}

	@Test
	public void requestConfigurationUsesDetectionBeforeSQLGeneration() throws Exception {
		Config c = new Config();
		Map<String, Object> request = new LinkedHashMap<>();
		request.put("@database", "KINGBASE_AUTO");
		request.put("@column", "id,name");
		request.put("id", 1);
		SQLConfig<Long, Map<String, Object>, List<Object>> result =
				AbstractSQLConfig.newSQLConfig(
						RequestMethod.GET,
						"User",
						null,
						request,
						null,
						false,
						new AbstractSQLConfig.SimpleCallback<
								Long, Map<String, Object>, List<Object>>() {
							public SQLConfig<Long, Map<String, Object>, List<Object>> getSQLConfig(
									RequestMethod method,
									String database,
									String datasource,
									String namespace,
									String catalog,
									String schema,
									String table) {
								return c;
							}
						});
		assertTrue(result.gainSQL(false).contains("`id`"));
		assertEquals(1, c.opened.get());
	}

	@Test
	public void metadataRequestIdsFollowDetectedModesAndPreserveCustomCallbacks() throws Exception {
		String previous = AbstractSQLConfig.DEFAULT_DATABASE;
		AbstractSQLConfigJoinCountTest.installJSONParser();
		try {
			AbstractSQLConfig.DEFAULT_DATABASE = SQLConfig.DATABASE_KINGBASE_AUTO;
			Map<String, String> metadata = new LinkedHashMap<>();
			metadata.put("Table", "table_name");
			metadata.put("Column", "ordinal_position");
			metadata.put("PgClass", "oid");
			metadata.put("PgAttribute", "attnum");
			for (String mode : Arrays.asList("mysql", "oracle", "sqlserver")) {
				for (Map.Entry<String, String> table : metadata.entrySet()) {
					for (String configured : Arrays.asList(null, SQLConfig.DATABASE_KINGBASE_AUTO)) {
						for (boolean custom : Arrays.asList(false, true)) {
							Config c = new Config();
							c.mode = mode;
							String idKey = custom ? "custom_id" : table.getValue();
							Object id = "table_name".equals(idKey) ? "Orders" : 7L;
							Map<String, Object> request = new LinkedHashMap<>();
							if (configured != null) request.put("@database", configured);
							request.put("@datasource", mode + table.getKey() + configured + custom);
							request.put(idKey, id);
							SQLConfig<Long, Map<String, Object>, List<Object>> result = AbstractSQLConfig.newSQLConfig(
									RequestMethod.GET, table.getKey(), null, request, null, false,
									new AbstractSQLConfig.SimpleCallback<Long, Map<String, Object>, List<Object>>() {
										@Override public SQLConfig<Long, Map<String, Object>, List<Object>> getSQLConfig(
												RequestMethod method, String database, String datasource, String namespace,
												String catalog, String schema, String name) {
											c.setMethod(method); c.setTable(name); return c;
										}
										@Override public String getIdKey(String database, String datasource, String namespace,
												String catalog, String schema, String name) {
											return custom ? idKey : super.getIdKey(database, datasource, namespace, catalog, schema, name);
										}
									});
							assertEquals(mode + "/" + table.getKey() + "/" + idKey, id, result.getId());
							assertEquals(table.getValue(), result.getIdKey());
							assertFalse(result.isKingBase());
							assertTrue(result.isKingBaseCompatibilityMode());
							assertEquals(configured, result.getDatabase());
							assertEquals(1, c.opened.get());
							assertEquals(1, c.closed.get());
							assertEquals(id, request.get(idKey));
						}
					}
				}
			}
		} finally {
			AbstractSQLConfig.DEFAULT_DATABASE = previous;
			AbstractSQLConfigJoinCountTest.restoreJSONParser();
		}
	}

	@Test
	public void joinConfigurationsResolveTheirOwnDataSources() throws Exception {
		Config main = new Config();
		main.setDatasource("a");
		Config vice = new Config();
		vice.setDatasource("a");
		vice.setTable("Comment");
		Join<Long, Map<String, Object>, List<Object>> join = new Join<>();
		join.setJoinType("<");
		join.setTable("Comment");
		join.setJoinConfig(vice);
		Join.On on = new Join.On();
		on.setKey("userId");
		on.setTargetTable("User");
		on.setTargetKey("id");
		join.setOnList(Arrays.asList(on));
		main.setJoinList(Arrays.asList(join));
		assertTrue(main.gainSQL(false).contains("`Comment`"));
		assertEquals(1, main.opened.get() + vice.opened.get());
		vice.setDatasource("b");
		vice.setDatabase("KINGBASE_AUTO");
		vice.mode = "oracle";
		assertTrue(vice.isKingBaseOracle());
	}
}
