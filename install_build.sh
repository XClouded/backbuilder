#!/bin/bash

##引入基础shell
source install_base.sh

build_taobaocompat;
do_jar_build;
do_apklib_build;
do_aar_build;
do_awb_build;
do_awb_svn;
do_builder;
