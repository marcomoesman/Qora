package database.serializer;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.Serializable;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.mapdb.Serializer;

import qora.transaction.Transaction;
import qora.transaction.TransactionFactory;

public final class TransactionSerializer implements Serializer<Transaction>, Serializable {
	
	private static final Logger LOGGER = LogManager.getLogger(TransactionSerializer.class);
	private static final long serialVersionUID = -6538913048331349777L;

	@Override
	public void serialize(final DataOutput out, final Transaction value) throws IOException {
		out.writeInt(value.getDataLength());
		out.write(value.toBytes());
	}

	@Override
	public Transaction deserialize(DataInput in, int available) throws IOException {
		final int length = in.readInt();
		final byte[] bytes = new byte[length];
		in.readFully(bytes);
		
		try {
			return TransactionFactory.getInstance().parse(bytes);
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
