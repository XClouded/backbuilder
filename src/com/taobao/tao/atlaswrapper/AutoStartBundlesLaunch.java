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
	private final String[] asyncBundle = {"com.taobao.taobao.home", "com.taobao.login4android"};
	private final String[] delayHomeBundle = {};
	private final String[] delayLoginBundle = {"com.taobao.allspark"};
	private final String[] delayBundleOnXiaoMi = {"com.taobao.xiaomi"};
	private HomeFinishedBroadcastReceiver homeReceiver;
	private LoginBroadcastReciever loginReceiver;
	private boolean isAsyncStarted = false;
	private boolean isDelayHomeStarted = false;
	private boolean isDelayLoginStarted = false;
	private static int count = 0;
	
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


	public static void startBundles(final String[] bundles, final boolean sendNotify) {
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

	private static void initBundle(final String name) {
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
    	if(homeReceiver == null) {
    		homeReceiver = new HomeFinishedBroadcastReceiver();
    		Globals.getApplication().registerReceiver(homeReceiver, new IntentFilter("com.taobao.event.HomePageLoadFinished"));
    	}
    	if(loginReceiver == null) {
    		IntentFilter filter = new IntentFilter();  
            filter.addAction("NOTIFY_SESSION_VALID");
            filter.addAction("NOTIFY_LOGIN_SUCCESS");
    		loginReceiver = new LoginBroadcastReciever();
    		Globals.getApplication().registerReceiver(loginReceiver, filter);
    	}
	}
	
    private class HomeFinishedBroadcastReceiver extends BroadcastReceiver {

		@Override public void onReceive(Context context, Intent intent) {
	    	if(!isDelayHomeStarted) {
	    		startBundles(delayHomeBundle, false);
	    		isDelayHomeStarted = true;
	    	}
	    	Globals.getApplication().unregisterReceiver(homeReceiver);
	    	homeReceiver = null;
		}	
    }
    
    private class LoginBroadcastReciever extends BroadcastReceiver{  
		@Override public void onReceive(Context context, Intent intent) {
	    	if(!isDelayLoginStarted) {
	    		startBundles(delayLoginBundle, false);
	    		isDelayLoginStarted = true;
	    	}
	    	Globals.getApplication().unregisterReceiver(loginReceiver);
	    	loginReceiver = null;
		}	
    }  

}
