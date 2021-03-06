package com.taobao.tao;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.taobao.atlas.wrapper.AtlasApplicationDelegate;
import android.taobao.atlas.wrapper.IAtlasApplication;
import android.text.TextUtils;
import android.util.Log;
import com.taobao.android.lifecycle.PanguApplication;
import com.taobao.taobaocompat.R;
import com.taobao.barrier.startup.StartupMonitor;
import android.content.pm.ApplicationInfo;

import java.util.Arrays;

/**
 * Application 锁死不能修改，如果要修改，请将代码移入TaobaoApplicationFake，Application内用
 * 到的class无法支持动态部署
 * Created by guanjie on 15/7/8.
 */
public class TaobaoApplication extends PanguApplication implements IAtlasApplication {
    private static final String TAG = TaobaoApplication.class.getSimpleName();

    public TaobaoApplicationFake mApplicationFake;

    final static String[] HIGH_PRIORITY_BUNDLE_FOR_BLOCK_INSTALL = new String[]{"com.taobao.browser","com.taobao.taobao.home","com.taobao.dynamic","com.taobao.login4android", "com.taobao.passivelocation", "com.taobao.mytaobao", "com.taobao.wangxin", "com.taobao.allspark",
            "com.taobao.search", "com.taobao.android.scancode", "com.taobao.android.trade", "com.taobao.taobao.cashdesk", "com.taobao.weapp", "com.taobao.taobao.alipay"};

    final static String[] HIGH_PRIORITY_BUNDLE_FOR_DEMAND_INSTALL = new String[]{"com.taobao.browser","com.taobao.taobao.home", "com.taobao.login4android"};

    private AtlasApplicationDelegate mAtlasApplicationDelegate;
    public static boolean isFrameworkStartUp = false;

    //不要删，这个在 welcome 界面埋点需要
    public static long START;
    
    //step 1
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (mAtlasApplicationDelegate == null) {
            mAtlasApplicationDelegate = new AtlasApplicationDelegate(this);

            String[] demandInstallBundles = remakeDemandBundleList(base);
            for(String s : demandInstallBundles){
                Log.d(TAG, String.format("  demand_bundle: %s", s));
            }
            mAtlasApplicationDelegate.setHighPriorityBundles(demandInstallBundles, HIGH_PRIORITY_BUNDLE_FOR_BLOCK_INSTALL);
        }
        boolean isDebuggable = (0 != (base.getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE));
        if (isDebuggable)
            mAtlasApplicationDelegate.enableMultiDex();
        mAtlasApplicationDelegate.attachBaseContext(base);
    }

    /**
     * Re-form the bundle list for demand install, according to current build.
     * @param base
     * @return
     */
    private static String[] remakeDemandBundleList(Context base){
        try {
            String[] demandInstallBundles = HIGH_PRIORITY_BUNDLE_FOR_DEMAND_INSTALL;
            // package_type and publish_type are defined through MTL builds.
            // a. in debug or beta builds, try to load barrier
            // b. in release builds, don't load barrier
            if (!"1".equals(base.getString(R.string.package_type)) ||
                    "0".equals(base.getString(R.string.publish_type))) {
                Log.d(TAG, "need to start com.taobao.barrier");
                demandInstallBundles = Arrays.copyOf(HIGH_PRIORITY_BUNDLE_FOR_DEMAND_INSTALL,
                        HIGH_PRIORITY_BUNDLE_FOR_DEMAND_INSTALL.length + 1);
                demandInstallBundles[demandInstallBundles.length - 1] = "com.taobao.barrier";
            }
            return demandInstallBundles;
        }catch (Throwable e){
            // protection: in some cases(human error in integration, etc.), build variables may not be passed in, use default build list.
            Log.w(TAG, "unable to re-make bundle list for demand install, use default list instead", e);
            return HIGH_PRIORITY_BUNDLE_FOR_DEMAND_INSTALL;
        }
    }

    //step 1.5
    @Override
    public boolean isLightPackage() {
        String miniPackage = null;
        try{
            miniPackage = getString(R.string.isMiniPackage);
        }catch(Throwable e){
            return false;
        }

        if(!TextUtils.isEmpty(miniPackage)){
            return "1".equals(miniPackage.trim());
        }

        return false;
    }

    /////////////////////////////////////step2 之前的代码在主dex的patch load之前调用，无法支持动态部署///////////////////////////
    /////////////////////////////////////step2 开始使用的到的代码可以支持动态部署（前提是不在TaobaoApplication里面被引用）//////////

    //step 2
    @Override
    public void onCreate() {
        super.onCreate();
        StartupMonitor.monitor(this);
        START = System.currentTimeMillis();
        mApplicationFake = new TaobaoApplicationFake(this,mAtlasApplicationDelegate);
        mApplicationFake.onCreate();
    }

    //step 3
    @Override
    public void preFrameworkinit(Context mBaseContext) {
        mApplicationFake.preFrameworkinit(mBaseContext);
    }

    //step 4
    @Override
    public void onFrameworkStartUp() {
        isFrameworkStartUp = true;
        mApplicationFake.onFrameworkStartUp();
    }

    @Override
    public boolean skipLoadBundles(String processName) {
        return mApplicationFake.skipLoadBundles(processName);
    }

    @Override
    public boolean isBundleValid(String bundlePath) {
    	return mApplicationFake.isBundleValid(bundlePath);
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
    public PackageManager getPackageManager() {
        return mAtlasApplicationDelegate.getPackageManager();
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

    //增加一次db创建失败后的retry。
    public SQLiteDatabase hookDatabase(String name, int mode,
                                       SQLiteDatabase.CursorFactory factory) {
        SQLiteDatabase database = null;
        try {
            database = super.openOrCreateDatabase(name, mode, factory);
        } catch (SQLiteException e) {
            // try again by deleting the old db and create a new one
            Log.d("SQLiteDatabase", "fail to openOrCreateDatabase:" + name);
            if (deleteDatabase(name)) {
                database = super.openOrCreateDatabase(name, mode, factory);
            }
        }
        return database;
    }

}
