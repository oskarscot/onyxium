package dev.onyxium.proxy.api.command;

import module java.base;

import dev.onyxium.proxy.api.message.FormattedMessage;

public interface CommandFixtures {

	record ConsoleSource(Set<String> permissions, List<String> messages) implements CommandSource {
		public ConsoleSource(Set<String> permissions) {
			this(permissions, new ArrayList<>());
		}

		@Override
		public boolean hasPermission(String permission) {
			return permissions.contains(permission);
		}

		@Override
		public void sendMessage(FormattedMessage message) {
			messages.add(message.rawText());
		}
	}

	record PlayerSource(Set<String> permissions, List<String> messages) implements CommandSource {
		public PlayerSource(Set<String> permissions) {
			this(permissions, new ArrayList<>());
		}

		@Override
		public boolean hasPermission(String permission) {
			return permissions.contains(permission);
		}

		@Override
		public void sendMessage(FormattedMessage message) {
			messages.add(message.rawText());
		}
	}

}
