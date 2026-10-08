package dev.onyxium.eventbus;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks an instance method with one [Event] parameter as a subscriber.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Subscribe {

	/// Returns the dispatch priority. Higher values run first.
	///
	/// @return the priority, defaulting to `0`
	int value() default 0;

}
