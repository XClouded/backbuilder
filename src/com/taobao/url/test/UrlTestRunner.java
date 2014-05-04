package com.taobao.url.test;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.test.InstrumentationTestRunner;

/**
 * Created by lvshan on 14-4-22.
 */
public class UrlTestRunner extends InstrumentationTestRunner {
    public void onCreate(Bundle arguments) {
        SharedPreferences sp = getTargetContext().getSharedPreferences("global", 0);
        sp.edit().clear().commit();

        String inputArg = arguments.getString("arg");
        if (inputArg != null) sp.edit().putString("arg", inputArg).commit();
        super.onCreate(arguments);
    }

    @Override
    public void onStart() {
        // 首先设定设备，防止在运行过程中被锁定
//        tryToAcquireWakeLock();
//        tryToAcquireKeyguardLock();

        super.onStart();
    }

}
