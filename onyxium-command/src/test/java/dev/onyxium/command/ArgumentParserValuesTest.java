package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThat;

import module java.base;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ArgumentParserValuesTest {

	Class<?> type;

	String input;

	Object expected;

	public ArgumentParserValuesTest(Class<?> type, String input, Object expected) {
		this.type = type;
		this.input = input;
		this.expected = expected;
	}

	@Parameterized.Parameters(name = "type={0}, input={1}")
	public static List<Object[]> cases() {
		return List.of(
				new Object[] { int.class, "42", 42 },
				new Object[] { boolean.class, "TRUE", true },
				new Object[] { Boolean.class, "false", false },
				new Object[] { char.class, "é", 'é' },
				new Object[] { Mode.class, "creative", Mode.CREATIVE });
	}

	@Test
	public void handlesPrimitiveTargetsAndCaseInsensitiveValues() {
		var parser = new ArgumentParser();

		assertThat(parser.parse(input, type)).isEqualTo(expected);
	}

	enum Mode {

		GAME, CREATIVE

	}

}
