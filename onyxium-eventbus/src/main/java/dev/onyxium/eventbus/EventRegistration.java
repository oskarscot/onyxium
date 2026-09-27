package dev.onyxium.eventbus;

import java.lang.invoke.MethodHandle;

/// A subscriber's invocation handle and priority.
///
/// @param handle the subscriber handle, accepting a single event argument
/// @param priority the dispatch priority, higher values run first
public record EventRegistration(
	MethodHandle handle,
	int priority
) {  }
