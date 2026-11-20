package apijson.orm;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small, validated numeric comparison grammar for projected HAVING aliases. */
final class KingbaseHavingExpression {
	private static final Pattern TOKEN =
			Pattern.compile(
					"[A-Za-z_][A-Za-z0-9_]*|(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|<=|>=|<>|!=|[()+*/%<>=-]");
	private final String input;
	private final Set<String> aliases;
	private final String quote;
	private int offset;
	private String token;
	private boolean usedAlias;

	private KingbaseHavingExpression(String input, Set<String> aliases, String quote) {
		this.input = input;
		this.aliases = aliases;
		this.quote = quote;
		next();
	}

	static String parse(String input, Set<String> aliases, String quote) {
		// Function arguments can share names with projected aliases, for example
		// id:id with count(id)>0. Let the existing function parser handle them.
		if (Pattern.compile("[A-Za-z_][A-Za-z0-9_]*\\s*\\(").matcher(input).find()) return null;
		if (input.length() > 100) {
			throw new UnsupportedOperationException(
					"Kingbase alias HAVING cannot exceed 100 characters");
		}
		// Do not take over ordinary function HAVING expressions or backend raw SQL.
		boolean referencesAlias = false;
		for (String alias : aliases) {
			if (Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(alias) + "(?![A-Za-z0-9_])")
					.matcher(input)
					.find()) referencesAlias = true;
		}
		if (!referencesAlias) return null;
		KingbaseHavingExpression parser = new KingbaseHavingExpression(input, aliases, quote);
		String left = parser.sum();
		String operator = parser.token;
		if (!("=".equals(operator)
				|| "!=".equals(operator)
				|| "<>".equals(operator)
				|| "<".equals(operator)
				|| ">".equals(operator)
				|| "<=".equals(operator)
				|| ">=".equals(operator))) {
			throw parser.invalid();
		}
		parser.next();
		String right = parser.sum();
		if (parser.token != null || !parser.usedAlias) throw parser.invalid();
		return left + " " + operator + " " + right;
	}

	private String sum() {
		String value = product();
		while ("+".equals(token) || "-".equals(token)) {
			String operator = token;
			next();
			value += " " + operator + " " + product();
		}
		return value;
	}

	private String product() {
		String value = primary();
		while ("*".equals(token) || "/".equals(token) || "%".equals(token)) {
			String operator = token;
			next();
			value += " " + operator + " " + primary();
		}
		return value;
	}

	private String primary() {
		if ("+".equals(token) || "-".equals(token)) {
			String sign = token;
			next();
			// Parentheses keep two minus signs from becoming a SQL comment.
			return "(" + sign + primary() + ")";
		}
		if ("(".equals(token)) {
			next();
			String value = sum();
			if (!")".equals(token)) throw invalid();
			next();
			return "(" + value + ")";
		}
		if (token == null) throw invalid();
		String value = token;
		if (aliases.contains(value)) {
			usedAlias = true;
			value = quote + value + quote;
		} else if (!value.matches("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?")) {
			throw invalid();
		}
		next();
		return value;
	}

	private void next() {
		while (offset < input.length() && Character.isWhitespace(input.charAt(offset))) offset++;
		if (offset == input.length()) {
			token = null;
			return;
		}
		Matcher matcher = TOKEN.matcher(input);
		matcher.region(offset, input.length());
		if (!matcher.lookingAt()) throw invalid();
		token = matcher.group();
		offset = matcher.end();
	}

	private IllegalArgumentException invalid() {
		return new IllegalArgumentException(
				"Kingbase alias HAVING requires a numeric comparison of projected aliases");
	}
}
