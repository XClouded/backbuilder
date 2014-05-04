package com.taobao.url.test;

import android.app.Instrumentation;
import android.test.InstrumentationTestCase;
import android.util.Log;
import com.robotium.solo.Solo;
import com.taobao.android.nav.Nav;


/**
 * Created by lvshan on 14-4-22.
 */
public class UrlTestCase extends InstrumentationTestCase {
    private Solo solo;

    @Override
    public void setUp() throws Exception {
        solo = new Solo(this.getInstrumentation());
        super.setUp();
        //获取所有的测试用例
        Log.e("test", "test start");
    }

    @Override
    public void tearDown() throws Exception {
        solo.finishOpenedActivities();
        Log.e("test", "test finish");
    }

    public void testUrl() {
        //doVisitor( "http://a.m.tmall.com/i37505240912.htm");
        doVisitor( "http://fenl.m.tmall.com/?sid=35e5334c5c7fd37bc8f1ff83151133c6");
    }

    private void doVisitor(String url){
        Instrumentation instrumentation = this.getInstrumentation();
        Nav.from(instrumentation.getContext()).toUri(url);
//        Intent intent = new Intent(Intent.ACTION_MAIN);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
//        intent.addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
//        intent.addCategory(Intent.CATEGORY_LAUNCHER);
//
//        Log.e("test", "testUrl is " + url);
//        intent.putExtra("testUrl", url);
//        intent.setClassName(instrumentation.getTargetContext(), "com.taobao.taobao.test.TestUrlNavActivity");
//        Activity currentActivity = instrumentation.startActivitySync(intent);

        Log.e("test", "testStart now... ");
        solo.sleep(30000);
        Log.e("test",instrumentation.getComponentName().toString());
//        Log.e("test", currentActivity.getComponentName().toString());
//        solo.goBack();
    }
}
