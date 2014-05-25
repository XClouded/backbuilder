#!/bin/bash
echo "start to install android pack"

if [ ! -n "$1" ]; then
  echo "can't run it, beacuse of lack of . usage: install_build.sh {branch_name}"
  exit -1
fi

echo "usage: $1=branch_name/tag_name $2=mvn_home  $3=ANDROID_HOME $4=MVN_OPT $5=MVN_OPT_FOR_BUILD"

BRANCH=$1
MVN_HOME_PRJ=$2
MVN_D_FILE=$3
MVN_OPT_INPUT=$4
MVN_OPT_INPUT_FOR_BUILD=$5
ROOT_PATH=`pwd`
BUILD_GIT_CONF_FILE="$ROOT_PATH/git.list"
BUILD_GIT_CONF_FILE_APKLIB="$ROOT_PATH/git.list.apklib"
BUILD_GIT_CONF_FILE_AAR="$ROOT_PATH/git.list.aar"
BUILD_GIT_CONF_FILE_AWB="$ROOT_PATH/git.list.awb"
BUILD_SVN_CONF_FILE_AWB="$ROOT_PATH/svn.list.awb"
BUILD_PATH="$ROOT_PATH/build-project"
BUILD_PATH_AAR="$ROOT_PATH/build-project-aar"
BUILD_PATH_APKLIB="$ROOT_PATH/build-project-apklib"
BUILD_PATH_AWB="$ROOT_PATH/build-project-awb"
BUILD_PATH_SVN_AWB="$ROOT_PATH/build-project-svn-awb"
MVN_REPO_LOCAL="$ROOT_PATH/build-repo"
ERR_RET=`mvn -v|awk '{print $3}'`
MVN_OPT="-Dmaven.repo.local=$MVN_REPO_LOCAL"
MVN_OPT_BUILD="-Dmaven.repo.local=$MVN_REPO_LOCAL"
PROGUARD_BIN="$ROOT_PATH/proguard/proguard5/proguard.jar"
THREAD_NUM=`cat /proc/cpuinfo | grep "core id" | uniq | wc -l`

if [ $THREAD_NUM > 8 ]; then
  THREAD_NUM=$((THREAD_NUM/2))
else
  THREAD_NUM=3
fi


if [  "$MVN_HOME_PRJ" ]; then
  export MAVEN_HOME=$MVN_HOME_PRJ
  export PATH=$MAVEN_HOME/bin:$PATH
  echo ">>Current Maven is $MAVEN_HOME"
fi

if [ "$MVN_D_FILE" ]; then
  export ANDROID_HOME=$MVN_D_FILE
  export PATH=$ANDROID_HOME/tools:$ANDROID_HOME/platform-tools:$PATH
  echo "set android sdk $ANDROID_HOME"
fi

if [ "$MVN_OPT_INPUT" ]; then
  export MVN_OPT="$MVN_OPT_INPUT $MVN_OPT"
fi

if [ "$MVN_OPT_INPUT_FOR_BUILD" ]; then
  export MVN_OPT_BUILD="$MVN_OPT_INPUT_FOR_BUILD $MVN_OPT_BUILD"
fi

export MVN_OPT="-Dproguard.proguardJarPath=$PROGUARD_BIN $MVN_OPT"
export MVN_OPT_BUILD="-Dproguard.proguardJarPath=$PROGUARD_BIN $MVN_OPT_BUILD"

echo "MAVEN OPT IS: $MVN_OPT"
echo "MAVEN OPT FOR BUILD IS: $MVN_OPT_BUILD"

## 从builder里拉出proguard.cfg 和mapping.txt
## 并且从build中copy install脚本和proguard.cfg过来到当前root更新确保配置是对的
function prepare_builder(){
  echo ">> start to get builder project"
  rm -rf $ROOT_PATH/taobao_builder
  git clone git@gitlab.alibaba-inc.com:build/taobao_builder.git -b $BRANCH
#  cd "$ROOT_PATH/taobao_builder"
#  git checkout $BRANCH
  cp "$ROOT_PATH/taobao_builder/proguard.cfg" "$ROOT_PATH/"
  cp "$ROOT_PATH/taobao_builder/install_base.sh" "$ROOT_PATH/"
}

##定义proguard和mapping文件
function copy_proguard_file(){
  prepare_builder
  export PROGUARD_CFG="$ROOT_PATH/taobao_builder/proguard.cfg"
  export PROGUARD_MAPPING="$ROOT_PATH/taobao_builder/mapping.txt"
  echo "PROGUARD_CFG:$PROGUARD_CFG"
  echo "PROGUARD_MAPPING:$PROGUARD_MAPPING"
}

