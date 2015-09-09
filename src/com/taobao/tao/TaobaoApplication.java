package com.taobao.tao;

import android.content.*;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.taobao.atlas.wrapper.IAtlasApplication;
import android.text.TextUtils;
import android.util.Log;
import com.taobao.android.lifecycle.PanguApplication;

/**
 * Created by guanjie on 15/7/8.
 */
public class TaobaoApplication extends PanguApplication implements IAtlasApplication {

    public TaobaoApplicationFake mApplicationFake;

    @Override
    public void onCreate() {
        super.onCreate();
        mApplicationFake.onCreate();
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        mApplicationFake = new TaobaoApplicationFake(this);
        mApplicationFake.attachBaseContext(base);
    }

    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
        return mApplicationFake.bindService(service,conn,flags);
    }

    @Override
    public ComponentName startService(Intent service) {
        return mApplicationFake.startService(service);
    }

    @Override
    public PackageManager getPackageManager() {
        return mApplicationFake.getPackageManager();
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
    public boolean shouldResetFramework() {
        return mApplicationFake.shouldResetFramework();
    }

    @Override
    public void onAppUpgrade() {
        mApplicationFake.onAppUpgrade();
    }

    @Override
    public String getCustomVersionName() {
        return mApplicationFake.getCustomVersionName();
    }

    @Override
    public int getCustomVersionCode() {
        return mApplicationFake.getCustomVersionCode();
    }

    @Override
    public boolean isBundleValid(String bundlePath) {
    	return mApplicationFake.isBundleValid(bundlePath);
     }
    
    @Override
    public void onFrameworkStartUp() {
        mApplicationFake.onFrameworkStartUp();
    }

    @Override
    public void preFrameworkinit(Context mBaseContext) {
        mApplicationFake.preFrameworkinit(mBaseContext);

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
