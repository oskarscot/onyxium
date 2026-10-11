package dev.onyxium.command;

/// Reads input one argument at a time, leaving greedy text untouched until needed.
final class CommandInput {

	String raw;

	int position;

	Token pending;

	CommandInput(String raw) {
		this.raw = raw;
		skipWhitespace();

		if (position < raw.length() && raw.charAt(position) == '/') {
			position++;
		}
	}

	/// Reads the next word without consuming it or interpreting quotes and escapes.
	/// This leaves unknown commands and greedy arguments intact.
	String literal() {
		var start = position;
		while (start < raw.length() && Character.isWhitespace(raw.charAt(start))) {
			start++;
		}

		var end = start;
		while (end < raw.length() && !Character.isWhitespace(raw.charAt(end))) {
			end++;
		}

		return raw.substring(start, end);
	}

	Token peek() {
		if (pending == null) {
			pending = tokenize();
		}

		return pending;
	}

	Token read() {
		var token = peek();
		pending = null;

		return token;
	}

	String remaining() {
		var start = pending == null ? position : pending.start();

		while (start < raw.length() && Character.isWhitespace(raw.charAt(start))) {
			start++;
		}

		position = raw.length();
		pending = null;

		return raw.substring(start);
	}

	void skipWhitespace() {
		while (position < raw.length() && Character.isWhitespace(raw.charAt(position))) {
			position++;
		}
	}

	Token tokenize() {
		skipWhitespace();

		if (position == raw.length()) {
			return null;
		}

		var start = position;
		var value = new StringBuilder();
		char quote = 0;

		while (position < raw.length()) {
			var character = raw.charAt(position);
			if (quote == 0 && Character.isWhitespace(character)) {
				break;
			}

			position++;
			if (character == '\\') {
				if (position == raw.length()) {
					throw new IllegalArgumentException("Incomplete escape.");
				}

				value.append(raw.charAt(position++));
			}
			else if (quote != 0) {
				if (character == quote) {
					quote = 0;
				}
				else {
					value.append(character);
				}
			}
			else if (character == '"' || (character == '\'' && position - 1 == start)) {
				quote = character;
			}
			else {
				value.append(character);
			}
		}

		if (quote != 0) {
			throw new IllegalArgumentException("Unclosed quote.");
		}

		return new Token(value.toString(), start);
	}

	record Token(String value, int start) {
	}

}
