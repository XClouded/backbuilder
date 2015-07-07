package com.taobao.tao.atlaswrapper;
import java.io.File;

import android.app.Activity;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.widget.Toast;
import android.taobao.atlas.runtime.RuntimeVariables;

import com.taobao.android.lifecycle.PanguApplication.CrossActivityLifecycleCallback;

public class AppForgroundObserver implements CrossActivityLifecycleCallback{
	public static boolean isForeground = false;
	@Override
    public void onCreated(Activity activity) {
    }

    @Override
    public void onStarted(Activity activity) {
    	// App forground
    	if (isForeground == false){
	    	checkDiskSize();
	    	isForeground = true;
    	}
    }

    @Override
    public void onStopped(Activity activity) {
    	isForeground = false;
    }

    @Override
    public void onDestroyed(Activity activity) {
    }
    
    private void checkDiskSize(){
    	try {
	        File path = Environment.getDataDirectory();    
	        StatFs stat = new StatFs(path.getPath()); 
	        long availableBlocks = stat.getAvailableBlocks();
	        long totalBlocks = stat.getBlockCount();  
	        long blockSize = stat.getBlockSize();  
        	if((availableBlocks < (totalBlocks / 20)) && (availableBlocks * blockSize < 50 *1024*1024)){
        		Handler h = new Handler(Looper.getMainLooper());
        		h.post(new Runnable(){
					@Override
					public void run() {
						Toast.makeText(RuntimeVariables.androidApplication, "检测到手机内部存储空间不足，为不影响您的使用请清理！", Toast.LENGTH_SHORT).show();
					}
        			
        		});
        	}
        } catch(Exception e){
        }
	}
}
