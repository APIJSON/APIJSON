package apijson.orm;

import apijson.StringUtil;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Request-side type declarations. Never accepts arbitrary SQL expressions. */
final class KingbaseTypeSupport {
	private KingbaseTypeSupport() {}

	private static final Pattern BFILE_LOCATOR =
			Pattern.compile("(?i)bfilename\\('((?:''|[^'])*)',\\s*'((?:''|[^'])*)'\\)");

	/** Parse a locator as data; never splice its SQL-looking text into SQL. */
	static String[] bfileInput(String type, Object value) {
		if (type == null || !"BFILE".equalsIgnoreCase(type.trim()) || value == null) return null;
		if (!(value instanceof String))
			throw new IllegalArgumentException("BFILE @cast requires a bfilename locator string");
		Matcher match = BFILE_LOCATOR.matcher((String) value);
		if (!match.matches())
			throw new IllegalArgumentException(
					"Invalid BFILE locator; expected bfilename('directory', 'file')");
		return new String[] {match.group(1).replace("''", "'"), match.group(2).replace("''", "'")};
	}

	static String[] splitCasts(String casts) {
		if (casts == null || casts.trim().isEmpty()) return null;
		List<String> parts = new ArrayList<>();
		int depth = 0, start = 0;
		for (int i = 0; i < casts.length(); i++) {
			char c = casts.charAt(i);
			if ((c == '(' && ++depth > 1) || (c == ')' && --depth < 0))
				throw new IllegalArgumentException("Invalid @cast parentheses");
			if (c == ',' && depth == 0) {
				parts.add(casts.substring(start, i).trim());
				start = i + 1;
			}
		}
		if (depth != 0) throw new IllegalArgumentException("Invalid @cast parentheses");
		parts.add(casts.substring(start).trim());
		return parts.toArray(new String[0]);
	}

	static boolean validCast(String type) {
		if (type == null) return false;
		// Preserve existing single-name custom types, but only extend known SQL grammar.
		if (StringUtil.isName(type)) return true;
		String t = type.trim().toUpperCase(Locale.ROOT);
		return t.matches(
						"(?:DECIMAL|NUMERIC|NUMBER)\\s*\\(\\s*[1-9][0-9]*\\s*(?:,\\s*-?[0-9]+\\s*)?\\)")
				|| t.matches(
						"(?:CHAR|VARCHAR|VARCHAR2|NCHAR|NVARCHAR|NVARCHAR2|BINARY|VARBINARY|RAW|BIT|FLOAT)\\s*\\(\\s*[1-9][0-9]*\\s*\\)")
				|| t.matches("(?:DATETIME|DATETIME2)\\s*\\(\\s*[0-9]+\\s*\\)")
				|| t.matches(
						"(?:TIME|TIMESTAMP)(?:\\s*\\(\\s*[0-9]+\\s*\\))?(?: WITH(?: LOCAL)? TIME ZONE| WITHOUT TIME ZONE)?")
				|| t.matches("INTERVAL YEAR(?:\\s*\\([0-9]+\\))? TO MONTH")
				|| t.matches("INTERVAL DAY(?:\\s*\\([0-9]+\\))? TO SECOND(?:\\s*\\([0-9]+\\))?")
				|| "LONG RAW".equals(t)
				|| "DOUBLE PRECISION".equals(t)
				|| t.matches("(?:TINYINT|SMALLINT|MEDIUMINT|INT|INTEGER|BIGINT) UNSIGNED");
	}

	/** BIT(n) is exposed as an unsigned decimal JSON value in MySQL mode. */
	static Object mysqlBitInput(String type, Object value) {
		if (type != null
				&& type.trim()
						.toUpperCase(Locale.ROOT)
						.matches("BIT(?:\\s*\\(\\s*[1-9][0-9]*\\s*\\))?")
				&& value instanceof String
				&& ((String) value).matches("[0-9]+")) {
			return new java.math.BigInteger((String) value);
		}
		return value;
	}

	/** Explicit prefix keeps existing binary text literals and JSON strings unchanged. */
	static Object castInput(String type, Object value) {
		if (type == null || !(value instanceof String) || !((String) value).startsWith("base64:"))
			return value;
		String t = type.trim().toUpperCase(Locale.ROOT);
		if (!t.matches(
				"(?:BINARY|VARBINARY|RAW)(?:\\s*\\(\\s*[1-9][0-9]*\\s*\\))?|BYTEA|BLOB|TINYBLOB|MEDIUMBLOB|LONGBLOB|LONG RAW"))
			return value;
		try {
			return Base64.getDecoder().decode(((String) value).substring(7));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Invalid Base64 binary @cast input", e);
		}
	}

	/** KES rowversion getBytes returns ASCII 0x + 16 hex digits. Predicate-only input. */
	static String rowversionPredicateInput(Object value) {
		if (!(value instanceof String))
			throw new IllegalArgumentException(
					"ROWVERSION predicate requires hexadecimal text or base64: output");
		String text = (String) value;
		if (text.startsWith("base64:")) {
			try {
				text =
						new String(
								Base64.getDecoder().decode(text.substring(7)),
								java.nio.charset.StandardCharsets.US_ASCII);
			} catch (IllegalArgumentException e) {
				throw new IllegalArgumentException("Invalid Base64 ROWVERSION predicate", e);
			}
		}
		if (!text.matches("(?i)0x[0-9a-f]{16}"))
			throw new IllegalArgumentException(
					"ROWVERSION predicate requires 0x followed by 16 hexadecimal digits");
		return text.toLowerCase(Locale.ROOT);
	}
}
