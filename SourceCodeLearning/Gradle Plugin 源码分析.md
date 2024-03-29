
方法调用流程：

`BasePlugin.apply -> BasePlugin.basePluginApply -> BasePlugin.createTasks -> BasePlugin.createAndroidTask -> 
 VariantManager.createAndriodTasks -> VariantManager.createTasksForVariantData -> VariantManager.createTasksForVariantScope -> 
 TaskManager.createCompileTask -> TaskManager.createPostCompilationTasks -> TaskManager.createDexTasks -> 
 DexArchiveBuilderTransform.transform -> convertToDexArchive -> DexArchiveBuilderTransform.DexConversionWorkAction.run -> launchProcessing -> convert -> 
 D8.run -> ApplicationWriter.write -> VirtualFile.distribute -> VirtualFile.FillFilesDistributor -> VirtualFile.PackageSplitPopulator.call`



通过 “./gradlew app:assembleDebug --console=plain” 可查看 APK 构建过程所涉及的 Tasks
```java
 > Task :buildSrc:compileJava UP-TO-DATE
> Task :buildSrc:compileGroovy NO-SOURCE
> Task :buildSrc:processResources NO-SOURCE
> Task :buildSrc:classes UP-TO-DATE
> Task :buildSrc:jar UP-TO-DATE
> Task :buildSrc:assemble UP-TO-DATE
> Task :buildSrc:compileTestJava NO-SOURCE
> Task :buildSrc:compileTestGroovy NO-SOURCE
> Task :buildSrc:processTestResources NO-SOURCE
> Task :buildSrc:testClasses UP-TO-DATE
> Task :buildSrc:test NO-SOURCE
> Task :buildSrc:check UP-TO-DATE
> Task :buildSrc:build UP-TO-DATE
> Configure project :gradleplugin
> Task :app:preBuild UP-TO-DATE
> Task :app:preDebugBuild UP-TO-DATE
> Task :app:generateDebugBuildConfig UP-TO-DATE
> Task :app:compileDebugAidl NO-SOURCE
> Task :app:compileDebugRenderscript NO-SOURCE
> Task :app:generateDebugResValues UP-TO-DATE
> Task :app:generateDebugResources UP-TO-DATE
> Task :app:mergeDebugResources UP-TO-DATE
> Task :app:createDebugCompatibleScreenManifests UP-TO-DATE
> Task :app:extractDeepLinksDebug UP-TO-DATE
> Task :app:processDebugManifest UP-TO-DATE
> Task :app:javaPreCompileDebug UP-TO-DATE
> Task :app:mergeDebugShaders UP-TO-DATE
> Task :app:compileDebugShaders NO-SOURCE
> Task :app:generateDebugAssets UP-TO-DATE
> Task :app:mergeDebugAssets UP-TO-DATE
> Task :app:processDebugResources UP-TO-DATE
> Task :app:compileDebugKotlin

> Task :app:compileDebugJavaWithJavac
注: D:\GitCode\Android-Pratice\HencoderSourceCode\app\src\main\java\com\example\hencodersourcecode\Main.java使用了未经检查或不安全的操作。
注: 有关详细信息, 请使用 -Xlint:unchecked 重新编译。
> Task :app:compileDebugSources
> Task :app:processDebugJavaRes NO-SOURCE
> Task :app:mergeDebugJavaResource UP-TO-DATE
> Task :app:dexBuilderDebug
> Task :app:checkDebugDuplicateClasses UP-TO-DATE
> Task :app:mergeExtDexDebug UP-TO-DATE
> Task :app:mergeDebugJniLibFolders UP-TO-DATE
> Task :app:mergeDebugNativeLibs UP-TO-DATE
> Task :app:stripDebugDebugSymbols NO-SOURCE
> Task :app:validateSigningDebug UP-TO-DATE
> Task :app:mergeDexDebug
> Task :app:packageDebug
> Task :app:assembleDebug

BUILD SUCCESSFUL in 16s
21 actionable tasks: 5 executed, 16 up-to-date

```


打包 apk 使用的是 “com.android.application” 插件，在 `gradle-3.4.2.jar/META-INF/gradle-plugins/com.android.application.properties` 文件中配置了 “com.android.application” 插件对应的实现类
```groovy
    //com.android.application.properties
    implementation-class=com.android.build.gradle.AppPlugin
```

从以上代码可看出 `com.android.application` 插件的实现类是 AppPlugin，它继承自 AbstractAppPlugin 类，AbstractAppPlugin 继承自 BasePlugin，最终 `com.android.application` 插件的入口是 BasePlugin 中的 apply 方法。所有的插件的入口都是 Plugin 的 apply 方法。

//最终会调用 configureProject，configureExtension，createTasks 这三个函数
```java
//3.4.2
BasePlugin.apply {
    //com.android.build.gradle.BasePlugin.kt
    @Override
    public final void apply(@NonNull Project project) {
        CrashReporting.runAction(
                () -> {
                    basePluginApply(project);
                    pluginSpecificApply(project);
                });
    }
    
    //com.android.build.gradle.BasePlugin.kt
    private void basePluginApply(@NonNull Project project) {
        // We run by default in headless mode, so the JVM doesn't steal focus.
        System.setProperty("java.awt.headless", "true");

        this.project = project;
        this.projectOptions = new ProjectOptions(project);
        //检查 Gradle 版本，每个版本的 Android Gradle 插件都有对应的 Gradle 版本限制，不能低于某个版本。
        checkGradleVersion(project, getLogger(), projectOptions);
        //检查依赖，并确保在配置阶段不去解析依赖
        DependencyResolutionChecks.registerDependencyCheck(project, projectOptions);

        //应用一个 AndroidBasePlugin 插件，这是为了让其他插件识别当前应用的是一个 Android 插件
        project.getPluginManager().apply(AndroidBasePlugin.class);

        //检查 Project 的路径是否有错，有错则抛出一个 StopExecutionException 异常
        checkPathForErrors();
        //检查子 module 的结构是否有错，主要检查不同的 module 是否有相同的标识，如果有则抛出 StopExecutionException。
        checkModulesForErrors();

        //插件初始化，立即执行。（Gradle Deamon 永远不会同时执行两个构建流程）
        PluginInitializer.initialize(project);
        //初始化 ProcessProfileWriterFactory 实例，用于记录构建过程中的配置信息。并将该实例注册到 Project 中，用作监听
        RecordingBuildListener buildListener = ProfilerInitializer.init(project, projectOptions);
        ProfileAgent.INSTANCE.register(project.getName(), buildListener);
        threadRecorder = ThreadRecorder.get();

        Workers.INSTANCE.initFromProject(
                projectOptions,
                // possibly, in the future, consider using a pool with a dedicated size
                // using the gradle parallelism settings.
                ForkJoinPool.commonPool());

        //为 project 实例设置 Android Plugin Version，插件类型，插件生成器，project 路径等
        ProcessProfileWriter.getProject(project.getPath())
                .setAndroidPluginVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
                .setAndroidPlugin(getAnalyticsPluginType())
                .setPluginGeneration(GradleBuildProject.PluginGeneration.FIRST)
                .setOptions(AnalyticsUtil.toProto(projectOptions));

        if (!projectOptions.get(BooleanOption.ENABLE_NEW_DSL_AND_API)) {

            //执行 configProject 函数，用于配置 project，记录 configureProject 执行的时间
            threadRecorder.record(
                    ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
                    project.getPath(),
                    null,
                    this::configureProject);

            //执行 configureExtension 函数，用于配置 Extension，记录 configureExtension 执行的时间
            threadRecorder.record(
                    ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
                    project.getPath(),
                    null,
                    this::configureExtension);

            //执行 createTasks 函数，用于创建 Task，记录 createTasks 执行的时间
            threadRecorder.record(
                    ExecutionType.BASE_PLUGIN_PROJECT_TASKS_CREATION,
                    project.getPath(),
                    null,
                    this::createTasks);
        } else {
            // Apply the Java plugin
            project.getPlugins().apply(JavaBasePlugin.class);

            // create the delegate
            ProjectWrapper projectWrapper = new ProjectWrapper(project);
            PluginDelegate<E> delegate =
                    new PluginDelegate<>(
                            project.getPath(),
                            project.getObjects(),
                            project.getExtensions(),
                            project.getConfigurations(),
                            projectWrapper,
                            projectWrapper,
                            project.getLogger(),
                            projectOptions,
                            getTypedDelegate());

            delegate.prepareForEvaluation();

            // after evaluate callbacks
            project.afterEvaluate(
                    CrashReporting.afterEvaluate(
                            p -> {
                                threadRecorder.record(
                                        ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                                        p.getPath(),
                                        null,
                                        delegate::afterEvaluate);
                            }));
        }
    }
}
```

```java
/**
* 1. 获取 Gradle 实例
* 2. 获取 ObjectFactory 实例
* 3. 创建 DataBindingBuilder 实例
* 4. 检查 Gradle 插件版本，不能小于指定的最小版本
* 5. 应用 Java 插件
* 6. 如果启用了构建缓存，则创建 buildCache 实例
* 7. 给 Project 设置一个监听，在 Project 的整个构建过程完成之后进行资源的回收以及缓存的清除（buildFinished）
*/
//4.0.1
configureProject {
    //com.android.build.gradle.BasePlugin.kt
    private void configureProject() {
        //获取 Gradle 实例，表示对 Gradle 的调用
        final Gradle gradle = project.getGradle();
        //获取 Project 的 ObjectFactcory 实例，可用此实例创建各种类型的 model objects
        ObjectFactory objectFactory = project.getObjects();
        final Logger logger = project.getLogger();
        final String projectPath = project.getPath();

        syncIssueHandler = new SyncIssueReporterImpl(SyncOptions.getModelQueryMode(projectOptions), logger);

        DeprecationReporterImpl deprecationReporter = new DeprecationReporterImpl(syncIssueHandler, projectOptions, projectPath);

        //For storing additional model information.
        extraModelInfo = new ExtraModelInfo();

        SdkComponents sdkComponents =
                SdkComponents.Companion.createSdkComponents(
                        project,
                        projectOptions,
                        // We pass a supplier here because extension will only be set later.
                        this::getExtension,
                        getLogger(),
                        syncIssueHandler);

        //创建 DataBindingBuilder 实例
        dataBindingBuilder = new DataBindingBuilder();
        dataBindingBuilder.setPrintMachineReadableOutput(SyncOptions.getErrorFormatMode(projectOptions) == ErrorFormatMode.MACHINE_PARSABLE);

        projectOptions.getAllOptions().forEach(deprecationReporter::reportOptionIssuesIfAny);

        //检查当前的 Gradle 插件是否小于所支持的最小插件版本，如果是则抛出异常
        GradlePluginUtils.enforceMinimumVersionsOfPlugins(project, syncIssueHandler);

        //应用 Java 插件
        project.getPlugins().apply(JavaBasePlugin.class);

        DslScopeImpl dslScope =
                new DslScopeImpl(
                        syncIssueHandler,
                        deprecationReporter,
                        objectFactory,
                        project.getLogger(),
                        new BuildFeatureValuesImpl(projectOptions),
                        project.getProviders(),
                        new DslVariableFactory(syncIssueHandler),
                        project.getLayout(),
                        project::file);

        //如果启用了构建缓存，则会创建 buildCache 实例以便后面能重用缓存。
        @Nullable
        FileCache buildCache = BuildCacheUtils.createBuildCacheIfEnabled(project, projectOptions);

        globalScope =
                new GlobalScope(
                        project,
                        creator,
                        projectOptions,
                        dslScope,
                        sdkComponents,
                        registry,
                        buildCache,
                        messageReceiver,
                        componentFactory);

        project.getTasks()
                .named("assemble")
                .configure(task -> task.setDescription("Assembles all variants of all applications and secondary packages."));

        // buildFinished 函数会整个 Project 构建完成后回调，主要执行资源的回收，清除缓存等操作。
        gradle.addBuildListener(
                new BuildAdapter() {
                    @Override
                    public void buildFinished(@NonNull BuildResult buildResult) {
                        // Do not run buildFinished for included project in composite build.
                        if (buildResult.getGradle().getParent() != null) {
                            return;
                        }
                        ModelBuilder.clearCaches();
                        sdkComponents.unload();
                        SdkLocator.resetCache();
                        ConstraintHandler.clearCache();
                        threadRecorder.record(ExecutionType.BASE_PLUGIN_BUILD_FINISHED, projectPath, null, Main::clearInternTables);
                        DeprecationReporterImpl.Companion.clean();
                    }
                });

        createLintClasspathConfiguration(project);
    }
}
```

```java  
//4.0.1

configureExtension {
    //com\android\build\gradle\BasePlugin.java
	//配置扩展
	private void configureExtension() {
	    ObjectFactory objectFactory = project.getObjects();
        
        //首先创建盛放 buildType、productFlavor、signingConfig 的容器，保存 build.gradle 中 android{..} 配置的属性
	    //创建 BuildType 类型的 Container，也就是 debug 或者 release
	    final NamedDomainObjectContainer<BuildType> buildTypeContainer = project.container(BuildType.class, new BuildTypeFactory(dslScope));

	    //创建 ProductFlavor 的 Container
	    final NamedDomainObjectContainer<ProductFlavor> productFlavorContainer = project.container(ProductFlavor.class, new ProductFlavorFactory(dslScope));

	    //创建 SigningConfig 的 Container ，即签名配置。
	    final NamedDomainObjectContainer<SigningConfig> signingConfigContainer =
                project.container(
                        SigningConfig.class,
                        new SigningConfigFactory(dslScope.getObjectFactory(), GradleKeystoreHelper.getDefaultDebugKeystoreLocation()));

	    //创建一个容器，用于管理 BaseVariantOutput 类型的对象
	    final NamedDomainObjectContainer<BaseVariantOutput> buildOutputs = project.container(BaseVariantOutput.class);

        //创建一个 “buildOutputs” 扩展属性配置
	    project.getExtensions().add("buildOutputs", buildOutputs);

	    sourceSetManager = new SourceSetManager(project, isPackagePublished(), dslScope, new DelayedActionsExecutor());

	    //创建一个名为 “android” 的扩展，其实就是读取 build.gradle 中 android{...} 下的配置，然后保存到 BaseAppModuleExtension 这个类的对象中，这个扩展对应的实现类就是 BaseAppModuleExtension
	    extension = createExtension(
                    dslScope,
                    projectOptions,
                    globalScope,
                    buildTypeContainer,
                    defaultConfig,
                    productFlavorContainer,
                    signingConfigContainer,
                    buildOutputs,
                    sourceSetManager,
                    extraModelInfo);


        //dslScope 是一个包含了 Android Plugin 所需要的数据的 Scope，将 android 扩展中的 buildFeature 连接到 DslScope 的 BuildFeatureValues 中
        ((BuildFeatureValuesImpl) dslScope.getBuildFeatures()).setDslBuildFeatures(((CommonExtension) extension).getBuildFeatures());

        //设置扩展
	    globalScope.setExtension(extension);

	    //实现位于 AppPlugin 中，创建 ApplciationVariantFactory 实例，代表了一个 app project，通过此类生成 apk 文件
	    variantFactory = createVariantFactory(globalScope);

	    //createTaskManager 的具体实现类位于 AbstractAppPlugin.java 中，用于创建 Task
	    taskManager = createTaskManager(
                        globalScope,
                        project,
                        projectOptions,
                        dataBindingBuilder,
                        extension,
                        variantFactory,
                        registry,
                        threadRecorder);

        //VariantInputModel，由 DSL/API 执行时填充
        variantInputModel = new VariantInputModelImpl(globalScope, extension, variantFactory, sourceSetManager);

	    //变体管理类。用于创建或管理构建变体
	    variantManager = new VariantManager(
                globalScope,
                project,
                projectOptions,
                extension,
                variantFactory,
                variantInputModel,
                taskManager,
                sourceSetManager,
                threadRecorder);

	    registerModels(registry, globalScope, variantInputModel, variantManager, extension, extraModelInfo);

	    // 有新的 SigningConfig 对象添加到 signingConfigContainer 时，回调 variantManager.addSigningConfig 方法。
	    signingConfigContainer.whenObjectAdded(variantManager::addSigningConfig);

        //当有新的 BuildType 对象添加到 buildTypeContainer 时，通过 init 初始化之后，将其添加到 variantInputModel 中。
	    buildTypeContainer.whenObjectAdded(
                buildType -> {
                    if (!this.getClass().isAssignableFrom(DynamicFeaturePlugin.class)) {
                        SigningConfig signingConfig = signingConfigContainer.findByName(BuilderConstants.DEBUG);
                        buildType.init(signingConfig);
                    } else {
                        // initialize it without the signingConfig for dynamic-features.
                        buildType.init();
                    }
                    variantInputModel.addBuildType(buildType);
                });

        //有新的 ProductFlavor 对象添加到 productFlavorContainer 时，回调 variantManager.addProductFlavor 方法
	    productFlavorContainer.whenObjectAdded(variantManager::addProductFlavor);

	    // map whenObjectRemoved on the containers to throw an exception.
	    signingConfigContainer.whenObjectRemoved(new UnsupportedAction("Removing signingConfigs is not supported."));
	    buildTypeContainer.whenObjectRemoved(new UnsupportedAction("Removing build types is not supported."));
	    productFlavorContainer.whenObjectRemoved(new UnsupportedAction("Removing product flavors is not supported."));

        //首先创建一个 debug 签名和 debug 构建类型的配置
	    //创建默认的配置，增加 DEBUG 类型的 SigningConfig 对象，增加 DEBUG 和 RELEASE 类型的构建类型，对应 Debug 构建和 Release 构建
	    variantFactory.createDefaultComponents(buildTypeContainer, productFlavorContainer, signingConfigContainer);


        //为测试 apk 创建特殊的配置，确保在运行测试之前已经安装好了要测试的 apk
        createAndroidTestUtilConfiguration();
	}



    //AppPlugin.java  创建一个扩展，getExtensionClass() 返回此扩展的实现类，返回 BaseAppModuleExtension.class，因此 project.getExtensions().create(...) 实际创建一个 BaseAppModuleExtension 对象。
    protected AppExtension createExtension(DslServices dslServices, GlobalScope globalScope, DslContainerProvider<DefaultConfig, BuildType, ProductFlavor, SigningConfig> dslContainers, NamedDomainObjectContainer<BaseVariantOutput> buildOutputs, ExtraModelInfo extraModelInfo) {
        return (AppExtension)this.project.getExtensions().create("android", this.getExtensionClass(), new Object[]{dslServices, globalScope, buildOutputs, dslContainers.getSourceSetManager(), extraModelInfo, new ApplicationExtensionImpl(dslServices, dslContainers)});
    }


    //BaseAppModuleExtension.kt
    open class BaseAppModuleExtension(
        dslServices: DslServices,
        globalScope: GlobalScope,
        buildOutputs: NamedDomainObjectContainer<BaseVariantOutput>,
        sourceSetManager: SourceSetManager,
        extraModelInfo: ExtraModelInfo,
        private val publicExtensionImpl: ApplicationExtensionImpl
    ) : AppExtension(
        dslServices,
        globalScope,
        buildOutputs,
        sourceSetManager,
        extraModelInfo,
        true
    ), InternalApplicationExtension by publicExtensionImpl,
        ActionableVariantObjectOperationsExecutor<ApplicationVariant<ApplicationVariantProperties>, ApplicationVariantProperties> by publicExtensionImpl {
            ......
        }

    //AppExtension.groovy    https://android.googlesource.com/platform/tools/build/+/refs/heads/master/gradle/src/main/groovy/com/android/build/gradle/AppExtension.groovy
    public class AppExtension extends BaseExtension {

        AppExtension(AppPlugin plugin, ProjectInternal project, Instantiator instantiator,
                     NamedDomainObjectContainer<DefaultBuildType> buildTypes,
                     NamedDomainObjectContainer<DefaultProductFlavor> productFlavors,
                     NamedDomainObjectContainer<SigningConfig> signingConfigs) {
            super(plugin, project, instantiator)
            this.buildTypes = buildTypes
            this.productFlavors = productFlavors
            this.signingConfigs = signingConfigs
        }

        ......

        //获取所有 apk 的构建变体
        public DefaultDomainObjectSet<ApplicationVariant> getApplicationVariants() {
            return applicationVariantList
        }
    }
}
```

