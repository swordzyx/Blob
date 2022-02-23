::gradle_modifier 脚本的路径
set TemplatePythonPath=E:\personal_pratice\gradle_modifier.py
set gradleBuildTemplatePath=E:\personal_pratice\build.template
set AppVersion=1.0.0
set AppCode=35
set ApkABI=v7a
set TargetApi=29
set AssetModule="\":unity_resource\""
python "%TemplatePythonPath%" "%gradleBuildTemplatePath%" "%AppVersion%" "%AppCode%" "%ApkABI%" "%TargetApi%" "subPackageTag" " " "10769" "false" "%AssetModule%"