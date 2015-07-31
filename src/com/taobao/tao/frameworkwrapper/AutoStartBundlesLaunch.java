package com.taobao.tao.frameworkwrapper;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import com.taobao.android.task.Coordinator;
import com.taobao.android.task.Priority;
import com.taobao.android.task.Coordinator.TaggedRunnable;
import com.taobao.tao.Globals;
import org.osgi.framework.BundleException;
import android.os.Debug;
import android.taobao.atlas.framework.BundleImpl;
import android.taobao.atlas.framework.Atlas;

public class AutoStartBundlesLaunch {
	
	private final static String TAG = "AutoStartBundlesLaunch";
	private final String[] delayHomeBundle = {};
	private final String[] delayLoginBundle = {"com.taobao.allspark"};
	private HomeFinishedBroadcastReceiver homeReceiver;
	private LoginBroadcastReciever loginReceiver;
	private boolean isDelayHomeStarted = false;
	private boolean isDelayLoginStarted = false;

	public static void startBundles(final String[] bundles) {
        Coordinator.postTask(new TaggedRunnable("AsyncTask for bundle:"){
            @Override public void run() {
                for (String name : bundles) {
                    initBundle(name);
                }
            }
        }, Priority.BG_NORMAL);
	}

	private static void initBundle(final String name) {
		Atlas.getInstance().installBundleWithDependency(name);
        BundleImpl bundle = (BundleImpl)Atlas.getInstance().getBundle(name);
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
	
	public void registerDelayedBundlesAutoStart(){
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
	    		startBundles(delayHomeBundle);
	    		isDelayHomeStarted = true;
	    	}
	    	Globals.getApplication().unregisterReceiver(homeReceiver);
	    	homeReceiver = null;
		}	
    }
    
    private class LoginBroadcastReciever extends BroadcastReceiver{  
		@Override public void onReceive(Context context, Intent intent) {
	    	if(!isDelayLoginStarted) {
	    		startBundles(delayLoginBundle);
	    		isDelayLoginStarted = true;
	    	}
	    	Globals.getApplication().unregisterReceiver(loginReceiver);
	    	loginReceiver = null;
		}	
    }  

}
