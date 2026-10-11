package dev.onyxium.command;

import module java.base;

record RegisteredCommand(Object owner, CommandDefinition definition, MethodHandle invocation) {

	void invoke(Object[] arguments) {
		try {
			invocation.invokeExact(arguments);
		}
		catch (Throwable failure) {
			throw new IllegalStateException("Failed to invoke command /" + definition.name(), failure);
		}
	}

	static RegisteredCommand compile(Object owner, Object handler, Method method, ArgumentParser parsers) {
		validateSignature(method);

		var parameters = method.getParameters();
		var arguments = Stream.of(parameters)
				.skip(1)
				.map(parameter -> argument(method, parameter, parsers))
				.toList();
		var annotation = method.getAnnotation(Command.class);
		var declaredPath = path(annotation.name(), arguments);
		var name = declaredPath.stream().map(segment -> switch (segment) {
			case CommandSegment.Literal literal -> literal.text();
			case CommandSegment.Argument argument -> "{" + argument.argumentDefinition().name() + "}";
		}).collect(Collectors.joining(" "));

		var path = new ArrayList<>(declaredPath);
		for (var argument : arguments) {
			var segment = new CommandSegment.Argument(argument);
			if (!path.contains(segment)) {
				path.add(segment);
			}
		}
		validateOrder(method, path);

		var aliases = Stream.of(annotation.aliases()).map(RegisteredCommand::segment).toList();
		var leaf = declaredPath.reversed().stream()
				.filter(CommandSegment.Literal.class::isInstance)
				.map(CommandSegment.Literal.class::cast)
				.findFirst().orElseThrow().text();

		if (aliases.contains(leaf) || aliases.stream().distinct().count() != aliases.size()) {
			throw invalid(method, "Aliases must be distinct from the name and each other.");
		}

		var definition = new CommandDefinition(name, aliases, annotation.permission(), annotation.description(),
				parameters[0].getType().asSubclass(CommandSource.class), arguments, path);

		try {
			var invocation = MethodHandles.publicLookup()
					.unreflect(method)
					.bindTo(handler)
					.asSpreader(Object[].class, parameters.length);

			return new RegisteredCommand(owner, definition, invocation);
		}
		catch (IllegalAccessException exception) {
			throw new IllegalArgumentException("Command method is inaccessible: " + method, exception);
		}
	}

	static void validateSignature(Method method) {
		var modifiers = method.getModifiers();
		if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || method.isVarArgs()
				|| method.getReturnType() != void.class) {
			throw invalid(method, "Handlers must be public instance methods returning void, without varargs.");
		}

		var parameters = method.getParameters();
		if (parameters.length == 0 || !CommandSource.class.isAssignableFrom(parameters[0].getType())) {
			throw invalid(method, "The first parameter must be CommandSource or a subtype.");
		}

		if (parameters[0].isAnnotationPresent(Greedy.class)) {
			throw invalid(method, "The source cannot be greedy.");
		}
	}

	static ArgumentDefinition argument(Method method, Parameter parameter, ArgumentParser parsers) {
		if (!parameter.isNamePresent()) {
			throw invalid(method, "Compile handlers with -parameters to retain argument names.");
		}

		var optional = parameter.getType() == Optional.class;
		var type = argumentType(method, parameter.getParameterizedType(), optional);
		var greedy = parameter.isAnnotationPresent(Greedy.class);

		if (greedy && type != String.class) {
			throw invalid(method, "Only String or Optional<String> arguments can be greedy.");
		}

		if (!parsers.supports(type)) {
			throw invalid(method, "No argument parser registered for " + type.getTypeName() + ".");
		}

		return new ArgumentDefinition(parameter.getName(), type, optional, greedy);
	}

	static Class<?> argumentType(Method method, Type type, boolean optional) {
		if (optional && type instanceof ParameterizedType parameterized) {
			type = parameterized.getActualTypeArguments()[0];
		}
		else if (optional) {
			throw invalid(method, "Optional arguments must declare a concrete value type.");
		}

		if (type instanceof Class<?> concrete) {
			return ArgumentParser.boxed(concrete);
		}

		throw invalid(method, "Argument types must be concrete classes, optionally wrapped in Optional.");
	}

	static void validateOrder(Method method, List<CommandSegment> path) {
		var optionalTail = false;

		for (var index = 0; index < path.size(); index++) {
			if (path.get(index) instanceof CommandSegment.Argument segment) {
				var argument = segment.argumentDefinition();

				if (optionalTail && !argument.optional()) {
					throw invalid(method, "Required arguments must precede optional arguments.");
				}
				if (argument.greedy() && index != path.size() - 1) {
					throw invalid(method, "Greedy arguments must be last.");
				}

				optionalTail |= argument.optional();
			}
			else if (optionalTail) {
				throw invalid(method, "Literal segments cannot follow optional arguments.");
			}
		}
	}

	static List<CommandSegment> path(String name, List<ArgumentDefinition> arguments) {
		var normalized = name.strip();
		if (normalized.isEmpty()) {
			throw new IllegalArgumentException("Command paths cannot be empty.");
		}

		var result = new ArrayList<CommandSegment>();
		var referenced = new HashSet<String>();

		for (var token : normalized.split("\\s+")) {
			if (!token.contains("{") && !token.contains("}")) {
				result.add(new CommandSegment.Literal(segment(token)));
				continue;
			}

			if (!token.startsWith("{") || !token.endsWith("}") || token.length() <= 2) {
				throw new IllegalArgumentException("Invalid argument placeholder: " + token);
			}

			var argumentName = token.substring(1, token.length() - 1);
			if (argumentName.contains("{") || argumentName.contains("}")) {
				throw new IllegalArgumentException("Invalid argument placeholder: " + token);
			}

			var argument = arguments.stream()
				.filter(arg -> arg.name().equals(argumentName))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Unknown argument placeholder: " + argumentName));

			if (!referenced.add(argumentName)) {
				throw new IllegalArgumentException("Duplicate argument placeholder: " + argumentName);
			}

			result.add(new CommandSegment.Argument(argument));
		}

		if (!(result.getFirst() instanceof CommandSegment.Literal)) {
			throw new IllegalArgumentException("Command paths must start with a literal.");
		}

		return List.copyOf(result);
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
}
