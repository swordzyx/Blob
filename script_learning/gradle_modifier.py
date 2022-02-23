#coding=utf-8

import sys
import os
import io

gradleFilePath = sys.argv[1]
versionName = sys.argv[2]
versionCode = sys.argv[3]
abiFilters = sys.argv[4]
targetApi = sys.argv[5]
subPackageTag = sys.argv[6]
appendDependencies = sys.argv[7]
b2SvnVersionCode = sys.argv[8]
isFairGuard = sys.argv[9]

# sword add 
# 传入 "\\\":unity_resource\\\"" 或者 ""
assetPacks = sys.argv[10]

def modify(fPath, vName, vCode, Abi, api, subTag, appendDep, vB2SvnVersionCode, vIsFairGuard):
    with io.open(fPath, "r", encoding='UTF-8') as f1, io.open("%s.bak" % fPath, "w", encoding='UTF-8') as f2:
        for line in f1:
            if "%versionName%" in line:
                line = line.replace("%versionName%", vName)

            if "%versionCode%" in line:
                line = line.replace("%versionCode%", vCode)

            if "%targetApi%" in line:
                line = line.replace("%targetApi%", api)

            if "%subPackageTag%" in line:
                line = line.replace("%subPackageTag%", "'" + subTag + "'")

            if "%appendDependencies%" in line:
                line = line.replace("%appendDependencies%", appendDep)

            if "%abiFilters%" in line:
                if Abi == "v7a" :
                    line = line.replace("%abiFilters%", "\'armeabi-v7a\'")
                else:
                    line = line.replace("%abiFilters%", "\'armeabi-v7a\', \'arm64-v8a\'")

            if "%b2SvnVersionCode%" in line:
                line = line.replace("%b2SvnVersionCode%", "'" + vB2SvnVersionCode + "'")

            if "%isFairGuard%" in line:
                line = line.replace("%isFairGuard%", vIsFairGuard)

            # sword add
            if "%assets_module%" in line:
                line = line.replace("%assets_module%", assetPacks)
            # end add

            f2.write(line)

    os.remove(fPath)
    os.rename("%s.bak" % fPath, fPath)

modify(gradleFilePath, versionName, versionCode, abiFilters, targetApi, subPackageTag, appendDependencies, b2SvnVersionCode, isFairGuard)