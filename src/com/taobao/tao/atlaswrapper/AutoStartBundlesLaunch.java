package com.taobao.tao.atlaswrapper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

import com.taobao.android.task.Coordinator;
import com.taobao.android.task.Priority;
import com.taobao.android.task.Coordinator.TaggedRunnable;
import com.taobao.android.utils.Debuggable;
import com.taobao.tao.Globals;

import org.osgi.framework.BundleException;

import android.os.Build;
import android.os.Debug;
import android.taobao.atlas.framework.BundleImpl;
import android.taobao.atlas.framework.Atlas;

class AutoStartBundlesLaunch {
	
	private final static String TAG = "AutoStartBundlesLaunch";
    private final String[] asyncBundleDbg = {"com.taobao.barrier"};
	private final String[] asyncBundle = {"com.taobao.taobao.home", "com.taobao.allspark", "com.taobao.login4android"};
	private final String[] delayBundle = {"com.taobao.wangxin", "com.taobao.tao.contacts", "com.taobao.tbpoplayer"};
	private final String[] delayBundleOnXiaoMi = {"com.taobao.xiaomi"};
	private HomeFinishedBroadcastReceiver receiver;
	private boolean isAsyncStarted = false;
	private boolean isDelayStarted = false;
	private int count = 0;
	
	void launch_async_bundles() {
		if (isAsyncStarted == true){
			return;
		}
		
		startBundles(asyncBundle, true);
        if (Debuggable.isDebug()) {		
        	startBundles(asyncBundleDbg, false);
        }
        
        isAsyncStarted = true;
	}


	private void startBundles(final String[] bundles, final boolean sendNotify) {
		for (final String name : bundles) {
			Coordinator.postTask(new TaggedRunnable("AsyncTask for bundle:" + name){
				@Override public void run() {        		
		            initBundle(name);
		            if (sendNotify){
	                	count++;
	                	 if (count >= bundles.length){
	                    	Utils.notifyBundleInstalled(Globals.getApplication());
	                    	count = 0;
	                    }		           
		            }
		    	}
			}, Priority.UI_TOP);
		}
	}

	private void initBundle(final String name) {
		BundleImpl bundle = (BundleImpl) Atlas.getInstance().getBundleOnDemand(name);
        if (bundle != null) {
            try {
            	long cputime = Debug.threadCpuTimeNanos();
        		long realtime = System.nanoTime();
                bundle.startBundle();
        		cputime = (Debug.threadCpuTimeNanos() - cputime) / 1000000;
        		realtime = (System.nanoTime() - realtime) / 1000000;
        		Log.d(TAG, "Start bundle " + name + " cost cputime:" + cputime + " ms." + " cost real time:" + realtime + " ms.");
            } catch (BundleException e) {
                e.printStackTrace();
            }
        }
	}
	
	void registerDelayedBundlesAutoStart(){
    	if(receiver == null) {
    		receiver = new HomeFinishedBroadcastReceiver();
    		Globals.getApplication().registerReceiver(receiver, new IntentFilter("com.taobao.event.HomePageLoadFinished"));
    	}
	}
	
    private class HomeFinishedBroadcastReceiver extends BroadcastReceiver {

		@Override public void onReceive(Context context, Intent intent) {
	    	if(!isDelayStarted) {
	    		startBundles(delayBundle, false);
	    		if (Build.BRAND.equalsIgnoreCase("xiaomi")){
	    			startBundles(delayBundleOnXiaoMi, false);
	    		}
				isDelayStarted = true;
	    	}
	    	Globals.getApplication().unregisterReceiver(receiver);
		}	
    }

}
