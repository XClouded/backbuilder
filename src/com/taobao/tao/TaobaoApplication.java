package com.taobao.tao;

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
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.osgi.framework.Bundle;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.StatFs;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.framework.BundleImpl;
import android.taobao.atlas.runtime.ContextImplHook;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.util.ApkUtils;
import android.taobao.safemode.UTCrashCaughtListner;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.taobao.android.base.Versions;
import com.taobao.android.lifecycle.PanguApplication;
import com.taobao.android.task.Coordinator;
import com.taobao.android.task.Coordinator.TaggedRunnable;
import com.taobao.launch.BuildConfig;
import com.taobao.lightapk.BundleInfoManager;
import com.taobao.tao.util.Constants;
import com.ut.mini.crashhandler.UTCrashHandler;


public class TaobaoApplication extends PanguApplication {

    final static String   TAG                = "TaobaoApplication";

    /**
     * 指定Bundle包的处理顺序；程序首先按照这里的顺序来处理Bundle包，然后再乱序处理剩下的Bundle包。
     */
    final static String[] SORTED_PACKAGES = new String[]{"com.taobao.login4android", "com.taobao.taobao.home", "com.taobao.passivelocation", "com.taobao.mytaobao", "com.taobao.wangxin", "com.taobao.allspark", 
    	"com.taobao.search", "com.taobao.android.scancode", "com.taobao.android.trade", "com.taobao.taobao.cashdesk", "com.taobao.weapp", "com.taobao.taobao.alipay"};

    /**
     * 自动启动的bundle
     */
    final static String[] AUTOSTART_PACKAGES = new String[]{"com.taobao.login4android", "com.taobao.taobao.home", "com.taobao.mytaobao", "com.taobao.wangxin",
            "com.taobao.passivelocation", "com.taobao.allspark"};
    
    final static String[] DELAYED_PACKAGES = new String[]{
    	"com.taobao.fmagazine","com.taobao.taobao.pluginservice", "com.taobao.legacy", "com.ut.share",
    	"com.taobao.taobao.map", "com.taobao.android.gamecenter", "com.taobao.tongxue", "com.taobao.taobao.zxing", "com.taobao.labs"};

    
    
    private final String EXTERNAL_DIR_FOR_DEUBG_AWB = Environment.getExternalStorageDirectory().getAbsolutePath()+"/awb-debug";

    //doesn't delete used for online monitor
    private static long START = 0;
    
    private Map<String,Long> userTrackDataMap = new HashMap<String,Long>();

    private ArrayList<String> awbFilePathForDebug = new ArrayList<String>();
    /**
     * 是否支持外部awb
     */
    private boolean awbDebug = false;
    
    private String processName;
    @Override
    public void onCreate() {
        super.onCreate();

        START = System.currentTimeMillis();

        int uid = android.os.Process.myUid();
        int pid = android.os.Process.myPid();
        ArrayList<Integer> pidList = new ArrayList<Integer>();
        ActivityManager activityManager = (ActivityManager)getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo appProcess : activityManager.getRunningAppProcesses()) {
            if(appProcess.pid == pid){
                processName = appProcess.processName;
            }
            if(appProcess.uid == uid && appProcess.pid != pid){
                if(appProcess.processName.equals(getPackageName() + ":safemode")){
                    android.os.Process.killProcess(pid);
                    return;
                }
                pidList.add(appProcess.pid);
            }
            
        }
        boolean isSafeMode = false;
        if (processName == null) {
        	processName = "";
        }
        if(processName.equals(getPackageName() + ":safemode")){
            isSafeMode = true;
            for (Integer integer : pidList) {
                android.os.Process.killProcess(integer);
            }
        }
        
        if(Versions.isDebug()){
        	UTCrashHandler.getInstance().turnOnDebug();
        }
        
        try {
            TaoPackageInfo.init();
            UTCrashHandler.getInstance().setChannel(TaoPackageInfo.sTTID);
      
            String baseline = Globals.getBaselineVer();
            if(!TextUtils.isEmpty(baseline)){
            	UTCrashHandler.getInstance().setVersion(baseline);
            }
        } catch(Exception e) {
        	
        }

        UTCrashHandler.getInstance().setCrashCaughtListener(new UTCrashCaughtListner(getApplicationContext()));
        UTCrashHandler.getInstance().enable(getApplicationContext(), Constants.appkey);
        
        if(isSafeMode){
            return;
        }
        
