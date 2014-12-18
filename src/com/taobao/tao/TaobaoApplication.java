package com.taobao.tao;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.taobao.atlas.hack.AndroidHack;
import android.taobao.atlas.hack.AtlasHacks;
import android.taobao.atlas.hack.Reflect;
import com.taobao.tao.util.StringUtil;
import org.osgi.framework.Bundle;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.framework.BundleImpl;
import android.taobao.atlas.runtime.ContextImplHook;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.util.ApkUtils;
import android.taobao.atlas.util.StringUtils;
import android.taobao.safemode.UTCrashCaughtListner;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.taobao.android.base.Versions;
import com.taobao.android.lifecycle.PanguApplication;
import com.taobao.android.task.Coordinator;
import com.taobao.android.task.Coordinator.TaggedRunnable;
import com.taobao.launch.BuildConfig;
import com.taobao.lightapk.BundleInfoManager;
import com.taobao.tao.util.Constants;
import com.ut.mini.crashhandler.UTCrashHandler;
import com.taobao.tao.atlaswrapper.AtlasInitializer;


public class TaobaoApplication extends PanguApplication {

    final static String   TAG                = "TaobaoApplication";
    
    //doesn't delete used for online monitor
    private static long START = 0;

    private String processName;
    private boolean resetForOverrideInstall;
    @Override
    public void onCreate() {
        super.onCreate();

        START = System.currentTimeMillis();

        int uid = android.os.Process.myUid();
        int pid = android.os.Process.myPid();
        ArrayList<Integer> pidList = new ArrayList<Integer>();
        ActivityManager activityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo appProcess : activityManager.getRunningAppProcesses()) {
            if(appProcess.pid == pid){
                processName = appProcess.processName;
            }
            if(appProcess.uid == uid && appProcess.pid != pid){
                if(appProcess.processName.equals(getPackageName() + ":safemode")){
                    android.os.Process.killProcess(pid);
                    return;
                }
                pidList.add(appProcess.pid);
            }
            
        }
        boolean isSafeMode = false;
        if (processName == null) {
        	processName = "";
        }
        if(processName.equals(getPackageName() + ":safemode")){
            isSafeMode = true;
            for (Integer integer : pidList) {
                android.os.Process.killProcess(integer);
            }
        }
        
        if(Versions.isDebug()){
        	UTCrashHandler.getInstance().turnOnDebug();
        }
        
        try {
            TaoPackageInfo.init();
            UTCrashHandler.getInstance().setChannel(TaoPackageInfo.sTTID);

            StringBuilder sb = new StringBuilder(32);
            
            String baseline = Globals.getBaselineVer();
            if(!TextUtils.isEmpty(baseline)){
            	sb.append("_bv=").append(baseline).append(",");
            }
            
            boolean isMini = Globals.isMiniPackage(this);
            if(isMini){
            	sb.append("_jjb=1").append(",");
            } else {
            	sb.append("_jjb=0").append(",");
            }
            
            if(sb.length() > 0){
            	sb.setLength(sb.length()-1);
            	UTCrashHandler.getInstance().setExtraInfo(sb.toString());
            }
            
        } catch(Exception e) {
        	
        }

        UTCrashHandler.getInstance().setCrashCaughtListener(new UTCrashCaughtListner(getApplicationContext()));
        UTCrashHandler.getInstance().enable(getApplicationContext(), Constants.appkey);
        
        if(isSafeMode){
            return;
        }
        
        if(processName.contains(":watchdog")){
        	//watchdog进程启动, 什么都不初始化了。进入安全模式
        	Log.d(TAG, "watchdog process");
        	return;
        }
        
