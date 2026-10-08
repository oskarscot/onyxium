package dev.onyxium.command;

import module java.base;

/// Primitive targets return boxed values. Enums and booleans ignore case; floating-point values
/// must be finite. Register custom converters before registering handlers that use them.
public final class ArgumentParser {

	Map<Class<?>, Function<String, ?>> converters = new ConcurrentHashMap<>(Map.<Class<?>, Function<String, ?>>of(
			String.class, Function.identity(), Byte.class, Byte::valueOf, Short.class, Short::valueOf, Integer.class,
			Integer::valueOf, Long.class, Long::valueOf, Float.class, Float::valueOf, Double.class, Double::valueOf,
			Boolean.class, Boolean::valueOf, Character.class, input -> input.charAt(0), UUID.class, UUID::fromString));

	public <T> T parse(String input, Class<T> targetType) {
		return parse(input, targetType, () -> new IllegalArgumentException(
				"Cannot parse '%s' as %s.".formatted(input, targetType.getTypeName())));
	}

	/// The supplier runs once for invalid input or an unsupported target.
	/// Converters report invalid input with IllegalArgumentException;
	/// unexpected failures propagate.
	@SuppressWarnings("unchecked")
	public <T> T parse(String input, Class<T> targetType, Supplier<? extends RuntimeException> exceptionSupplier) {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(targetType, "targetType");
		Objects.requireNonNull(exceptionSupplier, "exceptionSupplier");
		var type = boxed(targetType);
		if (!supports(type) || type == Character.class && input.length() != 1)
			throw exceptionSupplier.get();
		Object value;
		try {
			value = type.isEnum() ? enumValue(input, type) : converters.get(type).apply(input);
		}
		catch (IllegalArgumentException _) {
			throw exceptionSupplier.get();
		}
		if (!type.isInstance(value))
			throw new IllegalStateException(
					"Converter for %s returned a null or incompatible value.".formatted(type.getTypeName()));
		if (!valid(input, value))
			throw exceptionSupplier.get();
		// Class.cast rejects boxed values when the target is a primitive class.
		return (T) value;
	}

	/// Converters cannot be replaced after registration, so existing commands retain
	/// their behavior.
	public <T> void register(Class<T> targetType, Function<String, T> converter) {
		var type = boxed(Objects.requireNonNull(targetType, "targetType"));
		Objects.requireNonNull(converter, "converter");
		if (type.isEnum())
			throw new IllegalArgumentException("Enums already have a built-in converter.");
		var existing = converters.putIfAbsent(type, converter);
		if (existing != null)
			throw new IllegalArgumentException("An argument parser already exists for " + targetType.getTypeName());
	}

	public List<String> suggestions(Class<?> targetType) {
		var type = boxed(Objects.requireNonNull(targetType, "targetType"));
		if (type == Boolean.class)
			return List.of("true", "false");
		return type.isEnum() ? enumValues(type).map(Enum::name).toList() : List.of();
	}

	boolean supports(Class<?> type) {
		return type.isEnum() || converters.containsKey(boxed(type));
	}

	static boolean valid(String input, Object value) {
		return switch (value) {
			case Boolean _ -> input.equalsIgnoreCase("true") || input.equalsIgnoreCase("false");
			case Float number -> Float.isFinite(number);
			case Double number -> Double.isFinite(number);
			default -> true;
		};
	}

	static Object enumValue(String input, Class<?> type) {
		return enumValues(type).filter(value -> value.name().equalsIgnoreCase(input)).findFirst()
				.orElseThrow(IllegalArgumentException::new);
	}

	static Stream<Enum<?>> enumValues(Class<?> type) {
		return Stream.of(type.getEnumConstants()).map(value -> (Enum<?>) value);
	}

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

}
