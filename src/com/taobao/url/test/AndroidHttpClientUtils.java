/*
 * Copyright 2013 Alibaba.com All right reserved. This software is the
 * confidential and proprietary information of Alibaba.com ("Confidential
 * Information"). You shall not disclose such Confidential Information and shall
 * use it only in accordance with the terms of the license agreement you entered
 * into with Alibaba.com.
 */
package com.taobao.url.test;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.taobao.android.nav.Nav;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.message.BasicNameValuePair;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 类AndroidHttpClientUtils.java的实现描述：TODO 类实现描述
 *
 * @author shan.lvs 2013-10-8 下午9:07:36
 */
public class AndroidHttpClientUtils implements Runnable {

    public static String TESTCASE_RPC = "http://mtl.alibaba-inc.com/mcip/rpc/url/getTestCase.json";
    private Context context;
    private String buildId;

    public AndroidHttpClientUtils(Context _context, String _buildId) {
        context = _context;
        buildId = _buildId;
    }

    @Override
    public void run() {
        // 设置Http请求参数
        Map<String, String> params = new HashMap<String, String>();
        String result = sendHttpClientPost(TESTCASE_RPC, params, "utf-8");
        Log.e("test", result);
        if (result != null) {
            RpcResultBO rpcResult = JSON.parseObject(result,
                    RpcResultBO.class);
            Log.e("test", rpcResult.getContent());
            Message msg = new Message();
            Bundle responseData = new Bundle();
            responseData.putString("testcase", rpcResult.getContent());
            responseData.putString("buildId", buildId);
            msg.setData(responseData);
            handler.sendMessage(msg);
        }
        // 把返回的接口输出
    }

    /**
     * 发送Http请求到Web站点
     *
     * @param path   Web站点请求地址
     * @param map    Http请求参数
     * @param encode 编码格式
     * @return Web站点响应的字符串
     */
    public String sendHttpClientPost(String path, Map<String, String> map, String encode) {
        List<NameValuePair> list = new ArrayList<NameValuePair>();
        if (map != null && !map.isEmpty()) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                // 解析Map传递的参数，使用一个键值对对象BasicNameValuePair保存。
                list.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
            }
        }
        AndroidHttpClient client = null;
        try {
            // 实现将请求 的参数封装封装到HttpEntity中。
            UrlEncodedFormEntity entity = new UrlEncodedFormEntity(list, encode);
            // 使用HttpPost请求方式
            HttpPost httpPost = new HttpPost(path);
            // 设置请求参数到Form中。
            httpPost.setEntity(entity);
            // 实例化一个默认的Http客户端，使用的是AndroidHttpClient
            client = AndroidHttpClient.newInstance("");
            // 执行请求，并获得响应数据
            HttpResponse httpResponse = client.execute(httpPost);
            // 判断是否请求成功，为200时表示成功，其他均问有问题。
            if (httpResponse.getStatusLine().getStatusCode() == 200) {
                // 通过HttpEntity获得响应流
                InputStream inputStream = httpResponse.getEntity().getContent();
                return changeInputStream(inputStream, encode);
            }

        } catch (UnsupportedEncodingException e) {
            Log.e("mylog", "请求异常-->" + e.getMessage());
        } catch (ClientProtocolException e) {
            Log.e("mylog", "请求异常-->" + e.getMessage());
        } catch (IOException e) {
            Log.e("mylog", "请求异常-->" + e.getMessage());
        } finally {
            if (client != null) {
                client.close();
            }
        }

        return null;
    }

    /**
     * 把Web站点返回的响应流转换为字符串格式
     *
     * @param inputStream 响应流
     * @param encode      编码格式
     * @return 转换后的字符串
     */
    private String changeInputStream(InputStream inputStream, String encode) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        byte[] data = new byte[1024];
        int len = 0;
        String result = "";
        if (inputStream != null) {
            try {
                while ((len = inputStream.read(data)) != -1) {
                    outputStream.write(data, 0, len);
                }
                result = new String(outputStream.toByteArray(), encode);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    Handler handler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            Bundle data = msg.getData();
            String val = data.getString("testcase");
            String buildId = data.getString("buildId");
            Log.e("test", "start run test,testcase is " + val + ",buildId is " + buildId);
            List<UrlTestCaseBO> testCaseList = JSON.parseObject(val,
                    new TypeReference<List<UrlTestCaseBO>>() {
                    }
            );
            Log.e("num of testcase", "请求结果-->" + testCaseList.size());


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

            final PackageManager packageManager = context.getPackageManager();
            if (testCaseList != null && !testCaseList.isEmpty()) {
                for (UrlTestCaseBO testcase : testCaseList) {
                    try {
                        String actualResult = visit(testcase.getTestUrl(), packageManager, getIntent, optimumIntent);

                        //上报执行结果
                        Map<String, String> params = new HashMap<String, String>();
                        params.put("buildId", buildId);
                        params.put("bizName", testcase.getBizName());
                        params.put("testUrl", testcase.getTestUrl());
                        params.put("expectResult", testcase.getAndroidExpectResult());
                        params.put("actualResult", actualResult);
                        params.put("platform", "android");
                        sendHttpClientPost("http://mtl.alibaba-inc.com/mcip/rpc/url/updateResult.json", params, "utf-8");

                    } catch (InvocationTargetException e) {
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        e.printStackTrace();
                    }
                }
            }

        }
    };

    private String visit(String testUrl, PackageManager packageManager, Method getIntent, Method optimumIntent) throws InvocationTargetException, IllegalAccessException {
        Uri uri = Uri.parse(testUrl);
        Intent newIntent = (Intent) getIntent.invoke(Nav.from(context), uri);

        List<ResolveInfo> resolveInfoList =
                packageManager.queryIntentActivities(newIntent,
                        PackageManager.MATCH_DEFAULT_ONLY);
        ResolveInfo result = (ResolveInfo) optimumIntent.invoke(Nav.from(context), resolveInfoList);
        Log.e("test", result.activityInfo.name);
        return result.activityInfo.name;
    }
}
