package dev.onyxium.command;

import module java.base;

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
		public void sendMessage(String message) {
			messages.add(message);
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
		public void sendMessage(String message) {
			messages.add(message);
		}
	}

}