        /*
         *  AtlasInitializer wraps the logic for Atlas Debug logic, 
         *  Mini packag logic, bundle install/dexopt, and Security check.
         */
        AtlasInitializer mAtlasInitializer = new AtlasInitializer(this, processName);
        mAtlasInitializer.init();
    }
    
    @SuppressLint("DefaultLocale")
	private boolean isLowDevice() {
        if(Build.BRAND!=null && Build.BRAND.toLowerCase().contains("xiaomi")){
            if(Build.HARDWARE!=null && Build.HARDWARE.toLowerCase().contains("mt65")){
                return true;
            }
        }
        if(Build.VERSION.SDK_INT < 14) {
            return true;
        }
        return false;
    }

    /**************ATLAS覆盖父类方法***************/
    
    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
    	ContextImplHook mContextImplHook = new ContextImplHook(getBaseContext(), null);
    	return mContextImplHook.bindService(service, conn, flags);
    }
    
    @Override
    public ComponentName startService(Intent service) {
    	ContextImplHook mContextImplHook = new ContextImplHook(getBaseContext(), null);
    	return mContextImplHook.startService(service);
    }  
    
	/**
	 * 为了解决多进程访问同一个webview.db的lock问题。
	 * 对不同进程做db名重载处理。   baiyi-2013-8-6
	 */
	@Override
	public SQLiteDatabase openOrCreateDatabase(String name, int mode,
			CursorFactory factory) {
		
		String processName = TaoApplication.getProcessName(this);
		if(!TextUtils.isEmpty(processName)){
			
			Log.i("SQLiteDatabase", processName);
			if(!processName.equals(this.getPackageName())){
				
				String[] pname = processName.split(":");
				if(pname != null && pname.length > 1){
					String dbname = pname[1] + "_" + name;
					Log.i("SQLiteDatabase", "openOrCreateDatabase:"+dbname);
					return hookDatabase(dbname, mode, factory);
				}
			}

		}

		return hookDatabase(name, mode, factory);
	}
	
	//对3.0以下版本增加一次db创建失败后的retry。
	public SQLiteDatabase hookDatabase(String name, int mode,
			CursorFactory factory) {
		
		if(Build.VERSION.SDK_INT < 11) {
		
			SQLiteDatabase database = null;
			try {
				database = super.openOrCreateDatabase(name, mode, factory);
			} catch (SQLiteException e) {
				// try again by deleting the old db and create a new one
				Log.d("SQLiteDatabase", "fail to openOrCreateDatabase:"+name);
				if (Globals.getApplication().deleteDatabase(name)) {
					database = super.openOrCreateDatabase(name, mode, factory);
				}
			}
			return database;
		} else {
			return super.openOrCreateDatabase(name, mode, factory);
		}
	}

    private PackageManagerProxyhandler mPackageManagerProxyhandler;
    private PackageManager mPackageManager;
    private Context mBaseContext;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        mBaseContext = base;
        Field sInstalledVersionName = null;
        try {
            sInstalledVersionName = Globals.class.getDeclaredField("sInstalledVersionName");
            sInstalledVersionName.setAccessible(true);
            sInstalledVersionName.set(null, mBaseContext.getPackageManager().getPackageInfo(base.getPackageName(),0).versionName);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public PackageManager getPackageManager(){
        if(mPackageManager!=null){
            return mPackageManager;
        }
        try {
            Class IPackageManagerClass = Class.forName("android.content.pm.IPackageManager");
            Class ActvityThread = Class.forName("android.app.ActivityThread");
            Method method = ActvityThread.getDeclaredMethod("getPackageManager");
            method.setAccessible(true);
            Object mPm = method.invoke(ActvityThread);
            if (mPm != null) {
                if (mPackageManagerProxyhandler == null) {
                    mPackageManagerProxyhandler = new PackageManagerProxyhandler(mPm);
                }
                Object mProxyPm = Proxy.newProxyInstance(getClassLoader(), new Class[]{IPackageManagerClass}, mPackageManagerProxyhandler);

                Class ApplicationPackageManager = Class.forName("android.app.ApplicationPackageManager");
                Class ContextImpl = Class.forName("android.app.ContextImpl");
                Class<?>[] constructorArgs = {ContextImpl, IPackageManagerClass};
                Constructor<?> constructor = ApplicationPackageManager.getDeclaredConstructor(constructorArgs);
                constructor.setAccessible(true);
                mPackageManager = (PackageManager)constructor.newInstance(mBaseContext, mProxyPm);
                return mPackageManager;
            }
        }catch(Exception e){
            e.printStackTrace();
            return super.getPackageManager();
        }
        return super.getPackageManager();
    }

    public class PackageManagerProxyhandler implements InvocationHandler{
        private Object mPm;
        public PackageManagerProxyhandler(Object pm){
            mPm = pm;
        }
        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
//            if(method.getName().equals("getPackageInfo") && args[0]!=null && args[0].equals(getPackageName())){
//                Log.d("TaobaoApplication","invoke method = " + "change version");
//                PackageInfo info = mBaseContext.getPackageManager().getPackageInfo(getPackageName(),0);
//                String containerVersion = info.versionName;
//                int baselineVersionCode = BaselineInfoProvider.getInstance().getMainVersionCode();
//                if(info.versionCode > baselineVersionCode){
//                    return info;
//                }
//                String mainVersion = BaselineInfoProvider.getInstance().getMainVersionName();
//                if(!StringUtil.isEmpty(mainVersion)){
//                    if(!containerVersion.equalsIgnoreCase(mainVersion)){
//                        return info;
//                    }
//                }
//                String baselineVersion = BaselineInfoProvider.getInstance().getBaselineVersion();
//                if(!StringUtil.isEmpty(mainVersion) && !StringUtil.isEmpty(baselineVersion)){
//
//                    String[] v = mainVersion.split("\\.");
//                    if(v.length >= 3) {
//                        v[2] = baselineVersion;
//                        info.versionName =  TextUtils.join(".", v);
//                        return info;
//                    }
//                }
//                return info;
//            }
            return method.invoke(mPm,args);
        }
    }

}
