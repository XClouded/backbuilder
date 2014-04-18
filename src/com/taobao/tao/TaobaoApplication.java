package com.taobao.tao;

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
import org.osgi.framework.BundleException;

import android.content.pm.PackageInfo;
import android.taobao.atlas.framework.Atlas;
import android.util.Log;

import com.taobao.android.lifecycle.PanguApplication;
import com.taobao.android.task.Coordinator;
import com.taobao.android.task.Coordinator.TaggedRunnable;

public class TaobaoApplication extends PanguApplication {

    final static String   TAG             = "TaobaoApplication";

    /**
     * 指定Bundle包的处理顺序；程序首先按照这里的顺序来处理Bundle包，然后再乱序处理剩下的Bundle包。
     */
    final static String[] SORTED_PACKAGES = new String[] { "com.taobao.android.trade", "com.taobao.mytaobao" };

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

        try {
            Properties props = new Properties();
            props.put("android.taobao.atlas.welcome", "com.taobao.tao.welcome.Welcome");
            
            Atlas.getInstance().init(this);
            Atlas.getInstance().startup(props);
        } catch (Exception e) {
            Log.e(TAG, "Could not start up atlas framework !!!", e);
        }

        Coordinator.postTask(new TaggedRunnable("ProcessBundles") {

            @Override
            public void run() {
                processBundles();
            }
        });

    }

    private void processBundles() {
        List<String> fileNames = getBundleFileNames();
        // 首先按照预先设定的顺序处理Bundle安装包
        for (int i = 0; i < SORTED_PACKAGES.length; i++) {
            String fileName = filterFileName(fileNames, SORTED_PACKAGES[i]);
            if (fileName != null) {
                processBundle(fileName);
                fileNames.remove(fileName);
            }
        }
        // 处理剩下的Bundle包
        for (String fileName : fileNames) {
            processBundle(fileName);
        }
    }

    private void processBundle(String fileName) {

        if (!(fileName.contains("-") && fileName.contains(".awb"))) {
            return;
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
                Atlas.getInstance().installBundle(packageName, inputStream);
                Log.d(TAG, "Succeed to install bundle " + packageName);
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

    private String filterFileName(List<String> fileNames, String packageName) {
        if (fileNames == null || packageName == null) {
            return null;
        }
        for (String fileName : fileNames) {
            if (fileName.contains(packageName)) {
                return fileName;
            }
        }
        return null;
    }

    private List<String> getBundleFileNames() {
        List<String> fileNames = new ArrayList<String>();
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(this.getApplicationInfo().sourceDir);
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                String entryName = zipEntry.getName();
                if (entryName.startsWith("assets/") && entryName.endsWith(".awb")) {
                    String fileName = entryName.substring(entryName.indexOf("/") + 1, entryName.length());
                    fileNames.add(fileName);
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Exception while get bundles in assets", e);
        } finally {
            if (zipFile != null) {
                try {
                    zipFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return fileNames;
    }

}
