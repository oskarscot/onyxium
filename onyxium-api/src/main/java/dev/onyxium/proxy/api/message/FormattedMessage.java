package dev.onyxium.proxy.api.message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/// Builder for the FormattedMessage, use it to display any sort of text to the player. You should
/// this record directly, use the builder instead.
///
///
/// # Example
/// ```java
/// var message = FormattedMessage.builder()
///         .text("Welcome ").color("#aaaaaa")
///         .append(child -> child.text("Oskar").bold().color("#55ff55"))
///         .build();
/// ```
public record FormattedMessage(String rawText, String messageId, List<FormattedMessage> children,
		Map<String, MessageParam> params, Map<String, FormattedMessage> messageParams, String color, Boolean bold,
		Boolean italic, Boolean monospace, Boolean underlined, Boolean strikethrough, String link,
		boolean markupEnabled, Image image) {

	public FormattedMessage {
		children = children == null ? null : List.copyOf(children);
		params = immutable(params);
		messageParams = immutable(messageParams);
	}

	private static <T> Map<String, T> immutable(Map<String, T> source) {
		if (source == null)
			return null;
		source.forEach((key, value) -> {
			Objects.requireNonNull(key, "parameter name");
			Objects.requireNonNull(value, "parameter value");
		});
		return Collections.unmodifiableMap(new LinkedHashMap<>(source));
	}

	public static Builder builder() {
		return new Builder();
	}

	public static FormattedMessage text(String text) {
		return builder().text(text).build();
	}

	public static FormattedMessage translation(String key) {
		return builder().translation(key).build();
	}

	public Builder toBuilder() {
		var builder = new Builder();
		builder.rawText = rawText;
		builder.messageId = messageId;
		builder.children = children == null ? null : new ArrayList<>(children);
		builder.params = params == null ? null : new LinkedHashMap<>(params);
		builder.messageParams = messageParams == null ? null : new LinkedHashMap<>(messageParams);
		builder.color = color;
		builder.bold = bold;
		builder.italic = italic;
		builder.monospace = monospace;
		builder.underlined = underlined;
		builder.strikethrough = strikethrough;
		builder.link = link;
		builder.markupEnabled = markupEnabled;
		builder.image = image;
		return builder;
	}

	public record Image(String filePath, int width, int height) {
		public Image {
			Objects.requireNonNull(filePath, "filePath");
		}
	}

	public static final class Builder {

		private String rawText;

		private String messageId;

		private List<FormattedMessage> children;

		private Map<String, MessageParam> params;

		private Map<String, FormattedMessage> messageParams;

		private String color;

		private Boolean bold;

		private Boolean italic;

		private Boolean monospace;

		private Boolean underlined;

		private Boolean strikethrough;

		private String link;

		private boolean markupEnabled;

		private Image image;

		private Builder() {
		}

		public Builder text(String text) {
			rawText = Objects.requireNonNull(text);
			messageId = null;
			return this;
		}

		public Builder translation(String key) {
			messageId = Objects.requireNonNull(key);
			rawText = null;
			return this;
		}

		public Builder append(String text) {
			return append(FormattedMessage.text(text));
		}

		public Builder append(FormattedMessage child) {
			if (children == null)
				children = new ArrayList<>();
			children.add(Objects.requireNonNull(child));
			return this;
		}

		public Builder append(Consumer<Builder> child) {
			var builder = builder();
			child.accept(builder);
			return append(builder.build());
		}

		public Builder param(String name, MessageParam value) {
			Objects.requireNonNull(name);
			Objects.requireNonNull(value);
			if (params == null)
				params = new LinkedHashMap<>();
			params.put(name, value);
			if (messageParams != null)
				messageParams.remove(name);
			return this;
		}

		public Builder param(String name, String value) {
			return param(name, new MessageParam.Text(value));
		}

		public Builder param(String name, boolean value) {
			return param(name, new MessageParam.Bool(value));
		}

		public Builder param(String name, double value) {
			return param(name, new MessageParam.Decimal(value));
		}

		public Builder param(String name, int value) {
			return param(name, new MessageParam.Int(value));
		}

		public Builder param(String name, long value) {
			return param(name, new MessageParam.Long(value));
		}

		public Builder param(String name, FormattedMessage value) {
			Objects.requireNonNull(name);
			Objects.requireNonNull(value);
			if (messageParams == null)
				messageParams = new LinkedHashMap<>();
			messageParams.put(name, value);
			if (params != null)
				params.remove(name);
			return this;
		}

		public Builder color(String value) {
			color = value;
			return this;
		}

		public Builder bold() {
			return bold(true);
		}

		public Builder bold(Boolean value) {
			bold = value;
			return this;
		}

		public Builder italic() {
			return italic(true);
		}

		public Builder italic(Boolean value) {
			italic = value;
			return this;
		}

		public Builder monospace() {
			return monospace(true);
		}

		public Builder monospace(Boolean value) {
			monospace = value;
			return this;
		}

		public Builder underlined() {
			return underlined(true);
		}

		public Builder underlined(Boolean value) {
			underlined = value;
			return this;
		}

		public Builder strikethrough() {
			return strikethrough(true);
		}

		public Builder strikethrough(Boolean value) {
			strikethrough = value;
			return this;
		}

		public Builder link(String value) {
			link = value;
			return this;
		}

		public Builder markupEnabled(boolean value) {
			markupEnabled = value;
			return this;
		}

		public Builder image(String path, int width, int height) {
			image = new Image(path, width, height);
			return this;
		}

		public Builder image(Image value) {
			image = value;
			return this;
		}

		public FormattedMessage build() {
			return new FormattedMessage(rawText, messageId, children, params, messageParams, color, bold, italic,
					monospace, underlined, strikethrough, link, markupEnabled, image);
		}

	}
}
