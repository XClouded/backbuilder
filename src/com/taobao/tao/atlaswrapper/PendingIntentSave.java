package com.taobao.tao.atlaswrapper;

import java.io.Serializable;
import java.lang.reflect.Method;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PendingIntentSave {
    protected static PendingIntentSave instance;
    Intent mIntent2Write;
    Intent mIntent2Read;
    
    private PendingIntentSave(){
    	mIntent2Write = new Intent();
    }

    public static synchronized PendingIntentSave getInstance() {
        if (instance == null) {
            instance = new PendingIntentSave();
        }
        return instance;
    }
	
	public Serializable getData(String key, Context context){
		if (android.os.Build.VERSION.SDK_INT <= 18){
			return null;
		}
		
		try{
			PendingIntent pi = PendingIntent.getActivity(context, 0, mIntent2Write, PendingIntent.FLAG_NO_CREATE);
			if (pi == null){
				return null;
			}
			Class<?> cls = pi.getClass();
			Method method = cls.getMethod("getIntent", new Class[]{});
			method.setAccessible(true);
			mIntent2Read = (Intent) method.invoke(pi);
	
			if (mIntent2Read != null){
				return mIntent2Read.getSerializableExtra(key);
			}
		} catch(Exception e){
			return null;
		}
		return null;
	}
	
	public void saveData(String key, Serializable data){
		if (android.os.Build.VERSION.SDK_INT <= 18){
			return;
		}
		try{
			mIntent2Write.putExtra(key, data);
		} catch(Exception e){
		}		
	}
	
	public void commit(Context context){
			if (android.os.Build.VERSION.SDK_INT <= 18){
				return;
			}
			try{
				PendingIntent.getActivity(context, 0, mIntent2Write, PendingIntent.FLAG_CANCEL_CURRENT);
			}catch(Exception e){
			}
	}
}
