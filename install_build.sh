#!/bin/bash
echo "start to install android pack"

if [ ! -n "$1" ]; then
	echo "can't run it, beacuse of lack of . usage: install_build.sh {branch_name}"
	exit -1
fi

BRANCH=$1
MVN_HOME_PRJ=$2
MVN_D_FILE=$3
ROOT_PATH=`pwd`
BUILD_GIT_CONF_FILE="$ROOT_PATH/git.list"
BUILD_PATH="$ROOT_PATH/build-project"
MVN_REPO_LOCAL="$ROOT_PATH/build-repo"
ERR_RET=`mvn -v|awk '{print $3}'`
MVN_OPT="-Dmaven.repo.local=$MVN_REPO_LOCAL"
if [  $MVN_HOME_PRJ ]; then
	export MAVEN_HOME=$MVN_HOME_PRJ
	export PATH=$MAVEN_HOME/bin:$PATH
	echo ">>Current Maven is $MAVEN_HOME"
fi
if [ $MVN_D_FILE -a -f $MVN_D_FILE ]; then
	$BUILD_GIT_CONF_FILE=$MVN_D_FILE
	echo ">>current build file:$BUILD_GIT_CONF_FILE"
fi


##初始化目录
function init_path(){
	echo ">>remove work path"
	rm -rf $BUILD_PATH
	mkdir $BUILD_PATH
	rm -rf $MVN_REPO_LOCAL
	mkdir $MVN_REPO_LOCAL
}


## taobaocompat是所有工程的基础
function build_taobaocompat(){
	echo ">> start to build taobaocompat"
	cd $ROOT_PATH
	rm -rf taobaocompat
	git clone git@gitlab.alibaba-inc.com:taobao-android/taobaocompat.git -b $BRANCH
	cd taobaocompat
	mvn install -U -e $MVN_OPT 
}


function doBundleBuilder(){
	echo ">>start to build bundle"
	cd $BUILD_PATH
	git_list=$(cat $BUILD_GIT_CONF_FILE)
	while read line ; do
		param_b=`echo $line | grep  -o ' \-b '`
		if [ $param_b ]; then
			git clone $line
		else
			git clone $line -b $BRANCH
		fi
	done < $BUILD_GIT_CONF_FILE

	for file in `ls $BUILD_PATH`
	do
	    if  test -d $file ; then
			echo ">>start to install in $file"
	        cd $BUILD_PATH/$file
			mvn install -e $MVN_OPT
	    fi
	done
}

function doBuilder(){
	echo "start to builder apk main"
	cd $ROOT_PATH
	mvn clean package -e $MVN_OPT
}

init_path;
build_taobaocompat;
doBundleBuilder;
doBuilder;






