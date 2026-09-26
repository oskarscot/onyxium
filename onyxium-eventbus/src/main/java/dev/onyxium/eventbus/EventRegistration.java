package dev.onyxium.eventbus;

import java.lang.reflect.Method;

public record EventRegistration(
	Object instance,
	Method targetMethod,
	int priotity
) {  }
