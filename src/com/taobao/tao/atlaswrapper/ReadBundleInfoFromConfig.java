package com.taobao.tao.atlaswrapper;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import android.content.Context;
import android.util.Log;

public class ReadBundleInfoFromConfig {

	//TODO: need remove the file once AndroidManifest is ready.
	private final static String TAG = "ReadConfig";
	
	Context mContext;
	Map<String,String> mMap;
	
	public ReadBundleInfoFromConfig(Context mContext) {
		this.mContext = mContext;
	}

	void process(String BundleInfoFile){
		
		if (BundleInfoFile == null)
			return;
		
	    try {
	    	mMap = new HashMap<String,String>();
	    	readConfigFileToMap(mMap, BundleInfoFile);
	    	printMap(mMap);
		    } catch (Exception e1) {
		        e1.printStackTrace();
		    }
    }   
    
	public Map<String,String> getBundleInfoMap(){
		return mMap;
	}
	/*
	 * Read the configuration property file under raw/ directory and convert to a Map<String, String>
	 */
	private final Map<String,String> 
	readConfigFileToMap(Map<String,String> map, String BundleInfo) throws Exception{
		if(map == null){
			map = new HashMap<String,String>();
		}
	    Properties props = new Properties();
    	int id = mContext.getResources().getIdentifier(BundleInfo, "raw", mContext.getPackageName());
	    props.load(mContext.getResources().openRawResource(id));
		Set<Entry<Object, Object>> entrySet = props.entrySet();
		for (Entry<Object, Object> entry : entrySet) {
		    String libName = ((String)(entry.getValue())).trim();
			map.put(((String) entry.getKey()).trim(), libName);
		}
		return map;
	}
	
	private final void printMap(Map<String,String> map){
		Set<Entry<String, String>> entrySet = map.entrySet();
		for (Entry<String, String> entry : entrySet) {
            Log.i(TAG, "key   :" + entry.getKey());  
            Log.i(TAG, "value :" + entry.getValue());  
            Log.i(TAG, "---------------");  
		}
	}
	
}
