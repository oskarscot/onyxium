package dev.onyxium.command;

/// Incremental tokenization lets a greedy tail bypass quote parsing and retain its original text.
final class CommandInput {

	String raw;

	int position;

	Token pending;

	CommandInput(String raw) {
		this.raw = raw;
		skipWhitespace();
		if (position < raw.length() && raw.charAt(position) == '/')
			position++;
	}

	static String root(String input) {
		var cursor = new CommandInput(input);
		var start = cursor.position;
		while (cursor.position < input.length() && !Character.isWhitespace(input.charAt(cursor.position)))
			cursor.position++;
		return input.substring(start, cursor.position);
	}

	Token peek() {
		if (pending == null)
			pending = tokenize();
		return pending;
	}

	Token read() {
		var token = peek();
		pending = null;
		return token;
	}

	String remaining() {
		var start = pending == null ? position : pending.start();
		while (start < raw.length() && Character.isWhitespace(raw.charAt(start)))
			start++;
		position = raw.length();
		pending = null;
		return raw.substring(start);
	}

	void skipWhitespace() {
		while (position < raw.length() && Character.isWhitespace(raw.charAt(position)))
			position++;
	}

	Token tokenize() {
		skipWhitespace();
		if (position == raw.length())
			return null;
		var start = position;
		var value = new StringBuilder();
		char quote = 0;
		while (position < raw.length()) {
			var character = raw.charAt(position);
			if (quote == 0 && Character.isWhitespace(character))
				break;
			position++;
			if (character == '\\') {
				if (position == raw.length())
					throw new IllegalArgumentException("Incomplete escape.");
				value.append(raw.charAt(position++));
			}
			else if (quote != 0) {
				if (character == quote)
					quote = 0;
				else
					value.append(character);
			}
			else if (character == '"' || (character == '\'' && position - 1 == start)) {
				quote = character;
			}
			else
				value.append(character);
		}
		if (quote != 0)
			throw new IllegalArgumentException("Unclosed quote.");
		return new Token(value.toString(), start);
	}

	record Token(String value, int start) {
	}

}
