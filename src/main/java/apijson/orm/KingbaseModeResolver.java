package apijson.orm;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Resolves Kingbase compatibility before dialect-dependent SQL processing. */
public final class KingbaseModeResolver {
	private final ConcurrentHashMap<Object, FutureTask<String>> cache = new ConcurrentHashMap<>();
	private final ThreadLocal<Boolean> resolving = new ThreadLocal<>();

    /** Only AUTO is a configuration entry; concrete modes are internal dialects. */
	public static boolean requiresDetection(String database) {
        return SQLConfig.DATABASE_KINGBASE_AUTO.equals(database);
	}

	@FunctionalInterface
	public interface ConnectionFactory {
		Connection open() throws SQLException;
	}

	public String resolve(AbstractSQLConfig<?, ?, ?> config) {
		return resolveWithKey(config::gainKingbaseModeCacheKey, config::openKingbaseModeConnection);
	}

	/** Share startup/runtime detections using the same stable data-source key.
	 * The factory must return a dedicated connection which this resolver closes. */
	public String resolve(Object key, ConnectionFactory factory) {
		return resolveWithKey(() -> key, factory);
	}

	private String resolveWithKey(
			java.util.function.Supplier<Object> keySupplier, ConnectionFactory factory) {
		if (Boolean.TRUE.equals(resolving.get())) {
			throw new IllegalStateException(
					"Kingbase detection connection/key must not depend on the resolved SQL dialect");
		}
		resolving.set(true);
		try {
			Object key = Objects.requireNonNull(keySupplier.get(), "Kingbase detection cache key");
			FutureTask<String> created =
					new FutureTask<>(
							() -> {
								try (Connection connection = factory.open()) {
									return detect(connection);
								}
							});
			FutureTask<String> task = cache.putIfAbsent(key, created);
			if (task == null) {
				task = created;
				task.run();
			}
			try {
				return task.get();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(
						"Interrupted while detecting Kingbase compatibility mode", e);
			} catch (ExecutionException e) {
				cache.remove(key, task);
				throw new IllegalStateException(
						"Cannot detect Kingbase compatibility mode; check the detection connection, permissions and server database_mode",
						e.getCause());
			}
		} finally {
			resolving.remove();
		}
	}

	public String detect(Connection connection) throws SQLException {
		try (Statement statement = connection.createStatement()) {
			statement.setQueryTimeout(5);
			try (ResultSet result = statement.executeQuery("SHOW database_mode")) {
				if (!result.next()) {
					throw new SQLException("Kingbase database_mode returned no row");
				}
				return fromServerMode(result.getString(1));
			}
		}
	}

	public static String fromServerMode(String value) throws SQLException {
		String mode = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
		switch (mode) {
			case "mysql":
				return SQLConfig.DATABASE_KINGBASE_MYSQL;
			case "oracle":
				return SQLConfig.DATABASE_KINGBASE_ORACLE;
			case "sqlserver":
			case "sql_server":
			case "mssql":
				return SQLConfig.DATABASE_KINGBASE_SQLSERVER;
			default:
				throw new SQLException("Unsupported Kingbase database_mode: " + value);
		}
	}

	public static String requireExpectedMode(String actual, String expected) {
		if (expected == null || expected.trim().isEmpty()) return actual;
		String normalized = expected.trim().toUpperCase(Locale.ROOT);
		if (!SQLConfig.DATABASE_KINGBASE_MYSQL.equals(normalized)
				&& !SQLConfig.DATABASE_KINGBASE_ORACLE.equals(normalized)
				&& !SQLConfig.DATABASE_KINGBASE_SQLSERVER.equals(normalized)) {
			throw new IllegalArgumentException("Unsupported expected Kingbase mode: " + expected);
		}
		if (!normalized.equals(actual)) {
			throw new IllegalStateException(
					"Kingbase database mode mismatch: expected "
							+ normalized
							+ " but server reported "
							+ actual);
		}
		return actual;
	}

	/** Invalidate the same key used by resolve(key, factory). */
	public void invalidateKey(Object key) {
		cache.remove(key);
	}

	/** Invalidate after changing a data source's target; in-flight callers may finish with the old result. */
	public void invalidate(AbstractSQLConfig<?, ?, ?> config) {
		cache.remove(config.gainKingbaseModeCacheKey());
	}

	public void clear() {
		cache.clear();
	}
}
