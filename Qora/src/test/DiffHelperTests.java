package test;

import static org.junit.Assert.*;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.junit.Test;

import com.google.common.base.Charsets;

import qora.crypto.Base58;
import utils.DiffHelper;
import utils.GZIP;

public class DiffHelperTests {

	@Test
	public void testDiffs() throws Exception {

		String source = "skerberus\nvbcs\n" + "\uAA75" + "\uBCFA" + "\u5902" + "\u2ed8";
		String destination = "skerberus\nvrontis\nvbcs\n" + "\uAA75" + "\uBCFA" + "\u5902" + "\u2ed8";
		String diff = DiffHelper.getDiff(source, destination);

		assertEquals(destination, DiffHelper.patch(source, diff));

		destination = "skerberus";

		diff = DiffHelper.getDiff(source, destination);

		assertEquals(destination, DiffHelper.patch(source, diff));

		destination = "\uAA75" + "\uBCFA" + "\u5902" + "\u2ed8" + "asdf\nwayne";

		diff = DiffHelper.getDiff(source, destination);

		assertEquals(destination, DiffHelper.patch(source, diff));

		destination = "\uAA75" + "\uBCFA" + "\u5902" + "\u2ed8" + "asdf\nwayne\n \na\ne\ndffdkf";

		diff = DiffHelper.getDiff(source, destination);

		assertEquals(destination, DiffHelper.patch(source, diff));
	}

	private static JSONObject getDataByKey(JSONObject jsonObject, String mainKey) {
		Object jsonData = jsonObject.get(mainKey);

		if (jsonData == null)
			return null;

		// remove string encapsulation
		if (jsonData instanceof String)
			jsonData = JSONValue.parse((String) jsonData);

		// must be JSON object now
		if (!(jsonData instanceof JSONObject)) {
			fail("Expecting JSONObject while looking for \"" + mainKey + "\" data");
			return null;
		}

		return (JSONObject) jsonData;
	}

	@Test
	public void testNameStoragePatch() throws Exception {
		/*
		 * This test inspired by errors in logfiles like "Invalid patch!" (pre v0.26.4) or "Invalid name storage patch" (v0.26.4+).
		 * 
		 * Block: 151633
		 * Transaction #1 sets initial storage: 5ZnAH4ZC4r7Av5Z2pVWhe9s5gdrUaGXLAnq9oHiFDY4pWdvDmUi8NeyUwL639GJCtPHVF6JUYP31LQkRyMotKyQq
		 * Data: ?gz!H4sIAAAAAAAAAKtWSkxJSc7PLchJLUlVslKqjlEqSS0uiVGyilF6tXLr6827ni5perR7RmJxSlpMTB66EBFKqKZrAISUapV0lPISc0EhY5SXAgoZpVoAgS1blzMBAAA=
		 * 
		 * Transaction #2 attempts patch: dfJJfaPzmuApa1kHmZ9EznzjCvWar9LGZeUwqfACR3mHeR1isTenWyAnx9PcRvdfg9z22XBJWryPE76RiWjKpf3
		 * Data: {"patch":"{\"test\":\"--- source\\n+++ dest\\n@@ -5,0 +5,1 @@\\n+ꩵ볺夂⻘asdf300\\n@@ -12,1 +13,0 @@\\n-ꩵ볺夂⻘asdf\"}","name":"2ndtest"}
		 */

		// Decoded initial data:
		final String prePatch = "ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdfꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdfꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf";

		// Post-patch data:
		final String postPatch = "ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdfꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdfꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf300\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf\n" +
								"ꩵ볺夂⻘asdf";

		// Transaction #1
		String dataAsString = "?gz!H4sIAAAAAAAAAKtWSkxJSc7PLchJLUlVslKqjlEqSS0uiVGyilF6tXLr6827ni5perR7RmJxSlpMTB66EBFKqKZrAISUapV0lPISc0EhY5SXAgoZpVoAgS1blzMBAAA=";
		dataAsString = GZIP.webDecompress(dataAsString);

		JSONObject jsonObject = (JSONObject) JSONValue.parse(dataAsString);
		assertNotNull(jsonObject);

		JSONObject addCompleteObject = getDataByKey(jsonObject, "addcomplete");
		String source = (String) addCompleteObject.get("test");

		assertEquals("Pre-patch source data wasn't extracted correctly", prePatch, source);

		// Transaction #2
		String patch58 = "3JnyJhPwbTsHrh7zMXxhc6S7kAE2c8HZE9CtwE5yFxPdUHHy2vnKGwcRkqJ7Cppas9B8q6" +
						"VDcV5TwY4j21QVukUdEf8ynsFS6gCNDZoFHGm6t7C8WWB7HiuYetTRoTHoa62QQQQDfiLt" +
						"5X7HGjKmrU7wmQXrieAZpz6Dd2ZgFUTLoAbXXkwmRWC1h3WaDBPX36nRmhhE";

		byte[] patchData = Base58.decode(patch58);
		String patchAsString = new String(patchData, Charsets.UTF_8);
		patchAsString = GZIP.webDecompress(patchAsString);

		JSONObject patchJsonObject = (JSONObject) JSONValue.parse(patchAsString);
		assertNotNull(patchJsonObject);

		JSONObject patchObject = getDataByKey(patchJsonObject, "patch");
		String patch = (String) patchObject.get("test");

		// Apply patch
		String output = DiffHelper.patch(source,  patch);

		assertNotEquals("Output should not be empty!", "", output);
		assertNotEquals("Output wasn't patched", prePatch, output);

		// This will fail if using the broken difflib-1.3.0
		// due to off-by-one bug when generating/applying unified diffs
		assertEquals("Output incorrectly patched", postPatch, output);
	}

	@Test
	public void testSimpleInsertPatch() throws Exception {
		final String initialData =	"1\n" + 
									"2\n" + 
									"3\n" + 
									"4\n" + 
									"5\n" + 
									"6\n" + 
									"7\n" + 
									"8\n";

		final String patch =	"--- source\n" + 
								"+++ dest\n" +
								"@@ -5,0 +5,1 @@\n" +
								"+NEW";

		final String output = DiffHelper.patch(initialData, patch);

		final String expected =	"1\n" +
								"2\n" + 
								"3\n" + 
								"4\n" + 
								"5\n" + 
								"NEW\n"+ 
								"6\n" + 
								"7\n" + 
								"8\n";

		// This will fail if using the broken difflib-1.3.0
		// due to off-by-one bug when generating/applying unified diffs
		assertEquals("Incorrectly patched", expected, output);
	}
}
