package network.message;

import java.io.DataInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import com.google.common.primitives.Ints;

import lang.Lang;
import network.Peer;
import qora.block.Block;
import qora.crypto.Crypto;
import qora.transaction.Transaction;

public final class MessageFactory {

	private static MessageFactory instance;

	public static MessageFactory getInstance() {
		if (instance == null) {
			instance = new MessageFactory();
		}

		return instance;
	}

	public Message createPingMessage() {
		return new Message(Message.PING_TYPE);
	}

	public Message createGetPeersMessage() {
		return new Message(Message.GET_PEERS_TYPE);
	}

	public Message createPeersMessage(final List<Peer> peers) {
		return new PeersMessage(peers);
	}

	public Message createHeightMessage(final int height) {
		return new HeightMessage(height);
	}

	public Message createVersionMessage(final String strVersion, final long buildDateTime) {
		return new VersionMessage(strVersion, buildDateTime);
	}

	public Message createFindMyselfMessage(final byte[] foundMyselfID) {
		return new FindMyselfMessage(foundMyselfID);
	}

	public Message createGetHeadersMessage(final byte[] parent) {
		return new GetSignaturesMessage(parent);
	}

	public Message createHeadersMessage(final List<byte[]> headers) {
		return new SignaturesMessage(headers);
	}

	public Message createGetBlockMessage(final byte[] header) {
		return new GetBlockMessage(header);
	}

	public Message createBlockMessage(final Block block) {
		return new BlockMessage(block);
	}

	public Message createTransactionMessage(final Transaction transaction) {
		return new TransactionMessage(transaction);
	}

	public Message parse(final Peer sender, final DataInputStream inputStream) throws Exception {
		// READ MESSAGE TYPE
		final byte[] typeBytes = new byte[Message.TYPE_LENGTH];
		inputStream.readFully(typeBytes);
		final int type = Ints.fromByteArray(typeBytes);

		// READ HAS ID
		final int hasId = inputStream.read();
		int id = -1;

		if (hasId == 1) {
			// READ ID
			final byte[] idBytes = new byte[Message.ID_LENGTH];
			inputStream.readFully(idBytes);
			id = Ints.fromByteArray(idBytes);
		}

		// READ LENGTH
		final int length = inputStream.readInt();

		// IF MESSAGE CONTAINS DATA READ DATA AND VALIDATE CHECKSUM
		final byte[] data = new byte[length];
		if (length > 0) {
			// READ CHECKSUM
			final byte[] checksum = new byte[Message.CHECKSUM_LENGTH];
			inputStream.readFully(checksum);

			// READ DATA
			inputStream.readFully(data);

			// VALIDATE CHECKSUM
			byte[] digest = Crypto.getInstance().digest(data);

			// TAKE FOR FIRST BYTES
			digest = Arrays.copyOfRange(digest, 0, Message.CHECKSUM_LENGTH);

			// CHECK IF CHECKSUM MATCHES
			if (!Arrays.equals(checksum, digest)) {
				throw new MessageException(Lang.getInstance().translate("Invalid data checksum length=") + length);
			}
		}

		Message message = null;

		switch (type) {
		// PING
		case Message.PING_TYPE:

			message = new Message(type);
			break;

		// GETPEERS
		case Message.GET_PEERS_TYPE:

			message = new Message(type);
			break;

		// PEERS
		case Message.PEERS_TYPE:

			// CREATE MESSAGE FROM DATA
			message = PeersMessage.parse(data);
			break;

		// HEIGHT
		case Message.HEIGHT_TYPE:

			// CREATE HEIGHT FROM DATA
			message = HeightMessage.parse(data);
			break;

		// GETSIGNATURES
		case Message.GET_SIGNATURES_TYPE:

			// CREATE MESSAGE FROM DATA
			message = GetSignaturesMessage.parse(data);
			break;

		// SIGNATURES
		case Message.SIGNATURES_TYPE:

			// CREATE MESSAGE FROM DATA
			message = SignaturesMessage.parse(data);
			break;

		// GETBLOCK
		case Message.GET_BLOCK_TYPE:

			// CREATE MESSAGE FROM DATA
			message = GetBlockMessage.parse(data);
			break;

		// BLOCK
		case Message.BLOCK_TYPE:

			// CREATE MESSAGE FROM DATA
			message = BlockMessage.parse(data);
			break;

		// TRANSACTION
		case Message.TRANSACTION_TYPE:

			// CREATE MESSAGE FRO MDATA
			message = TransactionMessage.parse(data);
			break;

		// VERSION
		case Message.VERSION_TYPE:

			// CREATE MESSAGE FROM DATA
			message = VersionMessage.parse(data);
			break;

		// FIND_MYSELF
		case Message.FIND_MYSELF_TYPE:

			// CREATE MESSAGE FROM DATA
			message = FindMyselfMessage.parse(data);
			break;

		default:

			// UNKNOWN MESSAGE
			Logger.getGlobal().info(Lang.getInstance().translate("Received unknown type message!"));
			return new Message(type);

		}

		// SET SENDER
		message.setSender(sender);

		// SET ID
		if (hasId == 1) {
			message.setId(id);
		}

		// RETURN
		return message;
	}
}
