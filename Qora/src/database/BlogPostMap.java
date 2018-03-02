package database;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.mapdb.DB;
import org.mapdb.DB.BTreeMapMaker;

import utils.ByteArrayUtils;

public class BlogPostMap extends DBListValueMap<String, byte[], List<byte[]>> {
	private Map<Integer, Integer> observableData = new HashMap<Integer, Integer>();

	public final static String MAINBLOG = "QORA";

	public BlogPostMap(DBSet databaseSet, DB database) {
		super(databaseSet, database);
	}

	public BlogPostMap(DBMap<String, List<byte[]>> parent) {
		super(parent);
	}

	@Override
	protected Map<String, List<byte[]>> getMap(DB database) {
		// Open map
		BTreeMapMaker createTreeMap = database.createTreeMap("BlogPostMap");
		return createTreeMap.makeOrGet();
	}

	@Override
	protected Map<String, List<byte[]>> getMemoryMap() {
		return new HashMap<>();
	}

	@Override
	protected Map<Integer, Integer> getObservableData() {
		return this.observableData;
	}

	@Override
	protected void createIndexes(DB database) {
	}

	@Override
	protected List<byte[]> getDefaultValue() {
		return null;
	}

	@Override
	protected List<byte[]> newListValue() {
		return new ArrayList<byte[]>();
	}

	public void add(String blogname, byte[] signature) {
		if (blogname == null)
			blogname = MAINBLOG;

		// Add signature to list if list doesn't already contain it
		this.listAdd(blogname, signature, (list, sig) -> !ByteArrayUtils.contains(list, sig), (list, sig) -> list.add(sig));
	}

	public void remove(String blogname, byte[] signature) {
		if (blogname == null)
			blogname = MAINBLOG;

		// Always remove signature from list
		this.listRemove(blogname, signature, (list, sig) -> true, (list, sig) -> ByteArrayUtils.remove(list, sig));
	}
}