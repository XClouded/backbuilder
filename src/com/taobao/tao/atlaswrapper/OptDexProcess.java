package com.taobao.tao.atlaswrapper;

import org.osgi.framework.Bundle;

import android.app.Application;
import android.content.Intent;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.framework.BundleImpl;
import android.taobao.atlas.framework.bundlestorage.BundleArchiveRevision;
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
	
	public synchronized  void processPackages(boolean onlyAuto, boolean force){
		// Never process once not initialized yet to avoid null exception
		if (!mIsInited){
			Log.e(TAG, "Bundle Installer not initialized yet, process abort!");
			return;
		} else if (mIsProcessed && !force){
			Log.i(TAG, "Bundle install already executed, just return");
			return;
		}
		
		if (onlyAuto) {
	    	// 完成auto start bundle的dexopt		
	        long start = System.currentTimeMillis();
			processPakcagesAutoStarted();
			if (!force){
				NotifyBundleInstalled();
			}
	        Log.d(TAG, "dexopt auto start bundles cost time = " + (System.currentTimeMillis() - start) + " ms");
		} else {
	    	// 完成非delayed bundle的dexopt		
	        long start = System.currentTimeMillis();
			processPakagesNotDelayed();
	        Log.d(TAG, "dexopt bundles not delayed cost time = " + (System.currentTimeMillis() - start) + " ms");
			if (!force){
				NotifyBundleInstalled();
			}
			// 完成delayed bundle的dexopt
	        start = System.currentTimeMillis();
	    	OptDexProcess mOptDexProcess = OptDexProcess.getInstance();
	    	mOptDexProcess.processPakcagesDelayed();
	        Log.d(TAG, "dexopt delayed bundles cost time = " + (System.currentTimeMillis() - start) + " ms");    	
		}
		
        /*
         *  Mark process flag as true to avoid bundle installation executed twice.
         *  Dont's set the flag when force since below logic:
         *  replaced receiver excecute-->TaobaoApplication onCreate()'s thread execution
         *  NotifyBundleInstalled() wouldn't be execute.
         */		
		if (!force){
			mIsProcessed = true;    	
		}
	}

	private void NotifyBundleInstalled() {
		// Send out BUNDLES_INSTALLED intent to inform welcome can start the HOME activity now.
		Utils.saveAtlasInfoBySharedPreferences(mApplication);
		System.setProperty("BUNDLES_INSTALLED", "true");
		mApplication.sendBroadcast(new Intent("com.taobao.taobao.action.BUNDLES_INSTALLED"));
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
                	if (e instanceof BundleArchiveRevision.DexLoadException){
                		throw (RuntimeException)e;
                	}
                    Log.e(TAG, "Error while dexopt >>>", e);
                }
            }
        }
        
	}	
    
	private void processPakcagesDelayed(){
		
		for (String pkg : Utils.DELAYED_PACKAGES) {
			Bundle bundle = Atlas.getInstance().getBundle(pkg);
			if (bundle != null) {
				try {
					((BundleImpl) bundle).optDexFile();
                    Atlas.getInstance().enableComponent(bundle.getLocation());					
				} catch (Exception e) {
                	if (e instanceof BundleArchiveRevision.DexLoadException){
                		throw (RuntimeException)e;
                	}
                    Log.e(TAG, "Error while dexopt >>>", e);
				}
			}
		}
		
	}	
	
	private void processPakcagesAutoStarted(){
		
		for (String pkg : Utils.AUTOSTART_PACKAGES) {
			Bundle bundle = Atlas.getInstance().getBundle(pkg);
			if (bundle != null) {
				try {
					((BundleImpl) bundle).optDexFile();
                    Atlas.getInstance().enableComponent(bundle.getLocation());					
				} catch (Exception e) {
                	if (e instanceof BundleArchiveRevision.DexLoadException){
                		throw (RuntimeException)e;
                	}
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
