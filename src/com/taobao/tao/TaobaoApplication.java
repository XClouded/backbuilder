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
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.taobao.atlas.framework.Atlas;
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

    final static String[] AUTOSTART_PACKAGES = new String[] { "com.taobao.wangxin" };

    @Override
    public void onCreate() {
        super.onCreate();

        // TODO: 如果当前版本大于记录版本说明客户端已经更新。
        // 先判断lib非空即arm平台，bundle已经解压出来覆盖了原文件；
        // lib目录为空则不是arm平台，需要我们手工将bundle文件覆盖老版本

        try {
            Properties props = new Properties();
            props.put("android.taobao.atlas.welcome", "com.taobao.tao.welcome.Welcome");

            Atlas.getInstance().init(this);
            
            //disableComponents(this);
            
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

            Atlas.getInstance().startup(props);

        } catch (Exception e) {
            Log.e(TAG, "Could not start up atlas framework !!!", e);
        }

        //enableComponents(this);

        Coordinator.postTask(new TaggedRunnable("ProcessBundles") {

            @Override
            public void run() {
                processLibsBundles();
                processAssetsBundles();
                // 或许有Bundle新增或更新，再次刷新Component的状态
                //enableComponents(TaobaoApplication.this);
            }
        });

    }

    private void processLibsBundles() {
        List<String> entryNames = getBundleEntryNames("libs/armeabi/libcom.taobao", ".so");
        // 首先按照预先设定的顺序处理Bundle安装包
        for (int i = 0; i < SORTED_PACKAGES.length; i++) {
            String entryName = filterEntryName(entryNames, SORTED_PACKAGES[i]);
            if (entryName != null) {
                processLibsBundle(entryName);
                entryNames.remove(entryName);
            }
        }
        // 处理剩下的Bundle包
        for (String entryName : entryNames) {
            processLibsBundle(entryName);
        }
    }

    private boolean processLibsBundle(String entryName) {

        String fileName = entryName.substring(entryName.indexOf("libs/armeabi/"));
        String packageName = entryName.substring(entryName.indexOf("libs/armeabi/lib"), entryName.indexOf(".so"));

        File libDir = new File(getFilesDir().getParentFile(), "lib");
        File soFile = new File(libDir, fileName);

        Bundle bundle = Atlas.getInstance().getBundle(packageName);
        if (bundle != null) {
            // TODO: 检测是否需要更新

        } else {
            // 新增Bundle,需要安装
            try {
                InputStream inputStream = null;
                if (soFile.exists()) {
                    bundle = Atlas.getInstance().installBundle(packageName, soFile);
                } else {
                    inputStream = this.getAssets().open(fileName);
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

    private void processAssetsBundles() {
        List<String> entryNames = getBundleEntryNames("assets/", ".awb");
        // 首先按照预先设定的顺序处理Bundle安装包
        for (int i = 0; i < SORTED_PACKAGES.length; i++) {
            String entryName = filterEntryName(entryNames, SORTED_PACKAGES[i]);
            if (entryName != null) {
                processAssetsBundle(entryName);
                entryNames.remove(entryName);
            }
        }
        // 处理剩下的Bundle包
        for (String entryName : entryNames) {
            processAssetsBundle(entryName);
        }
    }

    private boolean processAssetsBundle(String fileName) {

        if (!(fileName.contains("-") && fileName.contains(".awb"))) {
            return false;
        }

        String str = fileName.substring(0, fileName.indexOf(".awb"));
        String packageName = str.substring(0, str.indexOf("-"));
        String versionCode = str.substring(str.indexOf("-") + 1);

        Bundle bundle = Atlas.getInstance().getBundle(packageName);
        if (bundle != null) {
            PackageInfo packageInfo = Atlas.getInstance().getBundlePackageInfo(packageName);
            if (packageInfo != null) {
                try {
                    int v = Integer.valueOf(versionCode);
                    if (v < packageInfo.versionCode) {
                        InputStream inputStream = null;
                        try {
                            inputStream = this.getAssets().open(fileName);
                            Atlas.getInstance().updateBundle(packageName, inputStream);
                            Log.d(TAG, "Succeed to update bundle " + packageName);
                            return true;
                        } catch (Exception e) {
                            Log.e(TAG, "Could not update bundle.", e);
                        } finally {
                            if (inputStream != null) {
                                try {
                                    inputStream.close();
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                            }
                        }
                    }
                } catch (NumberFormatException e) {
                    Log.e(TAG, "Could not get version of " + fileName + " , Skip update");
                }
            } else {
                Log.e(TAG, "Could not get PackageInfo of " + fileName + " , Skip update");
            }
        } else {
            // 新增Bundle,需要安装
            InputStream inputStream = null;
            try {
                inputStream = this.getAssets().open(fileName);
                bundle = Atlas.getInstance().installBundle(packageName, inputStream);
                if (bundle != null && contains(AUTOSTART_PACKAGES, packageName)) {
                    bundle.start();
                }
                Log.d(TAG, "Succeed to install bundle " + packageName);
                return true;
            } catch (Exception e) {
                Log.e(TAG, "Could not install bundle.", e);
            } finally {
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
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
                    String fileName = entryName.substring(entryName.indexOf("/") + 1, entryName.length());
                    entryNames.add(fileName);
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
