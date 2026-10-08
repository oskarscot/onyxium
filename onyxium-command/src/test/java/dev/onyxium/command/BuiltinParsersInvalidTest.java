package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.util.List;
import java.util.UUID;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class BuiltinParsersInvalidTest {

	Class<?> type;

	String input;

	public BuiltinParsersInvalidTest(Class<?> type, String input) {
		this.type = type;
		this.input = input;
	}

	@Parameterized.Parameters(name = "type={0}, input={1}")
	public static List<Object[]> cases() {
		return List.of(new Object[] { Byte.class, "128" }, new Object[] { Integer.class, "2147483648" },
				new Object[] { Long.class, "9223372036854775808" }, new Object[] { Boolean.class, "maybe" },
				new Object[] { Float.class, "NaN" }, new Object[] { Double.class, "Infinity" },
				new Object[] { Character.class, "ab" }, new Object[] { UUID.class, "invalid" },
				new Object[] { BuiltinParsersTest.Mode.class, "unknown" });
	}

	@Test
	public void rejectsInvalidValuesInsteadOfCoercingThem() {
		var parser = BuiltinParsers.<String>find(type);
		assertThatIllegalArgumentException().isThrownBy(() -> parser.parse("console", input));
	}

}
