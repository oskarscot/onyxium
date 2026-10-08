package dev.onyxium.command;

import module java.base;

public interface CommandFixtures {

	interface Source {

		boolean hasPermission(String permission);

	}

	record ConsoleSource(Set<String> permissions) implements Source {
		@Override
		public boolean hasPermission(String permission) {
			return permissions.contains(permission);
		}
	}

	record PlayerSource(Set<String> permissions) implements Source {
		@Override
		public boolean hasPermission(String permission) {
			return permissions.contains(permission);
		}
	}

}