```java
//4.0.1
//创建构建变体及其所需的任务
createTasks {
    //BasePlugin.java
    private void createTasks() {
        //记录 taskManager.createTasksBeforeEvaluate 执行所消耗的时间。并记录结果
        //createTasksBeforeEvaluate 在 project 评估之前执行
        threadRecorder.record(
                ExecutionType.TASK_MANAGER_CREATE_TASKS,
                project.getPath(),
                null,
                () -> taskManager.createTasksBeforeEvaluate());

        //project 评估阶段完成之后创建 Android Task
        project.afterEvaluate(
                CrashReporting.afterEvaluate(
                        p -> {
                            sourceSetManager.runBuildableArtifactsActions();
                            //执行 createAndroidTasks 并记录执行时间。生成 Android 任务
                            threadRecorder.record(
                                    ExecutionType.BASE_PLUGIN_CREATE_ANDROID_TASKS,
                                    project.getPath(),
                                    null,
                                    this::createAndroidTasks);
                        }));
    }



    /**
     * TaskManager.java
     * 
     * 在评估之前创建任务（适用于插件），注册了以下任务
     * uninstallAllTask
     * deviceCheckTask
     * connectedCheckTask
     * perbuild
     * ExtractProguardFiles
     * SourceSetsTask
     * assembleAndroidTestTask
     * LintCompile
     * LintGlobalTask：该任务执行完毕之后执行 Task 的检查
     * LintFixTask
     * buildCache
     * ConfigAttrTask：仅用于测试
     * 
     * 
     * 创建配置：
     * CustomLintChecksConfig
     * CustomLintPublishConfig
     * androidJarConfig：Configuration 对象
     * CoreLibraryDesugaringConfig
     */
    public void createTasksBeforeEvaluate() {
        //卸载所有的 Task
        taskFactory.register(
                UNINSTALL_ALL,
                uninstallAllTask -> {
                    uninstallAllTask.setDescription("Uninstall all applications.");
                    uninstallAllTask.setGroup(INSTALL_GROUP);
                });

        //设备检查
        taskFactory.register(
                DEVICE_CHECK,
                deviceCheckTask -> {
                    deviceCheckTask.setDescription(
                            "Runs all device checks using Device Providers and Test Servers.");
                    deviceCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                });

        //检查已连接的设备
        taskFactory.register(
                CONNECTED_CHECK,
                connectedCheckTask -> {
                    connectedCheckTask.setDescription("Runs all device checks on currently connected devices.");
                    connectedCheckTask.setGroup(JavaBasePlugin.VERIFICATION_GROUP);
                });

        // Make sure MAIN_PREBUILD runs first:
        taskFactory.register(MAIN_PREBUILD);

        taskFactory.register(EXTRACT_PROGUARD_FILES,  ExtractProguardFiles.class, task -> task.dependsOn(MAIN_PREBUILD));

        taskFactory.register(new SourceSetsTask.CreationAction(extension));

        taskFactory.register(
                ASSEMBLE_ANDROID_TEST,
                assembleAndroidTestTask -> {
                    assembleAndroidTestTask.setGroup(BasePlugin.BUILD_GROUP);
                    assembleAndroidTestTask.setDescription("Assembles all the Test applications.");
                });

        taskFactory.register(new LintCompile.CreationAction(globalScope));

        // Lint task is configured in afterEvaluate, but created upfront as it is used as an anchor task.
        createGlobalLintTask();
        configureCustomLintChecksConfig();

        globalScope.setAndroidJarConfig(createAndroidJarConfig(project));

        //清除构建缓存
        if (buildCache != null) {
            taskFactory.register(new CleanBuildCache.CreationAction(buildCache));
        }

        // for testing only.
        taskFactory.register("resolveConfigAttr", ConfigAttrTask.class, task -> task.resolvable = true);
        taskFactory.register("consumeConfigAttr", ConfigAttrTask.class, task -> task.consumable = true);

        // This needs to be resolved before tasks evaluation since it does some configuration inside
        // By resolving it here we avoid configuration problems. The value returned will be cached
        // and returned immediately later when this method is invoked.
        //getAapt2FromMavenAndVersion 返回一个文件列表，包含了要使用的 AAPT2 目录以及版本号
        Aapt2MavenUtils.getAapt2FromMavenAndVersion(globalScope);

        createCoreLibraryDesugaringConfig(project);
    }



    1. 检测 CompileSdkVersion，是否有插件配置冲突（如 JavaPlugin，retrolambda 等）
    2. 将 Project Path，CompileSdk, BuildToolsVersion, Splits, KotlinPluginVersion, FirebasePerformancePluginVersion 等信息写入 Project 中
    3. 创建应用的 Tasks
    createAndroidTasks() {
        //主要生成 flavors 相关的数据，并根据 flavor 创建与之对应的 Taks，并注册到 Task 容器中
        @VisibleForTesting
        final void createAndroidTasks() {

            if (extension.getCompileSdkVersion() == null) {
                if (SyncOptions.getModelQueryMode(projectOptions).equals(SyncOptions.EvaluationMode.IDE)) {
                    String newCompileSdkVersion = findHighestSdkInstalled();
                    if (newCompileSdkVersion == null) {
                        newCompileSdkVersion = "android-" + SdkVersionInfo.HIGHEST_KNOWN_STABLE_API;
                    }
                    extension.setCompileSdkVersion(newCompileSdkVersion);
                }

                globalScope
                        .getDslScope()
                        .getIssueReporter()
                        .reportError(
                                Type.COMPILE_SDK_VERSION_NOT_SET,
                                "compileSdkVersion is not specified. Please add it to build.gradle");
            }

            // Make sure unit tests set the required fields.
            checkState(extension.getCompileSdkVersion() != null, "compileSdkVersion is not specified.");

            //设置 Java 的版本
            extension
                    .getCompileOptions()
                    .setDefaultJavaVersion(AbstractCompilesUtil.getDefaultJavaVersion(extension.getCompileSdkVersion()));

            //查找 Java Plugin
            if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
                throw new BadPluginException("The 'java' plugin has been applied, but it is not compatible with the Android plugins.");
            }

            //查找 “me.tatarka.retrolambda” 插件，此插件要求必须使用 Java 8， 发出一个警告
            if (project.getPlugins().hasPlugin("me.tatarka.retrolambda")) {
                String warningMsg =
                        "One of the plugins you are using supports Java 8 "
                                + "language features. To try the support built into"
                                + " the Android plugin, remove the following from "
                                + "your build.gradle:\n"
                                + "    apply plugin: 'me.tatarka.retrolambda'\n"
                                + "To learn more, go to https://d.android.com/r/"
                                + "tools/java-8-support-message.html\n";
                globalScope
                        .getDslScope()
                        .getIssueReporter()
                        .reportWarning(IssueReporter.Type.GENERIC, warningMsg);
            }

            //Only force the SDK resolution when in sync mode. Also move this to as late as possible so we configure most tasks as possible during sync.
            if (globalScope.getSdkComponents().getSdkFolder() == null) {
                return;
            }
            // 如果 project 没有被初始化，则不执行任何事情
            if ((!project.getState().getExecuted() || project.getState().getFailure() != null) && SdkLocator.getSdkTestDirectory() == null) {
                return;
            }

            if (hasCreatedTasks) {
                return;
            }
            hasCreatedTasks = true;

            extension.disableWrite();

            taskManager.configureCustomLintChecks();

            //写入 Project 配置
            ProcessProfileWriter.getProject(project.getPath())
                    .setCompileSdk(extension.getCompileSdkVersion())
                    .setBuildToolsVersion(extension.getBuildToolsRevision().toString())
                    .setSplits(AnalyticsUtil.toProto(extension.getSplits()));

            String kotlinPluginVersion = getKotlinPluginVersion();
            if (kotlinPluginVersion != null) {
                ProcessProfileWriter.getProject(project.getPath())
                        .setKotlinPluginVersion(kotlinPluginVersion);
            }

            //获取 Kotlin 插件的版本
            String kotlinPluginVersion = getKotlinPluginVersion();
            //设置 kotlin 插件版本
            if (kotlinPluginVersion != null) {
                ProcessProfileWriter.getProject(project.getPath()).setKotlinPluginVersion(kotlinPluginVersion);
            }

            //Firebase 插件相关
            AnalyticsUtil.recordFirebasePerformancePluginVersion(project);

            //创建构建变体和构建任务。
            List<VariantScope> variantScopes = variantManager.createVariantsAndTasks();

            //配置依赖
            new DependencyConfigurator(project, project.getName(), globalScope, variantInputModel).configureDependencies();

            // Run the old Variant API, after the variants and tasks have been created.
            ApiObjectFactory apiObjectFactory = new ApiObjectFactory(extension, variantFactory, project.getObjects());
            for (VariantScope variantScope : variantScopes) {
                BaseVariantData variantData = variantScope.getVariantData();
                apiObjectFactory.create(variantData);
            }

            // Make sure no SourceSets were added through the DSL without being properly configured
            sourceSetManager.checkForUnconfiguredSourceSets();

            // must run this after scopes are created so that we can configure kotlin kapt tasks
            taskManager.addBindingDependenciesIfNecessary(
                    globalScope.getBuildFeatures().getViewBinding(),
                    globalScope.getBuildFeatures().getDataBinding(),
                    extension.getDataBinding(),
                    variantManager.getVariantScopes());

            // configure compose related tasks.
            taskManager.configureKotlinPluginTasksForComposeIfNecessary(globalScope, variantManager.getVariantScopes());

            // create the global lint task that depends on all the variants
            taskManager.configureGlobalLintTask(variantManager.getVariantScopes());

            int flavorDimensionCount = 0;
            if (extension.getFlavorDimensionList() != null) {
                flavorDimensionCount = extension.getFlavorDimensionList().size();
            }

            //获取变体维度
            int flavorDimensionCount = 0;
            if (extension.getFlavorDimensionList() != null) {
                flavorDimensionCount = extension.getFlavorDimensionList().size();
            }

            //创建 assemble* 和 bundle* 任务作为锚定任务。
            taskManager.createAnchorAssembleTasks(variantScopes, extension.getProductFlavors().size(), flavorDimensionCount);

            // now publish all variant artifacts.
            for (VariantScope variantScope : variantManager.getVariantScopes()) {
                variantManager.publishBuildArtifacts(variantScope);
            }

            checkSplitConfiguration();
            variantManager.setHasCreatedTasks(true);
            // notify our properties that configuration is over for us. 评估阶段结束
            GradleProperty.Companion.endOfEvaluation();

        }

        1. 创建工程级别的测试任务
        2. 遍历所有的 variantScope，为其变体数据创建相应的 Tasks
        3. 创建报告相关的 Tasks
        createVariantsAndTasks() {
            //VariantManager.java
            public List<VariantScope> createVariantsAndTasks() {
                //variantInputModel 在 configureExtension 中初始化，执行校验工作
                variantFactory.validateModel(variantInputModel);
                variantFactory.preVariantWork(project);

                //创建构建变体，并往变体中填充信息，添加到 variantScope 中
                if (variantScopes.isEmpty()) {
                    computeVariants();
                }

                // Create top level test tasks.
                taskManager.createTopLevelTestTasks(!variantInputModel.getProductFlavors().isEmpty());

                // Create tasks for all variants (main and tests)
                for (final VariantScope variantScope : variantScopes) {
                    createTasksForVariant(variantScope);
                }

                taskManager.createReportTasks(variantScopes);

                return variantScopes;
            }

            //计算构建变体
            //VariantManger.java
            computeVariants() {
                //VariantManager.java
                @VisibleForTesting
                public void computeVariants() {
                    //获取所有的维度
                    List<String> flavorDimensionList = extension.getFlavorDimensionList();

                    //从 variantInputModel 中计算所有 dimension(维度)&productflavor(产品变种) 的组合，创建构建组件/变体
                    DimensionCombinator computer =
                            new DimensionCombinator(
                                    variantInputModel,
                                    globalScope.getDslScope().getIssueReporter(),
                                    variantFactory.getVariantType(),
                                    flavorDimensionList);

                    //获取计算出的构建变体。
                    List<DimensionCombination> variants = computer.computeVariants();

                    // get some info related to testing
                    BuildTypeData testBuildTypeData = getTestBuildTypeData();

                    // loop on all the new variant objects to create the legacy ones.
                    for (DimensionCombination variant : variants) {
                        createVariantsFromConfiguration(variant, testBuildTypeData);
                    }

                    //Configure artifact transforms that require variant-specific attribute information.
                    configureVariantArtifactTransforms(variantScopes);
                }

                //VariantManager.java
                //创建所有 dimension()&productflavor() 组合而成的构建变体，实际创建的是 variantData 对象
                private void createVariantsFromConfiguration(@NonNull DimensionCombination dimensionCombination, @Nullable BuildTypeData testBuildTypeData) {
                    //这里返回的是 VariantTypeImpl.BASE_APK 或者 VariantTypeImpl.OPTIONAL_APK
                    VariantType variantType = variantFactory.getVariantType();

                    // first run the old variantFilter API . This acts on buildtype/flavor only, and applies in one pass to prod/tests.
                    Action<com.android.build.api.variant.VariantFilter> variantFilterAction = extension.getVariantFilter();

                    //获取 build.gradle 文件中 productFlavor{...} 配置的信息（产品变种）
                    DefaultConfig defaultConfig = variantInputModel.getDefaultConfig().getProductFlavor();

                    //保存 build.gradle 文件中 buildTypes{...} 配置的信息（构建类型，一般是 debug 和 release）
                    BuildTypeData buildTypeData = variantInputModel.getBuildTypes().get(dimensionCombination.getBuildType());
                    BuildType buildType = buildTypeData.getBuildType();

                    //get the list of ProductFlavorData from the list of flavor name
                    //ProductFlavorData.ProductFlavor 代表了一个产品变种，此处根据 productFlavor 的名称创建出所有的 productFlavor 所对应的 ProductFlavor, 封装到 ProductFlavorData 中，保存到一个 List 中
                    List<ProductFlavorData<ProductFlavor>> productFlavorDataList =
                            dimensionCombination
                                    .getProductFlavors()
                                    .stream()
                                    .map(it -> variantInputModel.getProductFlavors().get(it.getSecond()))
                                    .collect(Collectors.toList());

                    List<ProductFlavor> productFlavorList = productFlavorDataList
                            .stream()
                            .map(ProductFlavorData::getProductFlavor)
                            .collect(Collectors.toList());

                    boolean ignore = false;

                    if (variantFilterAction != null) {
                        variantFilter.reset(dimensionCombination, defaultConfig, buildType, variantType, productFlavorList);

                        try {
                            // variantFilterAction != null always true here.
                            variantFilterAction.execute(variantFilter);
                        } catch (Throwable t) {
                            throw new ExternalApiUsageException(t);
                        }
                        ignore = variantFilter.getIgnore();
                    }

                    if (!ignore) {
                        // create the prod variant
                        BaseVariantData variantData = createVariant(dimensionCombination, buildTypeData, productFlavorDataList, variantType);
                        if (variantData != null) {
                            addVariant(variantData);

                            VariantDslInfo variantDslInfo = variantData.getVariantDslInfo();
                            VariantScope variantScope = variantData.getScope();

                            int minSdkVersion = variantDslInfo.getMinSdkVersion().getApiLevel();
                            int targetSdkVersion = variantDslInfo.getTargetSdkVersion().getApiLevel();
                            if (minSdkVersion > 0 && targetSdkVersion > 0 && minSdkVersion > targetSdkVersion) {
                                globalScope
                                        .getDslScope()
                                        .getIssueReporter()
                                        .reportWarning(
                                                IssueReporter.Type.GENERIC,
                                                String.format(Locale.US,
                                                        "minSdkVersion (%d) is greater than targetSdkVersion"
                                                                + " (%d) for variant \"%s\". Please change the"
                                                                + " values such that minSdkVersion is less than or"
                                                                + " equal to targetSdkVersion.",
                                                        minSdkVersion,
                                                        targetSdkVersion,
                                                        variantData.getName()));
                            }

                            GradleBuildVariant.Builder profileBuilder = ProcessProfileWriter.getOrCreateVariant(project.getPath(), variantData.getName())
                                    .setIsDebug(buildType.isDebuggable())
                                    .setMinSdkVersion(AnalyticsUtil.toProto(variantDslInfo.getMinSdkVersion()))
                                    .setMinifyEnabled(variantScope.getCodeShrinker() != null)
                                    .setUseMultidex(variantDslInfo.isMultiDexEnabled())
                                    .setUseLegacyMultidex(variantDslInfo.isLegacyMultiDexMode())
                                    .setVariantType(variantData.getType().getAnalyticsVariantType())
                                    .setDexBuilder(AnalyticsUtil.toProto(variantScope.getDexer()))
                                    .setDexMerger(AnalyticsUtil.toProto(variantScope.getDexMerger()))
                                    .setCoreLibraryDesugaringEnabled(variantScope.isCoreLibraryDesugaringEnabled())
                                    .setTestExecution(AnalyticsUtil.toProto(globalScope.getExtension().getTestOptions().getExecutionEnum()));

                            if (variantScope.getCodeShrinker() != null) {
                                profileBuilder.setCodeShrinker(AnalyticsUtil.toProto(variantScope.getCodeShrinker()));
                            }

                            if (variantDslInfo.getTargetSdkVersion().getApiLevel() > 0) {
                                profileBuilder.setTargetSdkVersion(AnalyticsUtil.toProto(variantDslInfo.getTargetSdkVersion()));
                            }
                            if (variantDslInfo.getMaxSdkVersion() != null) {
                                profileBuilder.setMaxSdkVersion(ApiVersion.newBuilder().setApiLevel(variantDslInfo.getMaxSdkVersion()));
                            }

                            VariantScope.Java8LangSupport supportType = variantData.getScope().getJava8LangSupportType();
                            if (supportType != VariantScope.Java8LangSupport.INVALID && supportType != VariantScope.Java8LangSupport.UNUSED) {
                                profileBuilder.setJava8LangSupport(AnalyticsUtil.toProto(supportType));
                            }

                            //AndroidTest 和 UnitTest 相关
                            if (variantFactory.hasTestScope()) {
                                if (buildTypeData == testBuildTypeData) {
                                    TestVariantData androidTestVariantData = createTestComponents(dimensionCombination, buildTypeData, productFlavorDataList, variantData, ANDROID_TEST);
                                    if (androidTestVariantData != null) {
                                        addVariant(androidTestVariantData);
                                    }
                                }

                                TestVariantData unitTestVariantData = createTestComponents(dimensionCombination, buildTypeData, productFlavorDataList, variantData, UNIT_TEST);
                                if (unitTestVariantData != null) {
                                    addVariant(unitTestVariantData);
                                }
                            }
                        }
                    }
                }
            }


            1. 创建 Assemble Task
            2. 如果 VariantType 是 base module，则创建相应的 Bundle Task，一般是构建 App Bundle 才有 base module 这一说
            3. 如果 VariantType 是一个 test module，则创建相应的 test variant
                - 添加依赖：将 variant-specific, build type, multi-flavor, defaultConfig 依赖添加到 variantData 中
                - 添加渲染脚本依赖：如果支持渲染脚本，则添加渲染脚本的依赖
                - 如果是 ANDROID_TEST，则会输出 apk ，创建相应的 AndroidTestVariantTasks
                - 如果是 UNIT_TEST，表明是单元测试，则创建 UnitTestVariantTask。
            4. 如果不是 test module，则创建正是构建相关的任务。
            //VariantManager
            createTasksForVariant() {

                /** 为特定的构建变体创建 Task */
                private void createTasksForVariant(final VariantScope variantScope) {
                    final BaseVariantData variantData = variantScope.getVariantData();
                    final VariantType variantType = variantData.getType();
                    final VariantDslInfo variantDslInfo = variantScope.getVariantDslInfo();
                    final VariantSources variantSources = variantScope.getVariantSources();

                    //创建 Assemble Task
                    taskManager.createAssembleTask(variantData);

                    //如果 variantType 是 base module，则创建相应的 bundle Task
                    //base module 指包含功能的 module，用于 test 的 module 不包含功能
                    //如果是 Bundle 构建中的基础模块，创建 Bundle 任务，不过我看的是 apk 构建的源码把。。。所以这里应该是 false 了。
                    if (variantType.isBaseModule()) {
                        taskManager.createBundleTask(variantData);
                    }

                    //测试相关
                    if (variantType.isTestComponent()) {
                        //将 variant-specific, build type, multi-flavor, defaultConfig 这些依赖添加到当前的 TestVariantData 中
                        final BaseVariantData testedVariantData = (BaseVariantData) ((TestVariantData) variantData).getTestedVariantData();

                        List<ProductFlavor> testProductFlavors = variantDslInfo.getProductFlavorList();
                        List<DefaultAndroidSourceSet> testVariantSourceSets = Lists.newArrayListWithExpectedSize(4 + testProductFlavors.size());

                        // 1. add the variant-specific if applicable.
                        if (!testProductFlavors.isEmpty()) {
                            testVariantSourceSets.add((DefaultAndroidSourceSet) variantSources.getVariantSourceProvider());
                        }

                        // 2. the build type.
                        final BuildTypeData buildTypeData = variantInputModel.getBuildTypes().get(variantDslInfo.getComponentIdentity().getBuildType());
                        DefaultAndroidSourceSet buildTypeConfigurationProvider = buildTypeData.getTestSourceSet(variantType);
                        if (buildTypeConfigurationProvider != null) {
                            testVariantSourceSets.add(buildTypeConfigurationProvider);
                        }

                        // 3. the multi-flavor combination
                        if (testProductFlavors.size() > 1) {
                            testVariantSourceSets.add((DefaultAndroidSourceSet) variantSources.getMultiFlavorSourceProvider());
                        }

                        // 4. the flavors.
                        for (ProductFlavor productFlavor : testProductFlavors) {
                            testVariantSourceSets.add(variantInputModel.getProductFlavors().get(productFlavor.getName()).getTestSourceSet(variantType));
                        }

                        // now add the default config
                        testVariantSourceSets.add(variantInputModel.getDefaultConfig().getTestSourceSet(variantType));

                        // If the variant being tested is a library variant, VariantDependencies must be computed after the tasks for the tested variant is created.  Therefore, the VariantDependencies is computed here instead of when the VariantData was created.
                        VariantDependencies.Builder builder = VariantDependencies.builder(project, variantScope.getGlobalScope().getDslScope().getIssueReporter(), variantDslInfo)
                                .addSourceSets(testVariantSourceSets)
                                .setFlavorSelection(getFlavorSelection(variantDslInfo))
                                .setTestedVariantScope(testedVariantData.getScope());

                        final VariantDependencies variantDep = builder.build(variantScope);
                        //到这里为止，为 TestVariantData 设置了 variant-specific, build type, multi-flavor, defaultConfig 依赖
                        variantData.setVariantDependency(variantDep);

                        //如果支持渲染脚本，则添加渲染脚本的依赖
                        if (testedVariantData.getVariantDslInfo().getRenderscriptSupportModeEnabled()) {
                            project.getDependencies().add(variantDep.getCompileClasspath().getName(), project.files(globalScope.getSdkComponents().getRenderScriptSupportJarProvider()));
                        }

                        //当前 TestVariantData 构建输出的是一个 apk，即当前执行的是一个 Android test（一般用来进行 UI 自动化测试），则创建线狗盈的 AndroidTestVariantTasks
                        //ANDROID_TEST 是构建一个测试 apk
                        //UNIT_TEST 则是单元测试，测试某一个方法或者 API
                        if (variantType.isApk()) { // ANDROID_TEST
                            if (variantDslInfo.isLegacyMultiDexMode()) {
                                String multiDexInstrumentationDep =
                                        globalScope.getProjectOptions().get(BooleanOption.USE_ANDROID_X)
                                                ? ANDROIDX_MULTIDEX_MULTIDEX_INSTRUMENTATION
                                                : COM_ANDROID_SUPPORT_MULTIDEX_INSTRUMENTATION;
                                project.getDependencies().add(variantDep.getCompileClasspath().getName(), multiDexInstrumentationDep);
                                project.getDependencies().add(variantDep.getRuntimeClasspath().getName(), multiDexInstrumentationDep);
                            }

                            //创建 AndroidTestVariantTasks 来构建 apk
                            taskManager.createAndroidTestVariantTasks((TestVariantData) variantData, variantScopes.stream().filter(TaskManager::isLintVariant).collect(Collectors.toList()));
                        } else { // UNIT_TEST
                            //单元测试，创建 UnitTestVariantTask。
                            taskManager.createUnitTestVariantTasks((TestVariantData) variantData);
                        }
                    } else {
                        //非测试，则创建正式的构建任务
                        taskManager.createTasksForVariantScope(variantScope, variantScopes.stream().filter(TaskManager::isLintVariant).collect(Collectors.toList()));
                    }
                }


                创建正式构建相关的 Task。这里面会创建 Android 打包流程过程中所需要的 Task，主要有以下几个步骤
                1. 通过 aidl 工具将 .aidl(Android Interface Description Language) 转换成 java 文件
                2. 使用 AAPT(Asset Packaging Tool，不过 Android Gradle Plugin 3.0.0 之后 AAPT 被 AAPT2 代替了)将资源文件（包括 AndroidManifest.xml，布局文件，xml 资源等）转为 resources.arsc 文件，并生成 R.java 以访问 resources.arsc 中的资源
                3. 使用 Java Compiler 将 R.java，Java 接口文件，Java 源文件编译成 .class 文件
                4. 使用 dex 工具将 .class 文件转成 Android 设备能够执行的 dex 文件（里面是 Dalvik 字节码），这个过程会压缩常量池，清除冗余信息等，添加依赖的第三方库等
                5. 使用 ApkBuilder 工具将资源文件（resources.arsc），Dex 文件打包成 apk 文件
                6. 使用 jarsigner 等签名工具对 apk 进行签名。
                7. 正式版的 apk 会使用 ZipAlign 工具进行对齐处理，以提高 apk 的加载和运行速度。对齐过程就是将 APK 文件中所有的资源文件都偏移 4 字节的整数倍，这样通过 mmap 访问 apk 文件的速度会更快，且减少运行时所占用的内存。

                //ApplicationTaskManager.java
                createTasksForVariantScope() {
                    @Override
                    public void createTasksForVariantScope(@NonNull final VariantScope variantScope, @NonNull List<VariantScope> variantScopesForLint) {
                        createAnchorTasks(variantScope);

                        taskFactory.register(new ExtractDeepLinksTask.CreationAction(variantScope));

                        handleMicroApp(variantScope);

                        // Create all current streams (dependencies mostly at this point)
                        createDependencyStreams(variantScope);

                        // Add a task to publish the applicationId.
                        createApplicationIdWriterTask(variantScope);

                        createBuildArtifactReportTask(variantScope);

                        // Add a task to check the manifest
                        taskFactory.register(new CheckManifest.CreationAction(variantScope));

                        // Add a task to process the manifest(s)
                        createMergeApkManifestsTask(variantScope);

                        // Add a task to create the res values
                        createGenerateResValuesTask(variantScope);

                        // Add a task to compile renderscript files.
                        createRenderscriptTask(variantScope);

                        // Add a task to merge the resource folders
                        createMergeResourcesTasks(variantScope);

                        // Add tasks to compile shader
                        createShaderTask(variantScope);

                        // Add a task to merge the asset folders
                        createMergeAssetsTask(variantScope);

                        // Add a task to create the BuildConfig class
                        createBuildConfigTask(variantScope);

                        // Add a task to process the Android Resources and generate source files
                        createApkProcessResTask(variantScope);

                        registerRClassTransformStream(variantScope);

                        // Add a task to process the java resources
                        createProcessJavaResTask(variantScope);

                        createAidlTask(variantScope);

                        // Add external native build tasks
                        createExternalNativeBuildJsonGenerators(variantScope);
                        createExternalNativeBuildTasks(variantScope);

                        // Add a task to merge the jni libs folders
                        createMergeJniLibFoldersTasks(variantScope);

                        // Add feature related tasks if necessary
                        if (variantScope.getType().isBaseModule()) {
                            // Base feature specific tasks.
                            taskFactory.register(new FeatureSetMetadataWriterTask.CreationAction(variantScope));

                            createValidateSigningTask(variantScope);
                            // Add a task to produce the signing config file.
                            taskFactory.register(new SigningConfigWriterTask.CreationAction(variantScope));

                            if (!(((BaseAppModuleExtension) extension).getAssetPacks().isEmpty())) {
                                createAssetPackTasks(variantScope);
                            }

                            if (globalScope.getBuildFeatures().getDataBinding()) {
                                // Create a task that will package the manifest ids(the R file packages) of all
                                // features into a file. This file's path is passed into the Data Binding annotation
                                // processor which uses it to known about all available features.
                                //
                                // <p>see: {@link TaskManager#setDataBindingAnnotationProcessorParams(VariantScope)}
                                taskFactory.register(new DataBindingExportFeatureApplicationIdsTask.CreationAction(variantScope));

                            }
                        } else {
                            // Non-base feature specific task.
                            // Task will produce artifacts consumed by the base feature
                            taskFactory.register(new FeatureSplitDeclarationWriterTask.CreationAction(variantScope));
                            if (globalScope.getBuildFeatures().getDataBinding()) {
                                // Create a task that will package necessary information about the feature into a
                                // file which is passed into the Data Binding annotation processor.
                                taskFactory.register(new DataBindingExportFeatureInfoTask.CreationAction(variantScope));
                            }
                            taskFactory.register(new ExportConsumerProguardFilesTask.CreationAction(variantScope));
                            taskFactory.register(new FeatureNameWriterTask.CreationAction(variantScope));
                        }

                        // Add data binding tasks if enabled
                        createDataBindingTasksIfNecessary(variantScope);

                        // Add a compile task
                        createCompileTask(variantScope);

                        taskFactory.register(new StripDebugSymbolsTask.CreationAction(variantScope));

                        createPackagingTask(variantScope);

                        maybeCreateLintVitalTask((ApkVariantData) variantScope.getVariantData(), variantScopesForLint);

                        // Create the lint tasks, if enabled
                        createLintTasks(variantScope, variantScopesForLint);

                        taskFactory.register(new PackagedDependenciesWriterTask.CreationAction(variantScope));

                        createDynamicBundleTask(variantScope);

                        taskFactory.register(new ApkZipPackagingTask.CreationAction(variantScope));

                        // do not publish the APK(s) if there are dynamic feature.
                        if (!variantScope.getGlobalScope().hasDynamicFeatures()) {
                            createSoftwareComponent(variantScope, "_apk", APK_PUBLICATION);
                        }
                        createSoftwareComponent(variantScope, "_aab", AAB_PUBLICATION);
                    }
                }
            }
        }
    }
}
```