        if(processName.contains(":watchdog")){
        	//watchdog进程启动, 什么都不初始化了。进入安全模式
        	Log.d(TAG, "watchdog process");
        	return;
        }
        
        Log.d(TAG, "Atlas safemode inited " + (System.currentTimeMillis() - START) + " ms");
        
        //awbDebug = this.getResources().getString(R.string.awb_debug).equals("1") ? true : false;
        awbDebug = BuildConfig.DEBUG ? true : false;
        try {
            Atlas.getInstance().init(this);
        } catch (Exception e) {
            Log.e(TAG, "Could not init atlas framework !!!", e);
            Map<String,String> atlasMap = new ConcurrentHashMap<String,String>();
            atlasMap.put("ATLAS_LAUNCH_ERROR", "Could not init atlas framework!");
            saveAtlasInfoBySharedPreferences(atlasMap);
            throw new RuntimeException("Could not init atlas framework !!!",e);
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
            throw new RuntimeException("Could not set Globals.sApplication & Globals.sClassLoader !!!",e);
        }
		
        Properties props = new Properties();
        props.put("android.taobao.atlas.welcome", "com.taobao.tao.welcome.Welcome");
        props.put("android.taobao.atlas.debug.bundles", "true");
        props.put("osgi.auto.install.1", "com.taobao.libs");
        props.put("osgi.auto.install.file", "libcom_taobao_libs.so");
		if(Globals.isMiniPackage()){
			ClassNotFundInterceptor calssNotFoundCallback = new ClassNotFundInterceptor();
	        Atlas.getInstance().setClassNotFoundInterceptorCallback(calssNotFoundCallback);
        	String versionName = getPackageInfo().versionName;
        	File path = new File(this.getFilesDir(),"storage"+File.separatorChar+versionName+File.separatorChar);
        	props.put("android.taobao.atlas.storage", path.getAbsolutePath());
        	Log.d(TAG, "miniPackage storage path "+path.getAbsolutePath());
        }
		
        /*********************↓ ↓ ↓ ↓ For awb debug ↓ ↓ ↓ ↓***************************/
        boolean supportExternalAwbDebug = false;
        if(awbDebug){
            File dir = new File(EXTERNAL_DIR_FOR_DEUBG_AWB);
            if(dir.isDirectory()){
                //StringBuffer filesStr = new StringBuffer();
                File[] files = dir.listFiles();
                for(File file : files){
                    if(file.isFile() && file.getName().endsWith(".so")){
                        awbFilePathForDebug.add(file.getAbsolutePath());
                        Log.d(TAG,"found external awb "+file.getAbsolutePath());
                        supportExternalAwbDebug = true;
                        continue;
                    }
                }
            }
        }
        /*********************↑ ↑ ↑ ↑ For awb debug ↑ ↑ ↑ ↑***************************/

