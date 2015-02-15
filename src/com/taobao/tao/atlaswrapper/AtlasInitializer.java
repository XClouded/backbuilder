package com.taobao.tao.atlaswrapper;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.taobao.atlas.bundleInfo.BundleInfoList;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.util.ApkUtils;
import android.text.TextUtils;
import android.util.Log;
import com.taobao.android.base.Versions;
import com.taobao.android.task.Coordinator;
import com.taobao.android.task.Coordinator.TaggedRunnable;
import com.taobao.lightapk.BundleInfoManager;
import com.taobao.lightapk.BundleListing;
import com.taobao.tao.Globals;
import android.content.SharedPreferences.Editor;
import com.taobao.tao.ClassNotFoundInterceptor;


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
    
    // Flag to check whether current process is com.taobao.taobao
    private static boolean mIsTaobaoProcess;
    
    // BundleInfoList parsed fail, fall back to install all bundles
    private boolean mIsBundleInfoParsed;
    
    /*
     *  Check whether need override when version is reversed
     *  i.e., from mini <-->full, if yes, need reinstall all bundles
     */
    private boolean resetForOverrideInstall;
    
    private boolean updated = false;
        
    public AtlasInitializer(Application mApplication, String mProcessName){
    	this.mApplication = mApplication;
    	this.mProcessName = mProcessName;
    	if (mApplication.getPackageName().equals(mProcessName)){
    		mIsTaobaoProcess = true;
    	}
    }
    
    public void injectApplication(){
    	try{
    		Atlas.getInstance().injectApplication(mApplication, mApplication.getPackageName());
    	}catch (Exception e) {
    		throw new RuntimeException("atlas inject mApplication fail" + e.getMessage());
    	}
    }
    
	public void init(){
		
			START = System.currentTimeMillis();
			
	        Properties props = new Properties();
	        props.put("android.taobao.atlas.welcome", "com.taobao.tao.welcome.Welcome");
	        props.put("android.taobao.atlas.debug.bundles", "true");
	        props.put("android.taobao.atlas.AppDirectory", mApplication.getFilesDir().getParent());	        
            
	        mMiniPackage = new MiniPackage(mApplication); 
	        mMiniPackage.init(props);
			
	        /*
	         * Set Global.sApplication since system bundle is
	         * started at Atlas initiate which would start TaoApplication.
	         */
	        try {
	            Field sApplication = Globals.class.getDeclaredField("sApplication");
	            sApplication.setAccessible(true);
	            sApplication.set(null, mApplication);
	        } catch (Exception e) {
	            Log.e(TAG, "Could not set Globals.sApplication !!!", e);
	            throw new RuntimeException("Could not set Globals.sApplication !!!",e);
	        }	        
   
	        // Atlas must initialize after properties set
	        try {
	            Atlas.getInstance().init(mApplication, props);
	        } catch (Exception e) {
	            Log.e(TAG, "Could not init atlas framework !!!", e);
	            throw new RuntimeException("atlas initialization fail" + e.getMessage());
	        }
	        
			Log.d(TAG, "Atlas framework inited end " + mProcessName + " " +(System.currentTimeMillis() - START) + " ms");			        

	        try {
	            Field sClassLoader = Globals.class.getDeclaredField("sClassLoader");
	            sClassLoader.setAccessible(true);
	            sClassLoader.set(null, Atlas.getInstance().getDelegateClassLoader());
	        } catch (Exception e) {
	            Log.e(TAG, "Could not set  Globals.sClassLoader !!!", e);
	            throw new RuntimeException("Could not set  Globals.sClassLoader !!!",e);
	        }
	        
			// Check whether awb debug from external storage is supported or not
			mAwbDebug = new AwbDebug();
		    
	        // 安装程序是否已经升级了
	        updated = isUpdated();

            if(updated){
            	/*
            	 *  Kill non-taobao process once updated until taobao main process installed all bundles
            	 *  this is to avoid non-taobao process hold those bundles and main process can not
            	 *  remove the storage directory
            	 */
            	boolean isTaobaoProcess = mApplication.getPackageName().equals(mProcessName);
    	        if (isTaobaoProcess == false) {
    	            android.os.Process.killProcess(android.os.Process.myPid());
    	        }
    	        
    	        /**
    	         * 如果发生了更新，清除动态部署缓存文件
    	         */    	        
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
	        }

	        // Check whether x86 platform
	        if (Utils.searchFile((mApplication.getFilesDir().getParentFile() + "/lib"), "libcom_taobao") == false){
	        	/*
	        	 * Current platform is x86 need install bundles when onCreate
	        	 */
	        	InstallSolutionConfig.install_when_oncreate = true;
	        }
	        
	        if (InstallSolutionConfig.install_when_findclass){	        	
		        /**
		         *  Read Bundle Info configurations for bundle's findClass() usage,
		         *  When findClass() can not find class due to bundle not installed/dexopt yet,
		         *  it is useful to locate which bundle to install/dexopt.
		         */
                if (UpdateBundleInfo() == false){
                	// Bundle Info list parsed fail, let's install all bundles
    	        	InstallSolutionConfig.install_when_oncreate = true;
    	        	mIsBundleInfoParsed = true;
                }
                	
	        }
			

	}

	public void startUp() {
		/*
		 * Make sure bundle installer/OptDex  processors are initialized 
		 * in OnCreate() to avoid Replaced Receiver's installer cannot work!
		 */	        
		final BundlesInstaller bundlesInstaller =BundlesInstaller.getInstance();	        
		final OptDexProcess optDexProcess = OptDexProcess.getInstance();		   	
		if (mApplication.getPackageName().equals(mProcessName) && (updated || mAwbDebug.checkExternalAwbFile())) {
		   	bundlesInstaller.init(mApplication, mMiniPackage, mAwbDebug, mIsTaobaoProcess);
		   	optDexProcess.init(mApplication);
		}
		
        Log.d(TAG, "Atlas framework prepare starting in process " + mProcessName + " " + (System.currentTimeMillis() - START)
                + " ms");	        
        		
        ClassNotFoundInterceptor calssNotFoundCallback = new ClassNotFoundInterceptor();
        Atlas.getInstance().setClassNotFoundInterceptorCallback(calssNotFoundCallback);		
        
		try {
		    Atlas.getInstance().startup();
		} catch (Exception e) {
		    Log.e(TAG, "Could not start up atlas framework !!!", e);
            throw new RuntimeException("atlas startUp fail " + e);
		}

		if (mApplication.getPackageName().equals(mProcessName) == false){
			// Non main process, just return
			return;
		}
		
        if (updated || mAwbDebug.checkExternalAwbFile()) {
		   	if (!InstallSolutionConfig.install_when_oncreate){
				// Install bundles
				Coordinator.postTask(new TaggedRunnable("AtlasStartup") {
				    @Override
				    public void run() {	        
			            bundlesInstaller.process(true, false);
			            optDexProcess.processPackages(true, false);
				    }
				});
		   	} else {		        
				// Install bundles
				Coordinator.postTask(new TaggedRunnable("AtlasStartup") {
				    @Override
				    public void run() {	        
			            bundlesInstaller.process(false, false);
			            optDexProcess.processPackages(false, false);
				    }
				});
		   	}
		} else if (!updated){
			if (mIsBundleInfoParsed == false){
				// Just send out the bundle installed message out, so that homepage could be started.
		        Utils.notifyBundleInstalled(mApplication);
			} else {
				// BundleInfoList parsed fail, fall back to install all bundles
				Coordinator.postTask(new TaggedRunnable("AtlasStartup") {
				    @Override
				    public void run() {	        
			            bundlesInstaller.process(false, false);
			            optDexProcess.processPackages(false, false);
				    }
				});
			}
		}
        
		Log.d(TAG, "Atlas framework end startUp in process " + mProcessName + " " + ( System.currentTimeMillis() - START)
		           + " ms");		
	}

	private boolean UpdateBundleInfo() {
		BundleListing listing = BundleInfoManager.instance().getBundleListing();
		if(listing==null || listing.getBundles()==null){
		    return false;
		}
        LinkedList<BundleInfoList.BundleInfo> list = new LinkedList<BundleInfoList.BundleInfo>();
		for(BundleListing.BundleInfo info : listing.getBundles()){
		    if(info!=null){
		        BundleInfoList.BundleInfo bf = new BundleInfoList.BundleInfo();
		        List<String> components = new ArrayList<String>();
		        if(info.getActivities()!=null)
		            components.addAll(info.getActivities());
		        if(info.getServices()!=null)
		            components.addAll(info.getServices());
		        if(info.getReceivers()!=null)
		            components.addAll(info.getReceivers());
		        if(info.getContentProviders()!=null)
		            components.addAll(info.getContentProviders());
		        bf.hasSO = info.isHasSO();
		        bf.bundleName = info.getPkgName();
		        bf.Components = components;
		        bf.DependentBundles = info.getDependency();
		        list.add(bf);
		    }
		}
		BundleInfoList.getInstance().init(list);
		return true;
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
            throw new RuntimeException(e);
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
