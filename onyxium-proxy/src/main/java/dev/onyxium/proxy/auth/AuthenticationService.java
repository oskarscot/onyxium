package dev.onyxium.proxy.auth;

import java.util.concurrent.CompletionStage;

import dev.onyxium.proxy.io.packet.auth.AuthToken;
import dev.onyxium.proxy.io.packet.auth.Connect;

public interface AuthenticationService extends AutoCloseable {

	CompletionStage<Grant> requestGrant(Connect connect);

	CompletionStage<String> authenticate(AuthToken token, AuthenticatedProfile identity, String clientFingerprint,
			String serverFingerprint);

	@Override
	default void close() {
	}

	record Grant(AuthenticatedProfile profile, String authorizationGrant, String serverIdentityToken) {
		@Override
		public String toString() {
			return "Grant[redacted]";
		}
	}

}
