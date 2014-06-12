#!/bin/bash
BUILD_BASE_PATH=`pwd`

echo "PROJECT ROOT:$BUILD_BASE_PATH"

rm -rf $BUILD_BASE_PATH/build_shell
git clone git@gitlab.alibaba-inc.com:android_build/build_shell.git -b master

INSTALL_SHELL="$BUILD_BASE_PATH/build_shell"

##引入核心库
source $INSTALL_SHELL/build_function.sh

build_apk
