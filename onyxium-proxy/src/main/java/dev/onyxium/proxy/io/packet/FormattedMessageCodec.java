package dev.onyxium.proxy.io.packet;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.function.Consumer;
import java.util.function.Function;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;

import dev.onyxium.proxy.api.message.FormattedMessage;
import dev.onyxium.proxy.api.message.MessageParam;
import dev.onyxium.proxy.util.NettyUtil;

/// The protocol's 40-byte header and eight heap-relative fields. Recursion and collection
/// limits are proxy resource limits, applied before allocations or recursive calls.
public final class FormattedMessageCodec {

	private static final int MAX_STRING_BYTES = 4_096_000;

	private static final int MAX_DEPTH = 32;

	private static final int MAX_ENTRIES = 1024;

	private FormattedMessageCodec() {
	}

	public static void serialize(ByteBuf buf, FormattedMessage message) {
		write(buf, message, 0);
	}

	public static FormattedMessage deserialize(ByteBuf buf) {
		return read(buf, 0);
	}

	private static void write(ByteBuf buf, FormattedMessage message, int depth) {
		checkDepth(depth);
		var flags = (message.bold() != null ? 1 : 0) | (message.italic() != null ? 2 : 0)
				| (message.monospace() != null ? 4 : 0) | (message.underlined() != null ? 8 : 0)
				| (message.strikethrough() != null ? 16 : 0) | (message.rawText() != null ? 32 : 0)
				| (message.messageId() != null ? 64 : 0) | (message.children() != null ? 128 : 0)
				| (message.params() != null ? 256 : 0) | (message.messageParams() != null ? 512 : 0)
				| (message.color() != null ? 1024 : 0) | (message.link() != null ? 2048 : 0)
				| (message.image() != null ? 4096 : 0);
		buf.writeShortLE(flags);
		buf.writeBoolean(Boolean.TRUE.equals(message.bold()));
		buf.writeBoolean(Boolean.TRUE.equals(message.italic()));
		buf.writeBoolean(Boolean.TRUE.equals(message.monospace()));
		buf.writeBoolean(Boolean.TRUE.equals(message.underlined()));
		buf.writeBoolean(Boolean.TRUE.equals(message.strikethrough()));
		buf.writeBoolean(message.markupEnabled());
		var slots = buf.writerIndex();
		for (var i = 0; i < 8; i++)
			buf.writeIntLE(-1);
		var heap = buf.writerIndex();
		field(buf, slots, heap, 0, message.rawText(), value -> string(buf, value));
		field(buf, slots, heap, 1, message.messageId(), value -> string(buf, value));
		field(buf, slots, heap, 2, message.children(), children -> {
			count(buf, children.size());
			children.forEach(child -> write(buf, child, depth + 1));
		});
		field(buf, slots, heap, 3, message.params(), params -> {
			count(buf, params.size());
			params.forEach((key, value) -> {
				string(buf, key);
				writeParam(buf, value);
			});
		});
		field(buf, slots, heap, 4, message.messageParams(), params -> {
			count(buf, params.size());
			params.forEach((key, value) -> {
				string(buf, key);
				write(buf, value, depth + 1);
			});
		});
		field(buf, slots, heap, 5, message.color(), value -> string(buf, value));
		field(buf, slots, heap, 6, message.link(), value -> string(buf, value));
		field(buf, slots, heap, 7, message.image(), value -> {
			buf.writeIntLE(value.width());
			buf.writeIntLE(value.height());
			string(buf, value.filePath());
		});
	}

	private static <T> void field(ByteBuf buf, int slots, int heap, int index, T value, Consumer<T> writer) {
		if (value == null)
			return;
		buf.setIntLE(slots + index * 4, buf.writerIndex() - heap);
		writer.accept(value);
	}

