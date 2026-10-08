package dev.onyxium.command;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import module java.base;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CommandInputMalformedTest {

	String input;

	public CommandInputMalformedTest(String input) {
		this.input = input;
	}

	@Parameterized.Parameters(name = "case={index}")
	public static List<String> cases() {
		return List.of("\"unfinished", "'unfinished", "tail\\");
	}

	@Test
	public void rejectsIncompleteQuotedTokensAndEscapes() {
		var command = new CommandInput(input);

		assertThatIllegalArgumentException().isThrownBy(command::read);
	}

}
