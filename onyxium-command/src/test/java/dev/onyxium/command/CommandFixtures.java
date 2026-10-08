package dev.onyxium.command;

import module java.base;

public interface CommandFixtures {

	record ConsoleSource(Set<String> permissions) implements CommandSource {
		@Override
		public boolean hasPermission(String permission) {
			return permissions.contains(permission);
		}

		@Override
		public void sendMessage(String message) {
		}
	}

	record PlayerSource(Set<String> permissions) implements CommandSource {
		@Override
		public boolean hasPermission(String permission) {
			return permissions.contains(permission);
		}

		@Override
		public void sendMessage(String message) {
		}
	}

}
