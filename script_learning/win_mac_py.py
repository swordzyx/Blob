import sys
import os
TemplatePythonPath="E:\\personal_pratice\\gradle_modifier.py"
gradleBuildTemplatePath="E:\\personal_pratice\\build.template"
AppVersion="1.0.1"
AppCode=1
ApkABI="v8a"
TargetApi=29
B2SvnInfoVersion=10769
IsFairGuard="false"
AssetModule="\\\":unity_resource\\\""
os.system("python {0} \"{1}\" \"{2}\" \"{3}\" \"{4}\" \"{5}\" \"{6}\" \"{7}\" {8} {9} \"{10}\"".format(TemplatePythonPath,gradleBuildTemplatePath,AppVersion,AppCode,ApkABI,TargetApi,"subPackageTag","",B2SvnInfoVersion,IsFairGuard, AssetModule))  