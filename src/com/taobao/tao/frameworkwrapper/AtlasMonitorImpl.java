package com.taobao.tao.frameworkwrapper;

import android.taobao.atlas.util.IMonitor;
import com.taobao.statistic.TBS;

/**
 * Created by guanjie on 15/7/8.
 */
public class AtlasMonitorImpl implements IMonitor{
    public void trace(String num, String arg1, String arg2, String detail){
        TBS.Ext.commitEvent(61005, num, arg1, arg2, detail);
    }

    public void trace(Integer num, String arg1, String arg2, String detail){
        TBS.Ext.commitEvent(61005, num.toString(), arg1, arg2, detail);
    }
}