function build_skt(){
  cd "$ROOT_PATH/taobao_builder"
  mvn install -e -Pskt $MVN_OPT -Dproguard.skip=true
}


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
  rm -rf $BUILD_PATH_SVN_AWB
  mkdir $BUILD_PATH_SVN_AWB
}


## taobaocompat是所有工程的基础
function build_taobaocompat(){
  echo ">> start to build taobaocompat"
  cd $ROOT_PATH
  rm -rf $ROOT_PATH/taobaocompat
  git clone git@gitlab.alibaba-inc.com:taobao-android/taobaocompat.git -b $BRANCH
  cd "$ROOT_PATH/taobaocompat"
  pwd
  mvn install -U -e -Papklib $MVN_OPT -Dproguard.skip=true
  mvn install -U -e -Paar $MVN_OPT -Dproguard.skip=true
  wait
  if [ $? -ne 0 ]; then
        echo "build compat error!"
        exit $?
  fi
  build_skt;
}

##编译jar或者无package配置的包
function do_jar_build(){
  echo ">>start to build bundle"
  cd $BUILD_PATH
  pwd
  git_list=$(cat $BUILD_GIT_CONF_FILE)
  i=0
  while read line ; do
    i=$((i+1))
    param_b=`echo $line | grep  -o ' \-b '`
    if [ $param_b ]; then
      git clone $line
    else
      git clone $line -b $BRANCH
    fi &
    if [ $((i%THREAD_NUM)) == 0 ]; then
      wait
    fi
  done < $BUILD_GIT_CONF_FILE
  wait
  i=0
  for file in `ls $BUILD_PATH`
  do
      i=$((i+1))
      if  test -d $file ; then
        echo ">>start to install in $file"
        cd $BUILD_PATH/$file
        #git checkout $BRANCH
        cp $PROGUARD_CFG $BUILD_PATH/$file
        cp $PROGUARD_MAPPING $BUILD_PATH/$file
        mvn install -e $MVN_OPT
        if [ $? -ne 0 ]; then
              echo "build $file error!"
              exit $?
        fi
      fi &
      if [ $((i%THREAD_NUM)) == 0 ]; then
        wait
      fi
  done
}
##编译apklib包
function do_apklib_build(){
        echo ">>start to build bundle"
        cd $BUILD_PATH_APKLIB
        pwd
        git_list=$(cat $BUILD_GIT_CONF_FILE_APKLIB)
        i=0
        while read line ; do
          i=$((i+1))
          param_b=`echo $line | grep  -o ' \-b '`
          if [ $param_b ]; then
            git clone $line
          else
            git clone $line -b $BRANCH
            #cd $BUILD_PATH_APKLIB/$file
            #git checkout $BRANCH
          fi &
          if [ $((i%THREAD_NUM)) == 0 ]; then
            wait
          fi
        done < $BUILD_GIT_CONF_FILE_APKLIB
        wait
        i=0
        for file in `ls $BUILD_PATH_APKLIB`
        do
            if  test -d $file ; then
                echo ">>start to install in $file"
                cd $BUILD_PATH_APKLIB/$file
                cp $PROGUARD_CFG $BUILD_PATH_APKLIB/$file
                cp $PROGUARD_MAPPING $BUILD_PATH_APKLIB/$file
                mvn install -e $MVN_OPT -Papklib
                if [ $? -ne 0 ]; then
                      echo "build $file error!"
                      exit $?
                fi
            fi &
            if [ $((i%THREAD_NUM)) == 0 ]; then
              wait
            fi
        done
        wait
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
                        #cd "$BUILD_PATH_AAR/$file"
                        #git checkout $BRANCH
                fi
        done < $BUILD_GIT_CONF_FILE_AAR

        for file in `ls $BUILD_PATH_AAR`
        do
            if  test -d $BUILD_PATH_AAR/$file ; then
                echo ">>start to install in $file"
                cp $PROGUARD_CFG $BUILD_PATH_AAR/$file
                cp $PROGUARD_MAPPING $BUILD_PATH_AAR/$file
                ls -l
                cd "$BUILD_PATH_AAR/$file"
                pwd
                mvn install -e $MVN_OPT -Paar
                if [ $? -ne 0 ]; then
                      echo "build $file error!"
                      exit $?
                fi
            fi
        done
}

