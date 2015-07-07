package com.atlas.application;

import java.io.File;
import java.util.ArrayList;

import org.osgi.framework.Bundle;
import android.os.Environment;
import android.taobao.atlas.framework.Atlas;
import android.util.Log;
import com.taobao.taobaocompat.BuildConfig;

class AwbDebug {
	
    final static String   TAG                = "AwbDebug";
    private boolean awbDebug = false;
    boolean supportExternalAwbDebug = false;
    private ArrayList<String> awbFilePathForDebug = new ArrayList<String>();
    private final String EXTERNAL_DIR_FOR_DEUBG_AWB = Environment.getExternalStorageDirectory().getAbsolutePath()+"/awb-debug";
    
	public AwbDebug(){
	    awbDebug = BuildConfig.DEBUG ? true : false;
	}
    
	public boolean checkExternalAwbFile(){
		
	    if(!awbDebug)
	    	return false;
	    
        File dir = new File(EXTERNAL_DIR_FOR_DEUBG_AWB);
        
        if(dir.isDirectory()){
            File[] files = dir.listFiles();
            for(File file : files){
                if(file.isFile() && file.getName().endsWith(".so")){
                    awbFilePathForDebug.add(file.getAbsolutePath());
                    Log.d(TAG,"found external awb "+file.getAbsolutePath());
                    supportExternalAwbDebug = true;
                    continue;
                }
            }
        }
        
        return supportExternalAwbDebug;
	}
	
	public boolean process(String entryName){
	    if(!awbDebug || awbFilePathForDebug.size()<=0)
	    	return false;
	    
        for(String filePath : awbFilePathForDebug){        	
            Log.d(TAG,"processLibsBundle filePath " + filePath);
            if(filePath.contains(Utils.getFileNameFromEntryName(entryName).substring(3))){
                File soFile = new File(filePath);
                String fileName = soFile.getName();
                String packageName = Utils.getBaseFileName(fileName).replace("_",".");
                Bundle bundle = Atlas.getInstance().getBundle(packageName);
                if(bundle==null){
                    try {
                        bundle = Atlas.getInstance().installBundle(packageName, soFile);
                    }catch(Throwable e){
                        Log.e(TAG, "Could not install external bundle.", e);
                    }
                    Log.d(TAG, "Succeed to install external bundle " + packageName);
                }
                soFile.delete();
                return true;
            }            
        }

        return false;
	}
}
