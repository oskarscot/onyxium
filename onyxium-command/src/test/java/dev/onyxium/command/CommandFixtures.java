package dev.onyxium.command;

import java.util.List;
import java.util.Set;

public interface CommandFixtures {

	interface Source {

		boolean hasPermission(String permission);

	}

	record ConsoleSource(List<String> calls, Set<String> permissions) implements Source {
		@Override
		public boolean hasPermission(String permission) {
			return permissions.contains(permission);
		}
	}

	record PlayerSource(List<String> calls, Set<String> permissions) implements Source {
		@Override
		public boolean hasPermission(String permission) {
			return permissions.contains(permission);
		}
	}

}
