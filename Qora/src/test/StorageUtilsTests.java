package test;

import static org.junit.Assert.*;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.Test;

import com.google.common.base.Charsets;

import database.DBSet;
import qora.crypto.Base58;
import utils.GZIP;
import utils.StorageUtils;

public class StorageUtilsTests {

	@Test
	public void testJsonKeys() {
		// Transaction: 752vp1Zr18jm1Xq35DyRGWs73WFya9ibEGTHqZEBicsFdoqyVspuoMBewiThUZX8LPgMTXqZZnTDaG8AoKxfNsa
		// Creator: QMcfbBpyGsMdXpkkMLhKv79yyF9DNeN1u7
		// Height: 179707
		// Base58: 25tJtm3KBamRSHX4NU81QWHwdp9BuwSCZv69E23xc77FuQisLREcSB8u56HutQLWGEf6TcqT7HKLpR5QapP1jHKkjujHMiXvXmqMNvQZEud3XAGF1Rkj3B7W3Y47WAxEHbAe6z6Y
		// Payload: {"addcomplete":{"test.txt":"this is a test text file"},"name":"QMcfbBpyGsMdXpkkMLhKv79yyF9DNeN1u7"}

		// NB: the JSON value for "addcomplete" is itself a JSON object and not a string (which is typical) - see issue #57

		byte[] data = Base58.decode(
				"25tJtm3KBamRSHX4NU81QWHwdp9BuwSCZv69E23xc77FuQisLREcSB8u56HutQLWGEf6TcqT7HKLpR5QapP1jHKkjujHMiXvXmqMNvQZEud3XAGF1Rkj3B7W3Y47WAxEHbAe6z6Y");

		// From StorageUtils.processUpdate():
		String payload = new String(data, Charsets.UTF_8);
		payload = GZIP.webDecompress(payload);
		JSONObject jsonObject = (JSONObject) JSONValue.parse(payload);

		DBSet db = DBSet.createEmptyDatabaseSet();
		String name = "QMcfbBpyGsMdXpkkMLhKv79yyF9DNeN1u7";

		try {
			StorageUtils.addTxChangesToStorage(jsonObject, name, db.getNameStorageMap(), null);
		} catch (Exception e) {
			fail("name-storage data wasn't extracted correctly");
		}
	}
}
