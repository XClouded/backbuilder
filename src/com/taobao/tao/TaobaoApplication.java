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

/**
 * Created by guanjie on 15/7/8.
 */
public class TaobaoApplication extends PanguApplication implements IAtlasApplication {

    public TaobaoApplicationFake mApplicationFake;
    final static String[] HIGH_PRIORITY_BUNDLE_FOR_BLOCK_INSTALL = new String[]{"com.taobao.browser","com.taobao.taobao.home","com.taobao.dynamic","com.taobao.login4android", "com.taobao.passivelocation", "com.taobao.mytaobao", "com.taobao.wangxin", "com.taobao.allspark",
            "com.taobao.search", "com.taobao.android.scancode", "com.taobao.android.trade", "com.taobao.taobao.cashdesk", "com.taobao.weapp", "com.taobao.taobao.alipay"};

    final static String[] HIGH_PRIORITY_BUNDLE_FOR_DEMAND_INSTALL = new String[]{"com.taobao.browser","com.taobao.taobao.home", "com.taobao.login4android","com.taobao.barrier"};

    private AtlasApplicationDelegate mAtlasApplicationDelegate;

    //step 1
    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        if (mAtlasApplicationDelegate == null) {
            mAtlasApplicationDelegate = new AtlasApplicationDelegate(this);
            mAtlasApplicationDelegate.setHighPriorityBundles(HIGH_PRIORITY_BUNDLE_FOR_DEMAND_INSTALL, HIGH_PRIORITY_BUNDLE_FOR_BLOCK_INSTALL);
        }
        mAtlasApplicationDelegate.attachBaseContext(base);
    }

    //step 2
    @Override
    public void onCreate() {
        super.onCreate();
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
        mApplicationFake.onFrameworkStartUp();
    }

    @Override
    public boolean isLightPackage() {
        return mApplicationFake.isLightPackage();
    }

    @Override
    public boolean skipLoadBundles(String processName) {
        return mApplicationFake.skipLoadBundles(processName);
    }

    @Override
    public boolean isBundleValid(String bundlePath) {
    	return mApplicationFake.isBundleValid(bundlePath);
     }
<<<<<<< HEAD
=======
    
    @Override
    public void onFrameworkStartUp() {
        AutoStartBundlesLaunch launchManager= new AutoStartBundlesLaunch();
        launchManager.registerDelayedBundlesAutoStart();
        //start xiaomi bundle on Channel process.
        if (mAtlasApplicationDelegate.getCurrentProcessName().equals(CHANNEL_PROCESS)){
            if (Build.BRAND.equalsIgnoreCase("xiaomi")){
                AutoStartBundlesLaunch.startBundles(new String[]{"com.taobao.xiaomi"});
            } else if(Build.BRAND.equalsIgnoreCase("huawei") || Build.BRAND.equalsIgnoreCase("honor")) {
                AutoStartBundlesLaunch.startBundles(new String[]{"com.taobao.huawei"});
            }
        }
    }
>>>>>>> fd57b2f330d2fe517ee929a61ed3f91403bcbcb6

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        return mAtlasApplicationDelegate.bindService(service,conn,flags);
    }

<<<<<<< HEAD
    @Override
    public ComponentName startService(Intent service) {
        return mAtlasApplicationDelegate.startService(service);
=======
    private void initAndStartHotpatch() {
        HotPatchManager hm = HotPatchManager.getInstance();
        hm.init(this, Globals.getVersionName(), null, null);
        if (getPackageName().equals(TaoApplication.getProcessName(Globals.getApplication()))
                || StringUtil.contains(TaoApplication.getProcessName(Globals.getApplication()), ":channel")) {
            SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(Globals.getApplication());
            if ("1".equals(settings.getString("hotpatch_priority", "0"))) {
                hm.startHotPatch();
            }
        }
>>>>>>> fd57b2f330d2fe517ee929a61ed3f91403bcbcb6
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
