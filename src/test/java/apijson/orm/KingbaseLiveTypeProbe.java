package apijson.orm;

import apijson.RequestMethod;
import java.io.*;
import java.math.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;

/** Explicit read-only live probe; never part of the automatic unit test suite. */
public final class KingbaseLiveTypeProbe {
	private static final String[] MODES = {"MYSQL", "ORACLE", "SQLSERVER"};
	private static final AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> EXECUTOR =
			new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {};

	static SQLConfig<Long, Map<String, Object>, List<Object>> config(final String mode) {
		return new AbstractSQLConfig<Long, Map<String, Object>, List<Object>>(
				RequestMethod.GET, "types") {
			@Override
			public String gainSQLDatabase() {
				return "KINGBASE_" + mode;
			}

			@Override
			public String gainDBVersion() {
				return "V009R001C010";
			}

			@Override
			public String gainDBUri() {
				return "";
			}

			@Override
			public String gainDBAccount() {
				return "";
			}

			@Override
			public String gainDBPassword() {
				return "";
			}

			@Override
			public boolean isKingBaseMySQL() {
				return "MYSQL".equals(mode);
			}

			@Override
			public boolean isKingBaseOracle() {
				return "ORACLE".equals(mode);
			}

			@Override
			public boolean isKingBaseSQLServer() {
				return "SQLSERVER".equals(mode);
			}
		};
	}

	private static String clean(Object text) {
		return String.valueOf(text).replace('\t', ' ').replace('\r', ' ').replace('\n', ' ');
	}

	private static boolean safe(Object value) {
		if (value == null || value instanceof String || value instanceof Boolean) return true;
		if (value instanceof Number)
			return !(value instanceof BigDecimal || value instanceof BigInteger)
					&& Double.isFinite(((Number) value).doubleValue());
		if (value instanceof List) {
			for (Object item : (List<?>) value) if (!safe(item)) return false;
			return true;
		}
		if (value instanceof Map) {
			for (Object item : ((Map<?, ?>) value).values()) if (!safe(item)) return false;
			return true;
		}
		return false;
	}

	static String sample(String type, String javaType) {
		if ("datetime".equals(type)) return "'2026-01-01 12:34:56.123'";
		if ("smalldatetime".equals(type)) return "'2026-01-01 12:34:00'";
		if ("refcursor".equals(type)) return "'codex_type_cursor'";
		if ("json".equals(type) || "jsonb".equals(type)) return "'{\"enabled\":true}'";
		if ("point".equals(type)) return "'(1,2)'";
		if ("yminterval".equals(type)) return "'1-2'";
		if ("dsinterval".equals(type)) return "'1 02:03:04.123456'";
		if ("uuid".equals(type)) return "'123e4567-e89b-12d3-a456-426614174000'";
		if ("year".equals(type)) return "2026";
		if (javaType.endsWith("Boolean")) return "1";
		if (javaType.endsWith("Date")) return "'2026-01-01'";
		if (javaType.endsWith("Time")) return "'12:34:56'";
		if (javaType.endsWith("Timestamp")) return "'2026-01-01 12:34:56.123456'";
		if (javaType.endsWith("BigInteger")) return "18446744073709551615";
		if (javaType.endsWith("BigDecimal")) return "123.125";
		if (javaType.matches(".*(Integer|Short|Long|Float|Double)$")) return "123";
		return "'abc'";
	}

