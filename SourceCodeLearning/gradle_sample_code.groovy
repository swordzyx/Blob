
/**
* 修改生成的 aar 的名称，并将生成的 aar 拷贝到 E:\\personal_pratice\\Work Tree\\XLSDK-aar\\ 目录下
*/
def fileName

android.libraryVariants.all{ variant ->
    variant.outputs.all{
        fileName = "${project.name}_${variant.buildType.name}_v${android.defaultConfig.versionName}.aar"
        outputFileName = fileName
    }
}

afterEvaluate {
    def task = project.tasks.findByName("assembleRelease")
    if (task != null) {
        task.doLast {
            def intoFile = file("E:\\personal_pratice\\Work Tree\\XLSDK-aar\\${fileName}")

            if(intoFile.exists()) {
                intoFile.delete()
            }
            copy {
                from "build/outputs/aar"
                into "E:\\personal_pratice\\Work Tree\\XLSDK-aar"
            }
        }
    }
}
