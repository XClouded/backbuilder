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

import java.lang.Exception;

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
		DimensionSet dimensionSet = DimensionSet.create().addDimension("TypeID").addDimension("Detail").addDimension("BundleName").addDimension("diskLeverage");
		MeasureSet measureSet = MeasureSet.create().addMeasure("remainDisk");
		AppMonitor.register("Atlas", "Monitor", measureSet, dimensionSet);
            } catch (Exception e){
            }
	}
	
	public void trace(String TypeID, String BundleName, String Detail, String remainedDisk){
            try{
		AppMonitor.Stat.commit("Atlas", "Monitor",
				interactiveDSet.setValue("TypeID", TypeID).setValue("Detail", Detail).setValue("BundleName", BundleName).setValue("diskLeverage", getDiskLeverage(remainedDisk)),
				interactiveMSet.setValue("remainDisk", Double.parseDouble(remainedDisk)));
            } catch (Exception e){
            }
	}
	
	public void trace(Integer TypeID, String BundleName, String Detail, String remainedDisk){
            try{
		AppMonitor.Stat.commit("Atlas", "Monitor",
				interactiveDSet.setValue("TypeID", TypeID.toString()).setValue("Detail",Detail).setValue("BundleName", BundleName).setValue("diskLeverage", getDiskLeverage(remainedDisk)),
				interactiveMSet.setValue("remainDisk", Double.parseDouble(remainedDisk)));
            } catch (Exception e){
            }
	}

	private String getDiskLeverage(String remainedDisk){
		String leverage = "";
		try {
			long diskSize = Long.parseLong(remainedDisk);

			if (diskSize <= 50) {
				leverage = "0-50";
			} else if (diskSize <= 100) {
				leverage = "50-100";
			} else if (diskSize <= 200) {
				leverage = "100-200";
			} else if (diskSize <= 500) {
				leverage = "200-500";
			} else {
				leverage = "500+";
			}
		}catch (Exception e){
		}

		return leverage;
	}
}
