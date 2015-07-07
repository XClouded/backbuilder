package com.atlas.application;

import java.io.File;

import org.osgi.framework.BundleEvent;
import org.osgi.framework.BundleListener;
import com.taobao.wireless.security.sdk.SecurityGuardManager;
import com.taobao.wireless.security.sdk.pkgvaliditycheck.IPkgValidityCheckComponent;

import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.taobao.atlas.framework.Atlas;
import android.taobao.atlas.runtime.RuntimeVariables;
import android.taobao.atlas.util.ApkUtils;
import android.taobao.atlas.util.StringUtils;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

public class SecurityBundleListner implements BundleListener {
	final static String TAG = "SecurityBundleListner";
	private Handler mHandler;
	private HandlerThread mHandlerThread = null;
	ShutdownProcessHandler shutdownProcessHandler = new ShutdownProcessHandler();
	private boolean isSecurityCheckFailed = false;
	private SecurityGuardManager mSgManager = null;
	public final static String PUBLIC_KEY = "30819f300d06092a864886f70d010101050003818d00308189028181008406125f369fde2720f7264923a63dc48e1243c1d9783ed44d8c276602d2d570073d92c155b81d5899e9a8a97e06353ac4b044d07ca3e2333677d199e0969c96489f6323ed5368e1760731704402d0112c002ccd09a06d27946269a438fe4b0216b718b658eed9d165023f24c6ddaec0af6f47ada8306ad0c4f0fcd80d9b69110203010001";

	public SecurityBundleListner() {
		mSgManager = SecurityGuardManager
				.getInstance(RuntimeVariables.androidApplication);
		mHandlerThread = new HandlerThread("Check bundle security");
		mHandlerThread.start();
		mHandler = new SecurityBundleHandler(mHandlerThread.getLooper());
	}

	@Override
	public void bundleChanged(BundleEvent event) {
		switch (event.getType()) {
		case BundleEvent.INSTALLED:
		case BundleEvent.UPDATED:
			Message msg = Message.obtain();
			msg.obj = event.getBundle().getLocation();
			mHandler.sendMessage(msg);
			break;
		case BundleEvent.STARTED:
		case BundleEvent.STOPPED:
		case BundleEvent.UNINSTALLED:
			break;
		}
	}

	private final class SecurityBundleHandler extends Handler {
		public SecurityBundleHandler(Looper looper) {
			super(looper);
		}

		@Override
		public void handleMessage(Message msg) {

	if (msg == null || isSecurityCheckFailed == true) {
				return;
			}

			String location = (String) msg.obj;
			if (TextUtils.isEmpty(location)) {
				return;
			}

			File file = Atlas.getInstance().getBundleFile(location);
			if (file == null){
				return;
			}
			
			if (!isBundleValid(file.getAbsolutePath())) {
				Log.e(TAG, "Security check failed. " + location);
				Handler handler = new Handler(Looper.getMainLooper());
				handler.post(new postInvalidBundle());
				isSecurityCheckFailed = true;
			} else {
				// Bundle is valid, double check the public key
				String[] publicKeys = ApkUtils.getApkPublicKey(file
						.getAbsolutePath());
				if (!StringUtils.contains(publicKeys, PUBLIC_KEY)) {
					Log.e(TAG, "Security check failed. " + location);
					Handler handler = new Handler(Looper.getMainLooper());
					handler.post(new postInvalidBundle());
					isSecurityCheckFailed = true;
				}
			}
			if (isSecurityCheckFailed == false){
				Log.d(TAG, "Security check success. " + location);
			}
		};
	}

	public static class ShutdownProcessHandler extends Handler {

		public void handleMessage(Message msg) {
			android.os.Process.killProcess(android.os.Process.myPid());
		}
	}

	private boolean isBundleValid(String path) {

		if (mSgManager != null) {
			IPkgValidityCheckComponent pvcComp = mSgManager
					.getPackageValidityCheckComp();
			if (pvcComp != null) {
				return pvcComp.isPackageValid(path);
			}
		}
		return false;
	}

	private class postInvalidBundle implements Runnable {

		@Override
		public void run() {
			// TODO Auto-generated method stub
			Toast.makeText(RuntimeVariables.androidApplication,
					"检测到安装文件被损坏，请卸载后重新安装！", Toast.LENGTH_LONG).show();
			shutdownProcessHandler.sendEmptyMessageDelayed(0, 5000);
		}
	}
}
