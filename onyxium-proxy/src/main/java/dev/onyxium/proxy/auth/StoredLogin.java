package dev.onyxium.proxy.auth;

import com.nimbusds.jose.util.JSONObjectUtils;
import module java.base;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/// Only the OAuth refresh token and profile survive restarts; game sessions are created afresh.
record StoredLogin(String refreshToken, UUID profile) {

	private static final Logger LOGGER = LoggerFactory.getLogger(StoredLogin.class);

	StoredLogin {
		if (refreshToken == null || refreshToken.isBlank()) {
			throw new IllegalArgumentException("Missing OAuth refresh token");
		}
	}

	static Optional<StoredLogin> load(Path path) {
		try {
			var data = JSONObjectUtils.parse(Files.readString(path));
			if (!(data.get("refreshToken") instanceof String token)) {
				throw new IllegalArgumentException("Missing OAuth refresh token");
			}

			var profile = data.get("profile");
			if (profile != null && !(profile instanceof String)) {
				throw new IllegalArgumentException("Invalid saved profile");
			}

			return Optional.of(new StoredLogin(token, profile == null ? null : UUID.fromString((String) profile)));
		}
		catch (NoSuchFileException _) {
			return Optional.empty();
		}
		catch (ParseException | IllegalArgumentException _) {
			LOGGER.warn("Saved Hytale login is invalid; device login is required");
			return Optional.empty();
		}
		catch (IOException _) {
			throw new AuthenticationException("Could not read saved Hytale login from " + path);
		}
	}

	/// Replace atomically so an interrupted write cannot truncate the previous login.
	/// POSIX permissions are applied when creating the temporary file, before writing secrets.
	void save(Path path) {
		var target = path.toAbsolutePath();
		try {
			var attributes = Files.getFileStore(target.getParent()).supportsFileAttributeView("posix")
					? new FileAttribute<?>[] { PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")) }
					: new FileAttribute<?>[0];
			var temporary = Files.createTempFile(target.getParent(), ".onyxium-auth-", ".tmp", attributes);
			try {
				var data = new HashMap<String, Object>();
				data.put("refreshToken", refreshToken);
				if (profile != null) {
					data.put("profile", profile.toString());
				}

				Files.writeString(temporary, JSONObjectUtils.toJSONString(data));
				Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
			}
			finally {
				Files.deleteIfExists(temporary);
			}
		}
		catch (IOException _) {
			throw new AuthenticationException("Could not save Hytale login to " + target);
		}
	}

	@Override
	public String toString() {
		return "StoredLogin[profile=" + profile + "]";
	}

}