### APK 打包过程中的主要 Task 源码分析
#### 1. processDebugManifest

在 `createTasksForVariantScope` 中通过 `createMergeApkManifestsTask` 方法创建。

使用 `TaskFactory.register` 将 `ProcessApplicationManifest.CreationAction` 转换成了 `Task`，因此主要在 `ProcessApplicationManifest.CreationAction` 中处理 `Manifest` 文件
```java
//TaskManager.java
public void createMergeApkManifestsTask(@NonNull VariantScope variantScope) {
    AndroidArtifactVariantData androidArtifactVariantData = (AndroidArtifactVariantData) variantScope.getVariantData();
    Set<String> screenSizes = androidArtifactVariantData.getCompatibleScreens();

    taskFactory.register(new CompatibleScreensManifest.CreationAction(variantScope, screenSizes));

    TaskProvider<? extends ManifestProcessorTask> processManifestTask = createMergeManifestTask(variantScope);

    final MutableTaskContainer taskContainer = variantScope.getTaskContainer();
    if (taskContainer.getMicroApkTask() != null) {
        TaskFactoryUtils.dependsOn(processManifestTask, taskContainer.getMicroApkTask());
    }
}


//TaskManager.java
@NonNull
protected TaskProvider<? extends ManifestProcessorTask> createMergeManifestTask(@NonNull VariantScope variantScope) {
    return taskFactory.register(new ProcessApplicationManifest.CreationAction(
                    variantScope.getVariantData().getPublicVariantPropertiesApi(),
                    !getAdvancedProfilingTransforms(projectOptions).isEmpty()));
}
```


`ProcessApplicationManifest.CreationAction` 继承自 `VariantTaskCreationAction<ProcessApplicationManifest>`，处理 Manifest 文件的核心代码在 `ProcessApplicationManifest.doFullTaskAction()` 中

`ProcessApplicationManifest` 的类声明如下：
`public abstract class ProcessApplicationManifest extends ManifestProcessorTask `
`public abstract class ManifestProcessorTask extends IncrementalTask`



##### （1）配置阶段
```java
//设置 Task 的基本配置信息，例如输入以及输出，构建类型等等。
//ProcessApplicationManifest.java
@Override
public void configure(@NonNull ProcessApplicationManifest task) {
    super.configure(task);

    final VariantScope variantScope = getVariantScope();
    final VariantDslInfo variantDslInfo = variantScope.getVariantDslInfo();
    final VariantSources variantSources = variantScope.getVariantSources();
    final GlobalScope globalScope = variantScope.getGlobalScope();

    VariantType variantType = variantScope.getType();

    Project project = globalScope.getProject();

    // This includes the dependent libraries.
    task.manifests = variantScope.getArtifactCollection(RUNTIME_CLASSPATH, ALL, MANIFEST);

    // Also include rewritten auto-namespaced manifests if there are any
    if (variantType.isBaseModule() // TODO(b/112251836): Auto namespacing for dynamic features.
            && globalScope.getExtension().getAaptOptions().getNamespaced()
            && globalScope.getProjectOptions().get(BooleanOption.CONVERT_NON_NAMESPACED_DEPENDENCIES)) {
        variantScope
                .getArtifacts()
                .setTaskInputToFinalProduct(
                        InternalArtifactType.NAMESPACED_MANIFESTS.INSTANCE,
                        task.getAutoNamespacedManifests());
    }

    // optional manifest files too.
    if (variantScope.getTaskContainer().getMicroApkTask() != null
            && variantDslInfo.isEmbedMicroApp()) {
        task.microApkManifest = project.files(variantScope.getMicroApkManifestFile());
    }
    BuildArtifactsHolder artifacts = variantScope.getArtifacts();
    artifacts.setTaskInputToFinalProduct(
            InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST.INSTANCE,
            task.getCompatibleScreensManifest());

    //将 applicationId 设置为在 build.gradle 中设置的 applicationId，并且不允许在修改
    task.getApplicationId().set(variantScope.getVariantData().getPublicVariantPropertiesApi().getApplicationId());
    task.getApplicationId().disallowChanges();

    //设置 VariantType，且不允许在修改，分为 base module 和 dynamic feature
    task.getVariantType().set(variantScope.getVariantData().getType().toString());
    task.getVariantType().disallowChanges();

    //设置 minSdkVersion，且不允许在修改
    task.getMinSdkVersion().set(project.provider(() -> variantDslInfo.getMinSdkVersion().getApiString()));
    task.getMinSdkVersion().disallowChanges();

    //设置 targetS打开Version，且不允许在修改
    task.getTargetSdkVersion().set(project.provider(() -> {
            ApiVersion targetSdk = variantDslInfo.getTargetSdkVersion();
            return targetSdk.getApiLevel() < 1 ? null : targetSdk.getApiString();
        }));
    task.getTargetSdkVersion().disallowChanges();

    //设置 maxSdkVersion，且不允许在修改。
    task.getMaxSdkVersion().set(project.provider(variantDslInfo::getMaxSdkVersion));
    task.getMaxSdkVersion().disallowChanges();

    task.getOptionalFeatures().set(project.provider(() -> getOptionalFeatures(variantScope, isAdvancedProfilingOn)));
    task.getOptionalFeatures().disallowChanges();

    variantScope
            .getVariantData()
            .getPublicVariantPropertiesApi()
            .getOutputs()
            .getEnabledVariantOutputs()
            .forEach(variantOutput -> task.getApkDataList().add(variantOutput.getApkData()));
    task.getApkDataList().disallowChanges();

    // set optional inputs per module type
    if (variantType.isBaseModule()) {
        task.featureManifests = variantScope.getArtifactCollection(REVERSE_METADATA_VALUES, PROJECT, REVERSE_METADATA_FEATURE_MANIFEST);
    } else if (variantType.isDynamicFeature()) {//当前 module 时动态功能模块
        task.getFeatureName().set(variantScope.getFeatureName());
        task.getFeatureName().disallowChanges();

        task.packageManifest = variantScope.getArtifactFileCollection(COMPILE_CLASSPATH, PROJECT, BASE_MODULE_METADATA);

        task.dependencyFeatureNameArtifacts = variantScope.getArtifactFileCollection(RUNTIME_CLASSPATH, PROJECT, FEATURE_NAME);
    }

    if (!globalScope.getExtension().getAaptOptions().getNamespaced()) {
        task.navigationJsons = project.files(
                        variantScope.getArtifacts().getFinalProduct(InternalArtifactType.NAVIGATION_JSON.INSTANCE),
                        variantScope.getArtifactFileCollection(RUNTIME_CLASSPATH, ALL, NAVIGATION_JSON));
    }
    task.packageOverride.set(componentProperties.getApplicationId());
    task.packageOverride.disallowChanges();

    task.manifestPlaceholders.set(task.getProject().provider(variantDslInfo::getManifestPlaceholders));
    task.manifestPlaceholders.disallowChanges();

    //设置清单文件的路ing
    task.getMainManifest().set(project.provider(variantSources::getMainManifestFilePath));
    task.getMainManifest().disallowChanges();

    //获取模块下的所有的 AndroidManifest.xml 文件
    task.manifestOverlays.set(task.getProject().provider(variantSources::getManifestOverlays));
    task.manifestOverlays.disallowChanges();

    task.isFeatureSplitVariantType = variantDslInfo.getVariantType().isDynamicFeature();
    task.buildTypeName = variantDslInfo.getComponentIdentity().getBuildType();
    // TODO: here in the "else" block should be the code path for the namespaced pipeline
}
```


##### （2）执行阶段
```java
//其实就是合并 module 下的各个清单文件，生成一个最终的清单文件
//ProcessApplicationManifest.java
@Override
protected void doFullTaskAction() throws IOException {
    // read the output of the compatible screen manifest.
    BuildElements compatibleScreenManifests = ExistingBuildElements.from(
                    InternalArtifactType.COMPATIBLE_SCREEN_MANIFEST.INSTANCE,
                    getCompatibleScreensManifest().get().getAsFile());

    ModuleMetadata moduleMetadata = null;
    if (packageManifest != null && !packageManifest.isEmpty()) {
        //获取 Manifest 文件的信息。
        moduleMetadata = ModuleMetadata.load(packageManifest.getSingleFile());
        boolean isDebuggable = getOptionalFeatures().get().contains(Feature.DEBUGGABLE);
        if (moduleMetadata.getDebuggable() != isDebuggable) {
            String errorMessage =
                    String.format(
                            "Dynamic Feature '%1$s' (build type '%2$s') %3$s debuggable,\n"
                                    + "and the corresponding build type in the base "
                                    + "application %4$s debuggable.\n"
                                    + "Recommendation: \n"
                                    + "   in  %5$s\n"
                                    + "   set android.buildTypes.%2$s.debuggable = %6$s",
                            getProject().getPath(),
                            buildTypeName,
                            isDebuggable ? "is" : "is not",
                            moduleMetadata.getDebuggable() ? "is" : "is not",
                            getProject().getBuildFile(),
                            moduleMetadata.getDebuggable() ? "true" : "false");
            throw new InvalidUserDataException(errorMessage);
        }
    }


    @Nullable BuildOutput compatibleScreenManifestForSplit;

    ImmutableList.Builder<BuildOutput> mergedManifestOutputs = ImmutableList.builder();
    ImmutableList.Builder<BuildOutput> metadataFeatureMergedManifestOutputs = ImmutableList.builder();
    ImmutableList.Builder<BuildOutput> bundleManifestOutputs = ImmutableList.builder();
    ImmutableList.Builder<BuildOutput> instantAppManifestOutputs = ImmutableList.builder();

    List<File> navJsons = navigationJsons == null ? Collections.emptyList() : Lists.newArrayList(navigationJsons);
    // FIX ME : multi threading.
    for (ApkData apkData : getApkDataList().get()) {
        compatibleScreenManifestForSplit = compatibleScreenManifests.element(apkData);
        //getManifestOutputDirectory()：build/intermediates/merged_manifest/${buildType}${productFlavor}/
        //ANDROID_MANIFEST_XML = “AndroidManifest.xml”，获取清单文件的输出路径
        File manifestOutputFile = new File(
                getManifestOutputDirectory().get().getAsFile(),
                FileUtils.join(apkData.getDirName(), ANDROID_MANIFEST_XML));

        //获得功能模块的清单文件的输出路径，功能模块用于 App Bundle 中
        File metadataFeatureManifestOutputFile = FileUtils.join(
                getMetadataFeatureManifestOutputDirectory().get().getAsFile(),
                apkData.getDirName(),
                ANDROID_MANIFEST_XML);

        //所使用的 bundletool.jar 工具所使用的 AndroidManifest.xml 路径
        File bundleManifestOutputFile = FileUtils.join(
                getBundleManifestOutputDirectory().get().getAsFile(),
                apkData.getDirName(),
                ANDROID_MANIFEST_XML);

        //构建 Instant App 所使用的清单文件的路径
        File instantAppManifestOutputFile = getInstantAppManifestOutputDirectory().isPresent() ? FileUtils.join(
                        getInstantAppManifestOutputDirectory().get().getAsFile(),
                        apkData.getDirName(),
                        ANDROID_MANIFEST_XML) : null;

        //合并清单文件，在 mergeManifestsForApplication 中会执行 AndroidManifest.xml 中 placeholder 的替换
        MergingReport mergingReport = ManifestHelperKt.mergeManifestsForApplication(
                getMainManifest().get(),//主 AndroidManifest.xml，即 module/src/main/AndroidManifest.xml
                manifestOverlays.get(), //模块下，除了主 AndroidManifest.xml 之外的其他 AndroidManifest.xml。
                computeFullProviderList(compatibleScreenManifestForSplit),
                navJsons,
                getFeatureName().getOrNull(),
                moduleMetadata == null ? packageOverride.getOrNull() : moduleMetadata.getApplicationId(),
                moduleMetadata == null ? apkData.getVersionCode() : Integer.parseInt(moduleMetadata.getVersionCode()),
                moduleMetadata == null ? apkData.getVersionName() : moduleMetadata.getVersionName(),
                getMinSdkVersion().getOrNull(),
                getTargetSdkVersion().getOrNull(),
                getMaxSdkVersion().getOrNull(),
                manifestOutputFile.getAbsolutePath(),
                // no aapt friendly merged manifest file necessary for applications.
                null /* aaptFriendlyManifestOutputFile */,
                metadataFeatureManifestOutputFile.getAbsolutePath(),
                bundleManifestOutputFile.getAbsolutePath(),
                instantAppManifestOutputFile != null ? instantAppManifestOutputFile.getAbsolutePath() : null,
                ManifestMerger2.MergeType.APPLICATION,
                manifestPlaceholders.get(), //这里面保存在在 build.gradle 中通过 “manifestPlaceholders” 声明的键值对，manifestPlaceholders 在配置阶段被设置
                getOptionalFeatures().get(),
                getDependencyFeatureNames(),
                getReportFile().get().getAsFile(),
                LoggerWrapper.getLogger(ProcessApplicationManifest.class));

        XmlDocument mergedXmlDocument = mergingReport.getMergedXmlDocument(MergingReport.MergedManifestKind.MERGED);

        //将合并之后的清单文件输出成 “AndroidManifest.xml” 文件
        outputMergeBlameContents(mergingReport, getMergeBlameFile().get().getAsFile());

        /*
        *返回一个 Map，里面包含以下元素：{
        *   "packageId":mergedXmlDocument.getPackageName(), 
        *   "split":mergedXmlDocument.getSplitName(), 
        *   SdkConstants.ATTR_MIN_SDK_VERSION:mergedXmlDocument.getMinSdkVersion()
        *}
        * 在合并后的清单文件中添加一些内容。
        */
        ImmutableMap<String, String> properties = mergedXmlDocument != null ? ImmutableMap.of(
                "packageId",
                mergedXmlDocument.getPackageName(),
                "split",
                mergedXmlDocument.getSplitName(),
                SdkConstants.ATTR_MIN_SDK_VERSION,
                mergedXmlDocument.getMinSdkVersion()) : ImmutableMap.of();

        //清单文件合并后需要输出的内容
        mergedManifestOutputs.add(new BuildOutput(
                InternalArtifactType.MERGED_MANIFESTS.INSTANCE,
                apkData,
                manifestOutputFile,
                properties));

        //功能模块的清单文件合并后应输出的内容
        metadataFeatureMergedManifestOutputs.add(new BuildOutput(
                InternalArtifactType.METADATA_FEATURE_MANIFEST.INSTANCE,
                apkData,
                metadataFeatureManifestOutputFile));

        //bundletool.jar 使用到的清单文件中应包含的内容
        bundleManifestOutputs.add(new BuildOutput(
                InternalArtifactType.BUNDLE_MANIFEST.INSTANCE,
                apkData,
                bundleManifestOutputFile,
                properties));

        //Instant App 的清单文件应包含的内容。
        if (instantAppManifestOutputFile != null) {
            instantAppManifestOutputs.add(new BuildOutput(
                    InternalArtifactType.INSTANT_APP_MANIFEST.INSTANCE,
                    apkData,
                    instantAppManifestOutputFile,
                    properties));
        }
    }

    //封装任务执行完成之后的结果。 针对不同类型的拆分生成相同类型的结果
    new BuildElements(
                    BuildElements.METADATA_FILE_VERSION,
                    getApplicationId().get(),
                    getVariantType().get(),
                    mergedManifestOutputs.build()).save(getManifestOutputDirectory());

    new BuildElements(
                    BuildElements.METADATA_FILE_VERSION,
                    getApplicationId().get(),
                    getVariantType().get(),
                    metadataFeatureMergedManifestOutputs.build())
            .save(getMetadataFeatureManifestOutputDirectory());

    new BuildElements(
                    BuildElements.METADATA_FILE_VERSION,
                    getApplicationId().get(),
                    getVariantType().get(),
                    bundleManifestOutputs.build())
            .save(getBundleManifestOutputDirectory());

    if (getInstantAppManifestOutputDirectory().isPresent()) {
        new BuildElements(
                        BuildElements.METADATA_FILE_VERSION,
                        getApplicationId().get(),
                        getVariantType().get(),
                        instantAppManifestOutputs.build())
                .save(getInstantAppManifestOutputDirectory());
    }
}
```


### 2. mergeDebugResources
在 `createTasksForVariantScope` 中通过 `createMergeResourcesTasks` 方法创建。

##### （1）任务创建
```java
//ApplicationTaskManager.java
private void createMergeResourcesTasks(@NonNull VariantScope variantScope) {
    //对资源进行合并和编译，用于稍后的资源链接
    createMergeResourcesTask(variantScope, true, Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES));

    //用于测试
    if (projectOptions.get(BooleanOption.ENABLE_APP_COMPILE_TIME_R_CLASS)
            && !variantScope.getType().isForTesting()
            && !variantScope.getGlobalScope().getExtension().getAaptOptions().getNamespaced()) {

        basicCreateMergeResourcesTask(
                variantScope,
                MergeType.PACKAGE,
                variantScope.getIntermediateDir(InternalArtifactType.PACKAGED_RES.INSTANCE),
                false,
                false,
                false,
                ImmutableSet.of(),
                null);
    }
}

//TaskManager
public void createMergeResourcesTask(@NonNull VariantScope scope, boolean processResources, ImmutableSet<MergeResources.Flag> flags) {

    //是否输出未编译的资源，用于测试
    boolean alsoOutputNotCompiledResources = scope.getType().isApk() && !scope.getType().isForTesting() && scope.useResourceShrinker();

    basicCreateMergeResourcesTask(
            scope,
            MergeType.MERGE,
            null /*outputLocation*/,
            true /*includeDependencies*/,
            processResources,
            alsoOutputNotCompiledResources,
            flags,
            null /*configCallback*/);
}

//TaskManager.java
public TaskProvider<MergeResources> basicCreateMergeResourcesTask(@NonNull VariantScope scope, @NonNull MergeType mergeType, @Nullable File outputLocation, final boolean includeDependencies, final boolean processResources, boolean alsoOutputNotCompiledResources, @NonNull ImmutableSet<MergeResources.Flag> flags, @Nullable TaskProviderCallback<MergeResources> taskProviderCallback) {

    //Task 名称前缀
    String taskNamePrefix = mergeType.name().toLowerCase(Locale.ENGLISH);

    File mergedNotCompiledDir =
            alsoOutputNotCompiledResources ? new File(
                    globalScope.getIntermediatesDir()
                            + "/merged-not-compiled-resources/"
                            + scope.getVariantDslInfo().getDirName()) : null;

    //通过 taskFactory.register 从一个 Action 生成对应的 Task
    TaskProvider<MergeResources> mergeResourcesTask = taskFactory.register(new MergeResources.CreationAction(
                scope,
                mergeType,
                taskNamePrefix,
                mergedNotCompiledDir,
                includeDependencies,
                processResources,
                flags,
                isLibrary()), null, null, taskProviderCallback);

    scope.getArtifacts()
            .producesDir(
                    mergeType.getOutputType(),
                    mergeResourcesTask,
                    MergeResources::getOutputDir,
                    MoreObjects.firstNonNull(
                                    outputLocation, scope.getDefaultMergeResourcesOutputDir())
                            .getAbsolutePath(),
                    "");

    if (alsoOutputNotCompiledResources) {
        scope.getArtifacts().producesDir(
                MERGED_NOT_COMPILED_RES.INSTANCE,
                mergeResourcesTask,
                MergeResources::getMergedNotCompiledResourcesOutputDirectory,
                mergedNotCompiledDir.getAbsolutePath(),
                "");
    }

    if (extension.getTestOptions().getUnitTests().isIncludeAndroidResources()) {
        TaskFactoryUtils.dependsOn(scope.getTaskContainer().getCompileTask(), mergeResourcesTask);
    }

    return mergeResourcesTask;
}

```

`MergeResources.CreationAction extends VariantTaskCreationAction<MergeResources>` ，可以看出 `MergeResources.CreationAction` 对应的 Task 是 `MergeResources`，`MergeResources` 的声明如下：
- `public abstract class MergeResources extends ResourceAwareTask`
- `abstract class ResourceAwareTask : IncrementalTask()`

这是一个增量 Task，主要看以下三个方法
- `getIncremental`
- `doFullTaskAction`
- `doIncrementalTaskAction`


##### （2）执行阶段
###### isIncremental
```java
//MergeResource.java
@Override
protected boolean getIncremental() {
    return true;
}
```
表示 MergeResource 是支持增量编译的，因此会重写 `doIncrementalTaskAction` 方法。

###### doFullTaskAction 
```java
//
@Override
protected void doFullTaskAction() throws IOException, JAXBException {
    ResourcePreprocessor preprocessor = getPreprocessor();

    //清空旧的编译输出
    File destinationDir = getOutputDir().get().getAsFile();
    FileUtils.cleanOutputDir(destinationDir);
    if (getDataBindingLayoutInfoOutFolder().isPresent()) {
        FileUtils.deleteDirectoryContents(getDataBindingLayoutInfoOutFolder().get().getAsFile());
    }

    //获取 resourceSets，这里面包括了自己的 res 下的资源以及 build/generated/res/rs 目录下的资源
    List<ResourceSet> resourceSets = getConfiguredResourceSets(preprocessor);

    //创建 ResourceMerger 实例，用于资源合并
    ResourceMerger merger = new ResourceMerger(getMinSdk().get());
    MergingLog mergingLog = null;
    if (blameLogFolder != null) {
        FileUtils.cleanOutputDir(blameLogFolder);
        mergingLog = new MergingLog(blameLogFolder);
    }

    try (WorkerExecutorFacade workerExecutorFacade = getAaptWorkerFacade();
            //ResourceCompilationService 里面会使用 aapt2
            ResourceCompilationService resourceCompiler = getResourceProcessor(
                    getProjectName(),
                    getPath(),
                    getAapt2FromMaven(),
                    workerExecutorFacade,
                    errorFormatMode,
                    flags,
                    processResources,
                    useJvmResourceCompiler,
                    getLogger(),
                    getAapt2DaemonBuildService().get())) {

        Blocks.recordSpan(
                getProject().getName(),
                getPath(),
                GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_1,
                () -> {
                    //遍历所有的资源，添加到 resourceMerge 中，之后会对 ResourceMerge 中的资源进行合并
                    for (ResourceSet resourceSet : resourceSets) {
                        resourceSet.loadFromFiles(new LoggerWrapper(getLogger()));
                        merger.addDataSet(resourceSet);
                    }
                });

        File publicFile = getPublicFile().isPresent() ? getPublicFile().get().getAsFile() : null;
        //MergedResourceWriter ，可以看作是资源输出流
        MergedResourceWriter writer = new MergedResourceWriter(
                workerExecutorFacade,
                destinationDir,
                publicFile,
                mergingLog,
                preprocessor,
                resourceCompiler,
                getIncrementalFolder(),
                dataBindingLayoutProcessor,
                mergedNotCompiledResourcesOutputDirectory,
                pseudoLocalesEnabled,
                getCrunchPng());

        Blocks.recordSpan(
                getProject().getName(),
                getPath(),
                GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_2,
                //对资源进行合并
                //mergeData 会调用 MergedResourceWriter 的 start，ignoreItemInMerge，removeItem，addItem，end 方法对资源 item 进行处理，资源 item 包含了需要处理的资源，包括 xml 和图片资源。
                //在 MergedResourceWriter.addItem 方法中会针对每一个 item，都会创建与之对应的 CompileResourceRequest 实例，并将该实例加入到 mCompileResourceRequests 中，这是一个 ConcurrentLinkedQueue 队列。
                //在 MergedResourceWriter.end 中最终会执行 mResourceCompiler.submitCompile 方法来处理资源，其实就是生成 aapt2 命令去处理资源
                () -> merger.mergeData(writer, false /*doCleanUp*/));

        Blocks.recordSpan(
                getProject().getName(),
                getPath(),
                GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_3,
                () -> {
                    if (dataBindingLayoutProcessor != null) {
                        dataBindingLayoutProcessor.end();
                    }
                });

        Blocks.recordSpan(
                getProject().getName(),
                getPath(),
                GradleBuildProfileSpan.ExecutionType.TASK_EXECUTION_PHASE_4,
                () -> merger.writeBlobTo(getIncrementalFolder(), writer, false));

    } catch (Exception e) {
        MergingException.findAndReportMergingException(e, new MessageReceiverImpl(errorFormatMode, getLogger()));
        try {
            throw e;
        } catch (MergingException mergingException) {
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(mergingException.getMessage(), mergingException);
        }
    } finally {
        cleanup();
    }
}
```

