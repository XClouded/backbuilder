package com.taobao.url.test;

/**
 * Created by lvshan on 14-5-6.
 */
public class RpcResultBO {
    private boolean hasError;

    private String content;

    public boolean isHasError() {
        return hasError;
    }

    public void setHasError(boolean hasError) {
        this.hasError = hasError;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
