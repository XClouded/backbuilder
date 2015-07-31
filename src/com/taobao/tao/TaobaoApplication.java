package com.taobao.tao;

import android.app.ActivityManager;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.preference.PreferenceManager;
import android.taobao.atlas.framework.Atlas;
import android.taobao.safemode.UTCrashCaughtListner;
import android.text.TextUtils;
import android.util.Log;
import android.taobao.atlas.wrapper.AtlasApplicationDelegate;
import android.taobao.atlas.wrapper.IAtlasApplication;
import com.taobao.android.base.Versions;
import com.taobao.android.lifecycle.PanguApplication;
import com.taobao.android.service.Services;
import com.taobao.tao.frameworkwrapper.AppForgroundObserver;
import com.taobao.tao.frameworkwrapper.AtlasMonitorImpl;
import com.taobao.tao.frameworkwrapper.AutoStartBundlesLaunch;
import com.taobao.tao.frameworkwrapper.ExternalLog;
import com.taobao.tao.update.Updater;
import com.taobao.tao.util.Constants;
import com.taobao.tao.util.StringUtil;
import com.taobao.tao.watchdog.LaunchdogAlarm;
import com.taobao.updatecenter.hotpatch.HotPatchManager;
import com.taobao.wireless.security.sdk.SecurityGuardManager;
import com.taobao.wireless.security.sdk.pkgvaliditycheck.IPkgValidityCheckComponent;
import com.ut.mini.crashhandler.UTCrashHandler;
import java.lang.reflect.Field;

/**
 * Created by guanjie on 15/7/8.
 */
public class TaobaoApplication extends PanguApplication implements IAtlasApplication {

    final static String TAG = "TaobaoApplication";
    private final static String CHANNEL_PROCESS = "com.taobao.taobao:channel";
    public final static String PUBLIC_KEY = "30819f300d06092a864886f70d010101050003818d00308189028181008406125f369fde2720f7264923a63dc48e1243c1d9783ed44d8c276602d2d570073d92c155b81d5899e9a8a97e06353ac4b044d07ca3e2333677d199e0969c96489f6323ed5368e1760731704402d0112c002ccd09a06d27946269a438fe4b0216b718b658eed9d165023f24c6ddaec0af6f47ada8306ad0c4f0fcd80d9b69110203010001";

    final static String[] HIGH_PRIORITY_BUNDLE_FOR_BLOCK_INSTALL = new String[]{"com.taobao.taobao.home","com.taobao.dynamic","com.taobao.login4android", "com.taobao.passivelocation", "com.taobao.mytaobao", "com.taobao.wangxin", "com.taobao.allspark",
            "com.taobao.search", "com.taobao.android.scancode", "com.taobao.android.trade", "com.taobao.taobao.cashdesk", "com.taobao.weapp", "com.taobao.taobao.alipay"};

    final static String[] HIGH_PRIORITY_BUNDLE_FOR_DEMAND_INSTALL = new String[]{"com.taobao.taobao.home", "com.taobao.login4android","com.taobao.barrier"};

    private AtlasApplicationDelegate mAtlasApplicationDelegate;

    @Override
    public void onCreate() {
        super.onCreate();
        /*
         * Set Global.sApplication since system bundle is
         * started at Atlas initiate which would start TaoApplication.
         */
        try {
            Field sClassLoader = Globals.class.getDeclaredField("sClassLoader");
            sClassLoader.setAccessible(true);
            sClassLoader.set(null, Atlas.getInstance().getDelegateClassLoader());
        } catch (Exception e) {
            throw new RuntimeException("Could not set Globals !!!", e);
        }

        Services.setSystemClassloader(Atlas.getInstance().getDelegateClassLoader());
        mAtlasApplicationDelegate.onCreate();
        ((PanguApplication) Globals.getApplication()).registerCrossActivityLifecycleCallback(new AppForgroundObserver());
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (mAtlasApplicationDelegate == null) {
            mAtlasApplicationDelegate = new AtlasApplicationDelegate(this);
            mAtlasApplicationDelegate.setRemoteMonitor(new AtlasMonitorImpl());
//            mAtlasApplicationDelegate.setsPublicKey(PUBLIC_KEY);
            mAtlasApplicationDelegate.setLocalLog(new ExternalLog());
            mAtlasApplicationDelegate.setClassNotFoundListener(new ClassNotFoundInterceptor());
            mAtlasApplicationDelegate.setHighPriorityBundles(HIGH_PRIORITY_BUNDLE_FOR_DEMAND_INSTALL, HIGH_PRIORITY_BUNDLE_FOR_BLOCK_INSTALL);
        }
        mAtlasApplicationDelegate.attachBaseContext(base);
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        return mAtlasApplicationDelegate.bindService(service, conn, flags);
    }

    @Override
    public ComponentName startService(Intent service) {
        return mAtlasApplicationDelegate.startService(service);
    }

    @Override
    public PackageManager getPackageManager() {
        return mAtlasApplicationDelegate.getPackageManager();
    }

    @Override
    public boolean isLightPackage() {
        return Globals.isMiniPackage();
    }

