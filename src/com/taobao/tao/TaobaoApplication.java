package com.taobao.tao;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import android.content.pm.PackageInfo;
import android.taobao.atlas.framework.Atlas;
import android.util.Log;

import com.taobao.android.lifecycle.PanguApplication;

public class TaobaoApplication extends PanguApplication {

    final static String TAG = "TaobaoApplication";

    @SuppressWarnings("deprecation")
    @Override
    public void onCreate() {
        super.onCreate();

        try {
            Field sApplication = Globals.class.getDeclaredField("sApplication");
            sApplication.setAccessible(true);
            sApplication.set(null, this);
        } catch (Exception e) {
            Log.e(TAG, "Could not set Globals.sApplication !!!", e);
        }
        
        // 兼容旧代码，新代码需要获取系统Application都使用Globals.getApplication()
        TaoApplication.context = this;

        try {
            Atlas.getInstance().init(this);
            Atlas.getInstance().startup(null);
        } catch (Exception e) {
            Log.e(TAG, "Could not start up atlas framework !!!", e);
        }

        // Coordinator.postTask(new TaggedRunnable("InstallBundles") {
        //
        // @Override
        // public void run() {
        // processBundles();
        // }
        // });

        processBundles();

    }

    private void processBundles() {

        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(this.getApplicationInfo().sourceDir);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                // 寻找assets目录下的后缀为.awb的文件
                if (zipEntry.getName().startsWith("assets/") && zipEntry.getName().endsWith(".awb")) {
                    Log.d(TAG, "Processing " + zipEntry.getName());
                    
                    String fileName = zipEntry.getName().substring(zipEntry.getName().lastIndexOf("/") + 1,
                                                                   zipEntry.getName().length());
                    String packageName = substringBetween(zipEntry.getName(), "assets/", "-");
                    String versionCode = substringBetween(zipEntry.getName(), "-", ".awb");
                    
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
                                    } catch (IOException e) {
                                        Log.e(TAG, "Could not update bundle.", e);
                                    } catch (BundleException e) {
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
                                Log.e(TAG, "Could not get version of " + zipEntry.getName() + " , Skip update");
                            }
                        } else {
                            Log.e(TAG, "Could not get PackageInfo of " + zipEntry.getName() + " , Skip update");
                        }
                    } else {
                        // 新增Bundle,需要安装
                        InputStream inputStream = null;
                        try {
                            inputStream = this.getAssets().open(fileName);
                            Atlas.getInstance().installBundle(packageName, inputStream);
                        } catch (IOException e) {
                            Log.e(TAG, "Could not install bundle.", e);
                        } catch (BundleException e) {
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
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception while process bunles in assets", e);
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    static String substringBetween(String str, String open, String close) {
        if (str == null || open == null || close == null) {
            return null;
        }
        int start = str.indexOf(open);
        if (start != -1) {
            int end = str.indexOf(close, start + open.length());
            if (end != -1) {
                return str.substring(start + open.length(), end);
            }
        }
        return null;
    }

}
