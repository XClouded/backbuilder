package com.taobao.tao.frameworkwrapper;

import android.app.Application;
import android.content.Context;
import android.taobao.atlas.util.IMonitor;
import com.alibaba.mtl.appmonitor.AppMonitor;
import com.alibaba.mtl.appmonitor.model.DimensionSet;
import com.alibaba.mtl.appmonitor.model.DimensionValueSet;
import com.alibaba.mtl.appmonitor.model.MeasureSet;
import com.alibaba.mtl.appmonitor.model.MeasureValueSet;
import com.taobao.tao.TaoPackageInfo;
import com.taobao.tao.util.GetAppKeyFromSecurity;

/**
 * Created by guanjie on 15/7/8.
 */
public class AtlasMonitorImpl implements IMonitor{
    private DimensionValueSet interactiveDSet = DimensionValueSet.create();
    private MeasureValueSet interactiveMSet = MeasureValueSet.create();
	
	public AtlasMonitorImpl(Application application){ 
            try{
		AppMonitor.init(application);
		AppMonitor.setRequestAuthInfo(true, GetAppKeyFromSecurity.getAppKey(0),null);
		AppMonitor.setChannel(TaoPackageInfo.getTTID());
		DimensionSet dimensionSet = DimensionSet.create().addDimension("TypeID").addDimension("Detail").addDimension("BundleName");
		MeasureSet measureSet = MeasureSet.create().addMeasure("remainDisk");
		AppMonitor.register("Atlas", "Monitor", measureSet, dimensionSet);
            } catch (Exception e){
            }
	}
	
	public void trace(String TypeID, String BundleName, String Detail, String remainedDisk){
            try{
		AppMonitor.Stat.commit("Atlas", "Monitor", interactiveDSet.setValue("TypeID", TypeID).setValue("Detail",Detail).setValue("BundleName", BundleName),
                interactiveMSet.setValue("remainDisk", Double.parseDouble(remainedDisk)));
            } catch (Exception e){
            }
	}
	
	public void trace(Integer TypeID, String BundleName, String Detail, String remainedDisk){
            try{
		AppMonitor.Stat.commit("Atlas", "Monitor", interactiveDSet.setValue("TypeID", TypeID.toString()).setValue("Detail",Detail).setValue("BundleName", BundleName),
                interactiveMSet.setValue("remainDisk", Double.parseDouble(remainedDisk)));
            } catch (Exception e){
            }
	}
}
