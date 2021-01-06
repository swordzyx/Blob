Android 中 Gradle 插件的应用都是从 apply 开始的。该方法位于 BasePlugin 中。



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
    checkGradleVersion(project, getLogger(), projectOptions);
    DependencyResolutionChecks.registerDependencyCheck(project, projectOptions);

    project.getPluginManager().apply(AndroidBasePlugin.class);

    checkPathForErrors();
    checkModulesForErrors();

    PluginInitializer.initialize(project);
    RecordingBuildListener buildListener = ProfilerInitializer.init(project, projectOptions);
    ProfileAgent.INSTANCE.register(project.getName(), buildListener);
    threadRecorder = ThreadRecorder.get();

    Workers.INSTANCE.initFromProject(
            projectOptions,
            // possibly, in the future, consider using a pool with a dedicated size
            // using the gradle parallelism settings.
            ForkJoinPool.commonPool());

    ProcessProfileWriter.getProject(project.getPath())
            .setAndroidPluginVersion(Version.ANDROID_GRADLE_PLUGIN_VERSION)
            .setAndroidPlugin(getAnalyticsPluginType())
            .setPluginGeneration(GradleBuildProject.PluginGeneration.FIRST)
            .setOptions(AnalyticsUtil.toProto(projectOptions));

    // 执行该分支
    if (!projectOptions.get(BooleanOption.ENABLE_NEW_DSL_AND_API)) {

    	//记录 configureProject 的路径和执行的时间点
        threadRecorder.record(
                ExecutionType.BASE_PLUGIN_PROJECT_CONFIGURE,
                project.getPath(),
                null,
                this::configureProject);

        //记录 configureExtension 的路径和执行的时间点
        threadRecorder.record(
                ExecutionType.BASE_PLUGIN_PROJECT_BASE_EXTENSION_CREATION,
                project.getPath(),
                null,
                this::configureExtension);

        //记录 createTasks 的路径和执行的时间点
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

//com.android.build.gradle.BasePlugin.kt
private void configureProject() {
    final Gradle gradle = project.getGradle();
    ObjectFactory objectFactory = project.getObjects();

    extraModelInfo = new ExtraModelInfo(project.getPath(), projectOptions, project.getLogger());

    final SyncIssueHandler syncIssueHandler = extraModelInfo.getSyncIssueHandler();

    SdkComponents sdkComponents =
            SdkComponents.Companion.createSdkComponents(
                    project,
                    projectOptions,
                    // We pass a supplier here because extension will only be set later.
                    this::getExtension,
                    getLogger(),
                    syncIssueHandler);

    dataBindingBuilder = new DataBindingBuilder();
    dataBindingBuilder.setPrintMachineReadableOutput(
            SyncOptions.getErrorFormatMode(projectOptions) == ErrorFormatMode.MACHINE_PARSABLE);

    if (projectOptions.hasRemovedOptions()) {
        syncIssueHandler.reportWarning(
                Type.GENERIC, projectOptions.getRemovedOptionsErrorMessage());
    }

    if (projectOptions.hasDeprecatedOptions()) {
        extraModelInfo
                .getDeprecationReporter()
                .reportDeprecatedOptions(projectOptions.getDeprecatedOptions());
    }

    if (!projectOptions.getExperimentalOptions().isEmpty()) {
        projectOptions
                .getExperimentalOptions()
                .forEach(extraModelInfo.getDeprecationReporter()::reportExperimentalOption);
    }

    // Enforce minimum versions of certain plugins
    GradlePluginUtils.enforceMinimumVersionsOfPlugins(project, syncIssueHandler);

    // Apply the Java plugin
    project.getPlugins().apply(JavaBasePlugin.class);

    DslScopeImpl dslScope =
            new DslScopeImpl(
                    syncIssueHandler, extraModelInfo.getDeprecationReporter(), objectFactory);

    @Nullable
    FileCache buildCache = BuildCacheUtils.createBuildCacheIfEnabled(project, projectOptions);

    globalScope =
            new GlobalScope(
                    project,
                    creator,
                    new ProjectWrapper(project),
                    projectOptions,
                    dslScope,
                    sdkComponents,
                    registry,
                    buildCache,
                    extraModelInfo.getMessageReceiver());

    project.getTasks()
            .named("assemble")
            .configure(
                    task ->
                            task.setDescription(
                                    "Assembles all variants of all applications and secondary packages."));

    // 执行过程中回调
    // call back on execution. This is called after the whole build is done (not
    // after the current project is done).
    // This is will be called for each (android) projects though, so this should support
    // being called 2+ times.
    gradle.addBuildListener(
            new BuildListener() {
                @Override
                public void buildStarted(@NonNull Gradle gradle) {}

                @Override
                public void settingsEvaluated(@NonNull Settings settings) {}

                @Override
                public void projectsLoaded(@NonNull Gradle gradle) {}

                @Override
                public void projectsEvaluated(@NonNull Gradle gradle) {}

                //构建完成时回调该方法，主要清除
                @Override
                public void buildFinished(@NonNull BuildResult buildResult) {
                    // Do not run buildFinished for included project in composite build.
                    if (buildResult.getGradle().getParent() != null) {
                        return;
                    }
                    ModelBuilder.clearCaches();
                    Workers.INSTANCE.shutdown();
                    sdkComponents.unload();
                    SdkLocator.resetCache();
                    threadRecorder.record(
                            ExecutionType.BASE_PLUGIN_BUILD_FINISHED,
                            project.getPath(),
                            null,
                            () -> {
                                if (!projectOptions.get(
                                        BooleanOption.KEEP_SERVICES_BETWEEN_BUILDS)) {
                                    WorkerActionServiceRegistry.INSTANCE
                                            .shutdownAllRegisteredServices(
                                                    ForkJoinPool.commonPool());
                                }
                                Main.clearInternTables();
                            });
                    DeprecationReporterImpl.Companion.clean();
                }
            });

    createLintClasspathConfiguration(project);
}

//创建 Gradle Android 插件的扩展对象，创建 build.gradle 中配置的 buildtype，productflavor，signingConfig 容器，并设置回调。创建 taskManager(任务管理类)，VariantFactory（变体工厂），variantManager（变体管理类）
configureExtension {
    //com\android\build\gradle\BasePlugin.java
	//配置扩展
	private void configureExtension() {
	    ObjectFactory objectFactory = project.getObjects();
	    //创建 BuildType 类型的 Container，也就是 debug 或者 release
	    final NamedDomainObjectContainer<BuildType> buildTypeContainer =
	            project.container(
	                    BuildType.class,
	                    new BuildTypeFactory(
	                            objectFactory,
	                            project,
	                            extraModelInfo.getSyncIssueHandler(),
	                            extraModelInfo.getDeprecationReporter()));
	    //创建 ProductFlavor 的 Container
	    final NamedDomainObjectContainer<ProductFlavor> productFlavorContainer =
	            project.container(
	                    ProductFlavor.class,
	                    new ProductFlavorFactory(
	                            objectFactory,
	                            project,
	                            extraModelInfo.getDeprecationReporter(),
	                            project.getLogger()));
	    //创建 SigningConfig 的 Container ，即签名配置。
	    final NamedDomainObjectContainer<SigningConfig> signingConfigContainer =
	            project.container(
	                    SigningConfig.class,
	                    new SigningConfigFactory(
	                            objectFactory,
	                            GradleKeystoreHelper.getDefaultDebugKeystoreLocation()));

	    //配置构建输出的 Container
	    final NamedDomainObjectContainer<BaseVariantOutput> buildOutputs =
	            project.container(BaseVariantOutput.class);

	    project.getExtensions().add("buildOutputs", buildOutputs);

	    sourceSetManager = new SourceSetManager(
			                    project,
			                    isPackagePublished(),
			                    globalScope.getDslScope(),
			                    new DelayedActionsExecutor());
	    //createExtension 的实现位于 AbstractAppPlugin 中
	    extension = createExtension(
	                    project,
	                    projectOptions,
	                    globalScope,
	                    buildTypeContainer,
	                    productFlavorContainer,
	                    signingConfigContainer,
	                    buildOutputs,
	                    sourceSetManager,
	                    extraModelInfo);

	    globalScope.setExtension(extension);

	    //createVariantFactory 的实现位于 ApplicationVariantFactory 和 LibraryVariantFactory 中
	    //VariantFactory 是构建变体的工厂类，主要生成 Variant 对象。
	    variantFactory = createVariantFactory(globalScope, extension);

	    //createTaskManager 是抽象方法，再 AppPlugin 和 LibraryPlugin 中均有实现。
	    //TaskManager 是具体的任务管理类，构建 app 和构建 library 所需的构建任务是不同的
	    taskManager = createTaskManager(
	                    globalScope,
	                    project,
	                    projectOptions,
	                    dataBindingBuilder,
	                    extension,
	                    variantFactory,
	                    registry,
	                    threadRecorder);
	    //变体管理类。
	    variantManager = new VariantManager(
	                    globalScope,
	                    project,
	                    projectOptions,
	                    extension,
	                    variantFactory,
	                    taskManager,
	                    sourceSetManager,
	                    threadRecorder);

	    registerModels(registry, globalScope, variantManager, extension, extraModelInfo);

	    // map the whenObjectAdded callbacks on the containers.
	    signingConfigContainer.whenObjectAdded(variantManager::addSigningConfig);

	    buildTypeContainer.whenObjectAdded(
	            buildType -> {
	                if (!this.getClass().isAssignableFrom(DynamicFeaturePlugin.class)) {
	                    SigningConfig signingConfig = signingConfigContainer.findByName(BuilderConstants.DEBUG);
	                    buildType.init(signingConfig);
	                } else {
	                    // initialize it without the signingConfig for dynamic-features.
	                    buildType.init();
	                }
	                variantManager.addBuildType(buildType);
	            });

	    productFlavorContainer.whenObjectAdded(variantManager::addProductFlavor);

	    // map whenObjectRemoved on the containers to throw an exception.
	    signingConfigContainer.whenObjectRemoved(
	            new UnsupportedAction("Removing signingConfigs is not supported."));
	    buildTypeContainer.whenObjectRemoved(
	            new UnsupportedAction("Removing build types is not supported."));
	    productFlavorContainer.whenObjectRemoved(
	            new UnsupportedAction("Removing product flavors is not supported."));

	    //创建默认配置，这里以构建 APP 为例，因此会调用 ApplicationVariantFactory 中该方法
	    variantFactory.createDefaultComponents(buildTypeContainer, productFlavorContainer, signingConfigContainer);
	}


	@NonNull
    @Override
    protected BaseExtension createExtension(
            @NonNull Project project,
            @NonNull ProjectOptions projectOptions,
            @NonNull GlobalScope globalScope,
            @NonNull NamedDomainObjectContainer<BuildType> buildTypeContainer,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavorContainer,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigContainer,
            @NonNull NamedDomainObjectContainer<BaseVariantOutput> buildOutputs,
            @NonNull SourceSetManager sourceSetManager,
            @NonNull ExtraModelInfo extraModelInfo) {
    	//对应 build.gradle 中的 android 配置块
        return project.getExtensions()
                .create("android",
                        getExtensionClass(),
                        project,
                        projectOptions,
                        globalScope,
                        buildTypeContainer,
                        productFlavorContainer,
                        signingConfigContainer,
                        buildOutputs,
                        sourceSetManager,
                        extraModelInfo,
                        isBaseApplication);
    }


    @Override
    public void createDefaultComponents(
            @NonNull NamedDomainObjectContainer<BuildType> buildTypes,
            @NonNull NamedDomainObjectContainer<ProductFlavor> productFlavors,
            @NonNull NamedDomainObjectContainer<SigningConfig> signingConfigs) {
        // must create signing config first so that build type 'debug' can be initialized
        // with the debug signing config.
        signingConfigs.create(DEBUG);
        buildTypes.create(DEBUG);
        buildTypes.create(RELEASE);
    } 
}

//创建构建变体及其所需的任务
createTasks {
    //BasePlugin.java
    private void createTasks() {
        //记录 taskManager.createTasksBeforeEvaluate 执行所消耗的时间。并记录结果
        threadRecorder.record(
                ExecutionType.TASK_MANAGER_CREATE_TASKS,
                project.getPath(),
                null,
                () -> taskManager.createTasksBeforeEvaluate());

        //项目评估完成之后开始创建 Android 任务
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


    @VisibleForTesting
    final void createAndroidTasks() {
        // Make sure unit tests set the required fields.
        checkState(extension.getCompileSdkVersion() != null, "compileSdkVersion is not specified.");
        extension
                .getCompileOptions()
                .setDefaultJavaVersion(AbstractCompilesUtil.getDefaultJavaVersion(extension.getCompileSdkVersion()));

        // get current plugins and look for the default Java plugin.
        if (project.getPlugins().hasPlugin(JavaPlugin.class)) {
            throw new BadPluginException(
                    "The 'java' plugin has been applied, but it is not compatible with the Android plugins.");
        }

        if (project.getPlugins().hasPlugin("me.tatarka.retrolambda")) {
            String warningMsg =
                    "One of the plugins you are using supports Java 8 "
                            + "language features. To try the support built into"
                            + " the Android plugin, remove the following from "
                            + "your build.gradle:\n"
                            + "    apply plugin: 'me.tatarka.retrolambda'\n"
                            + "To learn more, go to https://d.android.com/r/"
                            + "tools/java-8-support-message.html\n";
            extraModelInfo
                    .getSyncIssueHandler()
                    .reportWarning(EvalIssueReporter.Type.GENERIC, warningMsg);
        }

        // TODO(112700217): Only force the SDK resolution when in sync mode. Also move this to
        // as late as possible so we configure most tasks as possible  during sync.
        if (globalScope.getSdkComponents().getSdkFolder() == null) {
            return;
        }
        // don't do anything if the project was not initialized.
        // Unless TEST_SDK_DIR is set in which case this is unit tests and we don't return.
        // This is because project don't get evaluated in the unit test setup.
        // See AppPluginDslTest
        if ((!project.getState().getExecuted() || project.getState().getFailure() != null) && SdkLocator.getSdkTestDirectory() == null) {
            return;
        }

        if (hasCreatedTasks) {
            return;
        }
        hasCreatedTasks = true;

        extension.disableWrite();

        taskManager.configureCustomLintChecks();

        ProcessProfileWriter.getProject(project.getPath())
                .setCompileSdk(extension.getCompileSdkVersion())
                .setBuildToolsVersion(extension.getBuildToolsRevision().toString())
                .setSplits(AnalyticsUtil.toProto(extension.getSplits()));

        String kotlinPluginVersion = getKotlinPluginVersion();
        if (kotlinPluginVersion != null) {
            ProcessProfileWriter.getProject(project.getPath())
                    .setKotlinPluginVersion(kotlinPluginVersion);
        }

        //创建构建变体，并为构建变体创建构建任务
        List<VariantScope> variantScopes = variantManager.createAndroidTasks();

        ApiObjectFactory apiObjectFactory =
                new ApiObjectFactory(
                        extension,
                        variantFactory,
                        project.getObjects());
        for (VariantScope variantScope : variantScopes) {
            BaseVariantData variantData = variantScope.getVariantData();
            apiObjectFactory.create(variantData);
        }

        // Make sure no SourceSets were added through the DSL without being properly configured
        // Only do it if we are not restricting to a single variant (with Instant
        // Run or we can find extra source set
        if (projectOptions.get(StringOption.IDE_RESTRICT_VARIANT_NAME) == null) {
            sourceSetManager.checkForUnconfiguredSourceSets();
        }

        // must run this after scopes are created so that we can configure kotlin kapt tasks
        taskManager.addDataBindingDependenciesIfNecessary(extension.getDataBinding(), variantManager.getVariantScopes());


        // create the global lint task that depends on all the variants
        taskManager.configureGlobalLintTask(variantManager.getVariantScopes());

        int flavorDimensionCount = 0;
        if (extension.getFlavorDimensionList() != null) {
            flavorDimensionCount = extension.getFlavorDimensionList().size();
        }

        taskManager.createAnchorAssembleTasks(
                variantScopes,
                extension.getProductFlavors().size(),
                flavorDimensionCount,
                variantFactory.getVariantConfigurationTypes().size());

        // now publish all variant artifacts.
        for (VariantScope variantScope : variantManager.getVariantScopes()) {
            variantManager.publishBuildArtifacts(variantScope);
        }

        checkSplitConfiguration();
        variantManager.setHasCreatedTasks(true);
    }


    //VariantManager.java
    //变体/任务创建入口点
    public List<VariantScope> createAndroidTasks() {
        variantFactory.validateModel(this);
        variantFactory.preVariantWork(project);

        //此时 variantScopes 为空，创建构建变体数据（VariantData），并添加到 variantScope 中
        if (variantScopes.isEmpty()) {
            populateVariantDataList();
        }

        // Create top level test tasks.
        taskManager.createTopLevelTestTasks(!productFlavors.isEmpty());

        //构建任务。
        for (final VariantScope variantScope : variantScopes) {
            createTasksForVariantData(variantScope);
        }

        taskManager.createSourceSetArtifactReportTask(globalScope);

        taskManager.createReportTasks(variantScopes);

        return variantScopes;
    }


    //VariantManager.java
    populateVariantDataList {
        //VariantManager.java
        //创建所有的变体
        public void populateVariantDataList() {
            List<String> flavorDimensionList = extension.getFlavorDimensionList();

            if (productFlavors.isEmpty()) {
                configureDependencies();
                createVariantDataForProductFlavors(Collections.emptyList());
            } else {
                // 设置构建变体的维度
                if (flavorDimensionList == null || flavorDimensionList.isEmpty()) {
                    globalScope
                            .getErrorHandler()
                            .reportError(
                                    EvalIssueReporter.Type.UNNAMED_FLAVOR_DIMENSION,
                                    new EvalIssueException(
                                            "All flavors must now belong to a named flavor dimension. "
                                                    + "Learn more at "
                                                    + "https://d.android.com/r/tools/flavorDimensions-missing-error-message.html"));
                } else if (flavorDimensionList.size() == 1) {
                    // if there's only one dimension, auto-assign the dimension to all the flavors.
                    String dimensionName = flavorDimensionList.get(0);
                    for (ProductFlavorData<CoreProductFlavor> flavorData : productFlavors.values()) {
                        CoreProductFlavor flavor = flavorData.getProductFlavor();
                        if (flavor.getDimension() == null && flavor instanceof DefaultProductFlavor) {
                            ((DefaultProductFlavor) flavor).setDimension(dimensionName);
                        }
                    }
                }

                // can only call this after we ensure all flavors have a dimension.
                configureDependencies();

                // Create iterable to get GradleProductFlavor from ProductFlavorData.
                Iterable<CoreProductFlavor> flavorDsl = Iterables.transform(productFlavors.values(), ProductFlavorData::getProductFlavor);

                // 获取产品变种与维度的所有组合
                List<ProductFlavorCombo<CoreProductFlavor>> flavorComboList =
                        ProductFlavorCombo.createCombinations(
                                flavorDimensionList,
                                flavorDsl);

                for (ProductFlavorCombo<CoreProductFlavor>  flavorCombo : flavorComboList) {
                    //noinspection unchecked
                    createVariantDataForProductFlavors((List<ProductFlavor>) (List) flavorCombo.getFlavorList());
                }
            }

            configureVariantArtifactTransforms(variantScopes);
        }


        //将给定的产品变体与所有的构建类型进行组合成构建变体，并创建
        private void createVariantDataForProductFlavors(@NonNull List<ProductFlavor> productFlavorList) {
            for (VariantType variantType : variantFactory.getVariantConfigurationTypes()) {
                createVariantDataForProductFlavorsAndVariantType(productFlavorList, variantType);
            }
        }

        //VariantManager.java
        private void createVariantDataForProductFlavorsAndVariantType( @NonNull List<ProductFlavor> productFlavorList, @NonNull VariantType variantType) {

            BuildTypeData testBuildTypeData = null;
            if (extension instanceof TestedAndroidConfig) {
                TestedAndroidConfig testedExtension = (TestedAndroidConfig) extension;

                testBuildTypeData = buildTypes.get(testedExtension.getTestBuildType());
                if (testBuildTypeData == null) {
                    throw new RuntimeException(String.format(
                            "Test Build Type '%1$s' does not exist.", testedExtension.getTestBuildType()));
                }
            }

            BaseVariantData variantForAndroidTest = null;

            CoreProductFlavor defaultConfig = defaultConfigData.getProductFlavor();

            Action<com.android.build.api.variant.VariantFilter> variantFilterAction = extension.getVariantFilter();

            final String restrictedProject = projectOptions.get(StringOption.IDE_RESTRICT_VARIANT_PROJECT);
            final boolean restrictVariants = restrictedProject != null;

            // compare the project name if the type is not a lib.
            final boolean projectMatch;
            final String restrictedVariantName;
            if (restrictVariants) {
                projectMatch = variantType.isApk() && project.getPath().equals(restrictedProject);
                restrictedVariantName = projectOptions.get(StringOption.IDE_RESTRICT_VARIANT_NAME);
            } else {
                projectMatch = false;
                restrictedVariantName = null;
            }

            //获取并遍历所有的构建类型
            for (BuildTypeData buildTypeData : buildTypes.values()) {
                boolean ignore = false;

                //过滤不需要创建的构建变体
                if (restrictVariants || variantFilterAction != null) {
                    .......
                }

                if (!ignore) {
                    BaseVariantData variantData =
                            createVariantDataForVariantType(
                                    buildTypeData.getBuildType(), productFlavorList, variantType);
                    //注册一个新的变体，其实就是将 variantData 添加到 variantScopes 中，这是一个 VariantScope 集合
                    addVariant(variantData);

                    GradleVariantConfiguration variantConfig = variantData.getVariantConfiguration();
                    VariantScope variantScope = variantData.getScope();

                    int minSdkVersion = variantConfig.getMinSdkVersion().getApiLevel();
                    int targetSdkVersion = variantConfig.getTargetSdkVersion().getApiLevel();
                    //判断 SDK 版本号
                    if (minSdkVersion > 0 && targetSdkVersion > 0 && minSdkVersion > targetSdkVersion) {
                       ........
                    }

                    GradleBuildVariant.Builder profileBuilder =
                            ProcessProfileWriter.getOrCreateVariant(
                                            project.getPath(), variantData.getName())
                                    .setIsDebug(variantConfig.getBuildType().isDebuggable())
                                    .setMinSdkVersion(
                                            AnalyticsUtil.toProto(variantConfig.getMinSdkVersion()))
                                    .setMinifyEnabled(variantScope.getCodeShrinker() != null)
                                    .setUseMultidex(variantConfig.isMultiDexEnabled())
                                    .setUseLegacyMultidex(variantConfig.isLegacyMultiDexMode())
                                    .setVariantType(variantData.getType().getAnalyticsVariantType())
                                    .setDexBuilder(AnalyticsUtil.toProto(variantScope.getDexer()))
                                    .setDexMerger(AnalyticsUtil.toProto(variantScope.getDexMerger()))
                                    .setTestExecution(
                                            AnalyticsUtil.toProto(
                                                    globalScope
                                                            .getExtension()
                                                            .getTestOptions()
                                                            .getExecutionEnum()));

                    if (variantScope.getCodeShrinker() != null) {
                        profileBuilder.setCodeShrinker(AnalyticsUtil.toProto(variantScope.getCodeShrinker()));
                    }

                    //设置 TargetSdkVersion，从 build.gradle 中读取
                    if (variantConfig.getTargetSdkVersion().getApiLevel() > 0) {
                        profileBuilder.setTargetSdkVersion(AnalyticsUtil.toProto(variantConfig.getTargetSdkVersion()));
                    }
                    //设置支持的最大 SDK 版本号
                    if (variantConfig.getMergedFlavor().getMaxSdkVersion() != null) {
                        profileBuilder.setMaxSdkVersion(ApiVersion.newBuilder().setApiLevel(variantConfig.getMergedFlavor().getMaxSdkVersion()));
                    }

                    VariantScope.Java8LangSupport supportType = variantData.getScope().getJava8LangSupportType();
                    //检查是否支持 Java8
                    if (supportType != VariantScope.Java8LangSupport.INVALID && supportType != VariantScope.Java8LangSupport.UNUSED) {
                        profileBuilder.setJava8LangSupport(AnalyticsUtil.toProto(supportType));
                    }

                    if (variantFactory.hasTestScope()) {
                        if (buildTypeData == testBuildTypeData) {
                            variantForAndroidTest = variantData;
                        }

                        if (!variantType.isHybrid()) { // BASE_FEATURE/FEATURE
                            // There's nothing special about unit testing the feature variant, so
                            // there's no point creating the duplicate unit testing variant. This only
                            // causes tests to run twice when running "testDebug".
                            TestVariantData unitTestVariantData = createTestVariantData(variantData, UNIT_TEST);
                            addVariant(unitTestVariantData);
                        }
                    }
                }
            }

            if (variantForAndroidTest != null) {
                // TODO: b/34624400
                if (!variantType.isHybrid()) { // BASE_FEATURE/FEATURE
                    TestVariantData androidTestVariantData = createTestVariantData(variantForAndroidTest, ANDROID_TEST);
                    addVariant(androidTestVariantData);
                }
            }
        }
    }
    

    //VariantManager.java
    //为特定的构建变体创建任务
    createTasksForVariantData {
        //VariantManager.java
        public void createTasksForVariantData(final VariantScope variantScope) {
            //构建变体数据
            final BaseVariantData variantData = variantScope.getVariantData();
            //构建类型
            final VariantType variantType = variantData.getType();
            //
            final GradleVariantConfiguration variantConfig = variantScope.getVariantConfiguration();
            //创建 AssembleTask
            taskManager.createAssembleTask(variantData);
            if (variantType.isBaseModule()) {
                taskManager.createBundleTask(variantData);
            }

            //判断当前构建的是否为测试模块
            if (variantType.isTestComponent()) {
                ......
            } else {
                //createTasksForVariantScope 的具体实现位于 ApplicationTaskManager 中
                taskManager.createTasksForVariantScope(
                        variantScope,
                        variantScopes
                                .stream()
                                .filter(TaskManager::isLintVariant)
                                .collect(Collectors.toList()));
            }
        }


        //ApplicationTaskManager
        //创建生成 APK 所需要的所有任务
        @Override
        public void createTasksForVariantScope(@NonNull final VariantScope variantScope, @NonNull List<VariantScope> variantScopesForLint) {
            createAnchorTasks(variantScope);
            createCheckManifestTask(variantScope);

            handleMicroApp(variantScope);

            // Create all current streams (dependencies mostly at this point)
            createDependencyStreams(variantScope);

            // Add a task to publish the applicationId.
            createApplicationIdWriterTask(variantScope);

            taskFactory.register(new MainApkListPersistence.CreationAction(variantScope));
            createBuildArtifactReportTask(variantScope);

            // Add a task to process the manifest(s)
            createMergeApkManifestsTask(variantScope);

            // Add a task to create the res values
            createGenerateResValuesTask(variantScope);

            // Add a task to compile renderscript files.
            createRenderscriptTask(variantScope);

            // Add a task to merge the resource folders
            createMergeResourcesTask(variantScope, true, Sets.immutableEnumSet(MergeResources.Flag.PROCESS_VECTOR_DRAWABLES));

            // Add tasks to compile shader
            createShaderTask(variantScope);

            // Add a task to merge the asset folders
            createMergeAssetsTask(variantScope);

            // Add a task to create the BuildConfig class
            createBuildConfigTask(variantScope);

            // Add a task to process the Android Resources and generate source files
            createApkProcessResTask(variantScope);

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

                if (extension.getDataBinding().isEnabled()) {
                    // Create a task that will package the manifest ids(the R file packages) of all
                    // features into a file. This file's path is passed into the Data Binding annotation
                    // processor which uses it to known about all available features.
                    //
                    // <p>see: {@link TaskManager#setDataBindingAnnotationProcessorParams(VariantScope)}
                    taskFactory.register(
                            new DataBindingExportFeatureApplicationIdsTask.CreationAction(
                                    variantScope));

                }
            } else {
                // Non-base feature specific task.
                // Task will produce artifacts consumed by the base feature
                taskFactory.register(new FeatureSplitDeclarationWriterTask.CreationAction(variantScope));
                if (extension.getDataBinding().isEnabled()) {
                    // Create a task that will package necessary information about the feature into a
                    // file which is passed into the Data Binding annotation processor.
                    taskFactory.register(
                            new DataBindingExportFeatureInfoTask.CreationAction(variantScope));
                }
                taskFactory.register(new MergeConsumerProguardFilesTask.CreationAction(variantScope));
            }

            // Add data binding tasks if enabled
            createDataBindingTasksIfNecessary(variantScope, MergeType.MERGE);

            // Add a compile task
            createCompileTask(variantScope);

            createStripNativeLibraryTask(taskFactory, variantScope);

            if (variantScope.getVariantData().getMultiOutputPolicy().equals(MultiOutputPolicy.SPLITS)) {
                if (extension.getBuildToolsRevision().getMajor() < 21) {
                    throw new RuntimeException(
                            "Pure splits can only be used with buildtools 21 and later");
                }

                createSplitTasks(variantScope);
            }

            createPackagingTask(variantScope);

            maybeCreateLintVitalTask((ApkVariantData) variantScope.getVariantData(), variantScopesForLint);

            // Create the lint tasks, if enabled
            createLintTasks(variantScope, variantScopesForLint);

            taskFactory.register(new FeatureSplitTransitiveDepsWriterTask.CreationAction(variantScope));

            createDynamicBundleTask(variantScope);
        }
    }

    //ApiObjectFactory.java
    //此时已完成构建变体任务的初始化
    public BaseVariantImpl create(BaseVariantData variantData) {
        //测试变体
        if (variantData.getType().isTestComponent()) {
            .....
        }

        BaseVariantImpl variantApi =
                variantFactory.createVariantApi(
                        objectFactory,
                        variantData,
                        readOnlyObjectProvider);
        if (variantApi == null) {
            return null;
        }

        //如果需要构建测试
        if (variantFactory.hasTestScope()) {
            ........
        }

        createVariantOutput(variantData, variantApi);

        try {
            // Only add the variant API object to the domain object set once it's been fully initialized.
            extension.addVariant(variantApi);
        } catch (Throwable t) {
            // Adding variant to the collection will trigger user-supplied callbacks
            throw new ExternalApiUsageException(t);
        }

        return variantApi;
    }
}


Dex 编译过程 {
    createTasks > createTasksForVariantData > createTasksForVariantData {
        .....

        // Add a compile task
        createCompileTask(variantScope);
        .....
    }


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

        if (variantScope.getNeedsMainDexList()) {
            taskFactory.register(new D8MainDexListTask.CreationAction(variantScope, false));
        }

        if (variantScope.getNeedsMainDexListForBundle()) {
            taskFactory.register(new D8MainDexListTask.CreationAction(variantScope, true));
        }

        createDexTasks(variantScope, dexingType);

        maybeCreateResourcesShrinkerTransform(variantScope);

        // TODO: support DexSplitterTransform when IR enabled (http://b/77585545)
        maybeCreateDexSplitterTransform(variantScope);
        // TODO: create JavaResSplitterTransform and call it here (http://b/77546738)
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


    //生成 Dex 文件
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
            boolean supportsDesugaring =
                    java8SLangSupport == Java8LangSupport.UNUSED
                            || (java8SLangSupport == Java8LangSupport.D8
                                    && projectOptions.get(BooleanOption.ENABLE_DEXING_DESUGARING_ARTIFACT_TRANSFORM));
            boolean enableDexingArtifactTransform =
                    globalScope.getProjectOptions().get(BooleanOption.ENABLE_DEXING_ARTIFACT_TRANSFORM)
                            && extension.getTransforms().isEmpty()
                            && !minified
                            && supportsDesugaring
                            && !appliesCustomClassTransforms(variantScope, projectOptions);
            FileCache userLevelCache = getUserDexCache(minified, dexOptions.getPreDexLibraries());
            DexArchiveBuilderTransform preDexTransform = new DexArchiveBuilderTransformBuilder()
                    .setAndroidJarClasspath(globalScope.getFilteredBootClasspath())
                    .setDexOptions(dexOptions)
                    .setMessageReceiver(variantScope.getGlobalScope().getMessageReceiver())
                    .setErrorFormatMode(
                            SyncOptions.getErrorFormatMode(
                                    variantScope.getGlobalScope().getProjectOptions()))
                    .setUserLevelCache(userLevelCache)
                    .setMinSdkVersion(
                            variantScope
                                    .getVariantConfiguration()
                                    .getMinSdkVersionWithTargetDeviceApi()
                                    .getFeatureLevel())
                    .setDexer(variantScope.getDexer())
                    .setUseGradleWorkers(projectOptions.get(BooleanOption.ENABLE_GRADLE_WORKERS))
                    .setInBufferSize(projectOptions.get(IntegerOption.DEXING_READ_BUFFER_SIZE))
                    .setOutBufferSize(projectOptions.get(IntegerOption.DEXING_WRITE_BUFFER_SIZE))
                    .setIsDebuggable(
                            variantScope
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


        private void maybeCreateDexSplitterTransform(@NonNull VariantScope variantScope) {
            if (!variantScope.consumesFeatureJars()) {
                return;
            }

            File dexSplitterOutput = FileUtils.join(
                    globalScope.getIntermediatesDir(),
                    "dex-splitter",
                    variantScope.getVariantConfiguration().getDirName());
            FileCollection featureJars = variantScope.getArtifactFileCollection(METADATA_VALUES, PROJECT, METADATA_CLASSES);

            BuildableArtifact baseJars = variantScope.getArtifacts().getFinalArtifactFiles(
                    InternalArtifactType.MODULE_AND_RUNTIME_DEPS_CLASSES);

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
    }
    



}
