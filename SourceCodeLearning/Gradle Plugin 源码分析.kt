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


//调用 D8.run(builder.build(), MoreExecutors.newDirectExecutorService())，生成 Dex 文件
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

        //这里注册的任务用于生成 mainDexList.txt 文件
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

                task.aaptGeneratedRules = variantScope.artifacts.getFinalArtifactFiles(
                    InternalArtifactType.LEGACY_MULTIDEX_AAPT_DERIVED_PROGUARD_RULES).get()
                task.userMultidexProguardRules = variantScope.variantConfiguration.multiDexKeepProguard
                task.userMultidexKeepFile = variantScope.variantConfiguration.multiDexKeepFile

                task.inputClasses = inputClasses
                task.libraryClasses = libraryClasses

                task.bootClasspath = variantScope.bootClasspath
                task.errorFormat = SyncOptions.getErrorFormatMode(variantScope.globalScope.projectOptions)
            }
        }
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
                    .setDexer(variantScope.getDexer())
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

        //从 class 集生成 Dex 文件
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

                Set<File> additionalPaths;
                DesugarIncrementalTransformHelper desugarIncrementalTransformHelper;
                if (java8LangSupportType != VariantScope.Java8LangSupport.D8) {
                    additionalPaths = ImmutableSet.of();
                    desugarIncrementalTransformHelper = null;
                } else {
                    desugarIncrementalTransformHelper = new DesugarIncrementalTransformHelper(projectVariant, transformInvocation, executor);
                    additionalPaths = desugarIncrementalTransformHelper
                            .getAdditionalPaths()
                            .stream()
                            .map(Path::toFile)
                            .collect(Collectors.toSet());
                }

                List<DexArchiveBuilderCacheHandler.CacheableItem> cacheableItems = new ArrayList<>();
                boolean isIncremental = transformInvocation.isIncremental();
                List<Path> classpath = getClasspath(transformInvocation)
                        .stream()
                        .map(Paths::get)
                        .collect(Collectors.toList());
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
}


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
        public final DexApplication read(StringResource proguardMap, ExecutorService executorService, ProgramClassConflictResolver resolver) throws IOException, ExecutionException {
            this.timing.begin("DexApplication.read");
            LazyLoadedDexApplication.Builder builder = DexApplication.builder(this.itemFactory, this.timing, resolver);
            try {
                List<Future<?>> futures = new ArrayList<>();
                readProguardMap(proguardMap, (DexApplication.Builder<?>)builder, executorService, futures);
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
                //读取目录下的 class 文件
                readDexSources(dexResources, ClassKind.PROGRAM, this.programClasses);
                //读取 jar 文件中的 class
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
                        DexFileReader.populateIndexTables(reader); 
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