package apijson.orm;

import java.util.ArrayList;
import java.util.List;

/** Single-location MySQL JSON paths; keys never implicitly unwrap arrays. */
final class KingbaseMySQLJsonPath {
	private KingbaseMySQLJsonPath() {}

	static List<String> steps(String path) {
		List<String> steps = new ArrayList<>();
		if (path == null || path.trim().isEmpty()) return steps;
		path = path.trim();
		if (!path.startsWith("$")) throw invalid();
		int i = 1;
		while (i < path.length()) {
			int start = i;
			char ch = path.charAt(i++);
			if (ch == '.') {
				if (i < path.length() && path.charAt(i) == '"') {
					i++;
					boolean closed = false;
					while (i < path.length()) {
						char c = path.charAt(i++);
						if (c == '\\') { if (i >= path.length()) throw invalid(); i++; }
						else if (c == '"') { closed = true; break; }
					}
					if (!closed) throw invalid();
				}
				else {
					int keyStart = i;
					while (i < path.length() && (Character.isUnicodeIdentifierPart(path.charAt(i))
							|| path.charAt(i) == '$')) i++;
					if (keyStart == i || !(Character.isUnicodeIdentifierStart(path.charAt(keyStart))
							|| path.charAt(keyStart) == '$' || path.charAt(keyStart) == '_')) throw invalid();
				}
				steps.add("strict $" + path.substring(start, i));
			}
			else if (ch == '[') {
				int end = path.indexOf(']', i);
				if (end < 0) throw invalid();
				String index = path.substring(i, end).trim();
				if (!index.matches("(?:[0-9]+|last(?:\\s*-\\s*[0-9]+)?)")) throw invalid();
				// JSONPath rejects leading zeroes; MySQL accepts them as decimal indices.
				java.util.regex.Matcher digits = java.util.regex.Pattern.compile("[0-9]+").matcher(index);
				StringBuffer normalized = new StringBuffer();
				while (digits.find()) digits.appendReplacement(normalized, digits.group().replaceFirst("^0+(?!$)", ""));
				digits.appendTail(normalized);
				index = normalized.toString();
				i = end + 1;
				// MySQL treats a non-array as a singleton for array subscripts.
				steps.add("lax $[" + index + "]");
			}
			else throw invalid();
		}
		return steps;
	}

	private static IllegalArgumentException invalid() {
		return new IllegalArgumentException("JSON_CONTAINS requires a single JSON path without wildcards or ranges");
	}
}
