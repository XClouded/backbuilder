package com.taobao.tao.atlaswrapper;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.Properties;
import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.util.ApkUtils;
import android.text.TextUtils;
import android.util.Log;
import com.taobao.android.base.Versions;
import com.taobao.android.task.Coordinator;
import com.taobao.android.task.Coordinator.TaggedRunnable;
import com.taobao.tao.Globals;
import android.content.SharedPreferences.Editor;


public class AtlasInitializer {
	
    final static String   TAG                = "AtlasInitializer";
        
    //Online monitor to track Atlas start up time
    private static long START = 0;
    
    // TaobaoApplication
    private Application mApplication;
    
    // TaobaoApplication process name
    private String mProcessName;
    
    // Atlas debug bundles on external storage
    private AwbDebug mAwbDebug;
    
    // Mini package logic
    private MiniPackage mMiniPackage;
    
    // Defines the bundle<->components map
    private final String mBundleInfoFile = "bundleinfo";
    
    /*
     *  Check whether need override when version is reversed
     *  i.e., from mini <-->full, if yes, need reinstall all bundles
     */
    private boolean resetForOverrideInstall;
        
    public AtlasInitializer(Application mApplication, String mProcessName){
    	this.mApplication = mApplication;
    	this.mProcessName = mProcessName;
    }
    
