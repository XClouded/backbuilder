package com.taobao.tao;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.taobao.atlas.framework.Atlas;
import android.text.TextUtils;
import android.util.Log;

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
                props.put("android.taobao.atlas.publickey", "30819f300d06092a864886f70d010101050003818d0030818902818100d863f4f3100ca2bc9d15503284e09b64cad4008144bc48f0bc7e5d0e097f07041e5a2e29520dfbd4e0746401438cb20819de56dc9cf26cdc6c5d1a9da4b32ffa80bc960e7d01c7b067167c5df676d64d916d09d37f9ccad935275dd2e480c360cd95a045263a298b2718a03217ea822c5cef78035cd2b114baac552a104e48670203010001");
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
                    // 最后优化成按需要完成dexopt
                    try {
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

}
