package dev.onyxium.command;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

interface BuiltinParsers {

	Map<Class<?>, Function<String, ?>> CONVERSIONS = Map.ofEntries(Map.entry(String.class, Function.identity()),
			Map.entry(Byte.class, Byte::valueOf), Map.entry(Short.class, Short::valueOf),
			Map.entry(Integer.class, Integer::valueOf), Map.entry(Long.class, Long::valueOf),
			Map.entry(Float.class, BuiltinParsers::parseFloat), Map.entry(Double.class, BuiltinParsers::parseDouble),
			Map.entry(Boolean.class, BuiltinParsers::parseBoolean),
			Map.entry(Character.class, BuiltinParsers::parseCharacter), Map.entry(UUID.class, UUID::fromString));

	static Class<?> boxed(Class<?> type) {
		return switch (type.getName()) {
			case "byte" -> Byte.class;
			case "short" -> Short.class;
			case "int" -> Integer.class;
			case "long" -> Long.class;
			case "float" -> Float.class;
			case "double" -> Double.class;
			case "boolean" -> Boolean.class;
			case "char" -> Character.class;
			default -> type;
		};
	}

	static <S> ArgumentParser<S, ?> find(Class<?> type) {
		if (type.isEnum()) {
			var constants = Stream.of(type.getEnumConstants())
				.collect(Collectors.toUnmodifiableMap(BuiltinParsers::enumKey, Function.identity()));
			return new Scalar<>(input -> enumValue(constants, input),
					Stream.of(type.getEnumConstants()).map(BuiltinParsers::enumName).toList());
		}
		var conversion = CONVERSIONS.get(type);
		if (conversion == null)
			return null;
		return new Scalar<>(conversion, type == Boolean.class ? List.of("true", "false") : List.of());
	}

	static String enumName(Object value) {
		return ((Enum<?>) value).name();
	}

	static String enumKey(Object value) {
		return enumName(value).toLowerCase(Locale.ROOT);
	}

	static Object enumValue(Map<String, ?> constants, String input) {
		var value = constants.get(input.toLowerCase(Locale.ROOT));
		if (value == null)
			throw new IllegalArgumentException("Unknown enum value.");
		return value;
	}

	static boolean parseBoolean(String input) {
		if (input.equalsIgnoreCase("true"))
			return true;
		if (input.equalsIgnoreCase("false"))
			return false;
		throw new IllegalArgumentException("Expected true or false.");
	}

	static char parseCharacter(String input) {
		if (input.length() != 1)
			throw new IllegalArgumentException("Expected one character.");
		return input.charAt(0);
	}

	static float parseFloat(String input) {
		var value = Float.parseFloat(input);
		if (!Float.isFinite(value))
			throw new IllegalArgumentException("Expected a finite number.");
		return value;
	}

	static double parseDouble(String input) {
		var value = Double.parseDouble(input);
		if (!Double.isFinite(value))
			throw new IllegalArgumentException("Expected a finite number.");
		return value;
	}

	record Scalar<S>(Function<String, ?> conversion, List<String> choices) implements ArgumentParser<S, Object> {

		@Override
		public Object parse(S source, String input) {
			return conversion.apply(input);
		}

		@Override
		public List<String> suggest(S source, String partial) {
			return choices;
		}
	}

}
