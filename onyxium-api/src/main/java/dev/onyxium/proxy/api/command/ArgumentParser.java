package dev.onyxium.proxy.api.command;

import module java.base;

/// Converts command arguments to Java values.
///
/// Primitive types return boxed values. Enum and boolean values ignore case; floating-point values must be finite.
/// Register custom converters before registering their command handlers and before using the parser across threads.
public final class ArgumentParser {

	Map<Class<?>, Function<String, ?>> converters = new HashMap<>();

	/// Creates a parser with converters for strings, primitive values, UUIDs and enums.
	public ArgumentParser() {
		converters.put(String.class, Function.identity());
		converters.put(Byte.class, Byte::valueOf);
		converters.put(Short.class, Short::valueOf);
		converters.put(Integer.class, Integer::valueOf);
		converters.put(Long.class, Long::valueOf);
		converters.put(Float.class, Float::valueOf);
		converters.put(Double.class, Double::valueOf);
		converters.put(Boolean.class, Boolean::valueOf);
		converters.put(Character.class, input -> input.charAt(0));
		converters.put(UUID.class, UUID::fromString);
	}

	/// Converts input to the requested type.
	///
	/// @param <T> the result type
	/// @param input the text to convert
	/// @param targetType the requested Java type
	/// @return the converted value
	/// @throws IllegalArgumentException if the input is invalid or the type has no
	/// converter
	public <T> T parse(String input, Class<T> targetType) {
		return parse(input, targetType, () -> new IllegalArgumentException(
				"Cannot parse '%s' as %s.".formatted(input, targetType.getTypeName())));
	}

	/// Converts input to the requested type, using the supplied exception for invalid
	/// input or an unsupported type.
	///
	/// Invalid input and unsupported types call the supplier once. Other converter
	/// exceptions propagate to the caller.
	///
	/// @param <T> the result type
	/// @param input the text to convert
	/// @param targetType the requested Java type
	/// @param exceptionSupplier creates the exception for invalid input or an unsupported
	/// type
	/// @return the converted value
	@SuppressWarnings("unchecked")
	public <T> T parse(String input, Class<T> targetType, Supplier<? extends RuntimeException> exceptionSupplier) {
		Objects.requireNonNull(input, "input");
		Objects.requireNonNull(targetType, "targetType");
		Objects.requireNonNull(exceptionSupplier, "exceptionSupplier");

		var type = boxed(targetType);
		if (!supports(type) || type == Character.class && input.length() != 1) {
			throw exceptionSupplier.get();
		}

		Object value;
		try {
			value = type.isEnum() ? enumValue(input, type) : converters.get(type).apply(input);
		}
		catch (IllegalArgumentException _) {
			throw exceptionSupplier.get();
		}

		if (!type.isInstance(value)) {
			throw new IllegalStateException(
					"Converter for %s returned a null or incompatible value.".formatted(type.getTypeName()));
		}

		if (!valid(input, value)) {
			throw exceptionSupplier.get();
		}

		// Class.cast rejects boxed values when the target is a primitive class.
		return (T) value;
	}

	/// Registers a converter for a custom argument type. Existing converters cannot be
	/// replaced.
	///
	/// The converter must return a non-null value of the requested type or throw
	/// [IllegalArgumentException]
	/// for invalid input. Register it before any handlers that use the type.
	///
	/// @param <T> the result type
	/// @param targetType the type to register
	/// @param converter converts text to a value of that type
	/// @throws IllegalArgumentException if the type is an enum or already has a converter
	public <T> void register(Class<T> targetType, Function<String, T> converter) {
		var type = boxed(Objects.requireNonNull(targetType, "targetType"));
		Objects.requireNonNull(converter, "converter");

		if (type.isEnum()) {
			throw new IllegalArgumentException("Enums already have a built-in converter.");
		}

		var existing = converters.putIfAbsent(type, converter);
		if (existing != null) {
			throw new IllegalArgumentException("An argument parser already exists for " + targetType.getTypeName());
		}
	}

	/// {@return boolean values or enum constant names, or an empty list for other types}
	///
	/// @param targetType the type to suggest values for
	public List<String> suggestions(Class<?> targetType) {
		var type = boxed(Objects.requireNonNull(targetType, "targetType"));

		if (type == Boolean.class) {
			return List.of("true", "false");
		}

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
