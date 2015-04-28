package com.taobao.tao.atlaswrapper;

import android.content.BroadcastReceiver;
import com.taobao.android.task.Coordinator;
import com.taobao.android.task.Coordinator.TaggedRunnable;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ReplacedReceiver extends BroadcastReceiver {

    static final String TAG = "ReplacedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // 应用覆盖安装时，接收消息启动TaobaoApplication，自动完成Bundle安装
    	try{
            Log.d(TAG, "onReceive: " + intent == null ? "null" : intent.getAction());
    	} catch(Exception e){
    		// Reject DOS attack
    	}
        
        if (InstallSolutionConfig.install_when_onreceive && !InstallSolutionConfig.install_when_oncreate)
        Coordinator.postTask(new TaggedRunnable("ProcessBundlesInReceiver") {
            public void run() {
                Log.d(TAG, " Do bundle installation and dexopt when received PACKAG_REPLACED intent!");
                
               	BundlesInstaller bundlesInstaller =BundlesInstaller.getInstance();
               	bundlesInstaller.process(false, true);
               	
            	OptDexProcess mOptDexProcess = OptDexProcess.getInstance();
            	mOptDexProcess.processPackages(false, true);
            }
        });
    }
}
