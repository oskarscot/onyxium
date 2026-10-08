package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import module java.base;
import org.junit.Test;

public class ArgumentParserTest {

	@Test
	public void customConverterCannotBeReplacedAfterRegistration() {
		var parser = new ArgumentParser();
		parser.register(Backend.class, Backend::new);

		assertThat(parser.parse("lobby", Backend.class)).isEqualTo(new Backend("lobby"));
		assertThatIllegalArgumentException().isThrownBy(() -> parser.register(Backend.class, Backend::new));
		assertThatIllegalArgumentException().isThrownBy(() -> parser.register(String.class, String::strip));
	}

	@Test
	public void rejectsNullConverterResults() {
		var parser = new ArgumentParser();

		parser.register(Backend.class, _ -> null);

		assertThatIllegalStateException().isThrownBy(() -> parser.parse("lobby", Backend.class))
			.withMessageContaining("null or incompatible");
	}

	@Test
	public void createsTheSuppliedExceptionOnlyWhenConversionFails() {
		var parser = new ArgumentParser();
		var created = new AtomicInteger();
		var failure = new IllegalArgumentException("Invalid amount");
		Supplier<IllegalArgumentException> supplier = () -> supplied(created, failure);

		assertThat(parser.parse("42", Integer.class, supplier)).isEqualTo(42);
		assertThat(created.get()).isEqualTo(0);
		assertThatThrownBy(() -> parser.parse("invalid", Integer.class, supplier)).isSameAs(failure);
		assertThat(created.get()).isEqualTo(1);
	}

	@Test
	public void unexpectedConverterFailureIsPreserved() {
		var parser = new ArgumentParser();
		var failure = new IllegalStateException("Lookup failed");
		parser.register(Backend.class, _ -> broken(failure));

		assertThatThrownBy(
				() -> parser.parse("lobby", Backend.class, () -> new IllegalArgumentException("Invalid backend")))
			.isSameAs(failure);
	}

	static IllegalArgumentException supplied(AtomicInteger created, IllegalArgumentException failure) {
		created.incrementAndGet();
		return failure;
	}

	static Backend broken(IllegalStateException failure) {
		throw failure;
	}

	record Backend(String name) {
	}

}
