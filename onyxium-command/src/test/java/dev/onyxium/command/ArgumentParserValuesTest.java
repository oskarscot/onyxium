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
		var uuid = UUID.fromString("00000000-0000-0000-0000-000000000001");
		return List.of(new Object[] { String.class, "hello", "hello" }, new Object[] { byte.class, "127", (byte) 127 },
				new Object[] { short.class, "-32768", (short) -32768 },
				new Object[] { int.class, "2147483647", Integer.MAX_VALUE },
				new Object[] { long.class, "9223372036854775807", Long.MAX_VALUE },
				new Object[] { float.class, "1.5", 1.5f }, new Object[] { double.class, "-2.5", -2.5d },
				new Object[] { boolean.class, "TRUE", true }, new Object[] { Boolean.class, "false", false },
				new Object[] { char.class, "é", 'é' }, new Object[] { UUID.class, uuid.toString(), uuid },
				new Object[] { Mode.class, "creative", Mode.CREATIVE });
	}

	@Test
	public void convertsPrimitiveBoxedAndEnumValues() {
		var parser = new ArgumentParser();
		assertThat(parser.parse(input, type)).isEqualTo(expected);
	}

	enum Mode {

		GAME, CREATIVE

	}

}
