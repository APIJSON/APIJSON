package apijson.orm;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.IdentityHashMap;
import java.util.Map;

/** Execution-scoped capabilities, read lazily from the actual business connection. */
final class KingbaseMySQLCapabilities {

	private KingbaseMySQLCapabilities() {}

	interface Connections {
		Connection get(SQLConfig<?, ?, ?> config) throws Exception;
	}

	interface Scope extends AutoCloseable {
		@Override void close();
	}

	private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();

	static Scope enter(Object executor, Connections connections) {
		Context previous = CURRENT.get();
		if (previous != null && previous.executor == executor) return () -> {};
		CURRENT.set(new Context(executor, connections));
		return () -> {
			if (previous == null) CURRENT.remove();
			else CURRENT.set(previous);
		};
	}

	/** Null means standalone SQL generation, with no execution connection available. */
	static Boolean intervalStyleOn(SQLConfig<?, ?, ?> config) {
		Context context = CURRENT.get();
		if (context == null) return null;
		try {
			return context.settings(config).intervalStyleOn();
		} catch (Exception e) {
			throw new IllegalStateException("Cannot read KINGBASE_MYSQL execution-session mysql_interval_style", e);
		}
	}

	private static final class Context {
		final Object executor;
		final Connections connections;
		final Map<Connection, Settings> settings = new IdentityHashMap<>();

		Context(Object executor, Connections connections) {
			this.executor = executor;
			this.connections = connections;
		}

		Settings settings(SQLConfig<?, ?, ?> config) throws Exception {
			Connection connection = connections.get(config);
			Settings value = settings.get(connection);
			if (value == null) {
				value = new Settings(connection);
				settings.put(connection, value);
			}
			return value;
		}
	}

	private static final class Settings {
		final Connection connection;
		Boolean intervalStyleOn;

		Settings(Connection connection) {
			this.connection = connection;
		}

		boolean intervalStyleOn() throws SQLException {
			if (intervalStyleOn != null) return intervalStyleOn;
			try (Statement statement = connection.createStatement()) {
				statement.setQueryTimeout(5);
				try (ResultSet result = statement.executeQuery("SHOW mysql_interval_style")) {
					if (!result.next()) throw new SQLException("mysql_interval_style returned no row");
					String value = result.getString(1);
					if ("on".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value) || "1".equals(value)) {
						return intervalStyleOn = true;
					}
					if ("off".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value) || "0".equals(value)) {
						return intervalStyleOn = false;
					}
					throw new SQLException("Unexpected mysql_interval_style: " + value);
				}
			}
		}

	}
}
