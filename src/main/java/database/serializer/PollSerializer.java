package database.serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mapdb.Serializer;

import qora.voting.Poll;

public final class PollSerializer implements Serializer<Poll>, Serializable {

	private static final Logger LOGGER = LogManager.getLogger(PollSerializer.class);
	private static final long serialVersionUID = -6538913048331349777L;

	@Override
	public void serialize(final DataOutput out, final Poll value) throws IOException {
		out.writeInt(value.getDataLength());
		out.write(value.toBytes());
	}

	@Override
	public Poll deserialize(final DataInput in, final int available) throws IOException {
		final int length = in.readInt();
		final byte[] bytes = new byte[length];
		in.readFully(bytes);
		
		try {
			return Poll.parse(bytes);
		} catch (Exception e) {
			LOGGER.error(e.getMessage(), e);
		}
		return null;
	}

	@Override
	public int fixedSize() {
		return -1;
	}
}