        // 安装程序是否已经升级了
        boolean updated = false;
        PackageInfo packageInfo = null;

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
            if (supportExternalAwbDebug || packageInfo.versionCode > lastVersionCode
                || (packageInfo.versionCode == lastVersionCode && !TextUtils.equals(packageInfo.versionName,
                                                                                    lastVersionName))) {

                updated = true;
                // 把磁盘上的对应bundle全部删除，以便后面重新安装新版本
                props.put("osgi.init", "true");                
            }
        } else if (processName.endsWith(":push")) {
        	props.put("android.taobao.atlas.auto.load", "false");
        }

        Log.d(TAG, "Atlas framework starting in process " + processName + " " + (System.currentTimeMillis() - START)
                   + " ms");

        try {
            Atlas.getInstance().startup(props);
        	Bundle bundle = Atlas.getInstance().getBundle("com.taobao.libs");
            if(bundle !=null){                	
            	if(!(((BundleImpl)bundle).getArchive().isDexOpted())){
            		throw new Exception("Could not dexopt jar bundle ,please retart !!!");
            	}
           }
        } catch (Exception e) {
            Log.e(TAG, "Could not start up atlas framework !!!", e);
            Map<String,String> atlasMap = new ConcurrentHashMap<String,String>();
            atlasMap.put("ATLAS_LAUNCH_ERROR", "Could not start up atlas framework !");
            saveAtlasInfoBySharedPreferences(atlasMap);
            throw new RuntimeException("Could not start up atlas framework ,please reinstall !!!",e);
        }

        long startupTime = System.currentTimeMillis() - START;
        //userTrackDataMap.put("atlas_startup_time", startupTime);
        //saveUserTrackData();
        Log.d(TAG, "Atlas framework started in process " + processName + " " + (startupTime)
                   + " ms");

        final PackageInfo fpackageInfo = getPackageInfo();
        if (this.getPackageName().equals(processName) && updated) {
            Coordinator.postTask(new TaggedRunnable("ProcessBundles") {

                @Override
                public void run() {
                    long start = System.currentTimeMillis();

                    ZipFile zipFile = null;
                    SharedPreferences prefs = null;
                    try {
                        zipFile = new ZipFile(TaobaoApplication.this.getApplicationInfo().sourceDir);

                        final List<String> entryNames = getBundleEntryNames(zipFile, "lib/armeabi/libcom_", ".so");
						if(entryNames!=null && entryNames.size()>0){
                        	if(getAvailableInternalMemorySize() < (entryNames.size()*2*1024*1024 )){
                        		Handler h = new Handler(Looper.getMainLooper());
                        		h.post(new Runnable(){
									@Override
									public void run() {
										Toast.makeText(RuntimeVariables.androidApplication, "检测到手机存储空间不足，为不影响您的使用请清理！", Toast.LENGTH_LONG).show();
									}
                        			
                        		});
                        	}
                        }
                        processLibsBundles(zipFile, entryNames);
						//执行未变化的bundle安装
                        prefs = TaobaoApplication.this.getSharedPreferences("atlas_configs", MODE_PRIVATE);
                        if(Globals.isMiniPackage()){
                        	String lastVersionName = prefs.getString("last_version_name", "");
                        	File path = new File(TaobaoApplication.this.getFilesDir(),"storage"+File.separatorChar+lastVersionName+File.separatorChar);
                        	List<ParseAtlasMetaUtil.AtlasMetaInfo> metaInfoList = ParseAtlasMetaUtil.parseAtlasMetaInfo(path);
                        	String[] installedBundles = new String[metaInfoList.size()];
                        	Map<String,File> bundleMap = new HashMap<String,File>();
                        	Map<String,Boolean> bundlePersistent = new HashMap<String,Boolean>();
                        	for(int i=0; i<metaInfoList.size();i++){
                        		String pkgName = metaInfoList.get(i).getPackageName();
                        		File file = metaInfoList.get(i).getBundleFile();
                        		Boolean isPersistent = metaInfoList.get(i).isPersistently();
                        		bundleMap.put(pkgName, file);
                        		bundlePersistent.put(pkgName, isPersistent);
                        		installedBundles[i] = pkgName;
                        	}
                        	List<String> pkgList = BundleInfoManager.instance().resolveSameVersionBundle(installedBundles,lastVersionName,fpackageInfo.versionName, true);
                        	if(pkgList !=null && pkgList.size() >0){
                        		for(String pkg:pkgList){
                        			if(Atlas.getInstance().getBundle(pkg)==null){
                        				try {
											Bundle bundle = Atlas.getInstance().installBundle(pkg,bundleMap.get(pkg));
											if(bundle!=null){
												if(bundlePersistent.get(pkg)){
													bundle.start();
												}							
											}
										} catch (Exception e) {
											e.printStackTrace();
											Log.e(TAG, "Could not install bundle.", e);
										}
                        			}
                        		}
                        	}
                        	BundleInfoManager.instance().removeBundleListingByVersion(lastVersionName);
                        	clearPath(path);//TODO:
                        }
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
                    
                    for (Bundle bundle : Atlas.getInstance().getBundles()) {
                        if (bundle != null && !contains(DELAYED_PACKAGES, bundle.getLocation())) {
                            try {
                                ((BundleImpl) bundle).optDexFile();
                                //Atlas.getInstance().enableComponent(bundle.getLocation());
                            } catch (Exception e) {
                                try {
                                    ((BundleImpl) bundle).optDexFile();
                                    //Atlas.getInstance().enableComponent(bundle.getLocation());
                                } catch (Exception e1) {
                                    Log.e(TAG, "Error while dexopt >>>", e1);
                                }
                            }
                        }
                    }
                    
                    Log.d(TAG, "Install bundles in process " + processName + " " + (System.currentTimeMillis() - start) + " ms");
                    Map<String,String> atlasMap = new ConcurrentHashMap<String,String>();
                    atlasMap.put(fpackageInfo.versionName, "dexopt");
                    saveAtlasInfoBySharedPreferences(atlasMap);
                    System.setProperty("BUNDLES_INSTALLED", "true");
                    Log.d(TAG, "sendBroadcast: com.taobao.taobao.action.BUNDLES_INSTALLED");
                    TaobaoApplication.this.sendBroadcast(new Intent("com.taobao.taobao.action.BUNDLES_INSTALLED"));
                    
                    long updateTime = System.currentTimeMillis() - start;
                    //userTrackDataMap.put("atlas_update_time", updateTime);
                    //saveUserTrackData();
                    Log.d(TAG, "Install & dexopt bundles in process " + processName + " " + (updateTime) + " ms");
                    
					// 完成delayed bundle的dexopt，并且enable Activity
                    long dexoptTime = System.currentTimeMillis();
					for (String pkg : DELAYED_PACKAGES) {
						Bundle bundle = Atlas.getInstance().getBundle(pkg);
						if (bundle != null) {
							try {
								((BundleImpl) bundle).optDexFile();
								//Atlas.getInstance().enableComponent(bundle.getLocation());
							} catch (Exception e) {
							    try {
	                                ((BundleImpl) bundle).optDexFile();
	                                //Atlas.getInstance().enableComponent(bundle.getLocation());
	                            } catch (Exception e1) {
	                                Log.e(TAG, "Error while dexopt >>>", e1);
	                            }
							}
						}
					}
                    Log.d(TAG, "DexOpt delayed bundles in " + (System.currentTimeMillis() - dexoptTime) + " ms");
                    
                }
            });
        } else if (!updated) {
             if (this.getPackageName().equals(processName)) {
                Coordinator.postTask(new TaggedRunnable("ProcessBundles") {
					@Override
					public void run() {
		                    long dexoptTime = System.currentTimeMillis();
		                    for(Bundle b: Atlas.getInstance().getBundles()){
		                    	BundleImpl bundle = (BundleImpl) b;
		                    	if(!bundle.getArchive().isDexOpted()){
									try {
										bundle.optDexFile();
										//Atlas.getInstance().enableComponent(bundle.getLocation());
									} catch (Exception e) {
									    try{
									        bundle.optDexFile();  
									        //Atlas.getInstance().enableComponent(bundle.getLocation());
									    }catch(Exception e1){
									        Log.e(TAG, "Error while dexopt >>>", e1);
									    }
									    
									}
		                    	}
							}
		                    Log.d(TAG, "DexOpt delayed bundles in " + (System.currentTimeMillis() - dexoptTime) + " ms");  
		                    Map<String,String> atlasMap = new ConcurrentHashMap<String,String>();
		                    atlasMap.put(fpackageInfo.versionName, "dexopt");
		                    saveAtlasInfoBySharedPreferences(atlasMap);
		                    System.setProperty("BUNDLES_INSTALLED", "true");
		                    Log.d(TAG, "@_@ set property BUNDLES_INSTALLED = true");
		                    sendBroadcast(new Intent("com.taobao.taobao.action.BUNDLES_INSTALLED"));
		                }						
                });
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
        for (String pkg : AUTOSTART_PACKAGES) {
        	Bundle bundle = Atlas.getInstance().getBundle(pkg);
            if (bundle != null) {
                try {
                    bundle.start();
                } catch (Exception e) {
                    Log.e(TAG, "Could not auto start bundle: " + bundle.getLocation(), e);
                }
            }
        }
    }

    /**
     * 获取文件名"."前面的部分
     * @param fileName
     * @return
     */
    private String getBaseFileName(String fileName){
        int pos = fileName.lastIndexOf(".");
        if (pos > 0) {
            fileName = fileName.substring(0, pos);
        }
        return fileName;
    }

    private boolean processLibsBundle(ZipFile zipFile, String entryName) {
        Log.d(TAG,"processLibsBundle entryName " + entryName);

        /****************For awb debug *********************/
        if(awbDebug && awbFilePathForDebug.size()>0){
            for(String filePath : awbFilePathForDebug){
                Log.d(TAG,"processLibsBundle filePath " + filePath);
                if(filePath.contains(getFileNameFromEntryName(entryName).substring(3))){
                    File soFile = new File(filePath);
                    String fileName = soFile.getName();
                    String packageName = getBaseFileName(fileName).replace("_",".");
                    Bundle bundle = Atlas.getInstance().getBundle(packageName);
                    if(bundle==null){
                        try {
                            bundle = Atlas.getInstance().installBundle(packageName, soFile);
                        }catch(Throwable e){
                            Log.e(TAG, "Could not install external bundle.", e);
                        }
                        Log.d(TAG, "Succeed to install external bundle " + packageName);
                    }
                    soFile.delete();
                    return true;
                }
            }
        }
        /****************For awb debug *********************/

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
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry zipEntry = entries.nextElement();
                String entryName = zipEntry.getName();
                if (entryName.startsWith(prefix) && entryName.endsWith(suffix)) {
                    entryNames.add(entryName);
                }
            }
        } catch (Exception e) {
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
        editor.apply();
    }
    
    /**
     * 保存Atals启动、更新花费时间，欢迎页埋点用到这些数据，不要删除
     */
    private void saveAtlasInfoBySharedPreferences(Map<String,String> map){
    	if(map ==null || map.isEmpty()){
    		return ;
    	}
        SharedPreferences prefs = TaobaoApplication.this.getSharedPreferences("atlas_configs",
                                                                              MODE_PRIVATE);
        if(prefs ==null){
        	prefs = TaobaoApplication.this.getSharedPreferences("atlas_configs",MODE_PRIVATE);
        }
        Editor editor = prefs.edit();
        for(String entry : map.keySet()){
            editor.putString(entry,map.get(entry));
        }
        editor.commit();
    }
    
    @SuppressLint("DefaultLocale")
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

    /**************ATLAS覆盖父类方法***************/
    
    @Override
    public boolean bindService(Intent service, ServiceConnection conn, int flags) {
    	ContextImplHook mContextImplHook = new ContextImplHook(getBaseContext(), null);
    	return mContextImplHook.bindService(service, conn, flags);
    }
    
    @Override
    public ComponentName startService(Intent service) {
    	ContextImplHook mContextImplHook = new ContextImplHook(getBaseContext(), null);
    	return mContextImplHook.startService(service);
    }
	private long getAvailableInternalMemorySize(){  
        File path = Environment.getDataDirectory();    
        StatFs stat = new StatFs(path.getPath());  
        long blockSize = stat.getBlockSize();  
        long availableBlocks = stat.getAvailableBlocks();  
        return availableBlocks*blockSize;  
    }
    private PackageInfo getPackageInfo(){
    	PackageInfo packageInfo = null;
    	// 获取当前的版本号
        try {
            PackageManager packageManager = this.getPackageManager();
            packageInfo = packageManager.getPackageInfo(this.getPackageName(), 0);
        } catch (Exception e) {
            // 不可能发生
            Log.e(TAG, "Error to get PackageInfo >>>", e);
            packageInfo = new PackageInfo();
        }
        return packageInfo;
    }
    private void clearPath(File path){
        if(path.exists()){
            File[] files = path.listFiles();
            for(File file:files){
                if(file.isDirectory()){
                	clearPath(file);
                }else{
                    file.delete();
                }
            }
            //path.delete();
        }
    }
    
    
	/**
	 * 为了解决多进程访问同一个webview.db的lock问题。
	 * 对不同进程做db名重载处理。   baiyi-2013-8-6
	 */
	@Override
	public SQLiteDatabase openOrCreateDatabase(String name, int mode,
			CursorFactory factory) {
		
		String processName = TaoApplication.getProcessName(this);
		if(!TextUtils.isEmpty(processName)){
			
			Log.i("SQLiteDatabase", processName);
			if(!processName.equals(this.getPackageName())){
				
				String[] pname = processName.split(":");
				if(pname != null && pname.length > 1){
					String dbname = pname[1] + "_" + name;
					Log.i("SQLiteDatabase", "openOrCreateDatabase:"+dbname);
					return hookDatabase(dbname, mode, factory);
				}
			}

		}

		return hookDatabase(name, mode, factory);
	}
	
	//对3.0以下版本增加一次db创建失败后的retry。
	public SQLiteDatabase hookDatabase(String name, int mode,
			CursorFactory factory) {
		
		if(Build.VERSION.SDK_INT < 11) {
		
			SQLiteDatabase database = null;
			try {
				database = super.openOrCreateDatabase(name, mode, factory);
			} catch (SQLiteException e) {
				// try again by deleting the old db and create a new one
				Log.d("SQLiteDatabase", "fail to openOrCreateDatabase:"+name);
				if (Globals.getApplication().deleteDatabase(name)) {
					database = super.openOrCreateDatabase(name, mode, factory);
				}
			}
			return database;
		} else {
			return super.openOrCreateDatabase(name, mode, factory);
		}
	}
    
}