ResourceMerge 会遍历所有的 Resource Item，然后将需要编译的 item 添加到 MergeResourceWrite 中，通过 MergedResourceWriter.addItem 将待编译的的资源放入一个并发队列中（ConcurrentLinkedQueue），在遍历完成之后执行 MergedResourceWriter.end() ，在这里面会调用 mResourceCompiler.submitCompile 对资源进行编译，其本质就是生成 aapt2 命令去编译资源。


###### doIncrementalTaskAction
```java
//MergeResources.java
@Override
protected void doIncrementalTaskAction(@NonNull Map<File, ? extends FileStatus> changedInputs) throws IOException, JAXBException {
    ResourcePreprocessor preprocessor = getPreprocessor();

    // create a merger and load the known state.
    ResourceMerger merger = new ResourceMerger(getMinSdk().get());
    try {
        //从缓存中加载编译过的资源
        if (!merger.loadFromBlob(getIncrementalFolder(), true /*incrementalState*/)) {
            doFullTaskAction();
            return;
        }

        if (precompileDependenciesResources) {
            changedInputs =
                    changedInputs
                            .entrySet()
                            .stream()
                            .filter(
                                    fileEntry ->
                                            !isFilteredOutLibraryResource(fileEntry.getKey()))
                            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            if (changedInputs.isEmpty()) {
                return;
            }
        }

        //获取 ResourceSet
        for (ResourceSet resourceSet : merger.getDataSets()) {
            resourceSet.setPreprocessor(preprocessor);
        }

        //获取所有的资源
        List<ResourceSet> resourceSets = getConfiguredResourceSets(preprocessor);

        //将获取的资源的状态与之前保存的状态作比较，过滤掉未修改过的资源
        if (!merger.checkValidUpdate(resourceSets)) {
            getLogger().info("Changed Resource sets: full task run!");
            doFullTaskAction();
            return;
        }

        for (Map.Entry<File, ? extends FileStatus> entry : changedInputs.entrySet()) {
            File changedFile = entry.getKey();

            merger.findDataSetContaining(changedFile, fileValidity);
            if (fileValidity.getStatus() == FileValidity.FileStatus.UNKNOWN_FILE) {
                doFullTaskAction();
                return;
            } else if (fileValidity.getStatus() == FileValidity.FileStatus.VALID_FILE) {
                if (!fileValidity
                        .getDataSet()
                        .updateWith(fileValidity.getSourceFile(), changedFile, entry.getValue(), new LoggerWrapper(getLogger()))) {
                    getLogger().info(String.format("Failed to process %s event! Full task run", entry.getValue()));
                    doFullTaskAction();
                    return;
                }
            }
        }

        MergingLog mergingLog = getBlameLogFolder() != null ? new MergingLog(getBlameLogFolder()) : null;

        try (WorkerExecutorFacade workerExecutorFacade = getAaptWorkerFacade();
                //获取 ResourceCompilationService，用于编译资源
                ResourceCompilationService resourceCompiler =
                        getResourceProcessor(
                                getProjectName(),
                                getPath(),
                                getAapt2FromMaven(),
                                workerExecutorFacade,
                                errorFormatMode,
                                flags,
                                processResources,
                                useJvmResourceCompiler,
                                getLogger(),
                                getAapt2DaemonBuildService().get())) {

            File publicFile = getPublicFile().isPresent() ? getPublicFile().get().getAsFile() : null;

            MergedResourceWriter writer = new MergedResourceWriter(
                    workerExecutorFacade,
                    getOutputDir().get().getAsFile(),
                    publicFile,
                    mergingLog,
                    preprocessor,
                    resourceCompiler,
                    getIncrementalFolder(),
                    dataBindingLayoutProcessor,
                    mergedNotCompiledResourcesOutputDirectory,
                    pseudoLocalesEnabled,
                    getCrunchPng());

            merger.mergeData(writer, false /*doCleanUp*/);

            if (dataBindingLayoutProcessor != null) {
                dataBindingLayoutProcessor.end();
            }

            // No exception? Write the known state.
            merger.writeBlobTo(getIncrementalFolder(), writer, false);
        }
    } catch (Exception e) {
        MergingException.findAndReportMergingException(e, new MessageReceiverImpl(errorFormatMode, getLogger()));
        try {
            throw e;
        } catch (MergingException mergingException) {
            merger.cleanBlob(getIncrementalFolder());
            throw new ResourceException(mergingException.getMessage(), mergingException);
        }
    } finally {
        cleanup();
    }
}
```
增量编译和全量编译的差别并不大，区别在于增量编译获取的是已经被修改过的资源，全量编译获取的是所有的资源



### 3. DexArchiveBuilderTask
此 Task 用于将 Class 文件打包成 Dex 文件。它的实现类是 `DexArchiveBuilderTask.java`，该类可以通过 addFile 添加一个 Dex 文件。

createDexMerging 会创建合并 Dex 文件的 Task（DexMergerTransform），这个 Transform 会根据是否是 Debug 模式对 dex 文件做不同的处理，Debug 模式下，通过 dependencies 依赖的库，本地库(libs 目录下)，以及工程的源代码文件会分别合并到单独的 Dex 文件中（通过提交多个合并任务实现）。而 release 模式下会将所有的 Dex 文件（包括远程依赖库，本地库，源文件）合并到一个 Dex 文件中。

#### （1）创建
```java
//TaskManager.java
protected void createCompileTask(@NonNull VariantScope variantScope) {
    TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantScope);
    //获取所有 Java 源文件，将 .java 编译成 .class，最后将 .class 转成 .dex
    addJavacClassesStream(variantScope);
    setJavaCompilerTask(javacTask, variantScope);
    createPostCompilationTasks(variantScope);
}

//TaskManager.java
public void createPostCompilationTasks(@NonNull final VariantScope variantScope) {

    checkNotNull(variantScope.getTaskContainer().getJavacTask());

    final BaseVariantData variantData = variantScope.getVariantData();
    final VariantDslInfo variantDslInfo = variantData.getVariantDslInfo();

    TransformManager transformManager = variantScope.getTransformManager();

    //代码混淆
    taskFactory.register(new MergeGeneratedProguardFilesCreationAction(variantScope));

    // ---- Code Coverage first -----
    boolean isTestCoverageEnabled = variantDslInfo.isTestCoverageEnabled() && !variantDslInfo.getVariantType().isForTesting();
    if (isTestCoverageEnabled) {
        createJacocoTask(variantScope);
    }

    maybeCreateDesugarTask(variantScope, variantDslInfo.getMinSdkVersion(), transformManager, isTestCoverageEnabled);

    BaseExtension extension = variantScope.getGlobalScope().getExtension();

    // 合并 Java 的资源
    createMergeJavaResTask(variantScope);

    // ----- External Transforms -----
    // 应用所有自定义的 Transforms，所以自定义的 Transform 是在这里进行注册，可以指定这些 Transform 所依赖的 Task，并不是执行
    List<Transform> customTransforms = extension.getTransforms();
    List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

    boolean registeredExternalTransform = false;
    for (int i = 0, count = customTransforms.size(); i < count; i++) {
        Transform transform = customTransforms.get(i);

        List<Object> deps = customTransformsDependencies.get(i);
        registeredExternalTransform |=
                transformManager.addTransform(
                        taskFactory,
                        variantScope,
                        transform,
                        null,
                        task -> {
                            if (!deps.isEmpty()) {
                                task.dependsOn(deps);
                            }
                        },
                        taskProvider -> {
                            // if the task is a no-op then we make assemble task depend on it.
                            if (transform.getScopes().isEmpty()) {
                                TaskFactoryUtils.dependsOn(variantScope.getTaskContainer().getAssembleTask(),taskProvider);
                            }
                        }).isPresent();
    }

    // Add a task to create merged runtime classes if this is a dynamic-feature,
    // or a base module consuming feature jars. Merged runtime classes are needed if code
    // minification is enabled in a project with features or dynamic-features.
    if (variantData.getType().isDynamicFeature() || variantScope.consumesFeatureJars()) {
        taskFactory.register(new MergeClassesTask.CreationAction(variantScope));
    }

    // ----- Android studio profiling transforms
    if (appliesCustomClassTransforms(variantScope, projectOptions)) {
        for (String jar : getAdvancedProfilingTransforms(projectOptions)) {
            if (jar != null) {
                transformManager.addTransform(
                        taskFactory,
                        variantScope,
                        new CustomClassTransform(
                                jar,
                                packagesCustomClassDependencies(variantScope, projectOptions)));
            }
        }
    }

    // ----- Minify next -----
    maybeCreateCheckDuplicateClassesTask(variantScope);
    CodeShrinker shrinker = maybeCreateJavaCodeShrinkerTask(variantScope);
    if (shrinker == CodeShrinker.R8) {
        maybeCreateResourcesShrinkerTasks(variantScope);
        maybeCreateDexDesugarLibTask(variantScope, false);
        return;
    }

    // ----- Multi-Dex support
    DexingType dexingType = variantScope.getDexingType();

    // 启用了分 dex ，且 minSdk >= 21，则强制使用 NATIVE_MULTIDEX 类型的分 dex
    if (dexingType == DexingType.LEGACY_MULTIDEX) {
        if (variantScope.getVariantDslInfo().isMultiDexEnabled()
                && variantScope.getVariantDslInfo().getMinSdkVersionWithTargetDeviceApi().getFeatureLevel()>= 21) {
            dexingType = DexingType.NATIVE_MULTIDEX;
        }
    }

    //如果是 LEGACY_MULTIDEX 类型的分 dex ，则这里返回 true，也就是打出来的 apk 中会有一个主 dex 文件，这种情况下可以指定主 Dex 文件中包含哪些类
    if (variantScope.getNeedsMainDexList()) {
        taskFactory.register(new D8MainDexListTask.CreationAction(variantScope, false));
    }

    //当前 module 是 base module，且定义了动态功能模块，且为 LEGACY_MULTIDEX 类型的分 dex，minSdk >= 21
    if (variantScope.getNeedsMainDexListForBundle()) {
        taskFactory.register(new D8MainDexListTask.CreationAction(variantScope, true));
    }

    //创建 DexArchiveBuildTask，将 class 文件转成 dex 文件
    createDexTasks(variantScope, dexingType, registeredExternalTransform);

    maybeCreateResourcesShrinkerTasks(variantScope);

    maybeCreateDexSplitterTask(variantScope);
}


//TaskManager.java
private void createDexTasks(@NonNull VariantScope variantScope, @NonNull DexingType dexingType, boolean registeredExternalTransform) {
    DefaultDexOptions dexOptions;
    if (variantScope.getVariantData().getType().isTestComponent()) {//测试
        dexOptions = DefaultDexOptions.copyOf(extension.getDexOptions());
        dexOptions.setAdditionalParameters(ImmutableList.of());
    } else {
        dexOptions = extension.getDexOptions();
    }

    Java8LangSupport java8SLangSupport = variantScope.getJava8LangSupportType();
    boolean minified = variantScope.getCodeShrinker() != null;
    boolean supportsDesugaring =
            java8SLangSupport == Java8LangSupport.UNUSED
                    || (java8SLangSupport == Java8LangSupport.D8
                            && projectOptions.get(
                                    BooleanOption.ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM));
    boolean enableDexingArtifactTransform =
            globalScope.getProjectOptions().get(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM)
                    && !registeredExternalTransform
                    && !minified
                    && supportsDesugaring
                    && !appliesCustomClassTransforms(variantScope, projectOptions);
    FileCache userLevelCache = getUserDexCache(minified, dexOptions.getPreDexLibraries());

    //注册 DexArchiveBuilderTask 任务，将 class 文件转成 dex 文件
    taskFactory.register(new DexArchiveBuilderTask.CreationAction(dexOptions, enableDexingArtifactTransform, userLevelCache, variantScope));

    maybeCreateDexDesugarLibTask(variantScope, enableDexingArtifactTransform);

    //合并 dex 文件
    createDexMergingTasks(variantScope, dexingType, enableDexingArtifactTransform);
}


//TaskManager.java
private void createDexMerging(@NonNull VariantScope variantScope, @NonNull DexingType dexingType) {
    boolean isDebuggable = variantScope.getVariantConfiguration().getBuildType().isDebuggable();
    if (dexingType != DexingType.LEGACY_MULTIDEX
            && variantScope.getCodeShrinker() == null
            && extension.getTransforms().isEmpty()) {

        ExternalLibsMergerTransform externalLibsMergerTransform =
                new ExternalLibsMergerTransform(
                        dexingType,
                        variantScope.getDexMerger(),
                        variantScope.getMinSdkVersion().getFeatureLevel(),
                        isDebuggable,
                        variantScope.getGlobalScope().getMessageReceiver(),
                        DexMergerTransformCallable::new);

        variantScope
                .getTransformManager()
                .addTransform(taskFactory, variantScope, externalLibsMergerTransform);
    }

    DexMergerTransform dexTransform =
            new DexMergerTransform(
                    dexingType,
                    dexingType == DexingType.LEGACY_MULTIDEX
                            ? variantScope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST)
                            : null,
                    variantScope
                            .getArtifacts()
                            .getFinalArtifactFiles(InternalArtifactType.DUPLICATE_CLASSES_CHECK),
                    variantScope.getGlobalScope().getMessageReceiver(),
                    variantScope.getDexMerger(),
                    variantScope.getMinSdkVersion().getFeatureLevel(),
                    isDebuggable,
                    variantScope.consumesFeatureJars(),
                    variantScope.getInstantRunBuildContext().isInInstantRunMode());
    variantScope
            .getTransformManager()
            .addTransform(taskFactory, variantScope, dexTransform, null, null, variantScope::addColdSwapBuildTask);
}

//DexMergerTransform.java
public void transform(@NonNull TransformInvocation transformInvocation) throws TransformException, IOException, InterruptedException {
    TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
    Preconditions.checkNotNull(outputProvider, "Missing output object for transform " + getName());

    ProcessOutputHandler outputHandler =
            new ParsingProcessOutputHandler(
                    new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                    new ToolOutputParser(new DexParser(), logger),
                    messageReceiver);

    if (!transformInvocation.isIncremental()) {
        outputProvider.deleteAll();
    }

    ProcessOutput output = null;
    List<ForkJoinTask<Void>> mergeTasks;
    try (Closeable ignored = output = outputHandler.createOutput()) {
        if (dexingType == DexingType.NATIVE_MULTIDEX && isDebuggable) {
            mergeTasks =
                    handleNativeMultiDexDebug(
                            transformInvocation.getInputs(),
                            output,
                            outputProvider,
                            transformInvocation.isIncremental());
        } else {
            mergeTasks = mergeDex(transformInvocation.getInputs(), output, outputProvider);
        }

        // now wait for all merge tasks completion
        mergeTasks.forEach(ForkJoinTask::join);
    } catch (Exception e) {
        PluginCrashReporter.maybeReportException(e);
        // Print the error always, even without --stacktrace
        logger.error(null, Throwables.getStackTraceAsString(e));
        throw new TransformException(e);
    } finally {
        if (output != null) {
            try {
                outputHandler.handleOutput(output);
            } catch (ProcessException e) {
                // ignore this one
            }
        }
        forkJoinPool.shutdown();
        forkJoinPool.awaitTermination(100, TimeUnit.SECONDS);
    }
}

Merge Dex With Release Mode {
    private List<ForkJoinTask<Void>> mergeDex(Collection<TransformInput> inputs, ProcessOutput output, TransformOutputProvider outputProvider) throws IOException {
        Iterator<Path> dirInputs = TransformInputUtil.getDirectories(inputs).stream().map(File::toPath).iterator();
        Iterator<Path> jarInputs =
                inputs.stream()
                        .flatMap(transformInput -> transformInput.getJarInputs().stream())
                        .filter(jarInput -> jarInput.getStatus() != Status.REMOVED)
                        .map(jarInput -> jarInput.getFile().toPath())
                        .iterator();
        Iterator<Path> dexArchives = Iterators.concat(dirInputs, jarInputs);

        if (!dexArchives.hasNext()) {
            return ImmutableList.of();
        }

        File outputDir = getDexOutputLocation(outputProvider, "main", getScopes());
        // this deletes and creates the dir for the output
        FileUtils.cleanOutputDir(outputDir);

        Path mainDexClasses;
        if (mainDexListFile == null) {
            mainDexClasses = null;
        } else {
            mainDexClasses = BuildableArtifactUtil.singleFile(mainDexListFile).toPath();
        }

        return ImmutableList.of(submitForMerging(output, outputDir, dexArchives, mainDexClasses));
    }
}

Merge Dex With Debug Mode {
    //DexMergerTransform.java
    @NonNull
    private List<ForkJoinTask<Void>> handleNativeMultiDexDebug(Collection<TransformInput> inputs, ProcessOutput output, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException {

        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();

        List<DirectoryInput> directoryInputs = new ArrayList<>();
        List<JarInput> externalLibs = new ArrayList<>();
        List<JarInput> nonExternalJars = new ArrayList<>();
        //将所有的 dex 文件按照其作用域放到 directoryInputs, externalLibs, nonExternalJars 这三个集合中。
        collectInputsForNativeMultiDex(inputs, directoryInputs, externalLibs, nonExternalJars);

        boolean mergeAllInputs = shouldMergeInputsForNative(directoryInputs, nonExternalJars);
        subTasks.addAll(processDirectories(output, outputProvider, isIncremental, directoryInputs, mergeAllInputs));

        if (!nonExternalJars.isEmpty()) {
            if (mergeAllInputs) {
                subTasks.addAll(processNonExternalJarsTogether(output, outputProvider, isIncremental, nonExternalJars));
            } else {
                subTasks.addAll(processNonExternalJarsSeparately(output, outputProvider, isIncremental, nonExternalJars));
            }
        }

        subTasks.addAll(processExternalJars(output, outputProvider, isIncremental, externalLibs));
        return subTasks.build();
    }

    //DexMergerTransform.java
    private List<ForkJoinTask<Void>> processNonExternalJarsTogether(ProcessOutput output, TransformOutputProvider outputProvider, Collection<JarInput> inputs) throws IOException {

        if (inputs.isEmpty()) {
            return ImmutableList.of();
        }

        Set<Status> statuses = EnumSet.noneOf(Status.class);
        Iterable<? super Scope> allScopes = new HashSet<>();
        for (JarInput jarInput : inputs) {
            statuses.add(jarInput.getStatus());
            allScopes = Iterables.concat(allScopes, jarInput.getScopes());
        }
        if (isIncremental && statuses.equals(Collections.singleton(Status.NOTCHANGED))) {
            return ImmutableList.of();
        }

        File mergedOutput = getDexOutputLocation(outputProvider, "nonExternalJars", Sets.newHashSet(allScopes));
        FileUtils.cleanOutputDir(mergedOutput);

        Iterator<Path> toMerge =
                inputs.stream()
                        .filter(i -> i.getStatus() != Status.REMOVED)
                        .map(i -> i.getFile().toPath())
                        .iterator();

        if (toMerge.hasNext()) {
            return ImmutableList.of(submitForMerging(output, mergedOutput, toMerge, null));
        } else {
            return ImmutableList.of();
        }
    }

    //DexMergerTransform.java
    private List<ForkJoinTask<Void>> processExternalJars(ProcessOutput output, TransformOutputProvider outputProvider, boolean isIncremental, List<JarInput> externalLibs) throws IOException {
        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();
        File externalLibsOutput = getDexOutputLocation(outputProvider, "externalLibs", ImmutableSet.of(Scope.EXTERNAL_LIBRARIES));

        if (!isIncremental || externalLibs.stream().anyMatch(i -> i.getStatus() != Status.NOTCHANGED)) {
            // if non-incremental, or inputs have changed, merge again
            FileUtils.cleanOutputDir(externalLibsOutput);
            Iterator<Path> externalLibsToMerge =
                    externalLibs
                            .stream()
                            .filter(i -> i.getStatus() != Status.REMOVED)
                            .map(input -> input.getFile().toPath())
                            .iterator();
            if (externalLibsToMerge.hasNext()) {
                subTasks.add(submitForMerging(output, externalLibsOutput, externalLibsToMerge, null));
            }
        }

        return subTasks.build();
    }

    //DexMergerTransform.java
    private List<ForkJoinTask<Void>> processDirectories(ProcessOutput output, TransformOutputProvider outputProvider, boolean isIncremental, Collection<DirectoryInput> inputs, boolean mergeAllInputs) throws IOException {

        ImmutableList.Builder<ForkJoinTask<Void>> subTasks = ImmutableList.builder();
        List<DirectoryInput> deleted = new ArrayList<>();
        List<DirectoryInput> changed = new ArrayList<>();
        List<DirectoryInput> notChanged = new ArrayList<>();

        for (DirectoryInput directoryInput : inputs) {
            Path rootFolder = directoryInput.getFile().toPath();
            if (!Files.isDirectory(rootFolder)) {
                deleted.add(directoryInput);
            } else {
                boolean runAgain = !isIncremental;

                if (!runAgain) {
                    // check the incremental case
                    Collection<Status> statuses = directoryInput.getChangedFiles().values();
                    runAgain =
                            statuses.contains(Status.ADDED)
                                    || statuses.contains(Status.REMOVED)
                                    || statuses.contains(Status.CHANGED);
                }

                if (runAgain) {
                    changed.add(directoryInput);
                } else {
                    notChanged.add(directoryInput);
                }
            }
        }

        if (isIncremental && deleted.isEmpty() && changed.isEmpty()) {
            return subTasks.build();
        }

        if (mergeAllInputs) {
            File dexOutput =
                    getDexOutputLocation(
                            outputProvider,
                            isInInstantRunMode ? "slice_0" : "directories",
                            ImmutableSet.of(Scope.PROJECT));
            FileUtils.cleanOutputDir(dexOutput);

            Iterator<Path> toMerge = Iterators.transform(Iterators.concat(changed.iterator(), notChanged.iterator()), i -> Objects.requireNonNull(i).getFile().toPath());
            if (toMerge.hasNext()) {
                subTasks.add(submitForMerging(output, dexOutput, toMerge, null));
            }
        } else {
            for (DirectoryInput directoryInput : deleted) {
                File dexOutput = getDexOutputLocation(outputProvider, directoryInput);
                FileUtils.cleanOutputDir(dexOutput);
            }
            for (DirectoryInput directoryInput : changed) {
                File dexOutput = getDexOutputLocation(outputProvider, directoryInput);
                FileUtils.cleanOutputDir(dexOutput);
                subTasks.add(
                        submitForMerging(
                                output,
                                dexOutput,
                                Iterators.singletonIterator(directoryInput.getFile().toPath()),
                                null));
            }
        }
        return subTasks.build();
    }



    //DexMergerTransform.java
    private ForkJoinTask<Void> submitForMerging(
            @NonNull ProcessOutput output,
            @NonNull File dexOutputDir,
            @NonNull Iterator<Path> dexArchives,
            @Nullable Path mainDexList) {
        DexMergerTransformCallable callable = new DexMergerTransformCallable(messageReceiver, dexingType, output, dexOutputDir, dexArchives, mainDexList, forkJoinPool, dexMerger, minSdkVersion, isDebuggable);
        return forkJoinPool.submit(callable);
    }

    //DexMergerTransformCallable.java
    public Void call() throws Exception {
        DexArchiveMerger merger;
        switch (dexMerger) {
            case DX:
                DxContext dxContext =
                        new DxContext(
                                processOutput.getStandardOutput(), processOutput.getErrorOutput());
                merger = DexArchiveMerger.createDxDexMerger(dxContext, forkJoinPool, isDebuggable);
                break;
            case D8:
                int d8MinSdkVersion = minSdkVersion;
                if (d8MinSdkVersion < 21 && dexingType == DexingType.NATIVE_MULTIDEX) {
                    d8MinSdkVersion = 21;
                }
                merger =
                        DexArchiveMerger.createD8DexMerger(
                                messageReceiver, d8MinSdkVersion, isDebuggable, forkJoinPool);
                break;
            default:
                throw new AssertionError("Unknown dex merger " + dexMerger.name());
        }

        merger.mergeDexArchives(dexArchives, dexOutputDir.toPath(), mainDexList, dexingType);
        return null;
    }

    @Override
    public void mergeDexArchives(Iterator<Path> inputs, Path outputDir, Path mainDexClasses, DexingType dexingType) throws DexArchiveMergerException {
        List<Path> inputsList = Lists.newArrayList(inputs);
        if (LOGGER.isLoggable(Level.INFO)) {
            LOGGER.log(
                    Level.INFO,
                    "Merging to '"
                            + outputDir.toAbsolutePath().toString()
                            + "' with D8 from "
                            + inputsList
                                    .stream()
                                    .map(path -> path.toAbsolutePath().toString())
                                    .collect(Collectors.joining(", ")));
        }
        if (inputsList.isEmpty()) {
            return;
        }

        D8DiagnosticsHandler d8DiagnosticsHandler = new InterceptingDiagnosticsHandler();
        D8Command.Builder builder = D8Command.builder(d8DiagnosticsHandler);
        builder.setDisableDesugaring(true);

        for (Path input : inputsList) {
            try (DexArchive archive = DexArchives.fromInput(input)) {
                for (DexArchiveEntry dexArchiveEntry : archive.getFiles()) {
                    builder.addDexProgramData(
                            dexArchiveEntry.getDexFileContent(),
                            D8DiagnosticsHandler.getOrigin(dexArchiveEntry));
                }
            } catch (IOException e) {
                throw getExceptionToRethrow(e, d8DiagnosticsHandler);
            }
        }
        try {
            if (mainDexClasses != null) {
                builder.addMainDexListFiles(mainDexClasses);
            }
            builder.setMinApiLevel(minSdkVersion)
                    .setMode(compilationMode)
                    .setOutput(outputDir, OutputMode.DexIndexed)
                    .setDisableDesugaring(true)
                    .setIntermediate(false);
            D8.run(builder.build(), forkJoinPool);
        } catch (CompilationFailedException e) {
            throw getExceptionToRethrow(e, d8DiagnosticsHandler);
        }
    }
}

```

