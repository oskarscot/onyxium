package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThat;
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
		return List.of(new Object[] { Byte.class, "128" }, new Object[] { Integer.class, "2147483648" },
				new Object[] { Long.class, "9223372036854775808" }, new Object[] { Boolean.class, "maybe" },
				new Object[] { Float.class, "NaN" }, new Object[] { Double.class, "Infinity" },
				new Object[] { Character.class, "ab" }, new Object[] { char.class, "" },
                new Object[] { float.class, "1e100" }, new Object[] { double.class, "1e1000" },
                new Object[] { double.class, "NaN" }, new Object[] { float.class, "-Infinity" },
                new Object[] { boolean.class, "" }, new Object[] { short.class, "32768" },
                new Object[] { ArgumentParserTest.Backend.class, "lobby" }, new Object[] { UUID.class, "invalid" },
				new Object[] { ArgumentParserValuesTest.Mode.class, "unknown" });
	}

	@Test
	public void rejectsInvalidValuesInsteadOfCoercingThem() {
		var parser = new ArgumentParser();
		var created = new AtomicInteger();
        var failure = new IllegalArgumentException("Invalid argument");
        assertThatThrownBy(() -> parser.parse(input, type, () -> ArgumentParserTest.supplied(created, failure)))
                .isSameAs(failure);
        assertThat(created.get()).isEqualTo(1);
	}

}
