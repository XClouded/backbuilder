package com.taobao.tao.atlaswrapper;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;

import android.annotation.TargetApi;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources.NotFoundException;
import android.taobao.atlas.bundleInfo.BundleInfoList;
import android.os.Build;
import android.util.Log;
import com.taobao.lightapk.*;

public class BundleInfoCollection {
    private final static boolean DEBUG = true;
    private final String TAG = "BundleInfoManager";        
    private Context mContext;
    private BundleInfoList mBundleInfoList;
    BundleInfoCollection(Context mContext) {	
    	this.mContext = mContext;
    	mBundleInfoList = BundleInfoList.getInstance();
	}
	
	public void generateBundleInfos(){
		parseFromBundleLite();
		parseFromBundleInfoManager();
	}
	
	@TargetApi(Build.VERSION_CODES.GINGERBREAD)
	private boolean parseFromBundleLite(){
		PackageManager pm = mContext.getPackageManager();
		try {
			// Get Activity info
			PackageInfo mPackageInfo = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_ACTIVITIES);
			if (mPackageInfo.activities != null)
			for (int i = 0; i < mPackageInfo.activities.length; i++){
				String name = mPackageInfo.activities[i].name;
				ComponentName componentName = new ComponentName(mContext, name);
				ActivityInfo info = pm.getActivityInfo(componentName, PackageManager.GET_META_DATA);
				if (info != null && info.metaData != null){
					String bundleName = info.metaData.getString("bundleLocation");
					
					if (bundleName != null){
						mBundleInfoList.insertComponent(name, bundleName);							
					}
				}
			}
			
			// Get Service info			
			mPackageInfo = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_SERVICES);
			if (mPackageInfo.services != null)
			for (int i = 0; i < mPackageInfo.services.length; i++){
				String name = mPackageInfo.services[i].name;
				ComponentName componentName = new ComponentName(mContext, name);
				ServiceInfo info = pm.getServiceInfo(componentName, PackageManager.GET_META_DATA);
				if (info != null && info.metaData != null){
					String bundleName = info.metaData.getString("bundleLocation");
					
					if (bundleName != null){
						mBundleInfoList.insertComponent(name, bundleName);											
					}	
				}
			}
			
			// Get Receiver info			
			mPackageInfo = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_RECEIVERS);
			if (mPackageInfo.receivers != null)			
			for (int i = 0; i < mPackageInfo.receivers.length; i++){
				String name = mPackageInfo.receivers[i].name;
				ComponentName componentName = new ComponentName(mContext, name);
				ActivityInfo info = pm.getReceiverInfo(componentName, PackageManager.GET_META_DATA);
				if (info != null && info.metaData != null){
					String bundleName = info.metaData.getString("bundleLocation");
					
					if (bundleName != null){
						mBundleInfoList.insertComponent(name, bundleName);																	
					}		
				}
			}
			
			// Get Provider info			
			mPackageInfo = pm.getPackageInfo(mContext.getPackageName(), PackageManager.GET_PROVIDERS);
			if (mPackageInfo.providers != null)		
			for (int i = 0; i < mPackageInfo.providers.length; i++){
				String name = mPackageInfo.providers[i].name;
				ComponentName componentName = new ComponentName(mContext, name);
				ProviderInfo info = pm.getProviderInfo(componentName, PackageManager.GET_META_DATA);
				if (info != null && info.metaData != null){
					String bundleName = info.metaData.getString("bundleLocation");
					if (bundleName != null){
						mBundleInfoList.insertComponent(name, bundleName);											
					}
				}
			}			
		} catch (NameNotFoundException e) {
			Log.e(TAG, "Failed to get component info!");
			e.printStackTrace();
		}
		return true;
	}

//	private void parseFromBundleInfoProperty(){
//		if (!DEBUG)
//			return;
//		
//		Properties props = new Properties();
//        int id = mContext.getResources().getIdentifier("bundleinfo", "raw",mContext.getPackageName());
//        try {
//			props.load(mContext.getResources().openRawResource(id));
//		} catch (NotFoundException e) {
//			e.printStackTrace();
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//        
//        Set<Entry<Object, Object>> entrySet = props.entrySet();
//        for (Entry<Object, Object> entry : entrySet) {
//                String bundleName = ((String)(entry.getValue())).trim();
//                String componentName = (String) entry.getKey();
//                mBundleInfoList.insertComponent(componentName, bundleName);
//        }
//		
//	}	
	
	/*
	 * get the dependancies info from BundleInfoManager
	 */
	private void parseFromBundleInfoManager(){
		List<String> names = mBundleInfoList.getAllBundleNames();
		for (String name: names){
			/*
			 * Pay attention here, the bundleinfo is of LightedAPK, com.taobao.lightapk
			 */
			BundleListing.BundleInfo info = 
					BundleInfoManager.instance().getBundleInfoByPkg(name);
			if (info != null && info.getDependency() != null &&  info.getDependency().size() != 0){
				mBundleInfoList.insertDependentBundleList(info.getDependency(), name);
			}
		}

	}
	
}
