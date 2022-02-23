TemplatePythonPath=/Users/b2build/Project/Ope_Russia/Russia/PackagingRelated/jenkins_pythonScripts/gradle_modifier.py
gradleBuildTemplatePath=/Users/b2build/Platform/Best2_final_release/app/build.template
AppVersion=1.0.1
AppCode=1
B2SvnInfoVersion=10769
IsFairGuard=false
AssetModule="\\\"unity_resource\\\""
python "${TemplatePythonPath}" "${gradleBuildTemplatePath}" "${AppVersion}" "${AppCode}" "${ApkABI}" "${TargetApi}" "subPackageTag" " " "${B2SvnInfoVersion}" "${IsFairGuard}" "${AssetModule}"