DexArchiveBuilderTask 任务是在 createCompileTask 中创建，更具体一点，createCompileTask 里面创建了多个任务，包括 java 源代码编译，编译完成之后才创建 DexArchiveBuilderTask 来将 class 文件转成 dex 文件，最后在创建一个 DexMergeringTask 对生成的 Dex 进行合并。


#### （2）执行

##### doTaskAction 执行 DexArchiveBuilder

处理 .jar 文件中的 class

```java
//DexArchiveBuilder.kt
override fun doTaskAction(inputChanges: InputChanges) {
    DexArchiveBuilderTaskDelegate(
        isIncremental = inputChanges.isIncremental,

        projectClasses = projectClasses.files,
        projectChangedClasses = getChanged(inputChanges, projectClasses),
        subProjectClasses = subProjectClasses.files,
        subProjectChangedClasses = getChanged(inputChanges, subProjectClasses),
        externalLibClasses = externalLibClasses.files,
        externalLibChangedClasses = getChanged(inputChanges, externalLibClasses),
        mixedScopeClasses = mixedScopeClasses.files,
        mixedScopeChangedClasses = getChanged(inputChanges, mixedScopeClasses),

        projectOutputDex = projectOutputDex.asFile.get(),
        projectOutputKeepRules = projectOutputKeepRules.asFile.orNull,
        subProjectOutputDex = subProjectOutputDex.asFile.get(),
        subProjectOutputKeepRules = subProjectOutputKeepRules.asFile.orNull,
        externalLibsOutputDex = externalLibsOutputDex.asFile.get(),
        externalLibsOutputKeepRules = externalLibsOutputKeepRules.asFile.orNull,
        mixedScopeOutputDex = mixedScopeOutputDex.asFile.get(),
        mixedScopeOutputKeepRules = mixedScopeOutputKeepRules.asFile.orNull,

        dexParams = dexParams.toDexParameters(),
        dxDexParams = dxDexParams.toDxDexParameters(),

        desugarClasspathChangedClasses = getChanged(
            inputChanges,
            dexParams.desugarClasspath
        ),

        incrementalDexingV2 = incrementalDexingV2.get(),
        desugarGraphDir = desugarGraphDir.get().asFile.takeIf { incrementalDexingV2.get() },

        projectVariant = projectVariant.get(),
        inputJarHashesFile = inputJarHashesFile.get().asFile,
        dexer = dexer.get(),
        numberOfBuckets = numberOfBuckets.get(),
        useGradleWorkers = useGradleWorkers.get(),
        workerExecutor = workerExecutor,
        userLevelCache = userLevelCache,
        messageReceiver = MessageReceiverImpl(dexParams.errorFormatMode.get(), logger)
    ).doProcess()
}


//DexArchiveBuilderTaskDelegate.kt
fun doProcess() {
    if (dxDexParams.dxNoOptimizeFlagPresent) {
        loggerWrapper.warning(DefaultDexOptions.OPTIMIZE_WARNING)
    }

    loggerWrapper.verbose("Dex builder is incremental : %b ", isIncremental)

    // impactedFiles is not null iff !isImpactedFilesComputedLazily
    val impactedFiles: Set<File>? =
        if (isImpactedFilesComputedLazily) {
            null
        } else {
            if (dexParams.withDesugaring) {
                desugarIncrementalHelper!!.additionalPaths.map { it.toFile() }.toSet()
            } else {
                emptySet()
            }
        }

    try {
        Closer.create().use { closer ->
            val classpath = getClasspath(dexParams.withDesugaring)
            val bootclasspath = getBootClasspath(dexParams.desugarBootclasspath, dexParams.withDesugaring)

            val bootClasspathProvider = ClassFileProviderFactory(bootclasspath)
            closer.register(bootClasspathProvider)
            val libraryClasspathProvider = ClassFileProviderFactory(classpath)
            closer.register(libraryClasspathProvider)

            val bootclasspathServiceKey = ClasspathServiceKey(bootClasspathProvider.id)
            val classpathServiceKey = ClasspathServiceKey(libraryClasspathProvider.id)
            sharedState.registerServiceAsCloseable(bootclasspathServiceKey, bootClasspathProvider).also { closer.register(it) }

            sharedState.registerServiceAsCloseable(classpathServiceKey, libraryClasspathProvider).also { closer.register(it) }

            val processInputType = { classes: Set<File>,
                changedClasses: Set<FileChange>,
                outputDir: File,
                outputKeepRules: File?,
                desugarGraphDir: File?, // Not null iff impactedFiles == null
                useAndroidBuildCache: Boolean,
                cacheableDexes: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>?,
                cacheableKeepRules: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>? ->
                processClassFromInput(
                    inputFiles = classes,
                    inputFileChanges = changedClasses,
                    outputDir = outputDir,
                    outputKeepRules = outputKeepRules,
                    impactedFiles = impactedFiles,
                    desugarGraphDir = desugarGraphDir,
                    bootClasspathKey = bootclasspathServiceKey,
                    bootClasspath = bootclasspath,
                    classpathKey = classpathServiceKey,
                    classpath = classpath,
                    enableCaching = useAndroidBuildCache,
                    cacheableDexes = cacheableDexes,
                    cacheableKeepRules = cacheableKeepRules
                )
            }
            processInputType(
                projectClasses,
                projectChangedClasses,
                projectOutputDex,
                projectOutputKeepRules,
                desugarGraphDir?.resolve("currentProject").takeIf { impactedFiles == null },
                false, // useAndroidBuildCache
                null, // cacheableDexes
                null // cacheableKeepRules
            )
            //处理子 project 下的 class 文件
            processInputType(
                subProjectClasses,
                subProjectChangedClasses,
                subProjectOutputDex,
                subProjectOutputKeepRules,
                desugarGraphDir?.resolve("otherProjects").takeIf { impactedFiles == null },
                false, // useAndroidBuildCache
                null, // cacheableDexes
                null // cacheableKeepRules
            )
            processInputType(
                mixedScopeClasses,
                mixedScopeChangedClasses,
                mixedScopeOutputDex,
                mixedScopeOutputKeepRules,
                desugarGraphDir?.resolve("mixedScopes").takeIf { impactedFiles == null },
                false, // useAndroidBuildCache
                null, // cacheableDexes
                null // cacheableKeepRules
            )
            // Caching is currently not supported when isImpactedFilesComputedLazily == true
            val enableCachingForExternalLibs = !isImpactedFilesComputedLazily
            val cacheableDexes: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>? =
                mutableListOf<DexArchiveBuilderCacheHandler.CacheableItem>().takeIf { enableCachingForExternalLibs }
            val cacheableKeepRules: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>? =
                mutableListOf<DexArchiveBuilderCacheHandler.CacheableItem>().takeIf { enableCachingForExternalLibs }
            val shrinkDesugarLibrary = externalLibsOutputKeepRules != null
            //处理依赖库中的 class 
            processInputType(
                externalLibClasses,
                externalLibChangedClasses,
                externalLibsOutputDex,
                externalLibsOutputKeepRules,
                desugarGraphDir?.resolve("externalLibs").takeIf { impactedFiles == null },
                enableCachingForExternalLibs,
                cacheableDexes,
                cacheableKeepRules
            )

            // all work items have been submitted, now wait for completion.
            if (useGradleWorkers) {
                workerExecutor.await()
            } else {
                executor.waitForTasksWithQuickFail<Any>(true)
            }

            // and finally populate the caches.
            if (cacheableDexes != null && cacheableDexes.isNotEmpty()) {
                cacheHandler.populateDexCache(cacheableDexes, shrinkDesugarLibrary)
            }
            if (cacheableKeepRules != null && cacheableKeepRules.isNotEmpty()) {
                cacheHandler.populateKeepRuleCache(
                    cacheableKeepRules,
                    shrinkDesugarLibrary
                )
            }

            loggerWrapper.verbose("Done with all dex archive conversions");
        }
    } catch (e: Exception) {
        PluginCrashReporter.maybeReportException(e)
        loggerWrapper.error(null, Throwables.getStackTraceAsString(e))
        throw e
    }
}



private fun processClassFromInput(inputFiles: Set<File>, inputFileChanges: Set<FileChange>, outputDir: File, outputKeepRules: File?, impactedFiles: Set<File>?, desugarGraphDir: File?, bootClasspathKey: ClasspathServiceKey, bootClasspath: List<Path>, classpathKey: ClasspathServiceKey, classpath: List<Path>, enableCaching: Boolean, cacheableDexes: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>?, cacheableKeepRules: MutableList<DexArchiveBuilderCacheHandler.CacheableItem>? ) {

    //全量编译，清除所有的缓存
    if (!isIncremental) {
        FileUtils.cleanOutputDir(outputDir)
        outputKeepRules?.let { FileUtils.cleanOutputDir(it) }
        desugarGraphDir?.let { FileUtils.cleanOutputDir(it) }
    } else {
        removeChangedJarOutputs(
            inputFiles,
            inputFileChanges,
            impactedFiles ?: emptySet(),
            outputDir
        )
        deletePreviousOutputsFromDirs(inputFileChanges, outputDir)
    }

    val (directoryInputs, jarInputs) = inputFiles.filter { it.exists() }.partition { it.isDirectory }

    if (directoryInputs.isNotEmpty()) {
        directoryInputs.forEach { loggerWrapper.verbose("Processing input %s", it.toString()) }
        //将目录下的 class 转成 dex
        convertToDexArchive(
            inputs = DirectoryBucketGroup(directoryInputs, numberOfBuckets),
            outputDir = outputDir,
            isIncremental = isIncremental,
            bootClasspath = bootClasspathKey,
            classpath = classpathKey,
            changedFiles = changedFiles,
            impactedFiles = impactedFiles,
            desugarGraphDir = desugarGraphDir,
            outputKeepRulesDir = outputKeepRules
        )
    }

    for (input in jarInputs) {
        loggerWrapper.verbose("Processing input %s", input.toString())
        check(input.extension == SdkConstants.EXT_JAR) { "Expected jar, received $input" }

        val cacheInfo = if (enableCaching) {
            getD8DesugaringCacheInfo(desugarIncrementalHelper, bootClasspath, classpath, input)
        } else {
            DesugaringDontCache
        }

        //将 jar 中的 class 转成 dex
        val dexOutputs = convertJarToDexArchive(
            isIncremental = isIncremental,
            jarInput = input,
            outputDir = outputDir,
            bootclasspath = bootClasspathKey,
            classpath = classpathKey,
            changedFiles = changedFiles,
            impactedFiles = impactedFiles,
            desugarGraphDir = desugarGraphDir,
            cacheInfo = cacheInfo,
            outputKeepRulesDir = outputKeepRules
        )
        if (cacheInfo != DesugaringDontCache && dexOutputs.dexes.isNotEmpty()) {
            cacheableDexes!!.add(DexArchiveBuilderCacheHandler.CacheableItem(input, dexOutputs.dexes, cacheInfo.orderedD8DesugaringDependencies))
            if (dexOutputs.keepRules.isNotEmpty()) {
                cacheableKeepRules!!.add(
                    DexArchiveBuilderCacheHandler.CacheableItem(
                        input,
                        dexOutputs.keepRules,
                        cacheInfo.orderedD8DesugaringDependencies
                    )
                )
            }
        }
    }
}

```

DexArchiveBuilderTask 会按照两种情况处理 class 文件，一种是处理目录下的 class 文件，另一种是处理 .jar 中的 class 文件。 .jar 一般是依赖库，一般不会改变，因此 gradle 会缓存从 .jar 中生成的 dex 文件，用于重用。


##### convertJarToDexArchive


```java
private fun convertJarToDexArchive(isIncremental: Boolean, jarInput: File, outputDir: File, bootclasspath: ClasspathServiceKey, classpath: ClasspathServiceKey, changedFiles: Set<File>, impactedFiles: Set<File>?, desugarGraphDir: File?, cacheInfo: D8DesugaringCacheInfo, outputKeepRulesDir: File?
): DexOutputs {
    if (isImpactedFilesComputedLazily) {
        check(impactedFiles == null)
        return convertToDexArchive(
            inputs = JarBucketGroup(jarInput, numberOfBuckets),
            outputDir = outputDir,
            isIncremental = isIncremental,
            bootClasspath = bootclasspath,
            classpath = classpath,
            changedFiles = changedFiles,
            impactedFiles = null,
            desugarGraphDir = desugarGraphDir,
            outputKeepRulesDir = outputKeepRulesDir
        )
    } else {
        // This is the case where the set of impactedFiles was precomputed, so dexing
        // avoidance and caching is possible.
        checkNotNull(impactedFiles)
        if (isIncremental && jarInput !in changedFiles && jarInput !in impactedFiles) {
            return DexOutputs()
        }

        if (cacheInfo !== DesugaringDontCache) {
            val shrinkDesugarLibrary = outputKeepRulesDir != null
            val cachedDex = cacheHandler.getCachedDexIfPresent(jarInput, cacheInfo.orderedD8DesugaringDependencies, shrinkDesugarLibrary)
            val cachedKeepRule = cacheHandler.getCachedKeepRuleIfPresent(jarInput, cacheInfo.orderedD8DesugaringDependencies, shrinkDesugarLibrary)

            if (outputKeepRulesDir == null) {
                if (cachedDex != null) {
                    val outputFile = getDexOutputForJar(jarInput, outputDir, null)
                    Files.copy(cachedDex.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    return DexOutputs()
                }
            } else {
                if ((cachedDex == null) xor (cachedKeepRule == null)) {
                    loggerWrapper.warning("One of the cached dex outputs is missing. Re-dex without using existing cached outputs.")
                }
                //有缓存，则直接获取缓存的 jar 
                if (cachedDex != null && cachedKeepRule != null) {
                    val outputFile = getDexOutputForJar(jarInput, outputDir, null)
                    Files.copy(cachedDex.toPath(), outputFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    FileUtils.copyDirectory(cachedKeepRule, outputKeepRulesDir)
                    return DexOutputs()
                }
            }
        }
        //没有缓存，则通过 convertToDexArchive 生成 Dex
        return convertToDexArchive(
            inputs = JarBucketGroup(jarInput, numberOfBuckets),
            outputDir = outputDir,
            isIncremental = false,
            bootClasspath = bootclasspath,
            classpath = classpath,
            changedFiles = setOf(),
            impactedFiles = setOf(),
            desugarGraphDir = null,
            outputKeepRulesDir = outputKeepRulesDir
        )
    }
}

//对目录下的 class 文件的处理最终也会执行此方法
private fun convertToDexArchive(inputs: ClassBucketGroup, outputDir: File, isIncremental: Boolean, bootClasspath: ClasspathServiceKey, classpath: ClasspathServiceKey, changedFiles: Set<File>, impactedFiles: Set<File>?, desugarGraphDir: File?, outputKeepRulesDir: File?): DexOutputs {

    inputs.getRoots().forEach { loggerWrapper.verbose("Dexing ${it.absolutePath}") }

    //获取 Dex 输出目录
    val dexOutputs = DexOutputs()
    for (bucketId in 0 until numberOfBuckets) {
        val dexPerClass = inputs is DirectoryBucketGroup && outputKeepRulesDir == null

        //创建存放 Dex 的目录，与 java 源文件或者 jar 的目录结构一致
        val preDexOutputFile = when (inputs) {
            is DirectoryBucketGroup -> {
                if (dexPerClass) {
                    outputDir.also { FileUtils.mkdirs(it) }
                } else {
                    outputDir.resolve(bucketId.toString()).also { FileUtils.mkdirs(it) }
                }
            }
            is JarBucketGroup -> {
                getDexOutputForJar(inputs.jarFile, outputDir, bucketId).also { FileUtils.mkdirs(it.parentFile) }
            }
        }

        val outputKeepRuleFile = outputKeepRulesDir?.let { outputKeepRuleDir ->
            when (inputs) {
                is DirectoryBucketGroup -> outputKeepRuleDir.resolve(bucketId.toString())
                is JarBucketGroup ->  getKeepRulesOutputForJar(inputs.jarFile, outputKeepRuleDir, bucketId)
            }.also {
                FileUtils.mkdirs(it.parentFile)
                it.createNewFile()
            }
        }

        dexOutputs.addDex(preDexOutputFile)
        val classBucket = ClassBucket(inputs, bucketId)
        outputKeepRuleFile?.let { dexOutputs.addKeepRule(it) }
        val parameters = DexWorkActionParams(
            dexer = dexer,
            dexSpec = IncrementalDexSpec(
                inputClassFiles = classBucket,
                outputPath = preDexOutputFile,
                dexParams = dexParams.toDexParametersForWorkers(
                    dexPerClass,
                    bootClasspath,
                    classpath,
                    outputKeepRuleFile
                ),
                isIncremental = isIncremental,
                changedFiles = changedFiles,
                impactedFiles = impactedFiles,
                desugarGraphFile = if (impactedFiles == null) {
                    getDesugarGraphFile(desugarGraphDir!!, classBucket)
                } else {
                    null
                }
            ),
            dxDexParams = dxDexParams
        )

        //useGradleWorkers 默认为 true，因此默认创建一个 DexWorkActon 执行 Dex 转换
        if (useGradleWorkers) {
            workerExecutor.submit(DexWorkAction::class.java) { configuration ->
                configuration.isolationMode = IsolationMode.NONE
                configuration.setParams(parameters)
            }
        } else {
            executor.execute<Any> {
                val outputHandler = ParsingProcessOutputHandler(
                    ToolOutputParser(DexParser(), Message.Kind.ERROR, loggerWrapper),
                    ToolOutputParser(DexParser(), loggerWrapper),
                    messageReceiver
                )
                var output: ProcessOutput? = null
                try {
                    outputHandler.createOutput().use {
                        output = it
                        launchProcessing(
                            parameters,
                            output!!.standardOutput,
                            output!!.errorOutput,
                            messageReceiver
                        )
                    }
                } finally {
                    output?.let {
                        try {
                            outputHandler.handleOutput(it)
                        } catch (e: ProcessException) {
                            // ignore this one
                        }
                    }
                }
                null
            }
        }
    }
    return dexOutputs
}


//DexWorkAction.kt
override fun run() {
    try {
        launchProcessing(
            params,
            System.out,
            System.err,
            MessageReceiverImpl(params.dexSpec.dexParams.errorFormatMode, Logging.getLogger(DexArchiveBuilderTaskDelegate::class.java))
        )
    } catch (e: Exception) {
        throw BuildException(e.message, e)
    }
}


//DexWorkAction.kt
fun launchProcessing(dexWorkActionParams: DexWorkActionParams, outStream: OutputStream, errStream: OutputStream, receiver: MessageReceiver) {
    //getDexArchiveBuilder 根据使用的编译工具，创建不同的类来生成 dex，如果使用 DX 编译，则通过 DexArchiveBuilder.createDxDexBuilder 创建 DxDexBuilder 编译 class 文件。如果使用 D8，则通过 DexArchiveBuilder.createD8DexBuilder 创建 D8DexBuilder 来编译 class 文件
    //高版本的 Gradle 默认都是使用 D8
    val dexArchiveBuilder = getDexArchiveBuilder(dexWorkActionParams, outStream, errStream, receiver)
    if (dexWorkActionParams.dexSpec.isIncremental) {
        //增量编译
        processIncrementally(dexArchiveBuilder, dexWorkActionParams)
    } else {
        //全量编译
        processNonIncrementally(dexArchiveBuilder, dexWorkActionParams)
    }
}


//DexArchiveBuilder.kt
private fun getDexArchiveBuilder(dexWorkActionParams: DexWorkActionParams, outStream: OutputStream, errStream: OutputStream, messageReceiver: MessageReceiver): DexArchiveBuilder {
    val dexArchiveBuilder: DexArchiveBuilder
    with(dexWorkActionParams) {
        when (dexer) {
            DexerTool.DX -> {
                val config = DexArchiveBuilderConfig(
                    DxContext(outStream, errStream),
                    !dxDexParams.dxNoOptimizeFlagPresent, // optimizedDex
                    dxDexParams.inBufferSize,
                    dexSpec.dexParams.minSdkVersion,
                    DexerTool.DX,
                    dxDexParams.outBufferSize,
                    dxDexParams.jumboMode
                )

                dexArchiveBuilder = DexArchiveBuilder.createDxDexBuilder(config)
            }
            DexerTool.D8 -> dexArchiveBuilder = DexArchiveBuilder.createD8DexBuilder(
                com.android.builder.dexing.DexParameters(
                    minSdkVersion = dexSpec.dexParams.minSdkVersion,
                    debuggable = dexSpec.dexParams.debuggable,
                    dexPerClass = dexSpec.dexParams.dexPerClass,
                    withDesugaring = dexSpec.dexParams.withDesugaring,
                    desugarBootclasspath =
                    DexArchiveBuilderTaskDelegate.sharedState.getService(dexSpec.dexParams.desugarBootclasspath).service,
                    desugarClasspath =
                    DexArchiveBuilderTaskDelegate.sharedState.getService(dexSpec.dexParams.desugarClasspath).service,
                    coreLibDesugarConfig = dexSpec.dexParams.coreLibDesugarConfig,
                    coreLibDesugarOutputKeepRuleFile =
                    dexSpec.dexParams.coreLibDesugarOutputKeepRuleFile,
                    messageReceiver = messageReceiver
                )
            )
            else -> throw AssertionError("Unknown dexer type: " + dexer.name)
        }
    }
    return dexArchiveBuilder
}


@JvmStatic
fun createD8DexBuilder(dexParams: DexParameters): DexArchiveBuilder {
    return D8DexArchiveBuilder(dexParams)
}
```


##### D8DexArchiveBuilder
D8DexArchiveBuilder 的 convert 可以分为五个步骤
1. 创建 D8 诊断信息处理器实例，用于发出不同级别的诊断信息，共分为三类，由严重程度递减分别为：error、warning、info。
2. 创建一个 D8 命令构建器实例。
3. 遍历读取每一个类的字节数据。
4. 给 D8 命令构建器实例设置一系列的配置，例如 编译模式、最小 Sdk 版本等等。
5. 使用 com.android.tools.r8 工具包中的 D8 类的 run 方法运行组装后的 D8 命令。


