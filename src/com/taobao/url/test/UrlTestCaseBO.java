package com.taobao.url.test;

/**
 * Created by lvshan on 14-5-6.
 */
public class UrlTestCaseBO {

    private String bizName;

    public String getBizName() {
        return bizName;
    }

    public void setBizName(String bizName) {
        this.bizName = bizName;
    }

    private String testUrl;
    private String iosExpectResult;
    private String androidExpectResult;

    public String getTestUrl() {
        return testUrl;
    }

    public void setTestUrl(String testUrl) {
        this.testUrl = testUrl;
    }

    public String getIosExpectResult() {
        return iosExpectResult;
    }

    public void setIosExpectResult(String iosExpectResult) {
        this.iosExpectResult = iosExpectResult;
    }

    public String getAndroidExpectResult() {
        return androidExpectResult;
    }

    public void setAndroidExpectResult(String androidExpectResult) {
        this.androidExpectResult = androidExpectResult;
    }
}
