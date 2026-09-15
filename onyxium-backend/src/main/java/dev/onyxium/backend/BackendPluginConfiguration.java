package dev.onyxium.backend;

import com.hypixel.hytale.codec.Codec;
import com.hypixel.hytale.codec.KeyedCodec;
import com.hypixel.hytale.codec.builder.BuilderCodec;

import dev.onyxium.forwarding.ForwardingToken;

public final class BackendPluginConfiguration {

	public static final BuilderCodec<BackendPluginConfiguration> CODEC = BuilderCodec
		.builder(BackendPluginConfiguration.class, BackendPluginConfiguration::new)
		.append(new KeyedCodec<>("ForwardingSecret", Codec.STRING),
				(config, secret) -> config.forwardingSecret = secret, config -> config.forwardingSecret)
		.add()
		.build();

	private String forwardingSecret = "";

	public ForwardingToken forwardingTokens() {
		return new ForwardingToken(forwardingSecret);
	}

}