```java
@Override
public void convert(@NonNull Stream<ClassFileEntry> input, @NonNull Path output, @Nullable DependencyGraphUpdater<File> desugarGraphUpdater) throws DexArchiveBuilderException {

    List<ClassFile> inputClassFiles = new ArrayList<>();
    //创建一个 D8 诊断信息处理器实例，用于发出不同级别的诊断信息，共分为三类，由严重程度递减分别为：error、warning、info。
    D8DiagnosticsHandler d8DiagnosticsHandler = new InterceptingDiagnosticsHandler();
    try {
        //D8 命令构建器
        D8Command.Builder builder = D8Command.builder(d8DiagnosticsHandler);
        AtomicInteger entryCount = new AtomicInteger();
        //遍历每一个 class ，读取并保存里面的字节数据
        input.forEach(
                entry -> {
                    ClassFile classFile = new ClassFile(entry.getInput().getPath(), entry.getRelativePath(), readAllBytes(entry));
                    inputClassFiles.add(classFile);
                    builder.addClassProgramData(classFile.getContents(), D8DiagnosticsHandler.getOrigin(entry));
                    entryCount.incrementAndGet();
                });
        //如果没有转换的 class，直接 return
        if (entryCount.get() == 0) {
            return;
        }

        //设置 D8 构建器的一些配置信息，编译类型，输出路径，最小 API 等等
        builder.setMode(dexParams.getDebuggable() ? CompilationMode.DEBUG : CompilationMode.RELEASE)
                .setMinApiLevel(dexParams.getMinSdkVersion())
                .setIntermediate(true)
                .setOutput(output, dexParams.getDexPerClass() ? OutputMode.DexFilePerClassFile : OutputMode.DexIndexed)
                .setIncludeClassesChecksum(dexParams.getDebuggable());

        if (dexParams.getWithDesugaring()) {
            builder.addLibraryResourceProvider(dexParams.getDesugarBootclasspath().getOrderedProvider());
            builder.addClasspathResourceProvider(dexParams.getDesugarClasspath().getOrderedProvider());

            if (dexParams.getCoreLibDesugarConfig() != null) {
                builder.addSpecialLibraryConfiguration(dexParams.getCoreLibDesugarConfig());
                if (dexParams.getCoreLibDesugarOutputKeepRuleFile() != null) {
                    builder.setDesugaredLibraryKeepRuleConsumer(new FileConsumer(dexParams.getCoreLibDesugarOutputKeepRuleFile().toPath()));
                }
            }
        } else {
            builder.setDisableDesugaring(true);
        }

        //调用 D8.jar 工具运行 D8Command 组装的 D8 命令
        D8.run(builder.build(), MoreExecutors.newDirectExecutorService());
    } catch (Throwable e) {
        throw getExceptionToRethrow(e, d8DiagnosticsHandler);
    }

    if (desugarGraphUpdater != null) {
        D8DesugarGraphGenerator.generate(
                inputClassFiles,
                dexParams,
                new D8DesugarGraphConsumerAdapter(desugarGraphUpdater));
    }
}
```


#### createDexMergingTasks










//调用 D8.run(builder.build(), MoreExecutors.newDirectExecutorService())，生成 Dex 文件
```java
    createTasks > createTasksForVariantData > createTasksForVariantScope {
        .....

        // Add a compile task
        createCompileTask(variantScope);
        .....
    }

```
```java
    //TaskManager.java
    protected void createCompileTask(@NonNull VariantScope variantScope) {
        //创建 javac 任务，主要用于将 java 文件编译成 class 文件，此处创建的 javac 任务名字为 compileXXXXJavaWithJavac
        TaskProvider<? extends JavaCompile> javacTask = createJavacTask(variantScope);
        addJavacClassesStream(variantScope);
        setJavaCompilerTask(javacTask, variantScope);
        createPostCompilationTasks(variantScope);
    }

    创建 Javac 任务，编译 Java 类 {
        //TaskManager.java
        public TaskProvider<? extends JavaCompile> createJavacTask(@NonNull final VariantScope scope) {
            taskFactory.register(new JavaPreCompileTask.CreationAction(scope));

            boolean processAnnotationsTaskCreated = ProcessAnnotationsTask.taskShouldBeCreated(scope);
            if (processAnnotationsTaskCreated) {
                taskFactory.register(new ProcessAnnotationsTask.CreationAction(scope));
            }

            //创建 javac 任务，
            final TaskProvider<? extends JavaCompile> javacTask = taskFactory.register(new AndroidJavaCompile.CreationAction(scope, processAnnotationsTaskCreated));

            postJavacCreation(scope);

            return javacTask;
        }
    }


    //TaskManaager.java
    //让 compileXXXXSources 依赖 compileXXXXJavaWithJavac
    public static void setJavaCompilerTask(@NonNull TaskProvider<? extends JavaCompile> javaCompilerTask, @NonNull VariantScope scope) {
        TaskFactoryUtils.dependsOn(scope.getTaskContainer().getCompileTask(), javaCompilerTask);
    }


    //TaskManager.java
    //创建 java 编译完成后需执行的任务，通过 class 文件生成 dex 文件，proguard （混淆）和 jacoco 步骤也在此处进行
    public void createPostCompilationTasks(@NonNull final VariantScope variantScope) {

        checkNotNull(variantScope.getTaskContainer().getJavacTask());

        final BaseVariantData variantData = variantScope.getVariantData();
        final GradleVariantConfiguration config = variantData.getVariantConfiguration();

        TransformManager transformManager = variantScope.getTransformManager();

        //合并混淆文件
        taskFactory.register(new MergeGeneratedProguardFilesCreationAction(variantScope));

        // ---- Code Coverage first -----
        boolean isTestCoverageEnabled = config.getBuildType().isTestCoverageEnabled() && !config.getType().isForTesting();
        if (isTestCoverageEnabled) {
            createJacocoTask(variantScope);
        }

        maybeCreateDesugarTask(variantScope, config.getMinSdkVersion(), transformManager, isTestCoverageEnabled);

        //获取 Android Gradle 扩展对象
        AndroidConfig extension = variantScope.getGlobalScope().getExtension();

        // Merge Java Resources.
        createMergeJavaResTask(variantScope);

        // ----- External Transforms -----
        //应用所有的自定义 Transforms
        List<Transform> customTransforms = extension.getTransforms();
        List<List<Object>> customTransformsDependencies = extension.getTransformsDependencies();

        for (int i = 0, count = customTransforms.size(); i < count; i++) {
            Transform transform = customTransforms.get(i);

            List<Object> deps = customTransformsDependencies.get(i);
            transformManager.addTransform(
                    taskFactory,
                    variantScope,
                    transform,
                    null,
                    task -> {
                        if (!deps.isEmpty()) {
                            task.dependsOn(deps);
                        }
                    },
                    taskProvider -> {
                        // if the task is a no-op then we make assemble task depend on it.
                        if (transform.getScopes().isEmpty()) {
                            TaskFactoryUtils.dependsOn(variantScope.getTaskContainer().getAssembleTask(), taskProvider);
                        }
                    });
        }

        // Add transform to create merged runtime classes if this is a feature, a dynamic-feature, or a base module consuming feature jars. Merged runtime classes are needed if code minification is enabled in a project with features or dynamic-features.
        if (variantData.getType().isFeatureSplit() || variantScope.consumesFeatureJars()) {
            createMergeClassesTransform(variantScope);
        }

        // ----- Android studio profiling transforms
        if (appliesCustomClassTransforms(variantScope, projectOptions)) {
            for (String jar : getAdvancedProfilingTransforms(projectOptions)) {
                if (jar != null) {
                    transformManager.addTransform(
                            taskFactory,
                            variantScope,
                            new CustomClassTransform(
                                    jar,
                                    packagesCustomClassDependencies(variantScope, projectOptions)));
                }
            }
        }

        // ----- Minify next -----
        //只有开启混淆时，shrinker 才不为 null，添加代码压缩的 Transform
        CodeShrinker shrinker = maybeCreateJavaCodeShrinkerTransform(variantScope);
        if (shrinker == CodeShrinker.R8) {
            maybeCreateResourcesShrinkerTransform(variantScope);
            maybeCreateDexSplitterTransform(variantScope);
            // TODO: create JavaResSplitterTransform and call it here (http://b/77546738)
            return;
        }

        // ----- Multi-Dex support
        /**
        * 获取 dex 类型，可选值有 3 个
        * 1. MONO_DEX：不进行 dex 分包，最终只生成一个 Dex 文件
        * 2. LEGACY_MULTIDEX：启用分包，miniSDK 小于 21
        * 3. NATIVE_MULTIDEX：启用分包，miniSDK 大于等于 21
        */
        DexingType dexingType = variantScope.getDexingType();

        // 如果 minSDK > 21 ，则将 dex 类型从 LEGACY_MULTIDEX 改为 NATIVE_MULTIDEX
        if (dexingType == DexingType.LEGACY_MULTIDEX) {
            if (variantScope.getVariantConfiguration().isMultiDexEnabled()
                    && variantScope
                            .getVariantConfiguration()
                            .getMinSdkVersionWithTargetDeviceApi()
                            .getFeatureLevel() >= 21) {
                dexingType = DexingType.NATIVE_MULTIDEX;
            }
        }

        //这里注册的任务用于生成 mainDexList.txt 文件
        if (variantScope.getNeedsMainDexList()) {
            taskFactory.register(new D8MainDexListTask.CreationAction(variantScope, false));
        }

        if (variantScope.getNeedsMainDexListForBundle()) {
            taskFactory.register(new D8MainDexListTask.CreationAction(variantScope, true));
        }

        //将 class 文件编译成 Dex 文件
        createDexTasks(variantScope, dexingType);

        maybeCreateResourcesShrinkerTransform(variantScope);

        // TODO: support DexSplitterTransform when IR enabled (http://b/77585545)
        maybeCreateDexSplitterTransform(variantScope);
        // TODO: create JavaResSplitterTransform and call it here (http://b/77546738)
    }

```
```java
D8MainDexListTask {
    class CreationAction(scope: VariantScope, private val includeDynamicFeature: Boolean) : VariantTaskCreationAction<D8MainDexListTask>(scope) {

        //初始化
        init {
            val inputScopes: Set<QualifiedContent.ScopeType> = setOf(
                PROJECT,
                SUB_PROJECTS,
                EXTERNAL_LIBRARIES
            ) + (if (includeDynamicFeatures) setOf(FEATURES) else emptySet())

            val libraryScopes = setOf(PROVIDED_ONLY, TESTED_CODE)

            //检查多个 type/scope 中的 class 集合是否有重复。
            inputClasses = scope.transformManager.getPipelineOutputAsFileCollection { contentTypes, scopes ->
                contentTypes.contains(QualifiedContent.DefaultContentType.CLASSES) && inputScopes.intersect(scopes).isNotEmpty()
            }
            libraryClasses = scope.transformManager.getPipelineOutputAsFileCollection { contentTypes, scopes ->
                contentTypes.contains(QualifiedContent.DefaultContentType.CLASSES) && libraryScopes.intersect(scopes).isNotEmpty()
            }
        }

        //预配置，创建 mainDexList.txt 文件
        override fun preConfigure(taskName: String) {
            super.preConfigure(taskName)

            val outputType =
                if (includeDynamicFeatures) {
                    InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE
                } else {
                    InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST
                }
            //创建 mainDexList.txt 文件
            output = variantScope.artifacts.createArtifactFile(
                outputType, BuildArtifactsHolder.OperationType.INITIAL, taskName, "mainDexList.txt"
            )
        }

        override fun configure(task: D8MainDexListTask) {
            super.configure(task)
            //配置输出
            task.output = output

            task.aaptGeneratedRules = variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES).get()
            task.userMultidexProguardRules = variantScope.variantConfiguration.multiDexKeepProguard
            task.userMultidexKeepFile = variantScope.variantConfiguration.multiDexKeepFile

            task.inputClasses = inputClasses
            task.libraryClasses = libraryClasses

            task.bootClasspath = variantScope.bootClasspath
            task.errorFormat = SyncOptions.getErrorFormatMode(variantScope.globalScope.projectOptions)
        }
    }
}
```

