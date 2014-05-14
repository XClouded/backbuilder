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
BUILD_GIT_CONF_FILE_APKLIB="$ROOT_PATH/git.list.apklib"
BUILD_GIT_CONF_FILE_AAR="$ROOT_PATH/git.list.aar"
BUILD_GIT_CONF_FILE_AWB="$ROOT_PATH/git.list.awb"
BUILD_PATH="$ROOT_PATH/build-project"
BUILD_PATH_AAR="$ROOT_PATH/build-project-aar"
BUILD_PATH_APKLIB="$ROOT_PATH/build-project-apklib"
BUILD_PATH_AWB="$ROOT_PATH/build-project-awb"
MVN_REPO_LOCAL="$ROOT_PATH/build-repo"
ERR_RET=`mvn -v|awk '{print $3}'`
MVN_OPT="-Dmaven.repo.local=$MVN_REPO_LOCAL"
if [  $MVN_HOME_PRJ ]; then
  export MAVEN_HOME=$MVN_HOME_PRJ
  export PATH=$MAVEN_HOME/bin:$PATH
  echo ">>Current Maven is $MAVEN_HOME"
fi


##初始化目录
function init_path(){
  echo ">>remove work path"
  rm -rf $BUILD_PATH
  mkdir $BUILD_PATH
  rm -rf $BUILD_PATH_APKLIB
  mkdir $BUILD_PATH_APKLIB
  rm -rf $BUILD_PATH_AAR
  mkdir $BUILD_PATH_AAR
  rm -rf $BUILD_PATH_AWB
  mkdir $BUILD_PATH_AWB
  rm -rf $MVN_REPO_LOCAL
  mkdir $MVN_REPO_LOCAL
}


## taobaocompat是所有工程的基础
function build_taobaocompat(){
  echo ">> start to build taobaocompat"
  rm -rf $ROOT_PATH/taobaocompat
  git clone git@gitlab.alibaba-inc.com:taobao-android/taobaocompat.git -b $BRANCH
  cd $ROOT_PATH/taobaocompat
  mvn install -U -e $MVN_OPT -Papklib
  mvn install -U -e $MVN_OPT -Paar
}

##编译jar或者无package配置的包
function do_jar_build(){
  echo ">>start to build bundle"
  cd $BUILD_PATH
  git_list=$(cat $BUILD_GIT_CONF_FILE)
  while read line ; do
    if [ !$line ]; then
      continue
    fi
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
##编译apklib包
function do_apklib_build(){
        echo ">>start to build bundle"
        cd $BUILD_PATH_APKLIB
        git_list=$(cat $BUILD_GIT_CONF_FILE_APKLIB)
        while read line ; do
          if [ !$line ]; then
            continue
          fi
                param_b=`echo $line | grep  -o ' \-b '`
                if [ $param_b ]; then
                        git clone $line
                else
                        git clone $line -b $BRANCH
                fi
        done < $BUILD_GIT_CONF_FILE_APKLIB

        for file in `ls $BUILD_PATH_APKLIB`
        do
            if  test -d $file ; then
                        echo ">>start to install in $file"
                cd $BUILD_PATH_APKLIB/$file
                mvn install -e $MVN_OPT -Papklib
            fi
        done
}
##编译aar包
function do_aar_build(){
        echo ">>start to build bundle"
        cd $BUILD_PATH_AAR
        git_list=$(cat $BUILD_GIT_CONF_FILE_AAR)
        while read line ; do
          if [ !$line ]; then
            continue
          fi
                param_b=`echo $line | grep  -o ' \-b '`
                if [ $param_b ]; then
                        git clone $line
                else
                        git clone $line -b $BRANCH
                fi
        done < $BUILD_GIT_CONF_FILE_AAR

        for file in `ls $BUILD_PATH_AAR`
        do
            if  test -d $file ; then
                        echo ">>start to install in $file"
                cd $BUILD_PATH_AAR/$file
                        mvn install -e $MVN_OPT -Paar
            fi
        done
}
##编译awb包
function do_awb_build(){
        echo ">>start to build bundle"
        cd $BUILD_PATH_AWB
        git_list=$(cat $BUILD_GIT_CONF_FILE_AWB)
        while read line ; do
	#echo $line
                param_b=`echo $line | grep  -o ' \-b '`
                if [ $param_b ]; then
                        git clone $line
                else
                        git clone $line -b $BRANCH
                fi
        done < $BUILD_GIT_CONF_FILE_AWB
        for file in `ls $BUILD_PATH_AWB`
        do
            if  test -d $file ; then
              echo ">>start to install in $file"
              cd $BUILD_PATH_AWB/$file
              mvn install -e $MVN_OPT -Pawb -Dproguard.skip=false
            fi
        done
}

##编译builder
function do_builder(){
  echo "start to builder apk main"
  cd $ROOT_PATH
  mvn clean package -e $MVN_OPT -Dproguard.skip=false
}

##编译本工程
function build_self_awb(){
	cd $ROOT_PATH
	mvn clean install -e -Pawb $MVN_OPT
}

##编译本工程
function build_self_apk(){
  cd $ROOT_PATH
  mvn clean install -e -Papk $MVN_OPT
}

##编译本工程
function build_self(){
  cd $ROOT_PATH
  mvn clean install -e  $MVN_OPT
}

init_path;
