package com.taobao.tao.atlaswrapper;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.osgi.framework.Bundle;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.util.Log;
import android.widget.Toast;

public class BundlesInstaller {
	
    final static String   TAG                = "BundlesInstaller";
        
    private Application mApplication;
    private MiniPackage mMiniPackage;
	private PackageInfo mPackageInfo;
	AwbDebug mAwbDebug;  
	private boolean mIsInited;	
	private boolean mIsProcessed;
	
    // Flag to check whether current process is com.taobao.taobao
    private static boolean mIsTaobaoProcess;	
	
    private static BundlesInstaller uniqueInstance;
	
	BundlesInstaller(){
	}

	void init(Application mApplication, MiniPackage mMiniPackage,
			AwbDebug mAwbDebug, boolean mIsTaobaoProcess) {
		this.mApplication = mApplication;
		this.mMiniPackage = mMiniPackage;
		this.mAwbDebug = mAwbDebug;
		this.mIsTaobaoProcess = mIsTaobaoProcess;
		mPackageInfo = Utils.getPackageInfo(mApplication);
		mIsInited = true;
	}
	
	static synchronized BundlesInstaller getInstance(){
		if (uniqueInstance == null){
			uniqueInstance = new BundlesInstaller();
		}
		return uniqueInstance;
	}
	
	/*
	 * Make Bundle installation work just once executed by:
	 * (1) Make process function synchronized
	 * (2) Once enter, check whether executed already, if yes, just return
	 */
	public synchronized void  process(){

		// Never process once not initialized yet to avoid null exception
		if (!mIsInited){
			Log.e(TAG, "Bundle Installer not initialized yet, process abort!");
			return;
		} else if (mIsProcessed == true){
			Log.i(TAG, "Bundle install already executed, just return");
			return;
		}
		
        ZipFile zipFile = null;
        SharedPreferences prefs = null;
        try {
            zipFile = new ZipFile(mApplication.getApplicationInfo().sourceDir);

            final List<String> entryNames = getBundleEntryNames(zipFile, "lib/armeabi/libcom_", ".so");
			if(entryNames!=null && entryNames.size()>0){
            	if(getAvailableInternalMemorySize() < (entryNames.size()*2*1024*1024 )){
            		Handler h = new Handler(Looper.getMainLooper());
            		h.post(new Runnable(){
						@Override
						public void run() {
							Toast.makeText(RuntimeVariables.androidApplication, "检测到手机存储空间不足，为不影响您的使用请清理！", Toast.LENGTH_LONG).show();
						}
            			
            		});
            	}
            }
            processLibsBundles(zipFile, entryNames, mApplication);	                        

            UpdatePackageVersion();

        } catch (IOException e) {
            Log.e(TAG, "IOException while processLibsBundles >>>", e);
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        // Mark process flag as true to avoid bundle installation executed twice.
        mIsProcessed = true;
	}

	public void UpdatePackageVersion() {
		// Never process once not initialized yet to avoid null exception
		if (!mIsInited){
			Log.e(TAG, "Bundle Installer not initialized yet, process abort!");
			return;
		}
		
		SharedPreferences prefs;
		/*
		 *  For mini package, after upgrade, 1st install all bundles downloaded, then overide with those not supported bundles already installed.
		 */
		prefs = mApplication.getSharedPreferences("atlas_configs", Context.MODE_PRIVATE);	         
		mMiniPackage.process(prefs, mPackageInfo);
		
		Editor editor = prefs.edit();
		editor.putInt("last_version_code", mPackageInfo.versionCode);
		editor.putString("last_version_name", mPackageInfo.versionName);
		editor.putString(mPackageInfo.versionName, "dexopt");
		
		editor.commit();
	}	
    
    private List<String> getBundleEntryNames(ZipFile zipFile, String prefix, String suffix) {
        List<String> entryNames = new ArrayList<String>();
        try {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                String entryName = zipEntry.getName();
                if (entryName.startsWith(prefix) && entryName.endsWith(suffix)) {
                    entryNames.add(entryName);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Exception while get bundles in assets or lib", e);
        }
        return entryNames;
    }
    
	private long getAvailableInternalMemorySize(){  
        File path = Environment.getDataDirectory();    
        StatFs stat = new StatFs(path.getPath());  
        long blockSize = stat.getBlockSize();  
        long availableBlocks = stat.getAvailableBlocks();  
        return availableBlocks*blockSize;  
    }
	
    private void processLibsBundles(ZipFile zipFile, List<String> entryNames, Application mApplication) {
        // 首先按照预先设定的顺序处理Bundle安装包
        for (int i = 0; i < Utils.SORTED_PACKAGES.length; i++) {
            String pkg = Utils.SORTED_PACKAGES[i].replace(".", "_");
            String entryName = filterEntryName(entryNames, pkg);
            if (entryName != null && entryName.length() > 0) {
                processLibsBundle(zipFile, entryName, mApplication);
                entryNames.remove(entryName);
            }
        }
        // 处理剩下的Bundle包
        for (String entryName : entryNames) {
            processLibsBundle(zipFile, entryName, mApplication);
        }
        // 根据需要自动启动Bundle
        if (mIsTaobaoProcess){
	        for (String pkg :Utils.AUTOSTART_PACKAGES) {
	        	Bundle bundle = Atlas.getInstance().getBundle(pkg);
	            if (bundle != null) {
	                try {
	                    bundle.start();
	                } catch (Exception e) {
	                    Log.e(TAG, "Could not auto start bundle: " + bundle.getLocation(), e);
	                }
	            }
	        }
        }
    }    
    
    private String filterEntryName(List<String> entryNames, String packageName) {
        if (entryNames == null || packageName == null) {
            return null;
        }
        for (String fileName : entryNames) {
            if (fileName.contains(packageName)) {
                return fileName;
            }
        }
        return null;
    }    
    
    
    private boolean processLibsBundle(ZipFile zipFile, String entryName, Application mApplication) {
        Log.d(TAG,"processLibsBundle entryName " + entryName);

        mAwbDebug.process(entryName);
        
        String fileName = Utils.getFileNameFromEntryName(entryName);
        String packageName = Utils.getPackageNameFromEntryName(entryName);
        
        if (packageName == null || packageName.length() <= 0){
        	return false;
        }

        File libDir = new File(mApplication.getFilesDir().getParentFile(), "lib");
        File soFile = new File(libDir, fileName);

        Bundle bundle = Atlas.getInstance().getBundle(packageName);
        if (bundle == null) {
            // 由于需要更新的bundle已经在容器启动时删除了，这里只要安装bundle就好了
            try {
                InputStream inputStream = null;
                if (soFile.exists()) {
                    bundle = Atlas.getInstance().installBundle(packageName, soFile);
                } else {
                    inputStream = zipFile.getInputStream(zipFile.getEntry(entryName));
                    bundle = Atlas.getInstance().installBundle(packageName, inputStream);
                }
                Log.d(TAG, "Succeed to install bundle " + packageName);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Could not install bundle.", e);
            }
        }

        return false;
    }    
}
