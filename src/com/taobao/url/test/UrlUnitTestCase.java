package com.taobao.url.test;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.test.AndroidTestCase;
import android.text.TextUtils;
import android.util.Log;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.taobao.android.nav.Nav;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by lvshan on 14-5-6.
 */
public class UrlUnitTestCase extends AndroidTestCase {
    public static String TESTCASE_RPC = "http://mtl.alibaba-inc.com/mcip/rpc/url/getTestCase.json";
    public void testUrlNav() throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        //获取buildId
        String arg = this.getContext().getSharedPreferences("global", 0).getString("arg", "");
        if (TextUtils.isEmpty(arg)) {
            return;
        }
        String buildId = arg;

//        //获取服务端测试用例
//        AndroidHttpClientUtils httpClientUtils=new AndroidHttpClientUtils(this.getContext(),buildId);
//
//        new Thread(httpClientUtils).start();

        AndroidHttpClientUtils httpClientUtils = new AndroidHttpClientUtils(this.getContext(), buildId);
        Map<String, String> params = new HashMap<String, String>();
        String result = httpClientUtils.sendHttpClientPost(TESTCASE_RPC, params, "utf-8");
        Log.e("test", result);
        if (result != null) {
            RpcResultBO rpcResult = JSON.parseObject(result,
                    RpcResultBO.class);
            Log.e("test", "test Start");
            List<UrlTestCaseBO> testCaseList = JSON.parseObject(rpcResult.getContent(),
                    new TypeReference<List<UrlTestCaseBO>>() {
                    }
            );
            Log.e("test", "测试用例总数-->" + testCaseList.size());

            //执行测试
            Method getIntent = null;
            try {
                getIntent = Nav.class.getDeclaredMethod("to", Uri.class);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            getIntent.setAccessible(true);

            Method optimumIntent = null;
            try {
                optimumIntent = Nav.class.getDeclaredMethod("optimum", List.class);
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            }
            optimumIntent.setAccessible(true);

            final PackageManager packageManager = this.getContext().getPackageManager();
            if (testCaseList != null && !testCaseList.isEmpty()) {
                for (UrlTestCaseBO testcase : testCaseList) {
                    try {
                        String actualResult = visit(testcase.getTestUrl(), packageManager, getIntent, optimumIntent);

                        //上报执行结果
                        params = new HashMap<String, String>();
                        params.put("buildId", buildId);
                        params.put("bizName", testcase.getBizName());
                        params.put("testUrl", testcase.getTestUrl());
                        params.put("expectResult", testcase.getAndroidExpectResult());
                        params.put("actualResult", actualResult);
                        params.put("platform", "android");
                        httpClientUtils.sendHttpClientPost("http://mtl.alibaba-inc.com/mcip/rpc/url/updateResult.json", params, "utf-8");

                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    private String visit(String testUrl,PackageManager packageManager,Method getIntent,Method optimumIntent) throws InvocationTargetException, IllegalAccessException {
        if(TextUtils.isEmpty(testUrl))return "TestUrl is blank";
        Uri uri = Uri.parse(testUrl);
        Intent newIntent=(Intent)  getIntent.invoke(Nav.from(this.getContext()),uri);

        List<ResolveInfo> resolveInfoList =
                packageManager.queryIntentActivities(newIntent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        ResolveInfo result=(ResolveInfo) optimumIntent.invoke(Nav.from(this.getContext()),resolveInfoList);
        Log.e("test", result.activityInfo.name);
        return result.activityInfo.name;
    }
}
