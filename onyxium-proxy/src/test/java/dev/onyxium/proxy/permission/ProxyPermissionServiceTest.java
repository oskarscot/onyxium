package dev.onyxium.proxy.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import module java.base;
import org.junit.Test;

public class ProxyPermissionServiceTest {

	@Test
	public void registrationIsLiveExclusiveAndCanBeReleasedSafely() {
		var player = UUID.randomUUID();
		var service = new ProxyPermissionService();
		assertThat(service.hasPermission(player, "test.permission")).isFalse();

		var first = service.register((uuid, permission) -> permission.equals("test.permission"));
		assertThat(service.hasPermission(player, "test.permission")).isTrue();
		assertThat(service.hasPermission(player, "other.permission")).isFalse();
		assertThrows(IllegalStateException.class, () -> service.register((uuid, permission) -> true));

		first.close();
		assertThat(service.hasPermission(player, "test.permission")).isFalse();

		try (var second = service.register((uuid, permission) -> true)) {
			first.close();
			assertThat(service.hasPermission(player, "test.permission")).isTrue();
			assertThat(service.hasPermission(player, " ")).isFalse();
		}

		assertThat(service.hasPermission(player, "test.permission")).isFalse();
		assertThrows(IllegalArgumentException.class, () -> service.register(service));
	}

	@Test
	public void providerFailureDeniesTheCheck() {
		var service = new ProxyPermissionService();

		try (var registration = service.register((uuid, permission) -> {
			throw new IllegalStateException("unavailable");
		})) {
			assertThat(service.hasPermission(UUID.randomUUID(), "test.permission")).isFalse();
		}
	}
}
