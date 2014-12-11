package com.taobao.tao.atlaswrapper;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import android.util.Log;

public class ParseAtlasMetaUtil {
	final static String TAG = "ParseAtlasMetaUtil";
	private static AtlasMetaInfo metaInfo;
	
	public static List<AtlasMetaInfo> parseAtlasMetaInfo(File path){
		List<AtlasMetaInfo> metaList = new ArrayList<AtlasMetaInfo>();
		if(path==null){
			Log.w(TAG, "please input atlas meta path!");
			return metaList;
		}
		File[] metaFiles = path.listFiles();		
		for(File metaFile:metaFiles){
			// bundle location path
			if(metaFile.isDirectory()){
				metaInfo = new AtlasMetaInfo();
				parsePackageMetaInfo(metaFile,metaInfo);
				File[] bundleFiles = metaFile.listFiles(new FileFilter(){
					@Override
					public boolean accept(File pathname) {
						return pathname.isDirectory();
					}				
				});			
				if(bundleFiles.length >1){
					SortedMap<Long,File> metaMap = new TreeMap<Long,File>();
					for(File bundleFile:bundleFiles){
						// bundle storage path
						if(metaFile.isDirectory()){
							String pathName = bundleFile.getName();
							String sn = pathName.substring(pathName.indexOf(".")+1, pathName.length());
							metaMap.put(Long.parseLong(sn), bundleFile);
						}
					}
					parseStorageMetaInfo(metaMap.get(metaMap.lastKey()),metaInfo);
				}else{
					parseStorageMetaInfo(bundleFiles[0],metaInfo);
				}
				metaList.add(metaInfo);
			}
		}
		return metaList;
	}
	private static void parsePackageMetaInfo(File path,AtlasMetaInfo metaInfo){
		String packageName = null;
		File metaFile = new File(path,"meta");
		DataInputStream input = null;
		if(metaFile.exists()){
			try {
				input = new DataInputStream(new BufferedInputStream(new FileInputStream(metaFile)));
				long bundleID = input.readLong();//bundle id
				packageName = input.readUTF(); //bundle package
				int bundleStartLevel = input.readInt(); // bundle currentStartlevel
				boolean bundlePersistently = input.readBoolean();// bundle persistently
				metaInfo.bundleId = bundleID;
				metaInfo.packageName = packageName;
				metaInfo.startLevel = bundleStartLevel;
				metaInfo.isPersistently = bundlePersistently;
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				Log.e(TAG, "parse package info error : "+packageName+" "+e.getMessage());
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(TAG, "parse package info error : "+packageName+" "+e.getMessage());
			}finally{
				if(input !=null){
					try {
						input.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
	}
	private static void parseStorageMetaInfo(File path,AtlasMetaInfo metaInfo){		
		File bundleFile = null;
		File metaFile = new File(path,"meta");
		DataInputStream input = null;
		if(metaFile.exists()){
			try {
				input = new DataInputStream(new BufferedInputStream(new FileInputStream(metaFile)));
				String location = input.readUTF();
				int referenceIndex = location.lastIndexOf("reference:");
				int fileIndex = location.lastIndexOf("file:");
				if(referenceIndex > -1){
					bundleFile = new File(location.substring(referenceIndex+"reference:".length()));
				}
				if(fileIndex > -1){
					bundleFile = new File(path,"bundle.zip");
				}
				Log.i(TAG, "bundle file: "+bundleFile);			
			} catch (FileNotFoundException e) {
				e.printStackTrace();
				Log.e(TAG, "parse bundle info error : "+bundleFile+" "+e.getMessage());
			} catch (IOException e) {
				e.printStackTrace();
				Log.e(TAG, "parse bundle info error : "+bundleFile+" "+e.getMessage());
			}finally{
				if(input !=null){
					try {
						input.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
		}
		metaInfo.bundleFile = bundleFile;
	}
	public static final class AtlasMetaInfo{
		private String packageName;
		private long bundleId;
		private int startLevel;
		private boolean isPersistently;
		private File bundleFile;
		public String getPackageName() {
			return packageName;
		}
		public long getBundleId() {
			return bundleId;
		}
		public int getStartLevel() {
			return startLevel;
		}
		public boolean isPersistently() {
			return isPersistently;
		}
		public File getBundleFile() {
			return bundleFile;
		}
	}
}
