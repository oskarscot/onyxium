package dev.onyxium.command;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

record RegisteredCommand<S>(CommandDefinition definition, List<Binding> arguments, MethodHandle invocation) {

	static <S> RegisteredCommand<S> compile(Object handler, Method method, Class<S> sourceType,
			ArgumentParser parsers) {
		if (!Modifier.isPublic(method.getModifiers()) || Modifier.isStatic(method.getModifiers()) || method.isVarArgs()
				|| method.getReturnType() != void.class) {
			throw invalid(method, "Handlers must be public instance methods returning void, without varargs.");
		}
		var parameters = method.getParameters();
		if (parameters.length == 0 || !sourceType.isAssignableFrom(parameters[0].getType())) {
			throw invalid(method, "The first parameter must be the configured source type or a subtype.");
		}
		if (parameters[0].isAnnotationPresent(Greedy.class))
			throw invalid(method, "The source cannot be greedy.");
		var bindings = IntStream.range(1, parameters.length)
			.mapToObj(index -> binding(method, parameters[index], parsers))
			.toList();
		validateOrder(method, bindings);
		var annotation = method.getAnnotation(Command.class);
		var name = path(annotation.name());
		var aliases = Stream.of(annotation.aliases()).map(RegisteredCommand::segment).toList();
		var leaf = name.substring(name.lastIndexOf(' ') + 1);
		if (aliases.contains(leaf) || aliases.stream().distinct().count() != aliases.size()) {
			throw invalid(method, "Aliases must be distinct from the name and each other.");
		}
		var definition = new CommandDefinition(name, aliases, annotation.permission(), annotation.description(),
				parameters[0].getType(), bindings.stream().map(Binding::definition).toList());
		try {
			var invocation = MethodHandles.publicLookup()
				.unreflect(method)
				.bindTo(handler)
				.asSpreader(Object[].class, parameters.length)
				.asType(MethodType.methodType(void.class, Object[].class));
			return new RegisteredCommand<>(definition, bindings, invocation);
		}
		catch (IllegalAccessException exception) {
			throw new IllegalArgumentException("Command method is inaccessible: " + method, exception);
		}
	}

	static Binding binding(Method method, Parameter parameter, ArgumentParser parsers) {
		if (!parameter.isNamePresent())
			throw invalid(method, "Compile handlers with -parameters to retain argument names.");
		var optional = parameter.getType() == Optional.class;
		var type = argumentType(method, parameter.getParameterizedType(), optional);
		var greedy = parameter.isAnnotationPresent(Greedy.class);
		if (greedy && type != String.class)
			throw invalid(method, "Only String or Optional<String> arguments can be greedy.");
		if (!parsers.supports(type))
			throw invalid(method, "No argument parser registered for " + type.getTypeName() + ".");
		return new Binding(new ArgumentDefinition(parameter.getName(), type, optional, greedy), parsers);
	}

	static Class<?> argumentType(Method method, Type type, boolean optional) {
		if (optional && type instanceof ParameterizedType parameterized)
			type = parameterized.getActualTypeArguments()[0];
		else if (optional)
			throw invalid(method, "Optional arguments must declare a concrete value type.");
		if (type instanceof Class<?> concrete)
			return ArgumentParser.boxed(concrete);
		throw invalid(method, "Argument types must be concrete classes, optionally wrapped in Optional.");
	}

	static void validateOrder(Method method, List<Binding> bindings) {
		var optionalSeen = false;
		for (var index = 0; index < bindings.size(); index++) {
			var argument = bindings.get(index).definition();
			if (optionalSeen && !argument.optional())
				throw invalid(method, "Required arguments must precede optional arguments.");
			if (argument.greedy() && index != bindings.size() - 1)
				throw invalid(method, "Greedy arguments must be last.");
			optionalSeen |= argument.optional();
		}
	}

	static String path(String name) {
		var normalized = name.strip();
		if (normalized.isEmpty())
			throw new IllegalArgumentException("Command paths cannot be empty.");
		return Stream.of(normalized.split("\\s+")).map(RegisteredCommand::segment).collect(Collectors.joining(" "));
	}

	static String segment(String name) {
		var normalized = name.toLowerCase(Locale.ROOT);
		if (!normalized.matches("[a-z0-9][a-z0-9_-]*")) {
			throw new IllegalArgumentException("Invalid command segment: " + name);
		}
		return normalized;
	}

	static IllegalArgumentException invalid(Method method, String reason) {
		return new IllegalArgumentException(method.getName() + ": " + reason);
	}

	record Binding(ArgumentDefinition definition, ArgumentParser parser) {
	}
}
