package com.taobao.tao.atlaswrapper;

import java.io.File;
import java.util.List;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.FrameworkListener;
import android.annotation.SuppressLint;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.util.ApkUtils;
import android.taobao.atlas.util.StringUtils;
import android.util.Log;
import android.widget.Toast;

import com.taobao.wireless.security.sdk.SecurityGuardManager;
import com.taobao.wireless.security.sdk.pkgvaliditycheck.IPkgValidityCheckComponent;


public class SecurityFrameListener implements FrameworkListener {

    final static String TAG = "SecurityFrameListener";
    public final static String PUBLIC_KEY = "30819f300d06092a864886f70d010101050003818d00308189028181008406125f369fde2720f7264923a63dc48e1243c1d9783ed44d8c276602d2d570073d92c155b81d5899e9a8a97e06353ac4b044d07ca3e2333677d199e0969c96489f6323ed5368e1760731704402d0112c002ccd09a06d27946269a438fe4b0216b718b658eed9d165023f24c6ddaec0af6f47ada8306ad0c4f0fcd80d9b69110203010001";
    
    @SuppressLint("NewApi")
	@Override
    public void frameworkEvent(FrameworkEvent event) {
        switch (event.getType()) {
            case 0:/* STARTING */
                break;
            case FrameworkEvent.STARTED:
                if (Build.VERSION.SDK_INT >= 11) {
                    new SecurityTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                } else {
                    new SecurityTask().execute();
                }
                break;
            case FrameworkEvent.STARTLEVEL_CHANGED:
            case FrameworkEvent.PACKAGES_REFRESHED:
            case FrameworkEvent.ERROR:
        }
    }


    private class SecurityTask extends AsyncTask<String, Void, Boolean> {

        @Override
        protected Boolean doInBackground(String... params) {

            if (PUBLIC_KEY == null || PUBLIC_KEY.isEmpty()) {
                return true;
            }

            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
            }

            List<Bundle> bundles = Atlas.getInstance().getBundles();
            if (bundles != null) {
                for (Bundle bundle : bundles) {
                	
                    File file = Atlas.getInstance().getBundleFile(bundle.getLocation());
                    if(!isBundleValid(file.getAbsolutePath())){
                        return false;
                    } else{
                    	// Bundle is valid, double check the public key
                    	String[] publicKeys = ApkUtils.getApkPublicKey(file.getAbsolutePath());
                        if (!StringUtils.contains(publicKeys, PUBLIC_KEY)) {
                        	Log.e(TAG, "Security check failed. " + bundle.getLocation());
                            if (publicKeys == null || publicKeys.length == 0) {
                                saveSecurityData(bundle.getLocation() + ": NULL");
                            } else {
                                saveSecurityData(bundle.getLocation() + ": " + publicKeys[0]);
                            }
                            return false;
                        }
                    }
                    
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                    }
                }
                
            }

            return true;
        }

        @Override
        protected void onPostExecute(Boolean result) {
            if (result != null && !result.booleanValue()) {
                Toast.makeText(RuntimeVariables.androidApplication, "检测到安装文件被损坏，请卸载后重新安装！", Toast.LENGTH_LONG).show();
                shutdownProcessHandler.sendEmptyMessageDelayed(0, 5000);
            }
        }

    }

    private void saveSecurityData(String pkg) {
        SharedPreferences prefs = RuntimeVariables.androidApplication.getSharedPreferences("atlas_configs", 0);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString("BadSignature", pkg);
        editor.commit();
    }


    ShutdownProcessHandler shutdownProcessHandler = new ShutdownProcessHandler();

    public static class ShutdownProcessHandler extends Handler {

        public void handleMessage(Message msg) {
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }
    
    private boolean isBundleValid(String path){
    	SecurityGuardManager sgManager = SecurityGuardManager.getInstance(RuntimeVariables.androidApplication);
    	if(sgManager !=null ){
    		IPkgValidityCheckComponent pvcComp = sgManager.getPackageValidityCheckComp();
    		if(pvcComp!=null){
    			return pvcComp.isPackageValid(path);
    		}
    	}
    	return false;
    }

}
