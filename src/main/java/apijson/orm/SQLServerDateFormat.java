package apijson.orm;

/** Translate a literal MySQL date format without treating its text as format tokens. */
final class SQLServerDateFormat {
	private SQLServerDateFormat() {}

	static String translate(String format, boolean kingbase) {
		StringBuilder result = new StringBuilder();
		StringBuilder literal = new StringBuilder();
		for (int i = 0; i < format.length(); i++) {
			char c = format.charAt(i);
			if (c != '%') {
				literal.append(c);
				continue;
			}
			if (++i == format.length()) {
				throw new IllegalArgumentException("DATE_FORMAT cannot end with %");
			}
			char token = format.charAt(i);
			if (token == '%') {
				literal.append('%');
				continue;
			}
			appendLiteral(result, literal);
			switch (token) {
				case 'Y':
					result.append(kingbase ? "YYYY" : "yyyy");
					break;
				case 'y':
					result.append(kingbase ? "YY" : "yy");
					break;
				case 'm':
					result.append("MM");
					break;
				case 'd':
					result.append(kingbase ? "DD" : "dd");
					break;
				case 'H':
					result.append(kingbase ? "HH24" : "HH");
					break;
				case 'h':
				case 'I':
					result.append(kingbase ? "HH12" : "hh");
					break;
				case 'i':
					result.append(kingbase ? "MI" : "mm");
					break;
				case 's':
				case 'S':
					result.append(kingbase ? "SS" : "ss");
					break;
				case 'f':
					result.append(kingbase ? "US" : "ffffff");
					break;
				case 'p':
					result.append(kingbase ? "AM" : "tt");
					break;
				default:
					throw new UnsupportedOperationException(
							"SQL Server DATE_FORMAT does not support %" + token);
			}
		}
		appendLiteral(result, literal);
		// .NET interprets an empty format as its default date/time format.
		// A quoted empty literal preserves MySQL's empty-string result.
		return result.length() == 0 ? "\"\"" : result.toString();
	}

	private static void appendLiteral(StringBuilder result, StringBuilder literal) {
		if (literal.length() == 0) return;
		result.append('"');
		for (int i = 0; i < literal.length(); i++) {
			char c = literal.charAt(i);
			if (c == '\\' || c == '"') result.append('\\');
			result.append(c);
		}
		result.append('"');
		literal.setLength(0);
	}
}