	public static void main(String[] args) throws Exception {
		Properties credentials = new Properties();
		String file = System.getProperty("kingbase.credentialsFile");
		if (file != null) {
			try (Reader reader = Files.newBufferedReader(Paths.get(file), StandardCharsets.UTF_8)) {
				credentials.load(reader);
			}
		}
		String user = credentials.getProperty("username", System.getenv("KINGBASE_USERNAME"));
		String password = credentials.getProperty("password", System.getenv("KINGBASE_PASSWORD"));
		if (user == null || password == null)
			throw new IllegalArgumentException(
					"Supply KINGBASE_USERNAME/KINGBASE_PASSWORD or -Dkingbase.credentialsFile (username/password properties)");
		// Only the controlled JSON sample is parsed by this probe; production uses the
		// application's parser.
		KingbaseCompatibilityTest.installJsonParser();
		Class.forName("com.kingbase8.Driver");
		Path report =
				Paths.get(System.getProperty("kingbase.report", "target/kingbase-live-types.tsv"));
		Files.createDirectories(report.getParent());
		List<String> specs;
		try (BufferedReader reader =
				new BufferedReader(
						new InputStreamReader(
								KingbaseLiveTypeProbe.class.getResourceAsStream(
										"/kingbase-v9r1c10-types.tsv"),
								StandardCharsets.UTF_8))) {
			specs = new ArrayList<>();
			reader.readLine();
			String line;
			while ((line = reader.readLine()) != null) specs.add(line);
		}
		String extraFile = System.getProperty("kingbase.extraTypes");
		if (extraFile != null) {
			List<String> extra = Files.readAllLines(Paths.get(extraFile), StandardCharsets.UTF_8);
			specs.addAll(extra.subList(1, extra.size()));
		}
		int failures = 0;
		try (BufferedWriter writer = Files.newBufferedWriter(report, StandardCharsets.UTF_8)) {
			writer.write(
					"mode\ttype\tcase\tstatus\tjdbcType\ttypeName\tjavaClass\tjsonClass\tvalueOrError\n");
			for (int modeIndex = 0; modeIndex < MODES.length; modeIndex++) {
				String mode = MODES[modeIndex];
				String envName = "KINGBASE_" + mode + "_URL";
				String url = System.getenv(envName);
				if (url == null || url.trim().isEmpty()) {
					throw new IllegalArgumentException("Missing environment variable: " + envName);
				}
				url = url.trim();
				try (Connection connection = DriverManager.getConnection(url, user, password)) {
					String detected = new KingbaseModeResolver().detect(connection);
					if (!("KINGBASE_" + mode).equals(detected))
						throw new SQLException("Unexpected mode: " + detected);
					writer.write(
							mode
									+ "\tSERVER\tversion\tINFO\t\t\t\t\t"
									+ clean(connection.getMetaData().getDatabaseProductVersion())
									+ "; driver="
									+ clean(connection.getMetaData().getDriverVersion())
									+ "\n");
					try (Statement versionStatement = connection.createStatement();
							ResultSet version = versionStatement.executeQuery("SELECT version()")) {
						if (version.next())
							writer.write(
									mode
											+ "\tSERVER\tfullVersion\tINFO\t\t\t\t\t"
											+ clean(version.getString(1))
											+ "\n");
					}
					try (ResultSet types = connection.getMetaData().getTypeInfo()) {
						while (types.next()) {
							String name = types.getString("TYPE_NAME");
							if (name.matches(
									"(?i).*(raw|bfile|image|ntext|offset|hierarchy|geometry|geography|smallmoney).*"))
								writer.write(
										mode
												+ "\tCATALOG\ttypeInfo\tINFO\t"
												+ types.getInt("DATA_TYPE")
												+ "\t"
												+ clean(name)
												+ "\t\t\t\n");
						}
					}
					connection.setReadOnly(true);
					connection.setAutoCommit(false);
					try {
						for (String spec : specs) {
							String[] fields = spec.split("\t");
							if (!mode.equals(fields[0])) continue;
							String type = fields[1];
							for (boolean isNull : new boolean[] {false, true}) {
								try (Statement statement = connection.createStatement()) {
									statement.setQueryTimeout(20);
									if ("refcursor".equals(type) && !isNull)
										statement.execute(
												"DECLARE codex_type_cursor CURSOR FOR SELECT 123 AS amount");
									String sql =
											"SELECT CAST("
													+ (isNull
															? "NULL"
															: fields.length > 4
																	? fields[4]
																	: sample(type, fields[3]))
													+ " AS "
													+ type
													+ ") AS value";
									if ("bfile".equals(type) && !isNull)
										sql =
												"SELECT bfilename(CAST(directory_name AS cstring), CAST('codex_type_probe_missing_file.bin' AS cstring)) AS value FROM ALL_DIRECTORIES WHERE ROWNUM=1";
									try (ResultSet rs = statement.executeQuery(sql)) {
										if (!rs.next()) throw new SQLException("No result");
										ResultSetMetaData md = rs.getMetaData();
										Object raw = rs.getObject(1);
										Object mapped =
												EXECUTOR.mapResultValue(
														config(mode),
														raw,
														md.getColumnType(1),
														md.getColumnTypeName(1),
														"value");
										if (isNull
												? mapped != null
												: mapped == null || !safe(mapped))
											throw new SQLException("Unexpected JSON output class");
										writer.write(
												mode
														+ "\t"
														+ type
														+ "\t"
														+ (isNull ? "NULL" : "VALUE")
														+ "\tPASS\t"
														+ md.getColumnType(1)
														+ "\t"
														+ clean(md.getColumnTypeName(1))
														+ "\t"
														+ (raw == null
																? "null"
																: raw.getClass().getName())
														+ "\t"
														+ (mapped == null
																? "null"
																: mapped.getClass().getName())
														+ "\t"
														+ clean(mapped)
														+ "\n");
									}
								} catch (Exception e) {
									failures++;
									writer.write(
											mode
													+ "\t"
													+ type
													+ "\t"
													+ (isNull ? "NULL" : "VALUE")
													+ "\tFAIL\t\t\t\t\t"
													+ clean(
															e.getClass().getName()
																	+ ": "
																	+ e.getMessage())
													+ "\n");
								} finally {
									connection.rollback();
									writer.flush();
								}
							}
						}
					} finally {
						connection.rollback();
					}
				} catch (Exception e) {
					failures++;
					writer.write(
							mode
									+ "\tCONNECTION\tsetup\tFAIL\t\t\t\t\t"
									+ clean(e.getClass().getName() + ": " + e.getMessage())
									+ "\n");
				}
			}
		}
		System.out.println("Report: " + report.toAbsolutePath() + "; failures=" + failures);
		if (failures != 0)
			throw new AssertionError(
					"Live probe failures require investigation; none are automatically exempted");
	}
}
