//package com.taobao.url.test;
//
//import android.app.Activity;
//import android.app.Instrumentation;
//import android.content.Intent;
//import android.test.InstrumentationTestCase;
//import android.util.Log;
//import android.widget.Button;
//import android.widget.EditText;
//import com.robotium.solo.Solo;
//import com.taobao.taobao.R;
//
//
///**
// * Created by lvshan on 14-4-22.
// */
//public class UrlTestCase extends InstrumentationTestCase {
//    private Solo solo;
//
//    @Override
//    public void setUp() throws Exception {
//        super.setUp();
//        solo = new Solo(this.getInstrumentation());
//        Log.e("test", "test start");
//    }
//
//    @Override
//    public void tearDown() throws Exception {
//        solo.finishOpenedActivities();
//        Log.e("test", "test finish");
//    }
//
//    public void testUrl() {
//        Instrumentation instrumentation = this.getInstrumentation();
//        Intent intent = new Intent(Intent.ACTION_MAIN);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
//        intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
//        intent.addCategory(Intent.CATEGORY_LAUNCHER);
//
//        intent.setClass(instrumentation.getTargetContext(), UrlTestActivity.class);
//        Activity currentActivity = instrumentation.startActivitySync(intent);
//        Log.e("test","I am here");
//        if(solo.waitForActivity(UrlTestActivity.class)) {
//            Log.e("test", solo.getCurrentActivity().getComponentName().toShortString());
//            doVisitor("http://a.m.tmall.com/i37505240912.htm", currentActivity);
//            doVisitor("http://fenl.m.tmall.com/?sid=35e5334c5c7fd37bc8f1ff83151133c6", currentActivity);
//        }
//    }
//
//    public void doVisitor(String url,Activity currentActivity){
//        EditText editTextUrl = (EditText) currentActivity.findViewById(R.id.etUrl);
//        solo.enterText(editTextUrl, url);
//        Button btnReset = (Button) currentActivity.findViewById(R.id.btnView);
//        solo.clickOnView(btnReset);
//        solo.sleep(3000);
//        Log.e("test", solo.getCurrentActivity().getComponentName().toString());
//        solo.goBack();
//    }
//}