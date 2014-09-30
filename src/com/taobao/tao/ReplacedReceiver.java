package com.taobao.tao;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class ReplacedReceiver extends BroadcastReceiver {

    static final String TAG = "ReplacedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        // 应用覆盖安装时，接收消息启动TaobaoApplication，自动完成Bundle安装
        Log.d(TAG, "onReceive: " + intent == null ? "null" : intent.getAction());
        //尝试更新
//        Updater.getInstance((Application)context).update(true);
    }

}
