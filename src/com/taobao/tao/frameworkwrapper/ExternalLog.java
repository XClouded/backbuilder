package com.taobao.tao.frameworkwrapper;

import com.taobao.tao.log.TLog;
import android.taobao.atlas.log.ILog;

public class ExternalLog implements ILog{
    public void v(String tag,String msg){
    	TLog.logi(tag, msg);
    }
    
    public void i(String tag,String msg){
    	TLog.logi(tag, msg);
    }
    
    public void d(String tag,String msg){
    	TLog.logd(tag, msg);
    }
    
    public void w(String tag,String msg){
    	TLog.logw(tag, msg);
    }
    
    public void e(String tag,String msg){
    	TLog.loge(tag, msg);
    }
    
    public void e(String tag,String msg,Throwable e){
    	TLog.loge(tag, msg, e);
    }
    
}
