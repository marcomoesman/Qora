package webserver;

import java.util.Collections;
import java.util.Map;

import database.QoraDb;

/**
 * Used to have read only access on namestoragemap for pebble and websites
 * @author Skerberus
 *
 */
public class NameStorageWebResource {
	
private static NameStorageWebResource instance;
	
	public static NameStorageWebResource getInstance()
	{
		if ( instance == null )
		{
			instance = new NameStorageWebResource();
		}
		return instance;
	}
	
	public String getOpt(String name, String key) {
		return QoraDb.getInstance().getNameStorageMap().getOpt(name, key);
	}
	
	public Map<String, String> getOpt(String name)
	{
		Map<String, String> map = QoraDb.getInstance().getNameStorageMap().get(name);
		return  map != null ? Collections.unmodifiableMap(map) : null;
	}

}
