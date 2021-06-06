package utils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;

public final class SaveStrToFile {

	public static void save(String path, String str) throws IOException {
		FileUtils.writeStringToFile(new File(path), str, StandardCharsets.UTF_8, false);
	}

	public static void saveJsonFine(String path, JSONObject json) throws IOException {
		save(path, StrJSonFine.convert(json));
	}
}