	private static FormattedMessage read(ByteBuf buf, int depth) {
		checkDepth(depth);
		var flags = buf.readUnsignedShortLE();
		var bold = style(buf, flags, 1);
		var italic = style(buf, flags, 2);
		var monospace = style(buf, flags, 4);
		var underlined = style(buf, flags, 8);
		var strikethrough = style(buf, flags, 16);
		var markup = buf.readBoolean();
		var offsets = new int[8];
		for (var i = 0; i < offsets.length; i++)
			offsets[i] = buf.readIntLE();
		var heap = buf.readerIndex();
		var raw = field(buf, flags, 32, offsets[0], heap, FormattedMessageCodec::string);
		var key = field(buf, flags, 64, offsets[1], heap, FormattedMessageCodec::string);
		var children = field(buf, flags, 128, offsets[2], heap, bytes -> {
			var size = readCount(bytes, 40);
			var result = new ArrayList<FormattedMessage>(size);
			for (var i = 0; i < size; i++)
				result.add(read(bytes, depth + 1));
			return result;
		});
		var params = field(buf, flags, 256, offsets[3], heap, bytes -> {
			var size = readCount(bytes, 3);
			var result = new LinkedHashMap<String, MessageParam>();
			for (var i = 0; i < size; i++) {
				if (result.put(string(bytes), readParam(bytes)) != null)
					throw corrupt("Duplicate message parameter");
			}
			return result;
		});
		var messageParams = field(buf, flags, 512, offsets[4], heap, bytes -> {
			var size = readCount(bytes, 41);
			var result = new LinkedHashMap<String, FormattedMessage>();
			for (var i = 0; i < size; i++) {
				if (result.put(string(bytes), read(bytes, depth + 1)) != null)
					throw corrupt("Duplicate message parameter");
			}
			return result;
		});
		var color = field(buf, flags, 1024, offsets[5], heap, FormattedMessageCodec::string);
		var link = field(buf, flags, 2048, offsets[6], heap, FormattedMessageCodec::string);
		var image = field(buf, flags, 4096, offsets[7], heap, bytes -> {
			var width = bytes.readIntLE();
			var height = bytes.readIntLE();
			return new FormattedMessage.Image(string(bytes), width, height);
		});
		return new FormattedMessage(raw, key, children, params, messageParams, color, bold, italic, monospace,
				underlined, strikethrough, link, markup, image);
	}

	private static <T> T field(ByteBuf buf, int flags, int bit, int offset, int heap, Function<ByteBuf, T> reader) {
		var present = (flags & bit) != 0;
		if (offset != (present ? buf.readerIndex() - heap : -1))
			throw corrupt("Invalid message field offset");
		return present ? reader.apply(buf) : null;
	}

	private static Boolean style(ByteBuf buf, int flags, int bit) {
		var value = buf.readBoolean();
		return (flags & bit) != 0 ? value : null;
	}

	private static void writeParam(ByteBuf buf, MessageParam param) {
		switch (param) {
			case MessageParam.Text(var value) -> {
				VarInts.write(buf, 0);
				buf.writeByte(value == null ? 0 : 1);
				if (value != null)
					string(buf, value);
			}
			case MessageParam.Bool(var value) -> {
				VarInts.write(buf, 1);
				buf.writeBoolean(value);
			}
			case MessageParam.Decimal(var value) -> {
				VarInts.write(buf, 2);
				buf.writeDoubleLE(value);
			}
			case MessageParam.Int(var value) -> {
				VarInts.write(buf, 3);
				buf.writeIntLE(value);
			}
			case MessageParam.Long(var value) -> {
				VarInts.write(buf, 4);
				buf.writeLongLE(value);
			}
		}
	}

	private static MessageParam readParam(ByteBuf buf) {
		return switch (VarInts.read(buf)) {
			case 0 -> new MessageParam.Text((buf.readUnsignedByte() & 1) != 0 ? string(buf) : null);
			case 1 -> new MessageParam.Bool(buf.readBoolean());
			case 2 -> new MessageParam.Decimal(buf.readDoubleLE());
			case 3 -> new MessageParam.Int(buf.readIntLE());
			case 4 -> new MessageParam.Long(buf.readLongLE());
			default -> throw corrupt("Invalid message parameter type");
		};
	}

	private static String string(ByteBuf buf) {
		return NettyUtil.readVarString(buf, MAX_STRING_BYTES);
	}

	private static void string(ByteBuf buf, String value) {
		NettyUtil.writeVarString(buf, value, MAX_STRING_BYTES);
	}

	private static void count(ByteBuf buf, int count) {
		if (count > MAX_ENTRIES)
			throw corrupt("Too many message entries");
		VarInts.write(buf, count);
	}

	private static int readCount(ByteBuf buf, int minimumSize) {
		var count = VarInts.read(buf);
		if (count < 0 || count > MAX_ENTRIES || (long) count * minimumSize > buf.readableBytes()) {
			throw corrupt("Invalid message collection size");
		}
		return count;
	}

	private static void checkDepth(int depth) {
		if (depth > MAX_DEPTH)
			throw corrupt("Message nesting is too deep");
	}

	private static CorruptedFrameException corrupt(String reason) {
		return new CorruptedFrameException(reason);
	}

}
