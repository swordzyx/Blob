入口类（META-INF/MANIFEST.MF）：com.android.tools.r8.D8


com.android.tools.r8.D8 {
	public static void main(String[] args) {
		if (args.length == 0) {
		  System.err.println(D8Command.USAGE_MESSAGE);
		  System.exit(1);
		} 
		ExceptionUtils.withMainProgramHandler(() -> run(args));
	}

	private static void run(String[] args) throws CompilationFailedException {
		//解析 d8 命令参数
		D8Command command = D8Command.parse(args, (Origin)CommandLineOrigin.INSTANCE).build();
		if (command.isPrintHelp()) {
			System.out.println(D8Command.USAGE_MESSAGE);
			return;
		} 
		if (command.isPrintVersion()) {
			Version.printToolVersion("D8");
			return;
		} 
		InternalOptions options = command.getInternalOptions();
		//获取 apk 里面的资源
		AndroidApp app = command.getInputApp();
		//再指定的线程中执行 runForTesting，runForTesting 里面调用 run(AndroidApp, InternalOptions, ExecutorService) ，
		ExceptionUtils.withD8CompilationHandler(options.reporter, () -> runForTesting(app, options));
	}


	private static void run(AndroidApp inputApp, InternalOptions options, ExecutorService executor) throws IOException, CompilationException {
		try {
			options.skipMinification = true;
			options.inlineAccessors = false;
			options.outline.enabled = false;
			//
			Timing timing = new Timing("DX timer");
			//实际执行 ApplicationReader.read(null(StringResource), executor(ExecutorService), ProgramClassCollection::resolveClassConflictImpl(ProgramClassConflictResolver))
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

}


com.android.tools.r8.D8Command {


	private static Builder parse(String[] args, Origin origin, Builder builder) {
		//编译模式 Debug  或者 Release
		CompilationMode compilationMode = null;
		Path outputPath = null;
		OutputMode outputMode = null;
		boolean hasDefinedApiLevel = false;
		try {
			//解析命令行参数
			for (int i = 0; i < args.length; i++) {
				String arg = args[i].trim();
				if (arg.length() != 0){
					if (arg.equals("--help")) {
						builder.setPrintHelp(true);
					} else if (arg.equals("--version")) {
						builder.setPrintVersion(true);
					} else if (arg.equals("--debug")) {
						if (compilationMode == CompilationMode.RELEASE) {
							builder.getReporter().error((Diagnostic)new StringDiagnostic("Cannot compile in both --debug and --release mode.", origin));
						} else {
							compilationMode = CompilationMode.DEBUG;
						} 
					} else if (arg.equals("--release")) {
						if (compilationMode == CompilationMode.DEBUG) {
							builder.getReporter().error((Diagnostic)new StringDiagnostic("Cannot compile in both --debug and --release mode.", origin));
						} else {
							compilationMode = CompilationMode.RELEASE;
						} 
					} else if (arg.equals("--file-per-class")) {
						outputMode = OutputMode.DexFilePerClassFile;
					} else if (arg.equals("--output")) {
						String output = args[++i];
						if (outputPath != null) {
							builder.getReporter().error((Diagnostic)new StringDiagnostic("Cannot output both to '" + outputPath
								.toString() + "' and '" + output + "'", origin));
						} else {
							outputPath = Paths.get(output, new String[0]);
						} 
					} else if (arg.equals("--lib")) {
						builder.addLibraryFiles(new Path[] { Paths.get(args[++i], new String[0]) });
					} else if (arg.equals("--classpath")) {
						builder.addClasspathFiles(new Path[] { Paths.get(args[++i], new String[0]) });
					} else if (arg.equals("--main-dex-list")) {
						builder.addMainDexListFiles(new Path[] { Paths.get(args[++i], new String[0]) });
					} else if (arg.equals("--min-api")) {
						hasDefinedApiLevel = parseMinApi(builder, args[++i], hasDefinedApiLevel, origin);
					} else if (arg.equals("--intermediate")) {
						builder.setIntermediate(true);
					} else if (arg.equals("--no-desugaring")) {
						builder.setDisableDesugaring(true);
					} else if (arg.startsWith("--")) {
						builder.getReporter().error((Diagnostic)new StringDiagnostic("Unknown option: " + arg, origin));
					} else {
						builder.addProgramFiles(new Path[] { Paths.get(arg, new String[0]) });
					}  
				} 
				if (compilationMode != null)
					builder.setMode(compilationMode); 
				if (outputMode == null)
					outputMode = OutputMode.DexIndexed; 
				if (outputPath == null)
					outputPath = Paths.get(".", new String[0]); 
				return builder.setOutput(outputPath, outputMode);
			} catch (CompilationError e) {
				throw builder.getReporter().fatalError(e);
			} 
		}
	}

	InternalOptions getInternalOptions() {
		InternalOptions internal = new InternalOptions(new DexItemFactory(), getReporter());
		assert !internal.debug;
		internal.debug = (getMode() == CompilationMode.DEBUG);
		internal.programConsumer = getProgramConsumer();
		internal.minimalMainDex = internal.debug;
		internal.minApiLevel = getMinApiLevel();
		internal.intermediate = this.intermediate;
		assert !internal.skipMinification;
		internal.skipMinification = true;
		assert internal.useTreeShaking;
		internal.useTreeShaking = false;
		assert internal.inlineAccessors;
		internal.inlineAccessors = false;
		assert internal.removeSwitchMaps;
		internal.removeSwitchMaps = false;
		assert internal.outline.enabled;
		internal.outline.enabled = false;
		assert internal.propagateMemberValue;
		internal.propagateMemberValue = false;
		internal.enableDesugaring = getEnableDesugaring();
		return internal;
	}
}


com.android.tools.r8.dex.ApplicationReader {
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
}



报错 Cannot fit requested classes in the main-dex file (# methods:87463 > 65536 ; # fields: 74531 > 65536) {

	VirtualFile {

		VirtualFile#DistributorBase {
			protected void fillForMainDexList(Set<DexProgramClass> classes) throws DexOverflowException {
				if (!this.application.mainDexList.isEmpty()) {
					VirtualFile mainDexFile = this.virtualFiles.get(0);
					for (UnmodifiableIterator<DexType> unmodifiableIterator = this.application.mainDexList.iterator(); unmodifiableIterator.hasNext(); ) {
						DexType type = unmodifiableIterator.next();
						DexClass clazz = this.application.definitionFor(type);
						if (clazz != null && clazz.isProgramClass()) {
							DexProgramClass programClass = (DexProgramClass)clazz;
							mainDexFile.addClass(programClass);
							classes.remove(programClass);
						} else {
							this.options.reporter.warning((Diagnostic)new StringDiagnostic("Application does not contain `" + type

								.toSourceString() + "` as referenced in main-dex-list."));
						} 
						mainDexFile.commitTransaction();
					} 
					mainDexFile.throwIfFull(true);
				} 
			}
		}
		

		VirtualFile#PackageSplitPopulator {

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
						String originalName = getOriginalName(clazz);
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
						//判断方法索引数是否超过 65535，
						//isFilledEnough 调用了 isFull(int numberOfMethods, int numberOfFields, int maxinum)，maxinum 为固定值
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

			private void addNonPackageClasses(VirtualFile.VirtualFileCycler cycler, List<DexProgramClass> nonPackageClasses) {
				cycler.restart();
				VirtualFile current = cycler.next();
				for (DexProgramClass clazz : nonPackageClasses) {
					if (current.isFilledEnough(this.fillStrategy))
						current = getVirtualFile(cycler); 
					current.addClass(clazz);
					while (current.isFull()) {
						current.abortTransaction();
						current = getVirtualFile(cycler);
						boolean wasEmpty = current.isEmpty();
						current.addClass(clazz);
						if (wasEmpty && current.isFull())
							throw new InternalCompilerError("Class " + clazz
								.toString() + " does not fit into a single dex file."); 
					} 
					current.commitTransaction();
				} 
			}
		}

		VirtualFile {
			void throwIfFull(boolean hasMainDexList) throws DexOverflowException {
				if (!isFull())
					return; 
				throw new DexOverflowException(hasMainDexList, this.transaction
					.getNumberOfMethods(), this.transaction
					.getNumberOfFields(), 65536L);
			}


			private static boolean isFull(int numberOfMethods, int numberOfFields, int maximum) {
				return (numberOfMethods > maximum || numberOfFields > maximum);
			}


			private boolean isFull() {
				return isFull(this.transaction.getNumberOfMethods(), this.transaction.getNumberOfFields(), 65536);
			}
		}
	}

	


	//当方法数超过 65535 时，会抛出 DexOVerflowException，指示方法数超过指定阈值
	//com.android.tools.r8.errors.DexOverflowException
	private String getNumberRelatedMessage() {
		//maxNumOfEntries 在 DexOverflowException 构造函数中初始化
		StringBuilder messageBuilder = new StringBuilder();
		if (this.numOfMethods > this.maxNumOfEntries) {
			messageBuilder.append("# methods: ");
			messageBuilder.append(this.numOfMethods);
			messageBuilder.append(" > ").append(this.maxNumOfEntries);
			if (this.numOfFields > this.maxNumOfEntries)
				messageBuilder.append(" ; "); 
		} 
		if (this.numOfFields > this.maxNumOfEntries) {
			messageBuilder.append("# fields: ");
			messageBuilder.append(this.numOfFields);
			messageBuilder.append(" > ").append(this.maxNumOfEntries);
		} 
		return messageBuilder.toString();
	}
}