	public void init(){
		
			START = System.currentTimeMillis();
			
	        try {
	            Atlas.getInstance().init(mApplication);
	        } catch (Exception e) {
	            Log.e(TAG, "Could not init atlas framework !!!", e);
	        }
	        
	        Log.d(TAG, "Atlas framework inited " + (System.currentTimeMillis() - START) + " ms");

	        try {
	            Field sApplication = Globals.class.getDeclaredField("sApplication");
	            sApplication.setAccessible(true);
	            sApplication.set(null, mApplication);
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
	        props.put("android.taobao.atlas.AppDirectory", mApplication.getFilesDir().getParent());	        
            
	        mMiniPackage = new MiniPackage(mApplication); 
	        mMiniPackage.init(props);
	        
			// Check whether awb debug from external storage is supported or not
			mAwbDebug = new AwbDebug();
		    
	        // 安装程序是否已经升级了
	        final boolean updated = isUpdated();
        /**
         * 如果发生了更新，清除动态部署缓存文件
         */
            if(updated){
                String baseLineInfoPath = mApplication.getFilesDir()+File.separator+"bundleBaseline"+File.separator+"baselineInfo";
                File file = new File(baseLineInfoPath);
                if(file.exists()){
                    file.delete();
                }
            }
	        if (mApplication.getPackageName().equals(mProcessName)) {
  	
	            // 非debug版本设置公钥，用于atlas校验签名
	            if (!Versions.isDebug() && !isLowDevice() && ApkUtils.isRootSystem()) {
	                props.put("android.taobao.atlas.publickey", SecurityFrameListener.PUBLIC_KEY);
	                Atlas.getInstance().addFrameworkListener(new SecurityFrameListener());
	            }
	            
	            if (updated || mAwbDebug.checkExternalAwbFile()){
		            // 把磁盘上的对应bundle全部删除，以便后面重新安装新版本
		            props.put("osgi.init", "true");            
	            }
	        } else if (mProcessName.endsWith(":push")) {
	        	props.put("android.taobao.atlas.auto.load", "false");
	        }

	        Log.d(TAG, "Atlas framework starting in process " + mProcessName + " " + (System.currentTimeMillis() - START)
	                   + " ms");

	        try {
	            Atlas.getInstance().startup(props);
	        } catch (Exception e) {
	            Log.e(TAG, "Could not start up atlas framework !!!", e);
	        }

	        long startupTime = System.currentTimeMillis() - START;
	        Log.d(TAG, "Atlas framework started in process " + mProcessName + " " + (startupTime)
	                   + " ms");
	        
	        // Check whether x86 platform
	        if (Utils.searchFile((mApplication.getFilesDir().getParentFile() + "/lib"), "libcom_taobao") == false){
	        	/*
	        	 * Current platform is x86 need install bundles when onCreate
	        	 */
	        	InstallSolutionConfig.install_when_oncreate = true;
	        }
	        
//	        if (InstallSolutionConfig.install_when_findclass && mApplication.getPackageName().equals(mProcessName)){
	        if (InstallSolutionConfig.install_when_findclass){	        	
		        /**
		         *  Read Bundle Info configurations for bundle's findClass() usage,
		         *  When findClass() can not find class due to bundle not installed/dexopt yet,
		         *  it is useful to locate which bundle to install/dexopt, **limted** only on the taobao process.
		         *  TODO: need optimization.
		         */
	        	BundleInfoCollection mBundleInfoCollection = new BundleInfoCollection(mApplication.getApplicationContext());
	        	mBundleInfoCollection.generateBundleInfos();
	        }
	        
	        if (mApplication.getPackageName().equals(mProcessName) && (updated || mAwbDebug.checkExternalAwbFile())) {
	        	
		        /*
		         * Make sure bundle installer/OptDex  processors are initialized 
		         * in OnCreate() to avoid Replaced Receiver's installer cannot work!
		         */
	           	final BundlesInstaller bundlesInstaller =BundlesInstaller.getInstance();
	           	bundlesInstaller.init(mApplication, mMiniPackage, mAwbDebug);
            	final OptDexProcess mOptDexProcess = OptDexProcess.getInstance();
            	mOptDexProcess.init(mApplication);
	           	
                /*
                 *  Only install/dexopt all bundles at Taobao process, why **not** do it in Nofity/Push process?
                 *  The reason is that it has potential issue that meta file could be written by two processes(Taobao and Notify/Push)
                 *  
                 *  A exception case is we still need do install/dexopt in findClass/execStartActivityInternal in InstrumentationHook
                 *  once work not done yet, since Notify/Push process, it would raise Taobao application and call findClass/execStartActivityInternal
                 *  maybe Taobao Process's install/dexopt is on-going, and it would lead to ClassNotFound issue.
                 */
	           	if (!InstallSolutionConfig.install_when_oncreate){
	           		// Just send out the bundle installed message out, so that homepage could be started.
                    System.setProperty("BUNDLES_INSTALLED", "true");
                    mApplication.sendBroadcast(new Intent("com.taobao.taobao.action.BUNDLES_INSTALLED"));
                    
                    /*
                     *  Need update package version when not install bundles at onCreate, to avoid storage directory
                     *  being deleted once and once again, since the updated flag would be always true. 
                     */
                    bundlesInstaller.UpdatePackageVersion();
	           	} else
	            Coordinator.postTask(new TaggedRunnable("ProcessBundles") {
	
	                @Override
	                public void run() {
	                    // Install bundles to Atlas frameworks and dexopt
	                    bundlesInstaller.process();
	        	        mOptDexProcess.processPackages();
	                }
	            });
	        } else if (!updated && mApplication.getPackageName().equals(mProcessName)){
            	final OptDexProcess mOptDexProcess = OptDexProcess.getInstance();
            	mOptDexProcess.init(mApplication);
	        	/*
	        	 *  Maybe the phone was shutdown when dexopt, here is to continue dexopt after
	        	 *  reboot
	        	 */
	            Coordinator.postTask(new TaggedRunnable("ProcessBundles") {
	            	
	                @Override
	                public void run() {
	        	        mOptDexProcess.processPackages();
	                }
	            });	        	

	        }
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
    
    private boolean isUpdated(){
        PackageInfo packageInfo = null;
        // 获取当前的版本号
        try {
            PackageManager packageManager = mApplication.getPackageManager();
            packageInfo = packageManager.getPackageInfo(mApplication.getPackageName(), 0);
        } catch (Exception e) {
            // 不可能发生
            Log.e(TAG, "Error to get PackageInfo >>>", e);
            packageInfo = new PackageInfo();
        }

        // 检测之前的版本记录
        SharedPreferences prefs = mApplication.getSharedPreferences("atlas_configs", Context.MODE_PRIVATE);
        int lastVersionCode = prefs.getInt("last_version_code", 0);
        String lastVersionName = prefs.getString("last_version_name", "");

        // 检测之前的版本记录, 如果出现版本反转，极简包<-->全量包，那么需要重新做bundleinstall.
        SharedPreferences configPrefs = mApplication.getSharedPreferences("atlas_configs", Context.MODE_PRIVATE);
        String isMiniPackageCache = configPrefs.getString("isMiniPackage","");
        resetForOverrideInstall = !String.valueOf(Globals.isMiniPackage()).equals(isMiniPackageCache);
        Log.d("TaobaoApplication","resetForOverrideInstall = " + resetForOverrideInstall);
        if(TextUtils.isEmpty(isMiniPackageCache) || resetForOverrideInstall) {
            Editor editor = configPrefs.edit();
            editor.clear();
            editor.putString("isMiniPackage", String.valueOf(Globals.isMiniPackage()));
            editor.commit();
        }
        
        // 判断版本是否更新了
        if (packageInfo.versionCode > lastVersionCode
            || (packageInfo.versionCode == lastVersionCode && !TextUtils.equals(Globals.getInstalledVersionName(),
                                                                                lastVersionName))
            || resetForOverrideInstall) {
        	return true;
        }
        
		return false;
    }
 
}
