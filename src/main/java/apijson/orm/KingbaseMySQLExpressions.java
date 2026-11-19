package apijson.orm;

import java.util.*;
import java.util.regex.*;

/** Lexical helpers for already validated MySQL-mode expressions, not a SQL parser. */
final class KingbaseMySQLExpressions {

	private KingbaseMySQLExpressions() {}

	static boolean hasAggregateOrWindow(String expression, Set<String> aggregates) {
		String code = withoutQuotedText(expression);
		Matcher functions = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(").matcher(code);
		while (functions.find()) {
			String name = functions.group(1).toLowerCase(Locale.ROOT);
			if (aggregates.contains(name) || "over".equals(name)
					|| name.matches("(?:group_concat|string_agg|array_agg|json_agg|jsonb_agg|json_arrayagg|json_objectagg|json_object_agg|jsonb_object_agg|xmlagg|bool_and|bool_or|every|bit_and|bit_or|bit_xor|stddev(?:_pop|_samp)?|variance|var_pop|var_samp|percentile_cont|percentile_disc)")) return true;
		}
		return false;
	}

	static boolean hasVolatileFunction(String expression) {
		return Pattern.compile("(?i)\\b(?:rand|random|uuid|uuid_short|sys_guid|sysdate)\\s*\\(")
				.matcher(withoutQuotedText(expression)).find();
	}

	static boolean references(String expression, String identifier) {
		return Pattern.compile("(?<![A-Za-z0-9_])" + Pattern.quote(identifier) + "(?![A-Za-z0-9_])")
				.matcher(withoutQuotedText(expression)).find();
	}

	/** Colon inside a quoted literal or function argument is not a projection alias. */
	static int aliasIndex(String expression) {
		String code = withoutQuotedText(expression);
		int depth = 0;
		for (int i = 0; i < code.length(); i++) {
			char c = code.charAt(i);
			if (c == '(') depth++;
			else if (c == ')') depth--;
			else if (c == ':' && depth == 0) return i;
		}
		return -1;
	}

	static int projectionSize(String expression) {
		String code = withoutQuotedText(expression);
		int count = 1, depth = 0;
		for (int i = 0; i < code.length(); i++) {
			if (code.charAt(i) == '(') depth++;
			else if (code.charAt(i) == ')') depth--;
			else if (code.charAt(i) == ',' && depth == 0) count++;
		}
		return count;
	}

	/** Translate word boundaries, preserving escaped backslashes and character classes. */
	static String regex(String pattern) {
		if (pattern == null) return null;
		StringBuilder translated = new StringBuilder();
		boolean inClass = false;
		for (int i = 0; i < pattern.length(); i++) {
			char c = pattern.charAt(i);
			if (c == '\\' && i + 1 < pattern.length()) {
				char next = pattern.charAt(++i);
				translated.append('\\').append(!inClass && next == 'b' ? 'y' : !inClass && next == 'B' ? 'Y' : next);
			} else {
				if (c == '[') inClass = true;
				else if (c == ']') inClass = false;
				translated.append(c);
			}
		}
		return translated.toString();
	}

	static List<String> projectionLabels(String projection, String quote) {
		String code = withoutQuotedText(projection);
		List<String> labels = new ArrayList<>();
		int depth = 0, start = 0;
		for (int i = 0; i <= code.length(); i++) {
			if (i < code.length()) {
				if (code.charAt(i) == '(') depth++;
				else if (code.charAt(i) == ')') depth--;
			}
			if (i == code.length() || code.charAt(i) == ',' && depth == 0) {
				String item = projection.substring(start, i).trim();
				String q = Pattern.quote(quote);
				Matcher m = Pattern.compile("(?is)(?:.*\\sAS\\s+|(?:" + q + "(?:[^" + quote + "]|" + q + q + ")+" + q + "\\.)?)"
						+ q + "((?:[^" + quote + "]|" + q + q + ")+)" + q + "$").matcher(item);
				if (!m.matches()) return null;
				labels.add(m.group(1).replace(quote + quote, quote));
				start = i + 1;
			}
		}
		return labels;
	}

	private static String withoutQuotedText(String input) {
		StringBuilder code = new StringBuilder(input == null ? "" : input);
		char quote = 0;
		for (int i = 0; i < code.length(); i++) {
			char c = code.charAt(i);
			if (quote == 0) {
				if (c == '\'' || c == '"' || c == '`') { quote = c; code.setCharAt(i, ' '); }
			} else {
				code.setCharAt(i, ' ');
				if (c == '\\' && i + 1 < code.length()) code.setCharAt(++i, ' ');
				else if (c == quote) {
					if (i + 1 < code.length() && code.charAt(i + 1) == quote) code.setCharAt(++i, ' ');
					else quote = 0;
				}
			}
		}
		return code.toString();
	}
}
