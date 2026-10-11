package dev.onyxium.proxy.command;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import module java.base;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import dev.onyxium.proxy.api.command.Command;
import dev.onyxium.proxy.api.command.CommandDispatcher;
import dev.onyxium.proxy.api.command.CommandSource;
import dev.onyxium.proxy.api.message.FormattedMessage;

public class CommandMessageTest {

	@Test
	public void commandRepliesKeepFormattingExceptForConsoleOutput() {
		var message = FormattedMessage.builder()
			.append(child -> child.text("Hello ").color("#aaaaaa")
				.append(name -> name.text("Oskar").bold().append("!")))
			.append(FormattedMessage.translation("test.notice"))
			.build();
		var dispatcher = new CommandDispatcher();
		dispatcher.registerHandler(new ReplyCommands(message));
		var console = new ProxyConsole(dispatcher);
		var player = new FormattedSource(new ArrayList<>());
		var logger = (Logger) LoggerFactory.getLogger(ProxyConsole.class);
		var output = new ListAppender<ILoggingEvent>();
		output.start();
		logger.addAppender(output);
		try {
			assertThat(dispatcher.dispatch(console, "reply")).isTrue();
			assertThat(dispatcher.dispatch(player, "reply")).isTrue();

			assertThat(output.list.stream().map(ILoggingEvent::getFormattedMessage).toList())
				.containsExactly("Hello Oskar!test.notice", "Plain reply.");
			assertThat(player.messages()).containsExactly(message, FormattedMessage.text("Plain reply."));
		}
		finally {
			logger.detachAppender(output);
			output.stop();
		}
	}

	public record ReplyCommands(FormattedMessage message) {

		@Command(name = "reply")
		public void reply(CommandSource source) {
			source.sendMessage(message);
			source.sendMessage("Plain reply.");
		}
	}

	record FormattedSource(List<FormattedMessage> messages) implements CommandSource {

		@Override
		public boolean hasPermission(String permission) {
			return true;
		}

		@Override
		public void sendMessage(FormattedMessage message) {
			messages.add(message);
		}
	}

}
