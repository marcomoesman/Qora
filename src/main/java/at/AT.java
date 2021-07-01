package at;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;

import org.json.simple.JSONObject;

import database.QoraDb;
import qora.account.Account;
import qora.crypto.Base58;

public final class AT extends AT_Machine_State {

	private final String name;
	private final String description;
	private final String type;
	private final String tags;

	public AT(final byte[] atId, final byte[] creator, final String name, final String description, final String type,
			final String tags, final byte[] creationBytes, final int height) {
		super(atId, creator, creationBytes, height);
		this.name = name;
		this.description = description;
		this.type = type;
		this.tags = tags;
	}

	public AT(final byte[] atId, final byte[] creator, final String name, final String description, final String type,
			final String tags, final short version, final byte[] stateBytes, final int csize, final int dsize,
			final int c_user_stack_bytes, final int c_call_stack_bytes, final long minActivationAmount,
			final int creationBlockHeight, final int sleepBetween, final byte[] apCode) {
		super(atId, creator, version, stateBytes, csize, dsize, c_user_stack_bytes, c_call_stack_bytes,
				creationBlockHeight, sleepBetween, minActivationAmount, apCode);
		this.name = name;
		this.description = description;
		this.type = type;
		this.tags = tags;
	}

	public static AT getAT(final String id, final QoraDb dbSet) {
		return getAT(Base58.decode(id), dbSet);
	}

	public static AT getAT(final byte[] atId, final QoraDb dbSet) {
		final AT at = dbSet.getATMap().getAT(atId);
		return at;
	}

	public static AT getAT(final byte[] atId) {
		return getAT(atId, QoraDb.getInstance());
	}

	public static Iterator<String> getOrderedATs(final QoraDb dbSet, final Integer height) {
		return dbSet.getATMap().getOrderedATs(height);
	}

	// public int getDataLength() {
	// return name.length() + description.length() + this.getStateSize();
	// }

	public byte[] toBytes(final boolean b) {
		final byte[] bname = getName().getBytes(StandardCharsets.UTF_8);
		final byte[] bdesc = description.getBytes(StandardCharsets.UTF_8);
		final byte[] btype = type.getBytes(StandardCharsets.UTF_8);
		final byte[] btags = tags.getBytes(StandardCharsets.UTF_8);

		final ByteBuffer bf = ByteBuffer
				.allocate(4 + bname.length + 4 + bdesc.length + getSize() + 4 + btype.length + 4 + btags.length);
		bf.order(ByteOrder.LITTLE_ENDIAN);

		bf.putInt(bname.length);
		bf.put(bname);

		bf.putInt(bdesc.length);
		bf.put(bdesc);

		bf.putInt(btype.length);
		bf.put(btype);

		bf.putInt(btags.length);
		bf.put(btags);

		bf.put(getBytes());
		return bf.array();
	}

	public int getCreationLength() {
		final byte[] bname = getName().getBytes(StandardCharsets.UTF_8);
		final byte[] bdesc = description.getBytes(StandardCharsets.UTF_8);
		final byte[] btype = type.getBytes(StandardCharsets.UTF_8);
		final byte[] btags = tags.getBytes(StandardCharsets.UTF_8);
		return 4 + bname.length + 4 + bdesc.length + 4 + btype.length + 4 + btags.length + getSize();
	}

	public static AT parse(final byte[] bytes) {
		final ByteBuffer bf = ByteBuffer.allocate(bytes.length);
		bf.order(ByteOrder.LITTLE_ENDIAN);
		bf.put(bytes);
		bf.clear();

		final int nameSize = bf.getInt();
		final byte[] bname = new byte[nameSize];
		bf.get(bname);
		final String name = new String(bname, StandardCharsets.UTF_8);

		final int descSize = bf.getInt();
		final byte[] bdesc = new byte[descSize];
		bf.get(bdesc);
		final String description = new String(bdesc, StandardCharsets.UTF_8);

		final int typeSize = bf.getInt();
		final byte[] btype = new byte[typeSize];
		bf.get(btype);
		final String type = new String(btype, StandardCharsets.UTF_8);

		final int tagsSize = bf.getInt();
		final byte[] btags = new byte[tagsSize];
		bf.get(btags);
		final String tags = new String(btags, StandardCharsets.UTF_8);

		final byte[] atId = new byte[AT_Constants.AT_ID_SIZE];
		bf.get(atId);

		final byte[] creator = new byte[AT_Constants.AT_ID_SIZE];
		bf.get(creator);

		final short version = bf.getShort();
		final int csize = bf.getInt();
		final int dsize = bf.getInt();
		final int c_call_stack_bytes = bf.getInt();
		final int c_user_stack_bytes = bf.getInt();
		final long minActivationAmount = bf.getLong();
		final int creationBlockHeight = bf.getInt();
		final int sleepBetween = bf.getInt();

		final byte[] ap_code = new byte[csize];
		bf.get(ap_code);

		final byte[] state = new byte[bf.capacity() - bf.position()];
		bf.get(state);

		final AT at = new AT(atId, creator, name, description, type, tags, version, state, csize, dsize,
				c_user_stack_bytes, c_call_stack_bytes, minActivationAmount, creationBlockHeight, sleepBetween,
				ap_code);
		return at;
	}

	@SuppressWarnings("unchecked")
	public JSONObject toJSON() {
		JSONObject atJSON = new JSONObject();
		atJSON.put("accountBalance", new Account(Base58.encode(getId())).getConfirmedBalance().toPlainString());
		atJSON.put("name", this.name);
		atJSON.put("description", description);
		atJSON.put("type", type);
		atJSON.put("tags", tags);
		atJSON.put("version", getVersion());
		atJSON.put("minActivation", BigDecimal.valueOf(minActivationAmount(), 8).toPlainString());
		atJSON.put("creationBlock", getCreationBlockHeight());
		atJSON.put("state", getStateJSON());
		atJSON.put("creator", new Account(Base58.encode(getCreator())).getAddress());
		return atJSON;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return this.description;
	}

	public String getType() {
		return type;
	}

	public String getTags() {
		return tags;
	}

}