```java
//生成 Dex 文件
//每个 class 文件对应生成一个 dex 文件
createDexTasks {
    //生成 Dex 文件
    private void createDexTasks(@NonNull VariantScope variantScope, @NonNull DexingType dexingType) {
        TransformManager transformManager = variantScope.getTransformManager();

        DefaultDexOptions dexOptions;
        if (variantScope.getVariantData().getType().isTestComponent()) {
            // Don't use custom dx flags when compiling the test FULL_APK. They can break the test FULL_APK,
            // like --minimal-main-dex.
            dexOptions = DefaultDexOptions.copyOf(extension.getDexOptions());
            dexOptions.setAdditionalParameters(ImmutableList.of());
        } else {
            dexOptions = extension.getDexOptions();
        }

        Java8LangSupport java8SLangSupport = variantScope.getJava8LangSupportType();
        boolean minified = variantScope.getCodeShrinker() != null;
        //ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM 的值为 android.enableDexingArtifactTransform.desugaring
        boolean supportsDesugaring = java8SLangSupport == Java8LangSupport.UNUSED || (java8SLangSupport == Java8LangSupport.D8 && projectOptions.get(BooleanOption.ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM));
        //ENABLE_DEXING_ARTIFACT_TRANSFORM = "android.enableDexingArtifactTransform"
        boolean enableDexingArtifactTransform = globalScope.getProjectOptions().get(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM)
                && extension.getTransforms().isEmpty()
                && !minified
                && supportsDesugaring
                && !appliesCustomClassTransforms(variantScope, projectOptions);
        FileCache userLevelCache = getUserDexCache(minified, dexOptions.getPreDexLibraries());
        //DexArchiveBuilderTransform 里面封装了生成 Dex 文件的具体逻辑
        DexArchiveBuilderTransform preDexTransform = new DexArchiveBuilderTransformBuilder()
                .setAndroidJarClasspath(globalScope.getFilteredBootClasspath())
                .setDexOptions(dexOptions)
                .setMessageReceiver(variantScope.getGlobalScope().getMessageReceiver())
                .setErrorFormatMode(SyncOptions.getErrorFormatMode(variantScope.getGlobalScope().getProjectOptions()))
                .setUserLevelCache(userLevelCache)
                .setMinSdkVersion(variantScope
                        .getVariantConfiguration()
                        .getMinSdkVersionWithTargetDeviceApi()
                        .getFeatureLevel())
                .setDexer(variantScope.getDexer()) //D8
                .setUseGradleWorkers(projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS))
                .setInBufferSize(projectOptions.get(IntegerOption.DEXING_READ_BUFFER_SIZE))
                .setOutBufferSize(projectOptions.get(IntegerOption.DEXING_WRITE_BUFFER_SIZE))
                .setIsDebuggable(variantScope
                        .getVariantConfiguration()
                        .getBuildType()
                        .isDebuggable())
                .setJava8LangSupportType(java8SLangSupport)
                .setProjectVariant(getProjectVariantId(variantScope))
                .setNumberOfBuckets(projectOptions.get(IntegerOption.DEXING_NUMBER_OF_BUCKETS))
                .setIncludeFeaturesInScope(variantScope.consumesFeatureJars())
                .setEnableDexingArtifactTransform(enableDexingArtifactTransform)
                .createDexArchiveBuilderTransform();
        transformManager.addTransform(taskFactory, variantScope, preDexTransform);

        if (projectOptions.get(BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK)) {
            taskFactory.register(new CheckDuplicateClassesTask.CreationAction(variantScope));
        }

        createDexMergingTasks(variantScope, dexingType, enableDexingArtifactTransform);
    }

    //Dex 转换
    //该 Transform 的 name 为 dexBuilder
    //将 class 文件转换成 dex 文件，最终会调用 D8.run 执行 Dex 转换
    DexArchiveBuilderTransform {
        @Override
        public void transform(@NonNull TransformInvocation transformInvocation) throws TransformException, IOException, InterruptedException {

            TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
            Preconditions.checkNotNull(outputProvider, "Missing output provider.");
            if (dexOptions.getAdditionalParameters().contains("--no-optimize")) {
                logger.warning(DefaultDexOptions.OPTIMIZE_WARNING);
            }

            logger.verbose("Task is incremental : %b ", transformInvocation.isIncremental());

            if (!transformInvocation.isIncremental()) {
                outputProvider.deleteAll();
            }

            //获取所有发生了变化的文件，用于稍后执行编译
            Set<File> additionalPaths;
            DesugarIncrementalTransformHelper desugarIncrementalTransformHelper;
            if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
                additionalPaths = ImmutableSet.of();
                desugarIncrementalTransformHelper = null;
            } else {
                desugarIncrementalTransformHelper = new DesugarIncrementalTransformHelper(projectVariant, transformInvocation, executor);
                additionalPaths = desugarIncrementalTransformHelper
                        .getAdditionalPaths() //添加发生改变的类的路径及其依赖
                        .stream()
                        .map(Path::toFile)
                        .collect(Collectors.toSet());
            }

            //是否为增量 build
            List<DexArchiveBuilderCacheHandler.CacheableItem> cacheableItems = new ArrayList<>();
            boolean isIncremental = transformInvocation.isIncremental();
            //
            List<Path> classpath = getClasspath(transformInvocation)
                    .stream()
                    .map(Paths::get)
                    .collect(Collectors.toList());
            //androidJarClasspath 为 android.jar 的路径，这个 jar 包里面有 Android 应用运行所必须的类
            List<Path> bootclasspath = getBootClasspath(androidJarClasspath)
                    .stream()
                    .map(Paths::get)
                    .collect(Collectors.toList());

            ClasspathServiceKey bootclasspathServiceKey = null;
            ClasspathServiceKey classpathServiceKey = null;
            try (
                ClassFileProviderFactory bootClasspathProvider = new ClassFileProviderFactory(bootclasspath);
                ClassFileProviderFactory libraryClasspathProvider = new ClassFileProviderFactory(classpath)
            ){
                bootclasspathServiceKey = new ClasspathServiceKey(bootClasspathProvider.getId());
                classpathServiceKey = new ClasspathServiceKey(libraryClasspathProvider.getId());
                INSTANCE.registerService(bootclasspathServiceKey, () -> new ClasspathService(bootClasspathProvider));
                INSTANCE.registerService(classpathServiceKey, () -> new ClasspathService(libraryClasspathProvider));

                //遍历项目中所有的目录和 jar 包，将目录下的 class 文件转成 dex 文件
                for (TransformInput input : transformInvocation.getInputs()) {
                    //从目录中读取 class
                    for (DirectoryInput dirInput : input.getDirectoryInputs()) {
                        logger.verbose("Dir input %s", dirInput.getFile().toString());
                        convertToDexArchive(
                            transformInvocation.getContext(),
                            dirInput,
                            outputProvider,
                            isIncremental,
                            bootclasspathServiceKey,
                            classpathServiceKey,
                            additionalPaths);
                    }

                    //从 jar 文件中读取 class
                    for (JarInput jarInput : input.getJarInputs()) {
                        logger.verbose("Jar input %s", jarInput.getFile().toString());

                        D8DesugaringCacheInfo cacheInfo = getD8DesugaringCacheInfo(
                                desugarIncrementalTransformHelper,
                                bootclasspath,
                                classpath,
                                jarInput);

                        List<File> dexArchives = processJarInput(
                                transformInvocation.getContext(),
                                isIncremental,
                                jarInput,
                                outputProvider,
                                bootclasspathServiceKey,
                                classpathServiceKey,
                                additionalPaths,
                                cacheInfo);

                        if (cacheInfo != D8DesugaringCacheInfo.DONT_CACHE && !dexArchives.isEmpty()) {
                            cacheableItems.add(new DexArchiveBuilderCacheHandler.CacheableItem(
                                    jarInput,
                                    dexArchives,
                                    cacheInfo.orderedD8DesugaringDependencies));
                        }
                    }
                }

                if (useGradleWorkers) {
                    transformInvocation.getContext().getWorkerExecutor().await();
                } else {
                    executor.waitForTasksWithQuickFail(true);
                }

                if (transformInvocation.isIncremental()) {
                    for (TransformInput transformInput : transformInvocation.getInputs()) {
                        removeDeletedEntries(outputProvider, transformInput);
                    }
                }

                if (!cacheableItems.isEmpty()) {
                    cacheHandler.populateCache(cacheableItems);
                }

                logger.verbose("Done with all dex archive conversions");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new TransformException(e);
            } catch (Exception e) {
                PluginCrashReporter.maybeReportException(e);
                logger.error(null, Throwables.getStackTraceAsString(e));
                throw new TransformException(e);
            } finally {
                if (classpathServiceKey != null) {
                    INSTANCE.removeService(classpathServiceKey);
                }
                if (bootclasspathServiceKey != null) {
                    INSTANCE.removeService(bootclasspathServiceKey);
                }
            }
        }

        //读取目录中的 class，生成 dex 文件
        private List<File> convertToDexArchive(@NonNull Context context, @NonNull QualifiedContent input, @NonNull TransformOutputProvider outputProvider, boolean isIncremental, @NonNull ClasspathServiceKey bootClasspath, @NonNull ClasspathServiceKey classpath, @NonNull Set<File> additionalPaths) {

            logger.verbose("Dexing %s", input.getFile().getAbsolutePath());

            ImmutableList.Builder<File> dexArchives = ImmutableList.builder();
            for (int bucketId = 0; bucketId < numberOfBuckets; bucketId++) {
                //输出的 Dex 文件的路径
                File preDexOutputFile;
                if (input instanceof DirectoryInput) {
                    preDexOutputFile = getOutputForDir(outputProvider, (DirectoryInput) input);
                    FileUtils.mkdirs(preDexOutputFile);
                } else {
                    preDexOutputFile = getOutputForJar(outputProvider, (JarInput) input, bucketId);
                }

                dexArchives.add(preDexOutputFile);
                DexConversionParameters parameters = new DexConversionParameters(
                        input,
                        bootClasspath,
                        classpath,
                        preDexOutputFile,
                        numberOfBuckets,
                        bucketId,
                        minSdkVersion,
                        dexOptions.getAdditionalParameters(),
                        inBufferSize,
                        outBufferSize,
                        dexer,
                        isDebuggable,
                        isIncremental,
                        java8LangSupportType,
                        additionalPaths,
                        errorFormatMode);
                //useGradleWorkers 默认为 true
                if (useGradleWorkers) {
                    //DexConversionWorkAction 具体实现位于 DexArchiveBuilder 中
                    context.getWorkerExecutor().submit(
                            DexConversionWorkAction.class,
                            configuration -> {
                                configuration.setIsolationMode(IsolationMode.NONE);
                                configuration.setParams(parameters);
                            });
                } else {
                    executor.execute(() -> {
                        ProcessOutputHandler outputHandler = new ParsingProcessOutputHandler(
                                new ToolOutputParser(new DexParser(), Message.Kind.ERROR, logger),
                                new ToolOutputParser(new DexParser(), logger),
                                messageReceiver);
                        ProcessOutput output = null;
                        try (Closeable ignored = output = outputHandler.createOutput()) {
                            launchProcessing(
                                    parameters,
                                    output.getStandardOutput(),
                                    output.getErrorOutput(),
                                    messageReceiver);
                        } finally {
                            if (output != null) {
                                try {
                                    outputHandler.handleOutput(output);
                                } catch (ProcessException e) {
                                    // ignore this one
                                }
                            }
                        }
                        return null;
                    });
                }
            }
            return dexArchives.build();
        }

        //DexArchiveBuilderTransform.java
        DexArchiveBuilderTransform.DexConversionWorkAction {
            @Override
            public void run() {
                try {
                    launchProcessing(
                            dexConversionParameters,
                            System.out,
                            System.err,
                            new MessageReceiverImpl(dexConversionParameters.errorFormatMode, Logging.getLogger(DexArchiveBuilderTransform.class)));
                } catch (Exception e) {
                    throw new BuildException(e.getMessage(), e);
                }
            }

            private static void launchProcessing(@NonNull DexConversionParameters dexConversionParameters, @NonNull OutputStream outStream, @NonNull OutputStream errStream, @NonNull MessageReceiver receiver) throws IOException, URISyntaxException {
                
                DexArchiveBuilder dexArchiveBuilder = getDexArchiveBuilder(
                        dexConversionParameters.minSdkVersion,
                        dexConversionParameters.dexAdditionalParameters,
                        dexConversionParameters.inBufferSize,
                        dexConversionParameters.outBufferSize,
                        dexConversionParameters.bootClasspath,
                        dexConversionParameters.classpath,
                        dexConversionParameters.dexer,
                        dexConversionParameters.isDebuggable,
                        VariantScope.Java8LangSupport.D8 == dexConversionParameters.java8LangSupportType,
                        outStream,
                        errStream,
                        receiver);

                Path inputPath = dexConversionParameters.input.getFile().toPath();
                Predicate<String> bucketFilter = dexConversionParameters::belongsToThisBucket;

                boolean hasIncrementalInfo = dexConversionParameters.isDirectoryBased() && dexConversionParameters.isIncremental;
                Predicate<String> toProcess = hasIncrementalInfo
                        ? path -> {
                            File resolved = inputPath.resolve(path).toFile();
                            if (dexConversionParameters.additionalPaths.contains(resolved)) {
                                return true;
                            }
                            Map<File, Status> changedFiles = ((DirectoryInput) dexConversionParameters.input).getChangedFiles();

                            Status status = changedFiles.get(resolved);
                            return status == Status.ADDED || status == Status.CHANGED;
                        } : path -> true;

                bucketFilter = bucketFilter.and(toProcess);

                logger.verbose("Dexing '" + inputPath + "' to '" + dexConversionParameters.output + "'");

                try (
                    ClassFileInput input = ClassFileInputs.fromPath(inputPath);
                    Stream<ClassFileEntry> entries = input.entries(bucketFilter)
                ) {
                    dexArchiveBuilder.convert(
                            entries,
                            Paths.get(new URI(dexConversionParameters.output)),
                            dexConversionParameters.isDirectoryBased());
                } catch (DexArchiveBuilderException ex) {
                    throw new DexArchiveBuilderException("Failed to process " + inputPath.toString(), ex);
                }
            }


            //通过 D8 将 class 转换为 Dex 
            @Override
            public void convert(@NonNull Stream<ClassFileEntry> input, @NonNull Path output, boolean isIncremental) throws DexArchiveBuilderException {

                D8DiagnosticsHandler d8DiagnosticsHandler = new InterceptingDiagnosticsHandler();
                try {

                    D8Command.Builder builder = D8Command.builder(d8DiagnosticsHandler);
                    AtomicInteger entryCount = new AtomicInteger();
                    input.forEach(entry -> {
                        builder.addClassProgramData(readAllBytes(entry), D8DiagnosticsHandler.getOrigin(entry));
                        entryCount.incrementAndGet();
                    });
                    if (entryCount.get() == 0) {
                        // nothing to do here, just return
                        return;
                    }

                    //判断是否执行增量 build
                    OutputMode outputMode = isIncremental ? OutputMode.DexFilePerClassFile : OutputMode.DexIndexed;
                    builder.setMode(compilationMode)
                            .setMinApiLevel(minSdkVersion)
                            .setIntermediate(true)
                            .setOutput(output, outputMode);

                    if (desugaring) {
                        builder.addLibraryResourceProvider(bootClasspath.getOrderedProvider());
                        builder.addClasspathResourceProvider(classpath.getOrderedProvider());
                    } else {
                        builder.setDisableDesugaring(true);
                    }

                    D8.run(builder.build(), MoreExecutors.newDirectExecutorService());
                } catch (Throwable e) {
                    throw getExceptionToRethrow(e, d8DiagnosticsHandler);
                }
            }
        }
    }


    //Dex 合并
    createDexMergingTasks {
        private void createDexMergingTasks(@NonNull VariantScope variantScope, @NonNull DexingType dexingType, boolean dexingUsingArtifactTransforms) {

            // When desugaring, The file dependencies are dexed in a task with the whole
            // remote classpath present, as they lack dependency information to desugar
            // them correctly in an artifact transform.
            boolean separateFileDependenciesDexingTask = variantScope.getJava8LangSupportType() == Java8LangSupport.D8 && dexingUsingArtifactTransforms;

            //获取远程依赖文件，并将其转为 dex
            if (separateFileDependenciesDexingTask) {
                DexFileDependenciesTask.CreationAction desugarFileDeps = new DexFileDependenciesTask.CreationAction(variantScope);
                taskFactory.register(desugarFileDeps);
            }

            if (dexingType == DexingType.LEGACY_MULTIDEX) {
                DexMergingTask.CreationAction configAction =
                        new DexMergingTask.CreationAction(
                                variantScope,
                                DexMergingAction.MERGE_ALL,
                                dexingType,
                                dexingUsingArtifactTransforms,
                                separateFileDependenciesDexingTask);
                taskFactory.register(configAction);
            } else if (variantScope.getCodeShrinker() != null) { //是否需要启用代码缩减
                DexMergingTask.CreationAction configAction =
                        new DexMergingTask.CreationAction(
                                variantScope,
                                DexMergingAction.MERGE_ALL,
                                dexingType,
                                dexingUsingArtifactTransforms);
                taskFactory.register(configAction);
            } else {
                //是否将导入的第三方 jar 包和项目的源码分隔开来，输出独立的 dex 文件
                boolean produceSeparateOutputs = dexingType == DexingType.NATIVE_MULTIDEX && variantScope.getVariantConfiguration().getBuildType().isDebuggable();

                //将第三方库的 class 文件合并成 Dex
                taskFactory.register(
                        new DexMergingTask.CreationAction(
                                variantScope,
                                DexMergingAction.MERGE_EXTERNAL_LIBS,
                                DexingType.NATIVE_MULTIDEX,
                                dexingUsingArtifactTransforms,
                                separateFileDependenciesDexingTask,
                                produceSeparateOutputs ? InternalArtifactType.DEX : InternalArtifactType.EXTERNAL_LIBS_DEX));

                if (produceSeparateOutputs) {
                    DexMergingTask.CreationAction mergeProject =
                            new DexMergingTask.CreationAction(
                                    variantScope,
                                    DexMergingAction.MERGE_PROJECT,
                                    dexingType,
                                    dexingUsingArtifactTransforms);
                    taskFactory.register(mergeProject);

                    DexMergingTask.CreationAction mergeLibraries =
                            new DexMergingTask.CreationAction(
                                    variantScope,
                                    DexMergingAction.MERGE_LIBRARY_PROJECTS,
                                    dexingType,
                                    dexingUsingArtifactTransforms);
                    taskFactory.register(mergeLibraries);
                } else {
                    //将所有的 class 文件合并成 Dex
                    DexMergingTask.CreationAction configAction = new DexMergingTask.CreationAction(
                            variantScope,
                            DexMergingAction.MERGE_ALL,
                            dexingType,
                            dexingUsingArtifactTransforms);
                    taskFactory.register(configAction);
                }
            }

            variantScope
                    .getTransformManager()
                    .addStream(OriginalStream.builder(project, "final-dex")
                            .addContentTypes(ExtendedContentType.DEX)
                            .addScope(Scope.PROJECT)
                            .setFileCollection(variantScope
                                    .getArtifacts()
                                    .getFinalArtifactFiles(InternalArtifactType.DEX)
                                    .get())
                            .build());
        }


        DexMergingTask {
            //创建并配置 DexMerge 任务
            DexMergingTask.CreationAction {
                override fun configure(task: DexMergingTask) {
                    super.configure(task)

                    task.dexFiles = getDexFiles(action)
                    task.mergingThreshold = getMergingThreshold(action, task)

                    task.dexingType = dexingType
                    if (DexMergingAction.MERGE_ALL == action && dexingType === DexingType.LEGACY_MULTIDEX) {
                        task.mainDexListFile = variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST)
                    }

                    task.errorFormatMode = SyncOptions.getErrorFormatMode(variantScope.globalScope.projectOptions)
                    task.dexMerger = variantScope.dexMerger
                    task.minSdkVersion = variantScope.variantConfiguration.minSdkVersionWithTargetDeviceApi.featureLevel
                    task.isDebuggable = variantScope.variantConfiguration.buildType.isDebuggable
                    if (variantScope.globalScope.projectOptions[BooleanOption.ENABLE_DUPLICATE_CLASSES_CHECK]) {
                        task.duplicateClassesCheck = variantScope.artifacts.getFinalArtifactFiles(InternalArtifactType.DUPLICATE_CLASSES_CHECK)
                    }
                    if (separateFileDependenciesDexingTask) {
                        task.fileDependencyDexFiles = variantScope.artifacts.getFinalProduct(InternalArtifactType.EXTERNAL_FILE_LIB_DEX_ARCHIVES)
                    }
                    task.outputDir = output
                }


                private fun getDexFiles(action: DexMergingAction): FileCollection {
                    val attributes = getDexingArtifactConfiguration(variantScope).getAttributes()

                    fun forAction(action: DexMergingAction): FileCollection {
                        when (action) {
                            DexMergingAction.MERGE_EXTERNAL_LIBS -> {
                                return if (dexingUsingArtifactTransforms) {
                                    // If the file dependencies are being dexed in a task, don't also include them here
                                    val artifactScope: AndroidArtifacts.ArtifactScope = if (separateFileDependenciesDexingTask) {
                                        AndroidArtifacts.ArtifactScope.REPOSITORY_MODULE
                                    } else {
                                        AndroidArtifacts.ArtifactScope.EXTERNAL
                                    }
                                     variantScope.getArtifactFileCollection(
                                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                        artifactScope,
                                        AndroidArtifacts.ArtifactType.DEX,
                                        attributes
                                    )
                                } else {
                                    variantScope.globalScope.project.files(
                                        variantScope.transformManager.getPipelineOutputAsFileCollection(
                                            StreamFilter.DEX_ARCHIVE,
                                            StreamFilter {_, scopes -> scopes == setOf(QualifiedContent.Scope.EXTERNAL_LIBRARIES) }
                                        ))
                                }
                            }
                            DexMergingAction.MERGE_LIBRARY_PROJECTS -> {
                                return if (dexingUsingArtifactTransforms) {
                                    variantScope.getArtifactFileCollection(
                                        AndroidArtifacts.ConsumedConfigType.RUNTIME_CLASSPATH,
                                        AndroidArtifacts.ArtifactScope.PROJECT,
                                        AndroidArtifacts.ArtifactType.DEX,
                                        attributes
                                    )
                                } else {
                                    variantScope.globalScope.project.files(
                                        variantScope.transformManager.getPipelineOutputAsFileCollection(
                                            StreamFilter.DEX_ARCHIVE,
                                            StreamFilter {_, scopes ->
                                                scopes == setOf(QualifiedContent.Scope.SUB_PROJECTS)
                                                        || scopes == setOf(
                                                    QualifiedContent.Scope.SUB_PROJECTS, QualifiedContent.Scope.EXTERNAL_LIBRARIES
                                                )}
                                        ))
                                }
                            }
                            DexMergingAction.MERGE_PROJECT -> {
                                val files =
                                    variantScope.globalScope.project.files(
                                        variantScope.transformManager.getPipelineOutputAsFileCollection { types, scopes ->
                                            types.contains(ExtendedContentType.DEX_ARCHIVE) && scopes.contains(
                                                QualifiedContent.Scope.PROJECT
                                            )
                                        }
                                    )
                                val variantType = variantScope.type
                                if (variantType.isTestComponent && variantType.isApk) {
                                    val testedVariantData =
                                        checkNotNull(variantScope.testedVariantData) { "Test component without testedVariantData" }
                                    if (dexingUsingArtifactTransforms && testedVariantData.type.isAar) {
                                        // If dexing using artifact transforms, library production code will
                                        // be dex'ed in a task, so we need to fetch the output directly.
                                        // Otherwise, it will be in the dex'ed in the dex builder transform.
                                        files.from(
                                            testedVariantData.scope.artifacts.getFinalArtifactFiles(
                                                InternalArtifactType.DEX
                                            )
                                        )
                                    }
                                }

                                return files
                            }
                            DexMergingAction.MERGE_ALL -> {
                                val external = if (dexingType == DexingType.LEGACY_MULTIDEX) {
                                    // we have to dex it
                                    forAction(DexMergingAction.MERGE_EXTERNAL_LIBS)
                                } else {
                                    // we merge external dex in a separate task
                                    variantScope.artifacts
                                        .getFinalArtifactFiles(InternalArtifactType.EXTERNAL_LIBS_DEX)
                                        .get()
                                }
                                return forAction(DexMergingAction.MERGE_PROJECT) +
                                        forAction(DexMergingAction.MERGE_LIBRARY_PROJECTS) +
                                        external
                            }
                        }
                    }

                    return forAction(action)
                }


                /**
                 * 获取触发 Dex 文件合并的文件数
                 */
                private fun getMergingThreshold(action: DexMergingAction, task: DexMergingTask): Int {
                    return when (action) {
                        //LIBRARIES_M_PLUS_MAX_THRESHOLD 值为 500
                        //LIBRARIES_MERGING_THRESHOLD 值为 51
                        DexMergingAction.MERGE_LIBRARY_PROJECTS ->
                            when {
                                variantScope.variantConfiguration.minSdkVersionWithTargetDeviceApi.featureLevel < 23 -> {
                                    task.outputs.cacheIf { getAllRegularFiles(task.dexFiles.files).size < LIBRARIES_MERGING_THRESHOLD }
                                    LIBRARIES_MERGING_THRESHOLD
                                }
                                else -> LIBRARIES_M_PLUS_MAX_THRESHOLD
                            }
                        else -> 0
                    }
                }
            }





            //执行 DexMerge 任务，最终调用 D8.run 执行 Dex 的合并
            DexMergingTask.doTaskAction {
                //DexMergingTask.kt
                override fun doTaskAction() {
                    workers.use {
                        it.submit(DexMergingTaskRunnable::class.java, DexMergingParams(
                                dexingType,
                                errorFormatMode,
                                dexMerger,
                                minSdkVersion,
                                isDebuggable,
                                mergingThreshold,
                                mainDexListFile?.singleFile(),
                                dexFiles.files,
                                fileDependencyDexFiles?.get()?.asFile,
                                outputDir
                            )
                        )
                    }
                }

                //DexMergingTask.kt
                override fun run() {
                    val logger = LoggerWrapper.getLogger(DexMergingTaskRunnable::class.java)
                    val messageReceiver = MessageReceiverImpl(
                        params.errorFormatMode,
                        Logging.getLogger(DexMergingTask::class.java)
                    )
                    val forkJoinPool = ForkJoinPool()

                    val outputHandler = ParsingProcessOutputHandler(
                        ToolOutputParser(DexParser(), Message.Kind.ERROR, logger),
                        ToolOutputParser(DexParser(), logger),
                        messageReceiver
                    )

                    var processOutput: ProcessOutput? = null
                    try {
                        processOutput = outputHandler.createOutput()
                        val dexFiles = params.getAllDexFiles()
                        FileUtils.cleanOutputDir(params.outputDir)

                        if (dexFiles.isEmpty()) {
                            return
                        }

                        val allDexFiles = lazy { getAllRegularFiles(dexFiles) }
                        if (dexFiles.size >= params.mergingThreshold || allDexFiles.value.size >= params.mergingThreshold) {
                            DexMergerTransformCallable(
                                messageReceiver,
                                params.dexingType,
                                processOutput,
                                params.outputDir,
                                dexFiles.map { it.toPath() }.iterator(),
                                params.mainDexListFile?.toPath(),
                                forkJoinPool,
                                params.dexMerger,
                                params.minSdkVersion,
                                params.isDebuggable
                            ).call()
                        } else {
                            val outputPath = { id: Int -> params.outputDir.resolve("classes_$id.${SdkConstants.EXT_DEX}") }
                            var index = 0
                            for (file in allDexFiles.value) {
                                if (file.extension == SdkConstants.EXT_JAR) {
                                    // Dex files can also come from jars when dexing is not done in artifact
                                    // transforms. See b/130965921 for details.
                                    ZipFile(file).use {
                                        for (entry in it.entries()) {
                                            BufferedInputStream(it.getInputStream(entry)).use { inputStream ->
                                                BufferedOutputStream(outputPath(index++).outputStream()).use { outputStream ->
                                                    inputStream.copyTo(outputStream)
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    file.copyTo(outputPath(index++))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        PluginCrashReporter.maybeReportException(e)
                        // Print the error always, even without --stacktrace
                        logger.error(null, Throwables.getStackTraceAsString(e))
                        throw TransformException(e)
                    } finally {
                        processOutput?.let {
                            try {
                                outputHandler.handleOutput(it)
                                processOutput.close()
                            } catch (ignored: ProcessException) {
                            }
                        }
                        forkJoinPool.shutdown()
                        forkJoinPool.awaitTermination(100, TimeUnit.SECONDS)
                    }
                }

                //DexMergerTransformCallable.java
                @Override
                public Void call() throws Exception {
                    DexArchiveMerger merger;
                    switch (dexMerger) {
                        case DX:
                            DxContext dxContext = new DxContext(processOutput.getStandardOutput(), processOutput.getErrorOutput());
                            merger = DexArchiveMerger.createDxDexMerger(dxContext, forkJoinPool, isDebuggable);
                            break;
                        case D8:
                            int d8MinSdkVersion = minSdkVersion;
                            if (d8MinSdkVersion < 21 && dexingType == DexingType.NATIVE_MULTIDEX) {
                                // D8 has baked-in logic that does not allow multiple dex files without
                                // main dex list if min sdk < 21. When we deploy the app to a device with api
                                // level 21+, we will promote legacy multidex to native multidex, but the min
                                // sdk version will be less than 21, which will cause D8 failure as we do not
                                // supply the main dex list. In order to prevent that, it is safe to set min
                                // sdk version to 21.
                                d8MinSdkVersion = 21;
                            }
                            merger = DexArchiveMerger.createD8DexMerger(messageReceiver, d8MinSdkVersion, isDebuggable, forkJoinPool);
                            break;
                        default:
                            throw new AssertionError("Unknown dex merger " + dexMerger.name());
                    }

                    //调用 D8DexArchiveMerger.java 中的 mergeDexArchives
                    merger.mergeDexArchives(dexArchives, dexOutputDir.toPath(), mainDexList, dexingType);
                    return null;
                }

                @Override
                public void mergeDexArchives(@NonNull Iterator<Path> inputs, @NonNull Path outputDir, @Nullable Path mainDexClasses, @NonNull DexingType dexingType) throws DexArchiveMergerException {

                    List<Path> inputsList = Lists.newArrayList(inputs);
                    if (LOGGER.isLoggable(Level.INFO)) {
                        LOGGER.log(Level.INFO, "Merging to '" + outputDir.toAbsolutePath().toString() + "' with D8 from " + inputsList .stream() .map(path -> path.toAbsolutePath().toString()) .collect(Collectors.joining(", ")));
                    }
                    if (inputsList.isEmpty()) {
                        return;
                    }

                    D8DiagnosticsHandler d8DiagnosticsHandler = new InterceptingDiagnosticsHandler();
                    D8Command.Builder builder = D8Command.builder(d8DiagnosticsHandler);
                    builder.setDisableDesugaring(true);

                    for (Path input : inputsList) {
                        try (DexArchive archive = DexArchives.fromInput(input)) {
                            for (DexArchiveEntry dexArchiveEntry : archive.getFiles()) {
                                builder.addDexProgramData(dexArchiveEntry.getDexFileContent(), D8DiagnosticsHandler.getOrigin(dexArchiveEntry));
                            }
                        } catch (IOException e) {
                            throw getExceptionToRethrow(e, d8DiagnosticsHandler);
                        }
                    }
                    try {
                        if (mainDexClasses != null) {
                            builder.addMainDexListFiles(mainDexClasses);
                        }
                        builder.setMinApiLevel(minSdkVersion)
                                .setMode(compilationMode)
                                .setOutput(outputDir, OutputMode.DexIndexed)
                                .setDisableDesugaring(true)
                                .setIntermediate(false);
                        D8.run(builder.build(), forkJoinPool);
                    } catch (CompilationFailedException e) {
                        throw getExceptionToRethrow(e, d8DiagnosticsHandler);
                    }
                }
            }

            D8 {

            }

        }
        
    }
}
```

```java
    maybeCreateDexSplitterTransform {

        //TaskManager
        private void maybeCreateDexSplitterTransform(@NonNull VariantScope variantScope) {
            if (!variantScope.consumesFeatureJars()) {
                return;
            }

            File dexSplitterOutput = FileUtils.join(globalScope.getIntermediatesDir(), "dex-splitter", variantScope.getVariantConfiguration().getDirName());
            FileCollection featureJars = variantScope.getArtifactFileCollection(METADATA_VALUES, PROJECT, METADATA_CLASSES);

            BuildableArtifact baseJars = variantScope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.MODULE_AND_RUNTIME_DEPS_CLASSES);

            BuildableArtifact mappingFileSrc = variantScope.getArtifacts().hasArtifact(InternalArtifactType.APK_MAPPING)
                    ? variantScope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.APK_MAPPING) : null;

            BuildableArtifact mainDexList = variantScope.getArtifacts().getFinalArtifactFilesIfPresent(
                    InternalArtifactType.MAIN_DEX_LIST_FOR_BUNDLE);

            DexSplitterTransform transform = new DexSplitterTransform(dexSplitterOutput, featureJars, baseJars, mappingFileSrc, mainDexList);

            Optional<TaskProvider<TransformTask>> transformTask = variantScope
                    .getTransformManager()
                    .addTransform(
                            taskFactory,
                            variantScope,
                            transform,
                            taskName -> variantScope.getArtifacts().appendArtifact(
                                    InternalArtifactType.FEATURE_DEX,
                                    ImmutableList.of(dexSplitterOutput),
                                    taskName),
                            null,
                            null);

            if (transformTask.isPresent()) {
                publishFeatureDex(variantScope);
            } else {
                globalScope
                        .getErrorHandler()
                        .reportError(Type.GENERIC,new EvalIssueException("Internal error, could not add the DexSplitterTransform"));
            }
        }

        //DexSplitterTransform
        override fun transform(transformInvocation: TransformInvocation) {
            try {
                val mappingFile =
                    if (mappingFileSrc?.singleFile()?.exists() == true
                        && !mappingFileSrc.singleFile().isDirectory) {
                    mappingFileSrc.singleFile()
                } else {
                    null
                }

                val outputProvider = requireNotNull(
                    transformInvocation.outputProvider,
                    { "No output provider set" }
                )
                outputProvider.deleteAll()
                FileUtils.deleteRecursivelyIfExists(outputDir)

                val builder = DexSplitterTool.Builder(outputDir.toPath(), mappingFile?.toPath(), mainDexList?.singleFile()?.toPath())

                for (dirInput in TransformInputUtil.getDirectories(transformInvocation.inputs)) {
                    dirInput.listFiles()?.toList()?.map { it.toPath() }?.forEach { builder.addInputArchive(it) }
                }

                featureJars.files.forEach { file ->
                    builder.addFeatureJar(file.toPath(), file.nameWithoutExtension)
                    Files.createDirectories(File(outputDir, file.nameWithoutExtension).toPath())
                }

                baseJars.files.forEach { builder.addBaseJar(it.toPath()) }

                builder.build().run()

                val transformOutputDir =
                    outputProvider.getContentLocation(
                        "splitDexFiles", outputTypes, scopes, Format.DIRECTORY
                    )
                Files.createDirectories(transformOutputDir.toPath())

                outputDir.listFiles().find { it.name == "base" }?.let {
                    FileUtils.copyDirectory(it, transformOutputDir)
                    FileUtils.deleteRecursivelyIfExists(it)
                }
            } catch (e: Exception) {
                throw TransformException(e)
            }
        }
    }

    //生成 build/intermediates/res_stripped/release/resources-release-stripped.ap_
    protected void maybeCreateResourcesShrinkerTransform(@NonNull VariantScope scope) {
        if (!scope.useResourceShrinker()) {
            return;
        }

        // if resources are shrink, insert a no-op transform per variant output
        // to transform the res package into a stripped res package
        File shrinkerOutput =
                FileUtils.join(
                        globalScope.getIntermediatesDir(),
                        "res_stripped",
                        scope.getVariantConfiguration().getDirName());

        ShrinkResourcesTransform shrinkResTransform =
                new ShrinkResourcesTransform(
                        scope.getVariantData(),
                        scope.getArtifacts().getFinalArtifactFiles(InternalArtifactType.PROCESSED_RES),
                        shrinkerOutput,
                        logger);

        Optional<TaskProvider<TransformTask>> shrinkTask =
                scope.getTransformManager()
                        .addTransform(
                                taskFactory,
                                scope,
                                shrinkResTransform,
                                taskName ->
                                        scope.getArtifacts()
                                                .appendArtifact(
                                                        InternalArtifactType.SHRUNK_PROCESSED_RES,
                                                        ImmutableList.of(shrinkerOutput),
                                                        taskName),
                                null,
                                null);

        if (!shrinkTask.isPresent()) {
            globalScope
                    .getErrorHandler()
                    .reportError(
                            Type.GENERIC,
                            new EvalIssueException(
                                    "Internal error, could not add the ShrinkResourcesTransform"));
        }

        // And for the bundle
        taskFactory.register(new ShrinkBundleResourcesTask.CreationAction(scope));
    }
```