##编译awb包
function do_awb_build_multithread(){
        echo ">>start to build bundle"
        cd $BUILD_PATH_AWB
        git_list=$(cat $BUILD_GIT_CONF_FILE_AWB)
        i=0
        while read line ; do
            i=$((i+1))
            param_b=`echo $line | grep  -o ' \-b '`
            if [ $param_b ]; then
              git clone $line &
            else
              git clone $line -b $BRANCH &
              #cd "$BUILD_PATH_AWB/$file"
              #git checkout $BRANCH
            fi
            if [ $((i%THREAD_NUM)) == 0 ]; then
              wait
            fi
        done < $BUILD_GIT_CONF_FILE_AWB
        wait
        i=0
        for file in `ls $BUILD_PATH_AWB`
        do
            i=$((i+1))
            if  test -d $BUILD_PATH_AWB/$file ; then
              echo ">>start to install in $file"
              cp $PROGUARD_CFG $BUILD_PATH_AWB/$file
              cp $PROGUARD_MAPPING $BUILD_PATH_AWB/$file
              ls -l
              cd "$BUILD_PATH_AWB/$file"
              pwd
              echo "mvn install -e $MVN_OPT -Pawb"
              mvn install -e -Pawb $MVN_OPT &

            fi
            if [ $((i%THREAD_NUM)) == 0 ]; then
              echo "wait"
              wait
              if [ $? -ne 0 ]; then
                    echo "build $file error!"
                    exit $?
              fi
            fi
        done
        wait
}

##编译awb包
function do_awb_build(){
        echo ">>start to build bundle"
        cd $BUILD_PATH_AWB
        git_list=$(cat $BUILD_GIT_CONF_FILE_AWB)
        while read line ; do
                param_b=`echo $line | grep  -o ' \-b '`
                if [ $param_b ]; then
                        git clone $line
                else
                        git clone $line -b $BRANCH
                        #cd "$BUILD_PATH_AWB/$file"
                        #git checkout $BRANCH
                fi
        done < $BUILD_GIT_CONF_FILE_AWB
        for file in `ls $BUILD_PATH_AWB`
        do
            if  test -d $BUILD_PATH_AWB/$file ; then
              echo ">>start to install in $file"
              cp $PROGUARD_CFG $BUILD_PATH_AWB/$file
              cp $PROGUARD_MAPPING $BUILD_PATH_AWB/$file
              ls -l
              cd "$BUILD_PATH_AWB/$file"
              pwd
              echo "mvn install -e $MVN_OPT -Pawb"
              mvn install -e -Pawb $MVN_OPT
              if [ $? -ne 0 ]; then
                    echo "build $file error!"
                    exit $?
              fi
            fi
        done
}

##svn下的awb项目编译
function do_awb_svn(){
  echo ">>start to build bundle with svn"
  cd $BUILD_PATH_SVN_AWB
  git_list=$(cat $BUILD_SVN_CONF_FILE_AWB)
  i=0
  while read line ; do
    i=$((i+1))
    svn co $line &
    if [ $((i%THREAD_NUM)) == 0 ]; then
      wait
    fi
  done < $BUILD_SVN_CONF_FILE_AWB
  wait
  i=0
  for file in `ls $BUILD_PATH_SVN_AWB`
  do
    i=$((i+1))
    if  test -d $BUILD_PATH_SVN_AWB/$file ; then
      echo ">>start to install in $file"
      cp $PROGUARD_CFG $BUILD_PATH_SVN_AWB/$file
      cp $PROGUARD_MAPPING $BUILD_PATH_SVN_AWB/$file
      ls -l
      cd "$BUILD_PATH_SVN_AWB/$file"
      pwd
      mvn install -e $MVN_OPT -Pawb
      if [ $? -ne 0 ]; then
            echo "build $file error!"
            exit $?
      fi
    fi &
    if [ $((i%THREAD_NUM)) == 0 ]; then
      wait
    fi
  done
  wait
}

##编译builder
function do_builder(){
  echo "start to builder apk main"
  cd $ROOT_PATH
  pwd
  #git checkout $BRANCH
  mvn clean package -e $MVN_OPT_BUILD
  if [ $? -ne 0 ]; then
        exit $?
  fi
}

##编译本工程
function build_self_awb(){
	cd $ROOT_PATH
  pwd
  #git checkout $BRANCH
	mvn clean install -e -Pawb $MVN_OPT
  if [ $? -ne 0 ]; then
        exit $?
  fi
}

##编译本工程
function build_self_apk(){
  cd $ROOT_PATH
  pwd
  #git checkout $BRANCH
  mvn clean install -e -Papk $MVN_OPT_BUILD
  if [ $? -ne 0 ]; then
        exit $?
  fi
}

##编译本工程
function build_self(){
  cd $ROOT_PATH
  pwd
  #git checkout $BRANCH
  mvn clean install -e $MVN_OPT_BUILD
  if [ $? -ne 0 ]; then
        exit $?
  fi
}

init_path;
copy_proguard_file;
