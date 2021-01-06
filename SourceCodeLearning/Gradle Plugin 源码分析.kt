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

        //重点
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

        //此时 variantScopes 为空
        if (variantScopes.isEmpty()) {
            populateVariantDataList();
        }

        // Create top level test tasks.
        taskManager.createTopLevelTestTasks(!productFlavors.isEmpty());

        for (final VariantScope variantScope : variantScopes) {
            createTasksForVariantData(variantScope);
        }

        taskManager.createSourceSetArtifactReportTask(globalScope);

        taskManager.createReportTasks(variantScopes);

        return variantScopes;
    }


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
                TestVariantData androidTestVariantData =
                        createTestVariantData(variantForAndroidTest, ANDROID_TEST);
                addVariant(androidTestVariantData);
            }
        }
    }


}

