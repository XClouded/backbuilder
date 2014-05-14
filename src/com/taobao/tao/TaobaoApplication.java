package com.taobao.tao;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.osgi.framework.Bundle;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.taobao.atlas.framework.Atlas;
import android.text.TextUtils;
import android.util.Log;

import com.taobao.android.lifecycle.PanguApplication;
import com.taobao.android.task.Coordinator;
import com.taobao.android.task.Coordinator.TaggedRunnable;

public class TaobaoApplication extends PanguApplication {

    final static String   TAG                = "TaobaoApplication";

    /**
     * 指定Bundle包的处理顺序；程序首先按照这里的顺序来处理Bundle包，然后再乱序处理剩下的Bundle包。
     */
    final static String[] SORTED_PACKAGES    = new String[] { "com.taobao.browser", "com.taobao.android.trade",
            "com.taobao.mytaobao", "com.taobao.shop" };

    final static String[] AUTOSTART_PACKAGES = new String[] { "com.taobao.mytaobao", "com.taobao.wangxin",
            "com.taobao.passivelocation", "com.taobao.ble.checkin" };

    //doesn't delete used for online monitor
    static long START = 0;
    
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

        final List<String> entryNames = new ArrayList<String>();

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
                // 找到安装包中所有的bundle名称，然后把磁盘上的对应bundle全部删除，以便后面重新安装新版本
                entryNames.addAll(getBundleEntryNames("lib/armeabi/libcom_", ".so"));
                StringBuffer stringBuffer = new StringBuffer();
                for (String entryName : entryNames) {
                    String packageName = getPackageNameFromEntryName(entryName);
                    if (stringBuffer.length() > 0) {
                        stringBuffer.append(",");
                    }
                    stringBuffer.append(packageName);
                }
                String autodelete = stringBuffer.toString();
                props.put("android.taobao.atlas.autodelete", autodelete);
                Log.d(TAG, "autodelete: " + autodelete);
            }
        }

        Log.d(TAG, "Atlas framework starting in process " + processName + " " + (System.currentTimeMillis() - START)
                   + " ms");

        try {
            // disableComponents(this);
            Atlas.getInstance().startup(props);
        } catch (Exception e) {
            Log.e(TAG, "Could not start up atlas framework !!!", e);
        }

        // enableComponents(this);

        Log.d(TAG, "Atlas framework started in process " + processName + " " + (System.currentTimeMillis() - START)
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
                    // 或许有Bundle新增或更新，再次刷新Component的状态
                    // enableComponents(TaobaoApplication.this);

                    System.setProperty("BUNDLES_INSTALLED", "true");

                    Log.d(TAG, "sendBroadcast: com.taobao.taobao.action.BUNDLES_INSTALLED");
                    TaobaoApplication.this.sendBroadcast(new Intent("com.taobao.taobao.action.BUNDLES_INSTALLED"));

                    Log.d(TAG, "Updated bundles in process " + processName + " " + (System.currentTimeMillis() - start)
                               + " ms");
                }
            });
        } else if (!updated) {
            System.setProperty("BUNDLES_INSTALLED", "true");
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
                if (bundle != null && contains(AUTOSTART_PACKAGES, packageName)) {
                    bundle.start();
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

    private List<String> getBundleEntryNames(String prefix, String suffix) {
        List<String> entryNames = new ArrayList<String>();
        ZipFile zipFile = null;
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
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return entryNames;
    }

    void disableComponents(Context context) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(),
                                                                     PackageManager.GET_ACTIVITIES
                                                                             | PackageManager.GET_RECEIVERS
                                                                             | PackageManager.GET_SERVICES
                                                                             | PackageManager.GET_PROVIDERS
                                                                             | PackageManager.GET_DISABLED_COMPONENTS);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        if (packageInfo != null && packageInfo.activities != null) {
            for (ActivityInfo activityInfo : packageInfo.activities) {
                if (activityInfo.targetActivity == null) {// except activity-alias
                    try {
                        Class<?> clazz = Atlas.getInstance().getDelegateClassLoader().loadClass(activityInfo.name);
                        if (clazz != null) {
                            continue;
                        }
                    } catch (ClassNotFoundException e) {
                        ComponentName componentName = new ComponentName(context.getPackageName(), activityInfo.name);
                        context.getPackageManager().setComponentEnabledSetting(componentName,
                                                                               PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                                                               PackageManager.DONT_KILL_APP);
                    }
                }
            }
        }

        if (packageInfo != null && packageInfo.receivers != null) {
            for (ActivityInfo activityInfo : packageInfo.receivers) {
                try {
                    Class<?> clazz = Atlas.getInstance().getDelegateClassLoader().loadClass(activityInfo.name);
                    if (clazz != null) {
                        continue;
                    }
                } catch (ClassNotFoundException e) {
                    ComponentName componentName = new ComponentName(context.getPackageName(), activityInfo.name);
                    context.getPackageManager().setComponentEnabledSetting(componentName,
                                                                           PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                                                           PackageManager.DONT_KILL_APP);
                }
            }
        }
        if (packageInfo != null && packageInfo.services != null) {
            for (ServiceInfo activityInfo : packageInfo.services) {
                try {
                    Class<?> clazz = Atlas.getInstance().getDelegateClassLoader().loadClass(activityInfo.name);
                    if (clazz != null) {
                        continue;
                    }
                } catch (ClassNotFoundException e) {
                    ComponentName componentName = new ComponentName(context.getPackageName(), activityInfo.name);
                    context.getPackageManager().setComponentEnabledSetting(componentName,
                                                                           PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                                                           PackageManager.DONT_KILL_APP);
                }
            }
        }

        if (packageInfo != null && packageInfo.providers != null) {
            for (ProviderInfo activityInfo : packageInfo.providers) {
                try {
                    Class<?> clazz = Atlas.getInstance().getDelegateClassLoader().loadClass(activityInfo.name);
                    if (clazz != null) {
                        continue;
                    }
                } catch (ClassNotFoundException e) {
                    ComponentName componentName = new ComponentName(context.getPackageName(), activityInfo.name);
                    context.getPackageManager().setComponentEnabledSetting(componentName,
                                                                           PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                                                                           PackageManager.DONT_KILL_APP);
                }
            }
        }
    }

    void enableComponents(Context context) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(),
                                                                     PackageManager.GET_ACTIVITIES
                                                                             | PackageManager.GET_RECEIVERS
                                                                             | PackageManager.GET_SERVICES
                                                                             | PackageManager.GET_PROVIDERS
                                                                             | PackageManager.GET_DISABLED_COMPONENTS);
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        if (packageInfo != null && packageInfo.activities != null) {
            for (ActivityInfo activityInfo : packageInfo.activities) {
                if (activityInfo.targetActivity == null) {
                    try {
                        ComponentName componentName = new ComponentName(context.getPackageName(), activityInfo.name);
                        if (context.getPackageManager().getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                            Class<?> clazz = Atlas.getInstance().getDelegateClassLoader().loadClass(activityInfo.name);
                            if (clazz != null) {
                                context.getPackageManager().setComponentEnabledSetting(componentName,
                                                                                       PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                                                                       PackageManager.DONT_KILL_APP);
                            }
                        }
                    } catch (ClassNotFoundException e) {
                    }
                }
            }
        }

        if (packageInfo != null && packageInfo.receivers != null) {
            for (ActivityInfo activityInfo : packageInfo.receivers) {
                try {
                    ComponentName componentName = new ComponentName(context.getPackageName(), activityInfo.name);
                    if (context.getPackageManager().getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        Class<?> clazz = Atlas.getInstance().getDelegateClassLoader().loadClass(activityInfo.name);
                        if (clazz != null) {
                            context.getPackageManager().setComponentEnabledSetting(componentName,
                                                                                   PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                                                                   PackageManager.DONT_KILL_APP);
                        }
                    }
                } catch (ClassNotFoundException e) {
                }
            }
        }
        if (packageInfo != null && packageInfo.services != null) {
            for (ServiceInfo activityInfo : packageInfo.services) {
                try {
                    ComponentName componentName = new ComponentName(context.getPackageName(), activityInfo.name);
                    if (context.getPackageManager().getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        Class<?> clazz = Atlas.getInstance().getDelegateClassLoader().loadClass(activityInfo.name);
                        if (clazz != null) {
                            context.getPackageManager().setComponentEnabledSetting(componentName,
                                                                                   PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                                                                   PackageManager.DONT_KILL_APP);
                        }
                    }
                } catch (ClassNotFoundException e) {
                }
            }
        }

        if (packageInfo != null && packageInfo.providers != null) {
            for (ProviderInfo activityInfo : packageInfo.providers) {
                try {
                    ComponentName componentName = new ComponentName(context.getPackageName(), activityInfo.name);
                    if (context.getPackageManager().getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        Class<?> clazz = Atlas.getInstance().getDelegateClassLoader().loadClass(activityInfo.name);
                        if (clazz != null) {
                            context.getPackageManager().setComponentEnabledSetting(componentName,
                                                                                   PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                                                                                   PackageManager.DONT_KILL_APP);
                        }
                    }
                } catch (ClassNotFoundException e) {
                }
            }
        }
    }

}
