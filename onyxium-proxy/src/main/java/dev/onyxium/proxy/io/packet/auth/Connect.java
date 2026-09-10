package dev.onyxium.proxy.io.packet.auth;

import io.netty.buffer.ByteBuf;
import org.jetbrains.annotations.Nullable;

import dev.onyxium.proxy.io.packet.ClientType;
import dev.onyxium.proxy.io.packet.HostAddress;
import dev.onyxium.proxy.io.packet.Packet;
import dev.onyxium.proxy.util.NettyUtil;

/// The protocol-3 Connect layout: 30 fixed bytes, four heap-relative offsets, then
/// identity token, language and optional referral fields. Player identity comes from the JWT.
public record Connect(int protocolCrc, int protocolBuildNumber, String clientVersion, ClientType clientType,
		String language, @Nullable String identityToken, byte @Nullable [] referralData,
		@Nullable HostAddress referralSource) implements Packet {

	private static final int IDENTITY_TOKEN_PRESENT = 0x1;

	private static final int REFERRAL_DATA_PRESENT = 0x2;

	private static final int REFERRAL_SOURCE_PRESENT = 0x4;

	private static final int CLIENT_VERSION_WIDTH = 20;

	private static final int MAX_IDENTITY_TOKEN_LENGTH = 8192;

	private static final int MAX_LANGUAGE_LENGTH = 16;

	private static final int MAX_REFERRAL_DATA_LENGTH = 4096;

	/// Placeholder left in the offset slot of an absent field. Purely diagnostic —
	/// presence is
	/// decided from the null bits alone, since a peer is free to write anything here.
	private static final int ABSENT_OFFSET = -1;

	public static Connect deserialize(ByteBuf buf) {
		int nullBits = buf.readUnsignedByte();

		var protocolCrc = buf.readIntLE();
		var protocolBuildNumber = buf.readIntLE();
		var clientVersion = NettyUtil.readFixedAscii(buf, CLIENT_VERSION_WIDTH);
		var clientType = ClientType.getById(buf.readByte());

		var identityTokenOffset = buf.readIntLE();
		var languageOffset = buf.readIntLE();
		var referralDataOffset = buf.readIntLE();
		var referralSourceOffset = buf.readIntLE();

		var varsOffset = buf.readerIndex();

		var end = varsOffset;

		String identityToken = null;

		if ((nullBits & IDENTITY_TOKEN_PRESENT) != 0) {
			NettyUtil.seekToField(buf, varsOffset, identityTokenOffset);
			identityToken = NettyUtil.readVarString(buf, MAX_IDENTITY_TOKEN_LENGTH);
			end = Math.max(end, buf.readerIndex());
		}

		NettyUtil.seekToField(buf, varsOffset, languageOffset);
		var language = NettyUtil.readVarAscii(buf, MAX_LANGUAGE_LENGTH);
		end = Math.max(end, buf.readerIndex());

		byte[] referralData = null;

		if ((nullBits & REFERRAL_DATA_PRESENT) != 0) {
			NettyUtil.seekToField(buf, varsOffset, referralDataOffset);
			referralData = NettyUtil.readVarBytes(buf, MAX_REFERRAL_DATA_LENGTH);
			end = Math.max(end, buf.readerIndex());
		}

		HostAddress referralSource = null;

		if ((nullBits & REFERRAL_SOURCE_PRESENT) != 0) {
			NettyUtil.seekToField(buf, varsOffset, referralSourceOffset);
			referralSource = HostAddress.deserialize(buf);
			end = Math.max(end, buf.readerIndex());
		}

		buf.readerIndex(end);

		return new Connect(protocolCrc, protocolBuildNumber, clientVersion, clientType, language, identityToken,
				referralData, referralSource);
	}

	@Override
	public void serialize(ByteBuf buf) {
		var nullBits = 0;

		if (this.identityToken != null) {
			nullBits |= IDENTITY_TOKEN_PRESENT;
		}

		if (this.referralData != null) {
			nullBits |= REFERRAL_DATA_PRESENT;
		}

		if (this.referralSource != null) {
			nullBits |= REFERRAL_SOURCE_PRESENT;
		}

		buf.writeByte(nullBits);
		buf.writeIntLE(this.protocolCrc);
		buf.writeIntLE(this.protocolBuildNumber);
		NettyUtil.writeFixedAscii(buf, this.clientVersion, CLIENT_VERSION_WIDTH);
		buf.writeByte(this.clientType.getId());

		var identityTokenSlot = buf.writerIndex();
		buf.writeIntLE(ABSENT_OFFSET);
		var languageSlot = buf.writerIndex();
		buf.writeIntLE(ABSENT_OFFSET);
		var referralDataSlot = buf.writerIndex();
		buf.writeIntLE(ABSENT_OFFSET);
		var referralSourceSlot = buf.writerIndex();
		buf.writeIntLE(ABSENT_OFFSET);

		var varsOffset = buf.writerIndex();

		if (this.identityToken != null) {
			buf.setIntLE(identityTokenSlot, buf.writerIndex() - varsOffset);
			NettyUtil.writeVarString(buf, this.identityToken, MAX_IDENTITY_TOKEN_LENGTH);
		}

		buf.setIntLE(languageSlot, buf.writerIndex() - varsOffset);
		NettyUtil.writeVarAscii(buf, this.language, MAX_LANGUAGE_LENGTH);

		if (this.referralData != null) {
			buf.setIntLE(referralDataSlot, buf.writerIndex() - varsOffset);
			NettyUtil.writeVarBytes(buf, this.referralData, MAX_REFERRAL_DATA_LENGTH);
		}

		if (this.referralSource != null) {
			buf.setIntLE(referralSourceSlot, buf.writerIndex() - varsOffset);
			this.referralSource.serialize(buf);
		}
	}

	@Override
	public String toString() {
		return "Connect[protocolCrc=" + protocolCrc + ", clientType=" + clientType + "]";
	}

	@Override
	public int id() {
		return 0;
	}
}
