package apijson.orm;

/** SQL expressions specific to the verified KingbaseES MySQL compatibility mode. */
final class KingbaseMySQLFunctions {

	private KingbaseMySQLFunctions() {}

	/** Count bound parameters, excluding quoted SQL text and identifiers. */
	static int parameterCount(String sql) {
		int count = 0;
		char quote = 0;
		for (int i = 0; i < sql.length(); i++) {
			char ch = sql.charAt(i);
			if (quote != 0) {
				if (ch == '\\') i++;
				else if (ch == quote) {
					if (i + 1 < sql.length() && sql.charAt(i + 1) == quote) i++;
					else quote = 0;
				}
			}
			else if (ch == '\'' || ch == '"' || ch == '`') quote = ch;
			else if (ch == '?') count++;
		}
		return count;
	}
}
