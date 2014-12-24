package com.taobao.tao.atlaswrapper;

public class InstallSolutionConfig {
	
    //bundle install/dexopt when onCreate
	public static final boolean install_when_oncreate = true;
    
    // bundle install/dexopt when onReceive PACKAG_REPLACED intent
	public static final boolean install_when_onreceive= true;
	
	// bundle install/dexopt when findClass
	public static final boolean install_when_findclass = true;	

}