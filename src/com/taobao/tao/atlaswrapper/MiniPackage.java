package com.taobao.tao.atlaswrapper;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.osgi.framework.Bundle;
import android.app.Application;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.taobao.atlas.framework.Atlas;
import android.util.Log;
import com.taobao.lightapk.BundleInfoManager;
import com.taobao.tao.ClassNotFoundInterceptor;
import com.taobao.tao.Globals;

public class MiniPackage {
	
    final static String   TAG                = "MiniPackage";
	Application mApplication;
	
	MiniPackage(Application mApplication){
		this.mApplication = mApplication;
	}
	
		
	void init(Properties props){
		if(!Globals.isMiniPackage())
			return;
		
//		ClassNotFoundInterceptor calssNotFoundCallback = new ClassNotFoundInterceptor();
//        Atlas.getInstance().setClassNotFoundInterceptorCallback(calssNotFoundCallback);
//    	String versionName = Utils.getPackageInfo(mApplication).versionName;
//    	File path = new File(mApplication.getFilesDir(),"storage"+File.separatorChar+versionName+File.separatorChar);
//    	props.put("android.taobao.atlas.storage", path.getAbsolutePath());
//    	Log.d(TAG, "miniPackage storage path "+path.getAbsolutePath());	    
	}
	
	void process(SharedPreferences prefs, PackageInfo fpackageInfo){
		
        if(!Globals.isMiniPackage())
        	return;
        
//    	try{
//		final String lastVersionName = sharePrefs.getString("last_version_name", "");
//		final StringBuilder noDelBundls = new StringBuilder();
//		if(!StringUtils.isEmpty(lastVersionName)){
//			final File path = new File(TaobaoApplication.this.getFilesDir(),"storage"+File.separatorChar+lastVersionName+File.separatorChar);
//        	List<ParseAtlasMetaUtil.AtlasMetaInfo> metaInfoList = ParseAtlasMetaUtil.parseAtlasMetaInfo(path);
//        	final String[] installedBundles = new String[metaInfoList.size()];
//        	final Map<String,File> bundleMap = new HashMap<String,File>();
//        	final Map<String,Boolean> bundlePersistent = new HashMap<String,Boolean>();
//        	StringBuilder sb = new StringBuilder("input bundle listing: ");
//
//
//        	for(int i=0; i<metaInfoList.size();i++){
//        		String pkgName = metaInfoList.get(i).getPackageName();
//        		File file = metaInfoList.get(i).getBundleFile();
//        		Boolean isPersistent = metaInfoList.get(i).isPersistently();
//        		bundleMap.put(pkgName, file);
//        		bundlePersistent.put(pkgName, isPersistent);
//        		installedBundles[i] = pkgName;
//        		sb.append(pkgName);
//        		sb.append("-->");
//        		sb.append(file.getAbsolutePath());
//        		sb.append("-->");
//        		sb.append(isPersistent);
//        		sb.append("-->");
//        	}
//        	Log.d(TAG, sb.toString());
//            if(!resetForOverrideInstall) {
//                Handler h = new Handler(Looper.getMainLooper());
//                h.post(new Runnable() {
//                    @Override
//                    public void run() {
//                        List<String> pkgList = BundleInfoManager.instance().resolveSameVersionBundle(installedBundles, lastVersionName, getPackageInfo().versionName, true);
//                        StringBuilder sb = new StringBuilder("output bundle listing: ");
//                        if (pkgList != null && pkgList.size() > 0) {
//                            for (String pkg : pkgList) {
//                                sb.append(pkg);
//                                if (Atlas.getInstance().getBundle(pkg) == null) {
//                                    Bundle bundle = null;
//                                    try {
//                                        sb.append("install bundle-->packageName: " + pkg + "bundleFile: " + bundleMap.get(pkg));
//                                        bundle = Atlas.getInstance().installBundle(pkg, bundleMap.get(pkg).getAbsoluteFile());
//                                        if (bundle != null) {
//                                            noDelBundls.append(bundleMap.get(pkg).getAbsolutePath());
//                                            if (bundlePersistent.get(pkg)) {
//                                                bundle.start();
//                                            }
//                                            ((BundleImpl) bundle).optDexFile();
//                                            Atlas.getInstance().enableComponent(bundle.getLocation());
//
//                                        }
//                                    } catch (Exception e) {
//                                        try {
//                                            if (bundle != null) {
//                                                ((BundleImpl) bundle).optDexFile();
//                                                Atlas.getInstance().enableComponent(bundle.getLocation());
//
//                                            }
//                                        } catch (Exception e1) {
//                                            Log.e(TAG, "Could not install bundle.", e1);
//                                        }
//                                    }
//                                    sb.append("-->");
//                                    sb.append("installed");
//                                }
//                            }
//                        }
//                        sb.append("-->");
//                        sb.append("uninstall");
//                        Log.d(TAG, sb.toString());
//                        BundleInfoManager.instance().removeBundleListingByVersion(lastVersionName);
//                        Log.d(TAG, "del bundle listing");
//                        clearPath(path, noDelBundls.toString());
//                    }
//
//                });
//            }
//
//		}
//	}catch(Exception e){
//		Log.e(TAG, "Could not merge packageLight.",e);
//	}
//}
    }
	
    private void clearPath(File path,String noDelBundles){
        if(path.exists()){
            File[] files = path.listFiles();
            for(File file:files){
                if(file.isDirectory()){
                	clearPath(file,noDelBundles);
                }else{
                	if(noDelBundles.indexOf(file.getName()) < 0){
                		Log.d(TAG, "del file : "+file.getAbsolutePath());
                		file.delete();
                	}
                    
                }
            }
            if(noDelBundles.length() ==0){
            	 Log.d(TAG, "del file : "+path.getAbsolutePath());
            	 path.delete();
            }
           
        }
    }
    
}
