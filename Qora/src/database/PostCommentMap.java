package database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapdb.DB;

import utils.ByteArrayUtils;

import com.google.common.primitives.SignedBytes;

/**
 * Get all comments for a blogpost!
 * 
 * @author Skerberus
 *
 */
public class PostCommentMap extends DBListValueMap<byte[], byte[], List<byte[]>> {
	private Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	public PostCommentMap(DBSet databaseSet, DB database) {
		super(databaseSet, database);
	}

	public PostCommentMap(DBMap<byte[], List<byte[]>> parent) {
		super(parent);
	}

	@Override
	protected Map<byte[], List<byte[]>> getMap(DB database) {
		return database.createTreeMap("CommentPostMap").comparator(SignedBytes.lexicographicalComparator()).makeOrGet();
	}

	@Override
	protected Map<byte[], List<byte[]>> getMemoryMap() {
		return new HashMap<>();
	}

	@Override
	protected List<byte[]> getDefaultValue() {
		return null;
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	@Override
	protected void createIndexes(DB database) {
	}

	@Override
	protected List<byte[]> newListValue() {
		return new ArrayList<byte[]>();
	}

	public void add(byte[] signatureOfPostToComment, byte[] signatureOfComment) {
		// Add signature to list if list doesn't already contain it
		this.listAdd(signatureOfPostToComment, signatureOfComment, (list, sig) -> !ByteArrayUtils.contains(list, sig), (list, sig) -> list.add(sig)); 
	}

	public void remove(byte[] signatureOfPost, byte[] signatureOfComment) {
		// Always remove signature from list
		this.listRemove(signatureOfPost, signatureOfComment, (list, sig) -> true, (list, sig) -> ByteArrayUtils.remove(list, sig));
	}
}