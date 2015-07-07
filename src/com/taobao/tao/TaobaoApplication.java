package com.taobao.tao;

import java.lang.reflect.*;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.Build;
import android.taobao.atlas.framework.Atlas;
import com.atlas.application.AtlasApplication;
import com.atlas.application.AtlasApplicationDelegate;
import com.atlas.application.IAtlasApplication;
import com.taobao.android.service.Services;
import com.taobao.tao.update.Updater;
import com.taobao.tao.util.StringUtil;
import android.app.ActivityManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.preference.PreferenceManager;
import android.taobao.safemode.UTCrashCaughtListner;
import android.text.TextUtils;
import android.util.Log;
import com.taobao.android.base.Versions;
import com.taobao.android.lifecycle.PanguApplication;
import com.taobao.tao.util.Constants;
import com.taobao.tao.watchdog.LaunchdogAlarm;
import com.ut.mini.crashhandler.UTCrashHandler;
import com.taobao.tao.atlaswrapper.AppForgroundObserver;
import com.taobao.updatecenter.hotpatch.HotPatchManager;

public class TaobaoApplication extends PanguApplication implements IAtlasApplication{

    final static String   TAG                = "TaobaoApplication";
    //doesn't delete used for online monitor
    public static  boolean isPureProcess = false;
    private AtlasApplicationDelegate mAtlasApplicationDelegate;
    
    @Override
    public void onCreate() {
        super.onCreate();
                /*
         * Set Global.sApplication since system bundle is
         * started at Atlas initiate which would start TaoApplication.
         */
        try {
            Field sApplication = Globals.class.getDeclaredField("sApplication");
            sApplication.setAccessible(true);
            sApplication.set(null, this);
            Field sClassLoader = Globals.class.getDeclaredField("sClassLoader");
            sClassLoader.setAccessible(true);
            sClassLoader.set(null, Atlas.getInstance().getDelegateClassLoader());
        } catch (Exception e) {
            throw new RuntimeException("Could not set Globals !!!",e);
        }

        Services.setSystemClassloader(Atlas.getInstance().getDelegateClassLoader());
        mAtlasApplicationDelegate.onCreate();
        AppForgroundObserver AppForgroundObserver = new AppForgroundObserver();
        ((PanguApplication)Globals.getApplication()).registerCrossActivityLifecycleCallback(new AppForgroundObserver());
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if(mAtlasApplicationDelegate==null) {
            mAtlasApplicationDelegate = new AtlasApplicationDelegate(this);
        }
        mAtlasApplicationDelegate.attachBaseContext(base);
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        return mAtlasApplicationDelegate.bindService(service,conn,flags);
    }

    @Override
    public ComponentName startService(Intent service) {
        return mAtlasApplicationDelegate.startService(service);
    }

    @Override
    public PackageManager getPackageManager(){
        return mAtlasApplicationDelegate.getPackageManager();
    }

    @Override
    public boolean isLightPackage() {
        return Globals.isMiniPackage();
    }

    @Override
    public boolean shouldLoadedBundleInThisProcess(String processName) {
        return !isPureProcess;
    }

    @Override
    public boolean shouldResetFramework() {
        return Updater.needRollback();
    }

    @Override
    public void onAppUpgrade() {
        /**
         * 如果发生了更新，清除动态部署缓存文件
         */
        Updater.removeBaseLineInfo();
    }

    @Override
    public String getCustomVersionName() {
        return BaselineInfoProvider.getInstance().getBaselineVersion();
    }

    @Override
    public int getCustomVersionCode() {
        return BaselineInfoProvider.getInstance().getMainVersionCode();
    }

    @Override
    public void preInit(Context mBaseContext) {
        Field sInstalledVersionName = null;
        try {
            sInstalledVersionName = Globals.class.getDeclaredField("sInstalledVersionName");
            sInstalledVersionName.setAccessible(true);
            sInstalledVersionName.set(null, mBaseContext.getPackageManager().getPackageInfo(mBaseContext.getPackageName(),0).versionName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        initProcessInfos();
        // Start hotpatch if it is high priority.
        initAndStartHotpatch(mBaseContext);
        // start watchdog monitor alarm
        if (TaoApplication.getProcessName(mBaseContext).equals(getPackageName())) {
            LaunchdogAlarm.start(mBaseContext);
        }
        initCrashHandlerAndSafeMode(mBaseContext);

    }

	private void initCrashHandlerAndSafeMode(Context context) {
        if(Versions.isDebug()){
        	UTCrashHandler.getInstance().turnOnDebug();
        }
        try {
            TaoPackageInfo.init();
            UTCrashHandler.getInstance().setChannel(TaoPackageInfo.sTTID);

            StringBuilder sb = new StringBuilder(32);
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

        UTCrashHandler.getInstance().setCrashCaughtListener(new UTCrashCaughtListner(context));
        UTCrashHandler.getInstance().enable(context, Constants.appkey);
	}
    
    private void initAndStartHotpatch(Context base) {
    	HotPatchManager hm = HotPatchManager.getInstance();
		hm.init(this, Globals.getVersionName(), null, null);
		if (getPackageName().equals(TaoApplication.getProcessName(base))
				|| StringUtil.contains(TaoApplication.getProcessName(base), ":push")) {
			SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(Globals.getApplication());
			if ("1".equals(settings.getString("hotpatch_priority", "0"))) {
				hm.startHotPatch();
			}
		}
    }
    
    private void initProcessInfos() {
        int uid = android.os.Process.myUid();
        int pid = android.os.Process.myPid();
        ActivityManager activityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo appProcess : activityManager.getRunningAppProcesses()) {
            if(appProcess.pid == pid){
				if (appProcess.processName.equals(getPackageName()+ ":safemode")
						|| appProcess.processName.equals(getPackageName() + ":watchdog")) {
					isPureProcess = true;
				} else {
					return;
				}
            }
            // For safemode process, to kill brother processes.
            if(appProcess.uid == uid && appProcess.pid != pid){
                if(appProcess.processName.equals(getPackageName() + ":safemode")){
                    android.os.Process.killProcess(pid);
                }
            }            
        }
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

}
