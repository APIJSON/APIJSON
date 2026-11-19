package apijson.orm;

import apijson.RequestMethod;
import java.lang.reflect.Proxy;
import java.math.BigInteger;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class KingbaseTypeBoundaryTest {
	private final AbstractSQLExecutor<Long, Map<String, Object>, List<Object>> executor =
			new AbstractSQLExecutor<Long, Map<String, Object>, List<Object>>() {};

	@Test
	public void castGrammarKeepsPrecisionCommasAndRejectsSqlExpressions() {
		assertArrayEquals(
				new String[] {"amount:DECIMAL(24,9)", "created:TIMESTAMP(6) WITH TIME ZONE"},
				KingbaseTypeSupport.splitCasts(
						"amount:DECIMAL(24,9),created:TIMESTAMP(6) WITH TIME ZONE"));
		for (String t :
				Arrays.asList(
						"DECIMAL(24,9)",
						"NUMBER(12,-2)",
						"LONG RAW",
						"TIMESTAMP(6) WITH TIME ZONE",
						"TIMESTAMP WITH LOCAL TIME ZONE",
						"INTERVAL DAY(3) TO SECOND(6)",
						"INTERVAL YEAR TO MONTH",
						"RAW(16)",
						"custom_type",
						"x.y")) assertTrue(t, KingbaseTypeSupport.validCast(t));
		for (String t :
				Arrays.asList(
						"INT); DROP TABLE users;--",
						"INT/*x*/",
						"INT UNION SELECT 1",
						"DECIMAL(1,2,3)",
						"VARCHAR(1+2)",
						"TIMESTAMP WITH TIME ZONE;",
						"INT[]",
						"ENUM('a','b')")) assertFalse(t, KingbaseTypeSupport.validCast(t));
		for (String t : Arrays.asList("x:INT(", "x:INT)", "x:INT((1))")) {
			try {
				KingbaseTypeSupport.splitCasts(t);
				fail(t);
			} catch (IllegalArgumentException expected) {
			}
		}
	}

	@Test
	public void explicitBinaryInputPreservesOrdinaryTextAndRejectsInvalidBase64() {
		assertArrayEquals(
				new byte[] {0, 1, -1},
				(byte[]) KingbaseTypeSupport.castInput("VARBINARY(16)", "base64:AAH/"));
		assertArrayEquals(new byte[0], (byte[]) KingbaseTypeSupport.castInput("BLOB", "base64:"));
		assertEquals("AAH/", KingbaseTypeSupport.castInput("BLOB", "AAH/"));
		assertEquals("base64:AAH/", KingbaseTypeSupport.castInput("VARCHAR", "base64:AAH/"));
		try {
			KingbaseTypeSupport.castInput("BYTEA", "base64:!invalid!");
			fail();
		} catch (IllegalArgumentException expected) {
		}
	}

	@Test
	public void timestampsKeepInstantAndNanosecondsIndependentlyOfJvmTimezone() throws Exception {
		Timestamp ts = Timestamp.from(Instant.parse("2026-01-02T04:34:56.123456789Z"));
		TimeZone original = TimeZone.getDefault();
		try {
			for (String zone : Arrays.asList("UTC", "Asia/Shanghai", "America/New_York")) {
				TimeZone.setDefault(TimeZone.getTimeZone(zone));
				for (String mode : Arrays.asList("MYSQL", "ORACLE", "SQLSERVER")) {
					assertEquals(
							"2026-01-02T04:34:56.123456789Z",
							executor.mapResultValue(
									KingbaseLiveTypeProbe.config(mode),
									ts,
									Types.TIMESTAMP,
									"timestamptz",
									"v"));
					assertEquals(
							ts.toString(),
							executor.mapResultValue(
									KingbaseLiveTypeProbe.config(mode),
									ts,
									Types.TIMESTAMP,
									"timestamp",
									"v"));
				}
			}
		} finally {
			TimeZone.setDefault(original);
		}
	}

	@Test
	public void timeColumnReadsRetainFractionsAndOffset() throws Exception {
		for (String mode : Arrays.asList("MYSQL", "ORACLE", "SQLSERVER")) {
			for (String text :
					Arrays.asList("12:34:56.123456", "12:34:56.123456+08", "-30:34:56.123456")) {
				ResultSet rs =
						(ResultSet)
								Proxy.newProxyInstance(
										getClass().getClassLoader(),
										new Class[] {ResultSet.class},
										(p, m, a) -> {
											if (m.getName().equals("getObject"))
												return Time.valueOf("12:34:56");
											if (m.getName().equals("getString")) return text;
											throw new AssertionError(m.getName());
										});
				ResultSetMetaData md =
						(ResultSetMetaData)
								Proxy.newProxyInstance(
										getClass().getClassLoader(),
										new Class[] {ResultSetMetaData.class},
										(p, m, a) -> {
											if (m.getName().equals("getColumnType"))
												return Types.TIME;
											if (m.getName().equals("getColumnTypeName"))
												return "time";
											throw new AssertionError(m.getName());
										});
				assertEquals(
						text,
						executor.getValue(
								KingbaseLiveTypeProbe.config(mode),
								rs,
								md,
								0,
								null,
								1,
								"v",
								null,
								null));
			}
		}
	}

	@Test
	public void mysqlBitNumbersAreNotCollapsedToBoolean() throws Exception {
		assertEquals(
				"255",
				executor.mapResultValue(
						KingbaseLiveTypeProbe.config("MYSQL"),
						new byte[] {-1},
						Types.BIT,
						"bit",
						"v"));
		assertEquals(
				"18446744073709551615",
				executor.mapResultValue(
						KingbaseLiveTypeProbe.config("MYSQL"),
						new byte[] {-1, -1, -1, -1, -1, -1, -1, -1},
						Types.BIT,
						"bit",
						"v"));
		assertEquals(new BigInteger("255"), KingbaseTypeSupport.mysqlBitInput("BIT(8)", "255"));

		for (Number n : Arrays.asList(2, 255L, new BigInteger("18446744073709551615")))
			assertEquals(
					executor.getNumVal(n),
					executor.mapResultValue(
							KingbaseLiveTypeProbe.config("MYSQL"), n, Types.BIT, "bit", "v"));
		assertEquals(
				true,
				executor.mapResultValue(
						KingbaseLiveTypeProbe.config("MYSQL"), true, Types.BIT, "bit", "v"));
	}

	@Test
	public void bfileLocatorIsParsedAsParametersRatherThanExecuted() {
		assertArrayEquals(
				new String[] {"DIR", "a'b.bin"},
				KingbaseTypeSupport.bfileInput("BFILE", "bfilename('DIR', 'a''b.bin')"));
		assertNull(KingbaseTypeSupport.bfileInput("VARCHAR", "bfilename('DIR', 'f')"));
		for (String value :
				Arrays.asList(
						"bfilename('DIR', 'f'); DROP TABLE users",
						"bfilename(current_user, 'f')",
						"not-a-locator")) {
			try {
				KingbaseTypeSupport.bfileInput("BFILE", value);
				fail(value);
			} catch (IllegalArgumentException expected) {
			}
		}
	}
}
