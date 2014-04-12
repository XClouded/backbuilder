package com.taobao.tao;

import java.io.IOException;
import java.io.InputStream;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;

import android.taobao.atlas.framework.Atlas;
import android.util.Log;

import com.taobao.android.lifecycle.PanguApplication;

public class TaobaoApplication extends PanguApplication {

    final static String TAG = "TaobaoApplication";

    @Override
    public void onCreate() {
        super.onCreate();

        Globals.setApplication(this);
        TaoApplication.context = this;
        
        try {
            Atlas.getInstance().init(this);
            Atlas.getInstance().startup(null);
        } catch (Exception e) {
            Log.e(TAG, "Could not start up atlas framework !!!", e);
        }

//        Coordinator.postTask(new TaggedRunnable("InstallBundles") {
//
//            @Override
//            public void run() {
//                processBundles();
//            }
//        });
        
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
                    Log.d(TAG, "processing " + zipEntry.getName());
                    String packageName = substringBetween(zipEntry.getName(), "assets/", ".awb");
                    Bundle bundle = Atlas.getInstance().getBundle(packageName);
                    if (bundle != null) {
                        // TODO: 检测是否需要更新
                    } else {
                        // 新增Bundle,需要安装
                        InputStream inputStream = null;
                        try {
                            inputStream = this.getAssets().open(packageName + ".awb");
                            Atlas.getInstance().installBundle(packageName, inputStream);
                        } catch (IOException e) {
                            e.printStackTrace();
                        } catch (BundleException e) {
                            e.printStackTrace();
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
