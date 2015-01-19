package com.taobao.tao.atlaswrapper;

import org.osgi.framework.Bundle;

import android.app.Application;
import android.content.Intent;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.framework.BundleImpl;
import android.util.Log;

public class OptDexProcess {
    final static String   TAG                = "OptDexProcess";
    private static OptDexProcess uniqueInstance;
    private Application mApplication;
	private boolean mIsInited;	
	private boolean mIsProcessed;	
    
	OptDexProcess() {	
	}
	
	public static synchronized OptDexProcess getInstance(){
		if (uniqueInstance == null){
			uniqueInstance = new OptDexProcess();
		}
		return uniqueInstance;
	}
	
	void init(Application mApplication) {
		this.mApplication = mApplication;
		mIsInited = true;
	}	
	
	public synchronized  void processPackages(){
		// Never process once not initialized yet to avoid null exception
		if (!mIsInited){
			Log.e(TAG, "Bundle Installer not initialized yet, process abort!");
			return;
		} else if (mIsProcessed){
			Log.i(TAG, "Bundle install already executed, just return");
			return;
		}
		
    	// 完成非delayed bundle的dexopt		
        long start = System.currentTimeMillis();
		processPakagesNotDelayed();
        Log.d(TAG, "Install bundles not delayed cost time = " + (System.currentTimeMillis() - start) + " ms");
        
        // Send out BUNDLES_INSTALLED intent to inform welcome can start the HOME activity now.
        Utils.saveAtlasInfoBySharedPreferences(mApplication);
        System.setProperty("BUNDLES_INSTALLED", "true");
        notifyBundleInstalled();
        
		// 完成delayed bundle的dexopt
        start = System.currentTimeMillis();
    	OptDexProcess mOptDexProcess = OptDexProcess.getInstance();
    	mOptDexProcess.processPakcagesDelayed();
    	
        Log.d(TAG, "Install delayed bundles cost time = " + (System.currentTimeMillis() - start) + " ms");    	
        
    	mIsProcessed = true;    	
	}
	
	/*
	 * Make Bundle dexopt work just once executed by:
	 * (1) Make process function synchronized
	 * (2) Once enter, check whether executed already, if yes, just return
	 */
	private void processPakagesNotDelayed(){
		
        for (Bundle bundle : Atlas.getInstance().getBundles()) {
            if (bundle != null && !contains(Utils.DELAYED_PACKAGES, bundle.getLocation())) {
                try {
                    ((BundleImpl) bundle).optDexFile();
                    Atlas.getInstance().enableComponent(bundle.getLocation());
                } catch (Exception e) {
                    Log.e(TAG, "Error while dexopt >>>", e);
                }
            }
        }
        
	}	
	
    private void notifyBundleInstalled(){
    	mApplication.sendBroadcast(new Intent("com.taobao.taobao.action.BUNDLES_INSTALLED"));
    }
    
	private void processPakcagesDelayed(){
		
		for (String pkg : Utils.DELAYED_PACKAGES) {
			Bundle bundle = Atlas.getInstance().getBundle(pkg);
			if (bundle != null) {
				try {
					((BundleImpl) bundle).optDexFile();
                    Atlas.getInstance().enableComponent(bundle.getLocation());					
				} catch (Exception e) {
                    Log.e(TAG, "Error while dexopt >>>", e);
				}
			}
		}
		
	}	
	
	private boolean contains(String[] array, String search) {
        if (array == null || search == null) {
            return false;
        }
        for (String str : array) {
            if (str != null && str.equals(search)) {
                return true;
            }
        }
        return false;
    }	
}