```
D8 {
    public static void run(D8Command command, ExecutorService executor) throws CompilationFailedException {
        AndroidApp app = command.getInputApp();
        InternalOptions options = command.getInternalOptions();
        //开启一个线程，执行传进去 run
        ExceptionUtils.withD8CompilationHandler(command.getReporter(), () -> run(app, options, executor));
    }

    withD8CompilationHandler {
        //ExceptionUtil.java
        public static void withD8CompilationHandler(Reporter reporter, CompileAction action) throws CompilationFailedException {
            withCompilationHandler(reporter, action, Compiler.D8);
        }

        public static void withCompilationHandler(Reporter reporter, CompileAction action, Compiler compiler) throws CompilationFailedException {
            try {
                try {
                    action.run();
                } catch (IOException e) {
                    throw reporter.fatalError(new IOExceptionDiagnostic(e));
                } catch (CompilationException e) {
                    throw reporter.fatalError(new StringDiagnostic((compiler == Compiler.D8) ? e.getMessageForD8() : e.getMessageForR8()), e);
                } catch (CompilationError e) {
                    throw reporter.fatalError(e);
                } catch (ResourceException e) {
                    throw reporter.fatalError((e.getCause() instanceof IOException) ? 
                                new IOExceptionDiagnostic((IOException)e.getCause(), e.getOrigin()) : 
                                new StringDiagnostic(e.getMessage(), e.getOrigin()));
                } 
                
                reporter.failIfPendingErrors();
            } catch (AbortException e) {
                throw new CompilationFailedException(e);
            }
        }
    }

    //D8.java
    private static void run(AndroidApp inputApp, InternalOptions options, ExecutorService executor) throws IOException, CompilationException {
        try {
            options.skipMinification = true;
            options.inlineAccessors = false;
            options.outline.enabled = false;
            Timing timing = new Timing("DX timer");
            DexApplication app = (new ApplicationReader(inputApp, options, timing)).read(executor);
            AppInfo appInfo = new AppInfo(app);
            app = optimize(app, appInfo, options, timing, executor);
            if (options.hasMethodsFilter()) {
                System.out.println("Finished compilation with method filter: ");
                options.methodsFilter.forEach(m -> System.out.println("  - " + m));
            } 
            Marker marker = getMarker(options);
            (new ApplicationWriter(app, options, marker, null, NamingLens.getIdentityLens(), null, null)).write(executor);
            options.printWarnings();
        } catch (ExecutionException e) {
            R8.unwrapExecutionException(e);
            throw new AssertionError(e);
        } finally {
              options.signalFinishedToProgramConsumer();
        } 
    }

    ApplicationReader.read {
        public final DexApplication read(ExecutorService executorService) throws IOException, ExecutionException {
            return read(null, executorService, ProgramClassCollection::resolveClassConflictImpl);
        }


        //proguardMap: null
        public final DexApplication read(StringResource proguardMap, ExecutorService executorService, ProgramClassConflictResolver resolver) throws IOException, ExecutionException {
            this.timing.begin("DexApplication.read");
            LazyLoadedDexApplication.Builder builder = DexApplication.builder(this.itemFactory, this.timing, resolver);
            try {
                List<Future<?>> futures = new ArrayList<>();
                readProguardMap(proguardMap, (DexApplication.Builder<?>)builder, executorService, futures);
                //MainDexList 为空
                readMainDexList((DexApplication.Builder<?>)builder, executorService, futures);
                ClassReader classReader = new ClassReader(executorService, futures);
                classReader.readSources();
                ThreadUtils.awaitFutures(futures);
                classReader.initializeLazyClassCollection(builder);
            } catch (ResourceException e) {
                throw this.options.reporter.fatalError(new StringDiagnostic(e.getMessage(), e.getOrigin()));
            } finally {
                this.timing.end();
            } 
            return (DexApplication)builder.build();
        }

        private void readMainDexList(DexApplication.Builder<?> builder, ExecutorService executorService, List<Future<?>> futures) {
            if (this.inputApp.hasMainDexList())
                futures.add(executorService.submit(() -> {
                    for (StringResource resource : this.inputApp.getMainDexListResources())
                        builder.addToMainDexList(MainDexList.parseList(resource, this.itemFactory)); 
                    builder.addToMainDexList((Collection)this.inputApp.getMainDexClasses().stream().map(()).collect(Collectors.toList()));
                })); 
        }

        ClassReader {
            void readSources() throws IOException, ResourceException {
                Collection<ProgramResource> resources = ApplicationReader.this.inputApp.computeAllProgramResources();
                List<ProgramResource> dexResources = new ArrayList<>(resources.size());
                List<ProgramResource> cfResources = new ArrayList<>(resources.size());
                for (ProgramResource resource : resources) {
                    if (resource.getKind() == ProgramResource.Kind.DEX) {
                        dexResources.add(resource);
                        continue;
                    } 
                    assert resource.getKind() == ProgramResource.Kind.CF;
                    cfResources.add(resource);
                } 
                //读取 dex 文件
                readDexSources(dexResources, ClassKind.PROGRAM, this.programClasses);
                //读取 jar 文件
                readClassSources(cfResources, ClassKind.PROGRAM, this.programClasses);
            }

            private <T extends com.android.tools.r8.graph.DexClass> void readDexSources(List<ProgramResource> dexSources, ClassKind classKind, Queue<T> classes) throws IOException, ResourceException {

                if (dexSources.size() > 0) {
                    List<DexFileReader> fileReaders = new ArrayList<>(dexSources.size());
                    int computedMinApiLevel = ApplicationReader.this.options.minApiLevel;
                    for (ProgramResource input : dexSources) {
                        DexFile file = new DexFile(input);
                        computedMinApiLevel = ApplicationReader.this.verifyOrComputeMinApiLevel(computedMinApiLevel, file);
                        fileReaders.add(new DexFileReader(file, classKind, ApplicationReader.this.itemFactory));
                    } 
                    ApplicationReader.this.options.minApiLevel = computedMinApiLevel;
                    for (DexFileReader reader : fileReaders)
                        DexFileReader.populateIndexTables(reader); //计算 Dex 文件中的索引数
                    if (!ApplicationReader.this.options.skipReadingDexCode)
                    for (DexFileReader reader : fileReaders) {
                        this.futures.add(this.executorService.submit(() -> {
                            reader.addCodeItemsTo();
                            Objects.requireNonNull(classes);
                            reader.addClassDefsTo(classKind.bridgeConsumer(classes::add));
                            }));
                    }  
                } 
            }

            private <T extends com.android.tools.r8.graph.DexClass> void readClassSources(List<ProgramResource> classSources, ClassKind classKind, Queue<T> classes) {
                Objects.requireNonNull(classes);
                JarClassFileReader reader = new JarClassFileReader(this.application, classKind.bridgeConsumer(classes::add));
                for (ProgramResource input : classSources) {
                    this.futures.add(this.executorService.submit(() -> {
                        try (InputStream is = input.getByteStream()) {
                          reader.read(input.getOrigin(), classKind, is);
                        } 
                        return null;
                    }));
                } 
            }
        }
    }

    ApplicationWriter.write {
        //ApplicationWriter.java
        public void write(ExecutorService executorService) throws IOException, ExecutionException, DexOverflowException {
            this.application.timing.begin("DexApplication.write");
            try {
                insertAttributeAnnotations();
                this.application.dexItemFactory.sort(this.namingLens);
                assert this.markerString == null || this.application.dexItemFactory.extractMarker() != null;
                SortAnnotations sortAnnotations = new SortAnnotations();
                this.application.classes().forEach(clazz -> clazz.addDependencies(sortAnnotations));
                Map<VirtualFile, Future<ObjectToOffsetMapping>> offsetMappingFutures = new LinkedHashMap<>();
                for (VirtualFile newFile : distribute()) {
                    assert !newFile.isEmpty();
                    if (!newFile.isEmpty())
                        offsetMappingFutures.put(newFile, executorService.submit(() -> {
                            //new 一个 ObjectToOffsetMapping 实例并返回
                            ObjectToOffsetMapping mapping = newFile.computeMapping(this.application);
                            rewriteCodeWithJumboStrings(mapping, newFile.classes(), this.application);
                            return mapping;
                        })); 
                } 
                ThreadUtils.awaitFutures(offsetMappingFutures.values());
                List<Future<Boolean>> dexDataFutures = new ArrayList<>();
                try {
                    for (VirtualFile virtualFile : offsetMappingFutures.keySet()) {
                        assert !virtualFile.isEmpty();
                        ObjectToOffsetMapping mapping = ((Future<ObjectToOffsetMapping>)offsetMappingFutures.get(virtualFile)).get();
                        dexDataFutures.add(executorService.submit(() -> {
                            byte[] result = writeDexFile(mapping);
                            if (virtualFile.getPrimaryClassDescriptor() != null) {
                                this.options.getDexFilePerClassFileConsumer().accept(virtualFile.getPrimaryClassDescriptor(), result, virtualFile.getClassDescriptors(), (DiagnosticsHandler)this.options.reporter);
                            } else {
                                this.options.getDexIndexedConsumer().accept(virtualFile.getId(), result, virtualFile.getClassDescriptors(), (DiagnosticsHandler)this.options.reporter);
                            } 
                            return Boolean.valueOf(true);
                        }));
                    } 
                } catch (InterruptedException e) {
                    throw new RuntimeException("Interrupted while waiting for future.", e);
                } 
                offsetMappingFutures.clear();
                ThreadUtils.awaitFutures(dexDataFutures);
                this.options.reporter.failIfPendingErrors();
                supplyAdditionalConsumers(this.application, this.namingLens, this.options, this.deadCode, this.proguardMapSupplier, this.proguardSeedsData);
            } finally {
              this.application.timing.end();
            } 
        }

        //返回 List<VirtualFile>，VirtualFile.java
        distribute {
            private Iterable<VirtualFile> distribute() throws ExecutionException, IOException, DexOverflowException {
                VirtualFile.Distributor distributor;
                if (this.options.isGeneratingDexFilePerClassFile()) {
                    distributor = new VirtualFile.FilePerInputClassDistributor(this);
                } else if (!this.options.canUseMultidex() && this.options.mainDexKeepRules.isEmpty() && this.application.mainDexList.isEmpty() && this.options.enableMainDexListCheck) {

                    distributor = new VirtualFile.MonoDexDistributor(this, this.options);
                } else {
                    //只生成一个 Dex 文件
                    distributor = new VirtualFile.FillFilesDistributor(this, this.options);
                } 
                return distributor.run();
            }

            FilePerInputClassDistributor {
                public Collection<VirtualFile> run() {
                    HashMap<DexProgramClass, VirtualFile> files = new HashMap<>();
                    Collection<DexProgramClass> synthetics = new ArrayList<>();
                    for (DexProgramClass clazz : this.application.classes()) {
                        if (clazz.getSynthesizedFrom().isEmpty()) {
                            VirtualFile file = new VirtualFile(this.virtualFiles.size(), this.writer.namingLens, clazz);
                            this.virtualFiles.add(file);
                            file.addClass(clazz);
                            files.put(clazz, file);
                            file.commitTransaction();
                            continue;
                        } 
                        synthetics.add(clazz);
                    } 
                    for (DexProgramClass synthetic : synthetics) {
                        for (DexProgramClass inputType : synthetic.getSynthesizedFrom()) {
                            VirtualFile file = files.get(inputType);
                            file.addClass(synthetic);
                            file.commitTransaction();
                        } 
                    } 
                        
                    return this.virtualFiles;
                }
            }

            MonoDexDistributor {
                public Collection<VirtualFile> run() throws ExecutionException, IOException, DexOverflowException {
                    for (DexProgramClass programClass : this.classes)
                        this.mainDexFile.addClass(programClass); 
                    this.mainDexFile.commitTransaction();
                    this.mainDexFile.throwIfFull(false);
                    return this.virtualFiles;
                }
            }

            FillFilesDistributor {
                public Collection<VirtualFile> run() throws IOException, DexOverflowException {
                    //向主 Dex 文件中写入 class
                    fillForMainDexList(this.classes);
                    if (this.classes.isEmpty())
                        return this.virtualFiles; 
                    List<VirtualFile> filesForDistribution = this.virtualFiles;
                    int fileIndexOffset = 0;
                    if (this.options.minimalMainDex && !this.mainDexFile.isEmpty()) {
                        assert !((VirtualFile)this.virtualFiles.get(0)).isEmpty();
                        assert this.virtualFiles.size() == 1;
                        this.virtualFiles.add(new VirtualFile(1, this.writer.namingLens));
                        filesForDistribution = this.virtualFiles.subList(1, this.virtualFiles.size());
                        fileIndexOffset = 1;
                    } 
                    this.classes = sortClassesByPackage(this.classes, this.originalNames);
                    (new VirtualFile.PackageSplitPopulator(filesForDistribution, this.classes, this.originalNames, null, this.application.dexItemFactory, this.fillStrategy, fileIndexOffset, this.writer.namingLens)).call();
                    return this.virtualFiles;
                }


                fillForMainDexList {
                    protected void fillForMainDexList(Set<DexProgramClass> classes) throws DexOverflowException {
                        if (!this.application.mainDexList.isEmpty()) {
                            //获取主 Dex 文件
                            VirtualFile mainDexFile = this.virtualFiles.get(0);
                            //将 mainDexList 中定义的 class 写入到主 Dex 文件中
                            for (UnmodifiableIterator<DexType> unmodifiableIterator = this.application.mainDexList.iterator(); unmodifiableIterator.hasNext(); ) {
                                DexType type = unmodifiableIterator.next();
                                DexClass clazz = this.application.definitionFor(type);
                                if (clazz != null && clazz.isProgramClass()) {
                                    DexProgramClass programClass = (DexProgramClass)clazz;
                                    mainDexFile.addClass(programClass);
                                    classes.remove(programClass);
                                } else {
                                    this.options.reporter.warning((Diagnostic)new StringDiagnostic("Application does not contain `" + type.toSourceString() + "` as referenced in main-dex-list."));
                                }
                                mainDexFile.commitTransaction();
                            } 
                            mainDexFile.throwIfFull(true);
                        } 
                    }

                    void throwIfFull(boolean hasMainDexList) throws DexOverflowException {
                        if (!isFull())
                          return; 
                        throw new DexOverflowException(hasMainDexList, this.transaction.getNumberOfMethods(), this.transaction.getNumberOfFields(), 65536L);
                    }

                    private boolean isFull() {
                        return isFull(this.transaction.getNumberOfMethods(), this.transaction.getNumberOfFields(), 65536);
                    }

                    private static boolean isFull(int numberOfMethods, int numberOfFields, int maximum) {
                        return (numberOfMethods > maximum || numberOfFields > maximum);
                    }
                }


                //VirtualFile.java  分包
                PackageSplitPopulator {
                    PackageSplitPopulator(List<VirtualFile> files, Set<DexProgramClass> classes, Map<DexProgramClass, String> originalNames, Set<String> previousPrefixes, DexItemFactory dexItemFactory, VirtualFile.FillStrategy fillStrategy, int fileIndexOffset, NamingLens namingLens) {
                        this.classes = new ArrayList<>(classes);
                        this.originalNames = originalNames;
                        this.previousPrefixes = previousPrefixes;
                        this.dexItemFactory = dexItemFactory;
                        this.fillStrategy = fillStrategy;
                        this.cycler = new VirtualFile.VirtualFileCycler(files, namingLens, fileIndexOffset);
                    }

                    public Map<String, Integer> call() throws IOException {
                        int prefixLength = 4;
                        int transactionStartIndex = 0;
                        int fileStartIndex = 0;
                        String currentPrefix = null;
                        Map<String, Integer> newPackageAssignments = new LinkedHashMap<>();
                        VirtualFile current = this.cycler.next();
                        List<DexProgramClass> nonPackageClasses = new ArrayList<>();
                        int classIndex = 0;
                        while (true) {
                            if (classIndex < this.classes.size()) {
                                DexProgramClass clazz = this.classes.get(classIndex);
                                //获取 class 的原始类名
                                String originalName = getOriginalName(clazz);
                                //判断 originalName 是否包含指定前缀
                                if (!coveredByPrefix(originalName, currentPrefix)) {
                                    String newPrefix;
                                    if (currentPrefix != null) {
                                        //读取 class 文件中的 class，Field，Method，Proto，Type，String 和方法句柄，保存到 VirtualFile 的以下成员中：
                                        //classes，fields，methods，strings，protos，types，callSites，callSites，methodHandles
                                        current.commitTransaction();
                                        this.cycler.restart();
                                        assert !newPackageAssignments.containsKey(currentPrefix);
                                        newPackageAssignments.put(currentPrefix, Integer.valueOf(current.id));
                                        prefixLength = 3;
                                    } 
                                    do {
                                        newPrefix = VirtualFile.extractPrefixToken(++prefixLength, originalName, false);
                                    } while (currentPrefix != null && (currentPrefix.startsWith(newPrefix) || conflictsWithPreviousPrefix(newPrefix, originalName)));

                                    if (!newPrefix.equals(""))
                                        currentPrefix = VirtualFile.extractPrefixToken(prefixLength, originalName, true); 
                                        transactionStartIndex = classIndex;
                                    } 
                                    if (currentPrefix != null) {
                                        assert clazz.superType != null || clazz.type == this.dexItemFactory.objectType;
                                        current.addClass(clazz);
                                    } else {
                                        assert clazz.superType != null;
                                        assert current.transaction.classes.isEmpty();
                                        nonPackageClasses.add(clazz);
                                        classIndex++;
                                    } 
                                    //判断 Dex 文件中的方法数是否超过 65535
                                    if (current.isFilledEnough(this.fillStrategy) || current.isFull()) {
                                        current.abortTransaction();
                                        if (classIndex - transactionStartIndex > (classIndex - fileStartIndex) / 5 && prefixLength < 7) {
                                            prefixLength++;
                                        } else {
                                            fileStartIndex = transactionStartIndex;
                                            if (!this.cycler.hasNext()) {
                                                if (current.transaction.getNumberOfClasses() == 0) {
                                                    for (int j = transactionStartIndex; j <= classIndex; j++)
                                                        nonPackageClasses.add(this.classes.get(j)); 
                                                    transactionStartIndex = classIndex + 1;
                                                } 
                                            this.cycler.addFile();
                                        } 
                                        current = this.cycler.next();
                                    } 
                                    currentPrefix = null;
                                    classIndex = transactionStartIndex - 1;
                                    assert current != null;
                                } 
                            } else {
                                break;
                            } 
                            classIndex++;
                        } 
                        current.commitTransaction();
                        assert !newPackageAssignments.containsKey(currentPrefix);
                        if (currentPrefix != null)
                            newPackageAssignments.put(currentPrefix, Integer.valueOf(current.id)); 
                        if (nonPackageClasses.size() > 0)
                            addNonPackageClasses(this.cycler, nonPackageClasses); 
                        return newPackageAssignments;
                    }
                }
            }
        }

        rewriteCodeWithJumboStrings {
            private static void rewriteCodeWithJumboStrings(ObjectToOffsetMapping mapping, Collection<DexProgramClass> classes, DexApplication application) {
                if (!mapping.hasJumboStrings())
                    return; 
                if (application.highestSortingString != null && application.highestSortingString.slowCompareTo(mapping.getFirstJumboString()) < 0)
                    return; 
                for (DexProgramClass clazz : classes)
                    clazz.forEachMethod(method -> method.rewriteCodeWithJumboStrings(mapping, application)); 
            }
        }

        //生成 Dex 文件，并写入内容
        writeDexFile {
            private byte[] writeDexFile(ObjectToOffsetMapping mapping) throws ApiLevelException {
                FileWriter fileWriter = new FileWriter(mapping, this.application, this.options, this.namingLens);
                fileWriter.collect();
                return fileWriter.generate();
            }


            public FileWriter collect() {
                (new ProgramClassDependencyCollector(this.application, this.mapping.getClasses())).run(this.mapping.getClasses());
                this.mixedSectionOffsets.getClassesWithData().forEach(DexProgramClass::sortMembers);
                this.mixedSectionOffsets.getClassesWithData().forEach(this::addStaticFieldValues);
                assert this.mixedSectionOffsets.stringData.size() == 0;
                for (DexString string : this.mapping.getStrings())
                    this.mixedSectionOffsets.add(string); 
                for (DexProto proto : this.mapping.getProtos())
                    this.mixedSectionOffsets.add(proto.parameters); 
                DexItem.collectAll(this.mixedSectionOffsets, this.mapping.getCallSites());
                DexItem.collectAll(this.mixedSectionOffsets, (DexItem[])this.mapping.getClasses());
                return this;
            }

            public byte[] generate() throws ApiLevelException {
                //检查 class 文件中的接口方法
                checkInterfaceMethods();
                Layout layout = Layout.from(this.mapping);
                layout.setCodesOffset(layout.dataSectionOffset);
                List<DexCode> codes = sortDexCodesByClassName(this.mixedSectionOffsets.getCodes(), this.application);
                this.dest.moveTo(layout.getCodesOffset() + sizeOfCodeItems(codes));
                Objects.requireNonNull(layout);
                writeItems(this.mixedSectionOffsets.getDebugInfos(), layout::setDebugInfosOffset, this::writeDebugItem);
                layout.setTypeListsOffset(this.dest.align(4));
                this.dest.moveTo(layout.getCodesOffset());
                assert this.dest.isAligned(4);
                Objects.requireNonNull(layout);
                writeItems(codes, layout::alreadySetOffset, this::writeCodeItem, 4);
                assert layout.getDebugInfosOffset() == 0 || this.dest.position() == layout.getDebugInfosOffset();
                this.dest.moveTo(layout.getTypeListsOffset());
                Objects.requireNonNull(layout);
                writeItems(this.mixedSectionOffsets.getTypeLists(), layout::alreadySetOffset, this::writeTypeList);
                Objects.requireNonNull(layout);
                writeItems(this.mixedSectionOffsets.getStringData(), layout::setStringDataOffsets, this::writeStringData);
                Objects.requireNonNull(layout);
                writeItems(this.mixedSectionOffsets.getAnnotations(), layout::setAnnotationsOffset, this::writeAnnotation);
                Objects.requireNonNull(layout);
                writeItems(this.mixedSectionOffsets.getClassesWithData(), layout::setClassDataOffset, this::writeClassData);
                Objects.requireNonNull(layout);
                writeItems(this.mixedSectionOffsets.getEncodedArrays(), layout::setEncodedArrarysOffset, this::writeEncodedArray);
                Objects.requireNonNull(layout);
                writeItems(this.mixedSectionOffsets.getAnnotationSets(), layout::setAnnotationSetsOffset, this::writeAnnotationSet, 4);
                Objects.requireNonNull(layout);
                writeItems(this.mixedSectionOffsets.getAnnotationSetRefLists(), layout::setAnnotationSetRefListsOffset, this::writeAnnotationSetRefList, 4);
                Objects.requireNonNull(layout);
                writeItems(this.mixedSectionOffsets.getAnnotationDirectories(), layout::setAnnotationDirectoriesOffset, this::writeAnnotationDirectory, 4);
                layout.setMapOffset(this.dest.align(4));
                writeMap(layout);
                layout.setEndOfFile(this.dest.position());
                this.dest.moveTo(112);
                writeFixedSectionItems(this.mapping.getStrings(), layout.stringIdsOffset, this::writeStringItem);
                writeFixedSectionItems(this.mapping.getTypes(), layout.typeIdsOffset, this::writeTypeItem);
                writeFixedSectionItems(this.mapping.getProtos(), layout.protoIdsOffset, this::writeProtoItem);
                writeFixedSectionItems(this.mapping.getFields(), layout.fieldIdsOffset, this::writeFieldItem);
                writeFixedSectionItems(this.mapping.getMethods(), layout.methodIdsOffset, this::writeMethodItem);
                writeFixedSectionItems(this.mapping.getClasses(), layout.classDefsOffset, this::writeClassDefItem);
                writeFixedSectionItems(this.mapping.getCallSites(), layout.callSiteIdsOffset, this::writeCallSite);
                writeFixedSectionItems(this.mapping.getMethodHandles(), layout.methodHandleIdsOffset, this::writeMethodHandle);
                writeHeader(layout);
                writeSignature(layout);
                writeChecksum(layout);
                return Arrays.copyOf(this.dest.asArray(), layout.getEndOfFile());
            }
        }


        accept {
            ArchiveConsumer {
                public void accept(int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
                    super.accept(fileIndex, data, descriptors, handler);
                    synchronizedWrite(getDexFileName(fileIndex), data, handler);
                }
            }


            DirectoryConsumer {
                public void accept(int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
                    super.accept(fileIndex, data, descriptors, handler);
                    Path target = getTargetDexFile(this.directory, fileIndex);
                    try {
                        prepareDirectory();
                        writeFile(data, target);
                    } catch (IOException e) {
                        handler.error((Diagnostic)new IOExceptionDiagnostic(e, (Origin)new PathOrigin(target)));
                    } 
                }
            }


            ForwardingConsumer {
                public void accept(int fileIndex, byte[] data, Set<String> descriptors, DiagnosticsHandler handler) {
                    if (this.consumer != null)
                        this.consumer.accept(fileIndex, data, descriptors, handler); 
                }
            }
        }
    }
}
```