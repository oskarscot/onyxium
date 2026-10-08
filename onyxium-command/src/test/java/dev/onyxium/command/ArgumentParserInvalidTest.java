package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import module java.base;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ArgumentParserInvalidTest {

	Class<?> type;

	String input;

	public ArgumentParserInvalidTest(Class<?> type, String input) {
		this.type = type;
		this.input = input;
	}

	@Parameterized.Parameters(name = "type={0}, input={1}")
	public static List<Object[]> cases() {
		return List.of(
				new Object[] { Integer.class, "invalid" },
				new Object[] { Boolean.class, "maybe" },
				new Object[] { Float.class, "NaN" },
				new Object[] { Double.class, "Infinity" },
				new Object[] { Character.class, "ab" },
				new Object[] { char.class, "" },
				new Object[] { ArgumentParserTest.Backend.class, "lobby" },
				new Object[] { ArgumentParserValuesTest.Mode.class, "unknown" });
	}

	@Test
	public void rejectsInvalidValuesInsteadOfCoercingThem() {
		var parser = new ArgumentParser();
		var failure = new IllegalStateException("Invalid argument");

		assertThatThrownBy(() -> parser.parse(input, type, () -> failure))
				.isSameAs(failure);

	}

}
