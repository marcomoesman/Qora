package database.serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mapdb.Serializer;

import at.AT;

public final class ATSerializer implements Serializer<AT>, Serializable {

	private static final Logger LOGGER = LogManager.getLogger(ATSerializer.class);
	private static final long serialVersionUID = -6538913048331349777L;

	@Override
	public void serialize(final DataOutput out, final AT value) throws IOException {
		out.writeInt(value.getCreationLength());
		out.write(value.toBytes(true));
	}

	@Override
	public AT deserialize(final DataInput in, final int available) throws IOException {
		final int length = in.readInt();
		final byte[] bytes = new byte[length];
		in.readFully(bytes);
		
		try {
			return AT.parse(bytes);
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