    @Override
    public boolean skipLoadBundles(String processName) {
        if(processName.equals(getPackageName()))
            return false;
        else
            return true;
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
    public boolean isBundleValid(String bundlePath) {
        IPkgValidityCheckComponent pvcComp = SecurityGuardManager
                .getInstance(this)
                .getPackageValidityCheckComp();
        if (pvcComp != null) {
            return pvcComp.isPackageValid(bundlePath);
        }
        return true;
    }

    @Override
    public void onFrameworkStartUp() {
        AutoStartBundlesLaunch launchManager= new AutoStartBundlesLaunch();
        launchManager.registerDelayedBundlesAutoStart();
        //start xiaomi bundle on Channel process.
        if (mAtlasApplicationDelegate.getCurrentProcessName().equals(CHANNEL_PROCESS)){
            if (Build.BRAND.equalsIgnoreCase("xiaomi")){
                AutoStartBundlesLaunch.startBundles(new String[]{"com.taobao.xiaomi"});
            }
        }
    }

    @Override
    public void preFrameworkinit(Context mBaseContext) {
        Field sInstalledVersionName = null;
        try {
            Field sApplication = Globals.class.getDeclaredField("sApplication");
            sApplication.setAccessible(true);
            sApplication.set(null, this);
            sInstalledVersionName = Globals.class.getDeclaredField("sInstalledVersionName");
            sInstalledVersionName.setAccessible(true);
            sInstalledVersionName.set(null, mBaseContext.getPackageManager().getPackageInfo(mBaseContext.getPackageName(), 0).versionName);
        } catch (Exception e) {
            e.printStackTrace();
        }
        initProcessInfos();
        // Start hotpatch if it is high priority.
        initAndStartHotpatch();
        // start watchdog monitor alarm
        if (TaoApplication.getProcessName(mBaseContext).equals(getPackageName())) {
            LaunchdogAlarm.start(mBaseContext);
        }
        initCrashHandlerAndSafeMode(mBaseContext);

    }

    private void initCrashHandlerAndSafeMode(Context context) {
        if (Versions.isDebug()) {
            UTCrashHandler.getInstance().turnOnDebug();
        }
        try {
            TaoPackageInfo.init();
            UTCrashHandler.getInstance().setChannel(TaoPackageInfo.sTTID);

            StringBuilder sb = new StringBuilder(32);
            boolean isMini = Globals.isMiniPackage(this);
            if (isMini) {
                sb.append("_jjb=1").append(",");
            } else {
                sb.append("_jjb=0").append(",");
            }

            if (sb.length() > 0) {
                sb.setLength(sb.length() - 1);
                UTCrashHandler.getInstance().setExtraInfo(sb.toString());
            }

        } catch (Exception e) {
        }

        UTCrashHandler.getInstance().setCrashCaughtListener(new UTCrashCaughtListner(context));
        UTCrashHandler.getInstance().enable(context, Constants.appkey);
    }

    private void initAndStartHotpatch() {
        HotPatchManager hm = HotPatchManager.getInstance();
        hm.init(this, Globals.getVersionName(), null, null);
        if (getPackageName().equals(TaoApplication.getProcessName(Globals.getApplication()))
                || StringUtil.contains(TaoApplication.getProcessName(Globals.getApplication()), ":push")) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(Globals.getApplication());
            if ("1".equals(settings.getString("hotpatch_priority", "0"))) {
                hm.startHotPatch();
            }
        }
    }

    private void initProcessInfos() {
        int uid = android.os.Process.myUid();
        int pid = android.os.Process.myPid();
        ActivityManager activityManager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo appProcess : activityManager.getRunningAppProcesses()) {
            if (appProcess.pid == pid) {
                if (appProcess.processName.equals(getPackageName() + ":safemode")
                        || appProcess.processName.equals(getPackageName() + ":watchdog")) {
                } else {
                    return;
                }
            }
            // For safemode process, to kill brother processes.
            if (appProcess.uid == uid && appProcess.pid != pid) {
                if (appProcess.processName.equals(getPackageName() + ":safemode")) {
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
                                               SQLiteDatabase.CursorFactory factory) {

        String processName = TaoApplication.getProcessName(this);
        if (!TextUtils.isEmpty(processName)) {

            Log.i("SQLiteDatabase", processName);
            if (!processName.equals(this.getPackageName())) {

                String[] pname = processName.split(":");
                if (pname != null && pname.length > 1) {
                    String dbname = pname[1] + "_" + name;
                    Log.i("SQLiteDatabase", "openOrCreateDatabase:" + dbname);
                    return hookDatabase(dbname, mode, factory);
                }
            }

        }

        return hookDatabase(name, mode, factory);
    }

    //对3.0以下版本增加一次db创建失败后的retry。
    public SQLiteDatabase hookDatabase(String name, int mode,
                                       SQLiteDatabase.CursorFactory factory) {

        if (Build.VERSION.SDK_INT < 11) {

            SQLiteDatabase database = null;
            try {
                database = super.openOrCreateDatabase(name, mode, factory);
            } catch (SQLiteException e) {
                // try again by deleting the old db and create a new one
                Log.d("SQLiteDatabase", "fail to openOrCreateDatabase:" + name);
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
