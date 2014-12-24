package com.taobao.tao.atlaswrapper;

import java.io.File;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

public class Utils {
	
    final static String   TAG                = "Utils";
    
    /**
     * 指定Bundle包的处理顺序；程序首先按照这里的顺序来处理Bundle包，然后再乱序处理剩下的Bundle包。
     */
    final static String[] SORTED_PACKAGES = new String[]{"com.taobao.login4android", "com.taobao.taobao.home", "com.taobao.passivelocation", "com.taobao.mytaobao", "com.taobao.wangxin", "com.taobao.allspark", 
    	"com.taobao.search", "com.taobao.android.scancode", "com.taobao.android.trade", "com.taobao.taobao.cashdesk", "com.taobao.weapp", "com.taobao.taobao.alipay"};

    /**
     * 自动启动的bundle
     */
    final static String[] AUTOSTART_PACKAGES = new String[]{"com.taobao.login4android", "com.taobao.taobao.home", "com.taobao.mytaobao", "com.taobao.wangxin",
            "com.taobao.passivelocation", "com.taobao.allspark"};
    
    final static String[] DELAYED_PACKAGES = new String[]{
    	"com.taobao.fmagazine","com.taobao.taobao.pluginservice", "com.taobao.legacy", "com.ut.share",
    	"com.taobao.taobao.map", "com.taobao.android.gamecenter", "com.taobao.tongxue", "com.taobao.taobao.zxing", "com.taobao.labs"};
    
    public static String getFileNameFromEntryName(String entryName) {
        String fileName = entryName.substring(entryName.indexOf("lib/armeabi/") + "lib/armeabi/".length());
        return fileName;
    }
    
    public static String getPackageNameFromEntryName(String entryName) {
        String packageName = entryName.substring(entryName.indexOf("lib/armeabi/lib") + "lib/armeabi/lib".length(),
                                                 entryName.indexOf(".so"));
        packageName = packageName.replace("_", ".");
        return packageName;
    }   
    
    public static String getPackageNameFromSoName(String entryName) {
        String packageName = entryName.substring(entryName.indexOf("lib") + "lib".length(),
                                                 entryName.indexOf(".so"));
        packageName = packageName.replace("_", ".");
        return packageName;
    }       
    
    /**
     * 获取文件名"."前面的部分
     * @param fileName
     * @return
     */
    public static String getBaseFileName(String fileName){
        int pos = fileName.lastIndexOf(".");
        if (pos > 0) {
            fileName = fileName.substring(0, pos);
        }
        return fileName;
    }    
    
    public static PackageInfo getPackageInfo(Application mApplication){
    	PackageInfo packageInfo = null;
    	// 获取当前的版本号
        try {
            PackageManager packageManager = mApplication.getPackageManager();
            packageInfo = packageManager.getPackageInfo(mApplication.getPackageName(), 0);
        } catch (Exception e) {
            // 不可能发生
            Log.e(TAG, "Error to get PackageInfo >>>", e);
            packageInfo = new PackageInfo();
        }
        return packageInfo;
    }
    
    /**
     * 保存Atals启动、更新花费时间，欢迎页埋点用到这些数据，不要删除
     */
    public static void saveAtlasInfoBySharedPreferences(Application mApplication){
    	
        Map<String,String> atlasMap = new ConcurrentHashMap<String,String>();
        PackageInfo mPackageInfo = Utils.getPackageInfo(mApplication);
        atlasMap.put(mPackageInfo.versionName, "dexopt");
        
        SharedPreferences prefs = mApplication.getSharedPreferences("atlas_configs",
                                                                              Context.MODE_PRIVATE);
        if(prefs ==null){
        	prefs = mApplication.getSharedPreferences("atlas_configs", Context.MODE_PRIVATE);
        }
        Editor editor = prefs.edit();
        for(String entry : atlasMap.keySet()){
            editor.putString(entry,atlasMap.get(entry));
        }
        editor.commit();
    }    
    
    /*
     * Check whether bundle library exist under the directory
     */
    public static boolean searchFile(String directory, String keyword){
    	  if (directory == null || keyword == null){
    		  Log.e(TAG, "error in search File, direcoty or keyword is null");
    		  return false;
    	  }
    	  
    	  File fDir = new File(directory);
    	  if ((fDir == null) || (fDir.exists() == false)){
    		  Log.e(TAG, "error in search File, can not open directory " + directory);
    		  return false;
    	  }
    	   File[] files = new File(directory).listFiles(); 
    	   for (File file : files) { 
    	     if (file.getName().indexOf(keyword) >= 0) { 
    	    	 Log.i(TAG, "the file search success " + file.getName() + " keyword is " + keyword);
    	       return true;
    	     } 
    	   } 
    	Log.i(TAG, "the file search failed " + "directory is " + directory + " keyword is " + keyword);
    	return false;	
    }
}
