package com.taobao.tao;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.util.ApkUtils;
import android.text.TextUtils;
import android.util.Log;

import com.taobao.android.base.Versions;
import com.taobao.android.lifecycle.PanguApplication;
import com.taobao.android.task.Coordinator;
import com.taobao.android.task.Coordinator.TaggedRunnable;

import org.osgi.framework.Bundle;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class TaobaoApplication extends PanguApplication {

    final static String   TAG                = "TaobaoApplication";

    /**
     * 指定Bundle包的处理顺序；程序首先按照这里的顺序来处理Bundle包，然后再乱序处理剩下的Bundle包。
     */
    final static String[] SORTED_PACKAGES = new String[]{"com.taobao.browser", "com.taobao.passivelocation",
            "com.taobao.mytaobao", "com.taobao.wangxin", "com.taobao.shop"};

    final static String[] AUTOSTART_PACKAGES = new String[]{"com.taobao.mytaobao", "com.taobao.wangxin",
            "com.taobao.passivelocation", "com.taobao.allspark"};

    //doesn't delete used for online monitor
    static long START = 0;
    
    private Map<String,Long> userTrackDataMap = new HashMap<String,Long>();
    
    @Override
    public void onCreate() {
        super.onCreate();

        START = System.currentTimeMillis();

        try {
            Atlas.getInstance().init(this);
        } catch (Exception e) {
            Log.e(TAG, "Could not init atlas framework !!!", e);
        }

        Log.d(TAG, "Atlas framework inited " + (System.currentTimeMillis() - START) + " ms");

        try {
            Field sApplication = Globals.class.getDeclaredField("sApplication");
            sApplication.setAccessible(true);
            sApplication.set(null, this);
            Field sClassLoader = Globals.class.getDeclaredField("sClassLoader");
            sClassLoader.setAccessible(true);
            sClassLoader.set(null, Atlas.getInstance().getDelegateClassLoader());
        } catch (Exception e) {
            Log.e(TAG, "Could not set Globals.sApplication & Globals.sClassLoader !!!", e);
        }

        Properties props = new Properties();
        props.put("android.taobao.atlas.welcome", "com.taobao.tao.welcome.Welcome");
        props.put("android.taobao.atlas.debug.bundles", "true");

        // 安装程序是否已经升级了
        boolean updated = false;
        PackageInfo packageInfo = null;


        final String processName = TaoApplication.getProcessName(Globals.getApplication());
        if (this.getPackageName().equals(processName)) {

            // 非debug版本设置公钥，用于atlas校验签名
            if (!Versions.isDebug() && !isLowDevice() && ApkUtils.isRootSystem()) {
                props.put("android.taobao.atlas.publickey", "30819f300d06092a864886f70d010101050003818d00308189028181008406125f369fde2720f7264923a63dc48e1243c1d9783ed44d8c276602d2d570073d92c155b81d5899e9a8a97e06353ac4b044d07ca3e2333677d199e0969c96489f6323ed5368e1760731704402d0112c002ccd09a06d27946269a438fe4b0216b718b658eed9d165023f24c6ddaec0af6f47ada8306ad0c4f0fcd80d9b69110203010001");
                Atlas.getInstance().addFrameworkListener(new SecurityFrameListener());
            }

            // 获取当前的版本号
            try {
                PackageManager packageManager = this.getPackageManager();
                packageInfo = packageManager.getPackageInfo(this.getPackageName(), 0);
            } catch (Exception e) {
                // 不可能发生
                Log.e(TAG, "Error to get PackageInfo >>>", e);
                packageInfo = new PackageInfo();
            }

            // 检测之前的版本记录
            SharedPreferences prefs = this.getSharedPreferences("atlas_configs", MODE_PRIVATE);
            int lastVersionCode = prefs.getInt("last_version_code", 0);
            String lastVersionName = prefs.getString("last_version_name", "");

            // 判断版本是否更新了
            if (packageInfo.versionCode > lastVersionCode
                || (packageInfo.versionCode == lastVersionCode && !TextUtils.equals(packageInfo.versionName,
                                                                                    lastVersionName))) {

                updated = true;
                // 把磁盘上的对应bundle全部删除，以便后面重新安装新版本
                props.put("osgi.init", "true");
            }
        }

        Log.d(TAG, "Atlas framework starting in process " + processName + " " + (System.currentTimeMillis() - START)
                   + " ms");

        try {
            Atlas.getInstance().startup(props);
        } catch (Exception e) {
            Log.e(TAG, "Could not start up atlas framework !!!", e);
        }

        long startupTime = System.currentTimeMillis() - START;
        userTrackDataMap.put("atlas_startup_time", startupTime);
        Log.d(TAG, "Atlas framework started in process " + processName + " " + (startupTime)
                   + " ms");

        final PackageInfo fpackageInfo = packageInfo;
        if (this.getPackageName().equals(processName) && updated) {
            Coordinator.postTask(new TaggedRunnable("ProcessBundles") {

                @Override
                public void run() {
                    long start = System.currentTimeMillis();

                    ZipFile zipFile = null;
                    try {
                        zipFile = new ZipFile(TaobaoApplication.this.getApplicationInfo().sourceDir);

                        final List<String> entryNames = getBundleEntryNames(zipFile, "lib/armeabi/libcom_", ".so");
                        processLibsBundles(zipFile, entryNames);

                        SharedPreferences prefs = TaobaoApplication.this.getSharedPreferences("atlas_configs",
                                                                                              MODE_PRIVATE);
                        Editor editor = prefs.edit();
                        editor.putInt("last_version_code", fpackageInfo.versionCode);
                        editor.putString("last_version_name", fpackageInfo.versionName);
                        editor.commit();

                    } catch (IOException e) {
                        Log.e(TAG, "IOException while processLibsBundles >>>", e);
                    } finally {
                        if (zipFile != null) {
                            try {
                                zipFile.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }

                    // 所有的Bundle都安装完成后尝试加载一个不存在的类会使所有的bundle完成dexopt
                    // 最好优化成按需要完成dexopt?
                    try {
                        Thread.sleep(1000);//dexopt很耗时，等待1s以免ANR
                        Atlas.getInstance().getDelegateClassLoader().loadClass("android.taobao.atlas.Dummy");
                    } catch (Exception e) {
                    }


                    System.setProperty("BUNDLES_INSTALLED", "true");

                    Log.d(TAG, "sendBroadcast: com.taobao.taobao.action.BUNDLES_INSTALLED");
                    TaobaoApplication.this.sendBroadcast(new Intent("com.taobao.taobao.action.BUNDLES_INSTALLED"));

                    long updateTime = System.currentTimeMillis() - start;
                    userTrackDataMap.put("atlas_update_time", updateTime);
                    Log.d(TAG, "Updated bundles in process " + processName + " " + (updateTime)
                               + " ms");
                }
            });
        } else if (!updated) {
            System.setProperty("BUNDLES_INSTALLED", "true");
            if (this.getPackageName().equals(processName)) {
                sendBroadcast(new Intent("com.taobao.taobao.action.BUNDLES_INSTALLED"));
            }
        }
        saveUserTrackData();

    }

    private void processLibsBundles(ZipFile zipFile, List<String> entryNames) {
        // 首先按照预先设定的顺序处理Bundle安装包
        for (int i = 0; i < SORTED_PACKAGES.length; i++) {
            String pkg = SORTED_PACKAGES[i].replace(".", "_");
            String entryName = filterEntryName(entryNames, pkg);
            if (entryName != null) {
                processLibsBundle(zipFile, entryName);
                entryNames.remove(entryName);
            }
        }
        // 处理剩下的Bundle包
        for (String entryName : entryNames) {
            processLibsBundle(zipFile, entryName);
        }
        // 根据需要自动启动Bundle
        for (Bundle bundle : Atlas.getInstance().getBundles()) {
            if (bundle != null && contains(AUTOSTART_PACKAGES, bundle.getLocation())) {
                try {
                    bundle.start();
                } catch (Exception e) {
                    Log.e(TAG, "Could not auto start bundle: " + bundle.getLocation(), e);
                }
            }
        }
    }

    private boolean processLibsBundle(ZipFile zipFile, String entryName) {

        String fileName = getFileNameFromEntryName(entryName);
        String packageName = getPackageNameFromEntryName(entryName);

        File libDir = new File(getFilesDir().getParentFile(), "lib");
        File soFile = new File(libDir, fileName);

        Bundle bundle = Atlas.getInstance().getBundle(packageName);
        if (bundle == null) {
            // 由于需要更新的bundle已经在容器启动时删除了，这里只要安装bundle就好了
            try {
                InputStream inputStream = null;
                if (soFile.exists()) {
                    bundle = Atlas.getInstance().installBundle(packageName, soFile);
                } else {
                    inputStream = zipFile.getInputStream(zipFile.getEntry(entryName));
                    bundle = Atlas.getInstance().installBundle(packageName, inputStream);
                }
                Log.d(TAG, "Succeed to install bundle " + packageName);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Could not install bundle.", e);
            }
        }

        return false;
    }

    private boolean contains(String[] array, String search) {
        if (array == null || search == null) {
            return false;
        }
        for (String str : array) {
            if (str != null && str.equals(search)) {
                return true;
            }
        }
        return false;
    }

    private String getFileNameFromEntryName(String entryName) {
        String fileName = entryName.substring(entryName.indexOf("lib/armeabi/") + "lib/armeabi/".length());
        return fileName;
    }

    private String getPackageNameFromEntryName(String entryName) {
        String packageName = entryName.substring(entryName.indexOf("lib/armeabi/lib") + "lib/armeabi/lib".length(),
                                                 entryName.indexOf(".so"));
        packageName = packageName.replace("_", ".");
        return packageName;
    }

    private String filterEntryName(List<String> entryNames, String packageName) {
        if (entryNames == null || packageName == null) {
            return null;
        }
        for (String fileName : entryNames) {
            if (fileName.contains(packageName)) {
                return fileName;
            }
        }
        return null;
    }

    private List<String> getBundleEntryNames(ZipFile zipFile, String prefix, String suffix) {
        List<String> entryNames = new ArrayList<String>();
        try {
            zipFile = new ZipFile(this.getApplicationInfo().sourceDir);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                String entryName = zipEntry.getName();
                if (entryName.startsWith(prefix) && entryName.endsWith(suffix)) {
                    entryNames.add(entryName);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception while get bundles in assets or lib", e);
        }
        return entryNames;
    }

    /**
     * 保存Atals启动、更新花费时间，欢迎页埋点用到这些数据，不要删除
     */
    private void saveUserTrackData(){
        SharedPreferences prefs = TaobaoApplication.this.getSharedPreferences("atlas_configs",
                                                                              MODE_PRIVATE);
        Editor editor = prefs.edit();
        for(String entry : userTrackDataMap.keySet()){
            editor.putLong(entry,userTrackDataMap.get(entry));
        }
        editor.commit();
    }

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

}