Android MultiDex {
	构建 Task {
		createAndroidTasks() {
			D8MainDexListTransform multiDexTransform = new D8MainDexListTransform(variantScope);
			//multiDexTransform
			//第四个参数为预配置的任务，用于生成 mainDexList.txt
			transformManager.addTransform(taskFactory, variantScope, multiDexTransform,
				taskName -> {
					File mainDexListFile = variantScope
					.getArtifacts()
					.appendArtifact(
						InternalArtifactType.LEGACY_MULTIDEX_MAIN_DEX_LIST,
						taskName,
						"mainDexList.txt");
					multiDexTransform.setMainDexListOutputFile(mainDexListFile);
				}, null, variantScope::addColdSwapBuildTask);
		}


		com.android.build.gradle.internal.transforms.D8MainDexListTransform {
			构造参数 {
				//manifest_keep，multiDexKeepProgurad 和 multiDexKeepFile 文件最终会决定哪些 class 会被打包到 classex.dex 中
				class D8MainDexListTransform(
					//aapt 混淆规则，编译时产生在 build/intermediates/legacy_multidex_appt_derived_proguard_rules 目录下的 manifest_keep.txt
					private val manifestProguardRules: BuildableArtifact,
					//项目 multiDexKeepProguard 申明的 keep 规则
			        private val userProguardRules: Path? = null,
			        //项目 multiDexKeepFile 申明的 keep class
			        private val userClasses: Path? = null,
			        private val includeDynamicFeatures: Boolean = false,
			        private val bootClasspath: Supplier<List<Path>>,
			        private val messageReceiver: MessageReceiver) : Transform(), MainDexListWriter{}
			}

			//生成 MainClasses.dex 文件
			override fun transform(invocation: TransformInvocation) {
		        logger.verbose("Generating the main dex list using D8.")
		        try {
		            val inputs = getByInputType(invocation)
		            val programFiles = inputs[ProguardInput.INPUT_JAR]!!
		            val libraryFiles = inputs[ProguardInput.LIBRARY_JAR]!! + bootClasspath.get()
		            logger.verbose("Program files: %s", programFiles.joinToString())
		            logger.verbose("Library files: %s", libraryFiles.joinToString())
		            logger.verbose( 
		                    "Proguard rule files: %s",
		                    listOfNotNull(manifestProguardRules, userProguardRules).joinToString())
		            //获取到 multiDexKeepProguard keep 规则
		            val proguardRules = listOfNotNull(manifestProguardRules.singleFile().toPath(), userProguardRules)
		            val mainDexClasses = mutableSetOf<String>()
		            //生成所有需要 keep 在 classes.dex 中的 class 集合， getPlatformRules 获取一些强制的规则
		            mainDexClasses.addAll(
		                D8MainDexList.generate(
		                    getPlatformRules(),
		                    proguardRules,
		                    programFiles,
		                    libraryFiles,
		                    messageReceiver
		                )
		            )
		            if (userClasses != null) {
		                mainDexClasses.addAll(Files.readAllLines(userClasses))
		            }
		            Files.deleteIfExists(outputMainDexList)
		            Files.write(outputMainDexList, mainDexClasses)
		        } catch (e: D8MainDexList.MainDexListException) {
		            throw TransformException("Error while generating the main dex list:${System.lineSeparator()}${e.message}", e)
		        }
		    }
		}


		com.android.builder.multiDex.D8MainDexList.java {
			@NonNull
		    public static List<String> generate( @NonNull List<String> mainDexRules, @NonNull Collection<Path> programFiles, @NonNull Collection<Path> libraryFiles, @NonNull MessageReceiver messageReceiver) throws MainDexListException {

		        D8DiagnosticsHandler d8DiagnosticsHandler = new InterceptingDiagnosticsHandler(messageReceiver);
		        try {
		            GenerateMainDexListCommand.Builder command =
		                    GenerateMainDexListCommand.builder(d8DiagnosticsHandler)
		                            .addMainDexRules(mainDexRules, Origin.unknown())
		                            .addMainDexRulesFiles(mainDexRulesFiles)
		                            .addLibraryFiles(libraryFiles);
		            for (Path program : programFiles) {
		                if (Files.isRegularFile(program)) {
		                    command.addProgramFiles(program);
		                } else {
		                    try (Stream<Path> classFiles = Files.walk(program)) {
		                        List<Path> allClasses = classFiles
		                                .filter(p -> p.toString().endsWith(SdkConstants.DOT_CLASS))
		                                .collect(Collectors.toList());
		                        command.addProgramFiles(allClasses);
		                    }
		                }
		            }
		            return ImmutableList.copyOf(
		                    GenerateMainDexList.run(command.build(), ForkJoinPool.commonPool()));
		        } catch (Exception e) {
		            throw getExceptionToRethrow(e, d8DiagnosticsHandler);
		        }
		    }
		}
	}
}