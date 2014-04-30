#!/bin/bash
echo "start to install android pack"

if [ ! -n "$1" ]; then
	echo "can't run it, beacuse of lack of . usage: install_build.sh {branch_name}"
	exit
fi

BRANCH=$1

ROOT_PATH=`pwd`
BUILD_PATH="$ROOT_PATH/build-project"
rm -rf $BUILD_PATH
mkdir $BUILD_PATH


cd $ROOT_PATH
git clone git@gitlab.alibaba-inc.com:taobao-android/taobaocompat.git -b $BRANCH
cd taobaocompat
mvn install -U -e
cd ..

GITS=(
"git@gitlab.alibaba-inc.com:taobao-android/taobao_android_scancode.git"
"git@gitlab.alibaba-inc.com:taobao-android/allspark_android.git"
"git@gitlab.alibaba-inc.com:taobao-android/mytaobao.git"
"git@gitlab.alibaba-inc.com:taobao-android/nearby.git"
"git@gitlab.alibaba-inc.com:taobao-android/taobao_browser.git"
"git@gitlab.alibaba-inc.com:taobao-android/taobao_android_coupon.git"
"git@gitlab.alibaba-inc.com:taobao-android/taobao_wx.git"
"git@gitlab.alibaba-inc.com:taobao-android/taobao_android_trade.git"
"git@gitlab.alibaba-inc.com:taobao-android/tbsearch_android.git"
"git@gitlab.alibaba-inc.com:taobao-android/taobao_android_homepage.git"
"git@gitlab.alibaba-inc.com:taobao-android/taobao_gamecenter.git"
"git@gitlab.alibaba-inc.com:taobao-android/taobao_android_alipay.git"
"git@gitlab.alibaba-inc.com:taobao-android/rushpromotionactivity.git"
"git@gitlab.alibaba-inc.com:taobao-android/taobao_shop.git"
"git@gitlab.alibaba-inc.com:taobao-android/login4android_sdk.git"
"git@gitlab.alibaba-inc.com:taobao-android/taobao_shop_common.git"
"git@gitlab.alibaba-inc.com:taobao-android/arcticcircleplugin.git"
"git@gitlab.alibaba-inc.com:taobao-android/taobao_legacy.git"

)

cd $BUILD_PATH
for git in ${GITS[@]}
do
	git clone $git
done

for file in $BUILD_PATH
do
    if !test -d $file
    then
	echo ">>start to install in $file"
        cd $file
	mvn install -e
	cd ..
    fi
done

cd $ROOT_PATH
#git clone git@gitlab.alibaba-inc.com:build/taobao_builder.git -b feature_20140520
#cd taobao_builder
mvn clean package -e -o


