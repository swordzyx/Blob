
com.android.dx.command.Main.java
//Main 类主要解析通过 dx 命令传进来的参数
public static void main(String[] args) {
    boolean gotCmd = false;
    boolean showUsage = false;
    try {
      	for (int i = 0; i < args.length; i++) {
        	String arg = args[i];
        	.....
        	gotCmd = true;
        	//主要看 --dex 参数
        	if (arg.equals("--dex")) {
        		//会调用 com.android.dx.command.dexer.Main 中的 main 方法。
          		com.android.dx.command.dexer.Main.main(without(args, i));
          		break;
        	}
        	.....
        	gotCmd = false;
      	}
    } catch (UsageException ex) {
      	showUsage = true;
    } catch (RuntimeException ex) {
      	System.err.println("\nUNEXPECTED TOP-LEVEL EXCEPTION:");
      	ex.printStackTrace();
      	System.exit(2);
    } catch (Throwable ex) {
      	System.err.println("\nUNEXPECTED TOP-LEVEL ERROR:");
      	ex.printStackTrace();
      	if (((ex instanceof NoClassDefFoundError)) || ((ex instanceof NoSuchMethodError))) {
        	System.err.println("Note: You may be using an incompatible virtual machine or class library.\n(This program is known to be incompatible with recent releases of GCJ.)");
      	}
      	System.exit(3);
    } 

    ......
}


com.android.dx.command.dexer.Main.java {
	public static void main(String[] argArray) throws IOException {
	    DxContext context = new DxContext();
	    //解析参数，封装在 Arguments 中
	    Arguments arguments = new Arguments(context);
	    //解析 dx 命令里面的参数，“--set-max-idx-number” 参数也是在该方法里面被解析，指定 dex 文件最大的 id 数
	    arguments.parse(argArray);
	    
	    int result = new Main(context).runDx(arguments);
	    if (result != 0) {
			System.exit(result);
	    }
	}

	public int runDx(Arguments arguments) throws IOException {
		this.errors.set(0);


		this.libraryDexBuffers.clear();

		this.args = arguments;
		this.args.makeOptionsObjects();	
		......
		try {
			int i;
			//args 就是 Argument 类型的实例，multiDex 对应的命令行参数是 “--multi-dex”，配置是否分为多个 dex 文件，这里看为 true 的情况
			//即，如果 “--multi-dex” 传进来为 true，执行 runMultiDex，从方法名可看出就是执行分包
			//如果 --multi-dex 为 false ，则执行 runMonoDex ，将所有的代码打包到一个 Dex 文件中
			if (this.args.multiDex) {
				return runMultiDex();
			}
			return runMonoDex();
		}
		finally {
			closeOutput(humanOutRaw);
		}
	}

	private int runMultiDex() throws IOException {
		assert (!this.args.incremental);
		//获取主 dex 文件中应该包含
		if (this.args.mainDexListFile != null)
		{
			this.classesInMainDex = new HashSet();
			readPathsFromFile(this.args.mainDexListFile, this.classesInMainDex);
		}
		this.dexOutPool = Executors.newFixedThreadPool(this.args.numThreads);
		if (!processAllFiles()) {
			return 1;
		}
		if (!this.libraryDexBuffers.isEmpty()) {
			throw new DexException("Library dex files are not supported in multi-dex mode");
		}
		if (this.outputDex != null)
		{
			this.dexOutputFutures.add(this.dexOutPool.submit(new DexWriter(this.outputDex, null)));


			this.outputDex = null;
		}
		try
		{
			this.dexOutPool.shutdown();
			if (!this.dexOutPool.awaitTermination(600L, TimeUnit.SECONDS)) {
				throw new RuntimeException("Timed out waiting for dex writer threads.");
			}
			for (Future<byte[]> f : this.dexOutputFutures) {
				this.dexOutputArrays.add(f.get());
			}
		}
		catch (InterruptedException ex)
		{
			this.dexOutPool.shutdownNow();
			throw new RuntimeException("A dex writer thread has been interrupted.");
		}
		catch (Exception e)
		{
			this.dexOutPool.shutdownNow();
			throw new RuntimeException("Unexpected exception in dex writer thread");
		}
		if (this.args.jarOutput)
		{
			for (int i = 0; i < this.dexOutputArrays.size(); i++) {
				this.outputResources.put(getDexFileName(i), this.dexOutputArrays
					.get(i));
			}
			if (!createJar(this.args.outName)) {
				return 3;
			}
		}
		else if (this.args.outName != null)
		{
			File outDir = new File(this.args.outName);
			assert (outDir.isDirectory());
			for (int i = 0; i < this.dexOutputArrays.size(); i++)
			{
				OutputStream out = new FileOutputStream(new File(outDir, getDexFileName(i)));
				try
				{
					out.write((byte[])this.dexOutputArrays.get(i));
				}
				finally
				{
					closeOutput(out);
				}
			}
		}
		return 0;
	}
}


com.android.dx.command.dexer.Main$Arguements {
	Arguments{
		......
		//输出文件是否为 .zip .apk .jar
		public boolean jarOutput = false;
		//如果 dex 中 String 引用超过了 65535，forceJumbo 需要为 true，才能引用到所有的 String
		public boolean forceJumbo = false;
		//--main-dex-list 参数值
		public String mainDexListFile = null;
		//--main-main-dex 参数值，指示主 Dex 文件中是否仅包含 --main-dex-list 参数中指定的类
		public boolean minimalMainDex = false;
		//--multi-dex 参数值。是否分为多个 dex 文件
		public boolean multiDex = false;
		......
		//Dex 文件中的引用 ID 上限，通过 --set-max-idx-number 可以修改该属性值
		public int maxNumberOfIdxPerDex = 65536;
		......


		private void parseFlags(ArgumentsParser parser) {
			......

			else if (parser.isArg("--set-max-idx-number="))
			{
				this.maxNumberOfIdxPerDex = Integer.parseInt(parser.getLastValue());
			}
			.....
		}

	}

	Arguments 类方法 {

		private void parse(String[] args) {
			ArgumentsParser parser = new ArgumentsParser(args);

			parseFlags(parser);

			this.fileNames = parser.getRemaining();
			if ((this.inputList != null) && (!this.inputList.isEmpty()))
			{
				this.inputList.addAll(Arrays.asList(this.fileNames));
				this.fileNames = ((String[])this.inputList.toArray(new String[this.inputList.size()]));
			}
			if (this.fileNames.length == 0)
			{
				if (!this.emptyOk)
				{
					this.context.err.println("no input files specified");
					throw new UsageException();
				}
			}
			else if (this.emptyOk) {
				this.context.out.println("ignoring input files");
			}
			if ((this.humanOutName == null) && (this.methodToDump != null)) {
				this.humanOutName = "-";
			}
			if ((this.mainDexListFile != null) && (!this.multiDex))
			{
				this.context.err.println("--main-dex-list is only supported in combination with --multi-dex");

				throw new UsageException();
			}
			if ((this.minimalMainDex) && ((this.mainDexListFile == null) || (!this.multiDex)))
			{
				this.context.err.println("--minimal-main-dex is only supported in combination with --multi-dex and --main-dex-list");

				throw new UsageException();
			}
			if ((this.multiDex) && (this.incremental))
			{
				this.context.err.println("--incremental is not supported with --multi-dex");

				throw new UsageException();
			}
			if ((this.multiDex) && (this.outputIsDirectDex))
			{
				this.context.err.println("Unsupported output \"" + this.outName + "\". " + "--multi-dex" + " supports only archive or directory output");

				throw new UsageException();
			}
			if ((this.outputIsDirectory) && (!this.multiDex)) {
				this.outName = new File(this.outName, "classes.dex").getPath();
			}
			makeOptionsObjects();
		}


		private void parseFlags(ArgumentsParser parser) {
			while (parser.getNext()) {
				if (parser.isArg("--debug"))
				{
					this.debug = true;
				}
				else if (parser.isArg("--no-warning"))
				{
					this.warnings = false;
				}
				else if (parser.isArg("--verbose"))
				{
					this.verbose = true;
				}
				else if (parser.isArg("--verbose-dump"))
				{
					this.verboseDump = true;
				}
				else if (parser.isArg("--no-files"))
				{
					this.emptyOk = true;
				}
				else if (parser.isArg("--no-optimize"))
				{
					this.optimize = false;
				}
				else if (parser.isArg("--no-strict"))
				{
					this.strictNameCheck = false;
				}
				else if (parser.isArg("--core-library"))
				{
					this.coreLibrary = true;
				}
				else if (parser.isArg("--statistics"))
				{
					this.statistics = true;
				}
				else if (parser.isArg("--optimize-list="))
				{
					if (this.dontOptimizeListFile != null)
					{
						this.context.err.println("--optimize-list and --no-optimize-list are incompatible.");

						throw new UsageException();
					}
					this.optimize = true;
					this.optimizeListFile = parser.getLastValue();
				}
				else if (parser.isArg("--no-optimize-list="))
				{
					if (this.dontOptimizeListFile != null)
					{
						this.context.err.println("--optimize-list and --no-optimize-list are incompatible.");

						throw new UsageException();
					}
					this.optimize = true;
					this.dontOptimizeListFile = parser.getLastValue();
				}
				else if (parser.isArg("--keep-classes"))
				{
					this.keepClassesInJar = true;
				}
				else if (parser.isArg("--output="))
				{
					this.outName = parser.getLastValue();
					if (new File(this.outName).isDirectory())
					{
						this.jarOutput = false;
						this.outputIsDirectory = true;
					}
					else if (FileUtils.hasArchiveSuffix(this.outName))
					{
						this.jarOutput = true;
					}
					else if ((this.outName.endsWith(".dex")) || 
						(this.outName.equals("-")))
					{
						this.jarOutput = false;
						this.outputIsDirectDex = true;
					}
					else
					{
						this.context.err.println("unknown output extension: " + this.outName);

						throw new UsageException();
					}
				}
				else if (parser.isArg("--dump-to="))
				{
					this.humanOutName = parser.getLastValue();
				}
				else if (parser.isArg("--dump-width="))
				{
					this.dumpWidth = Integer.parseInt(parser.getLastValue());
				}
				else if (parser.isArg("--dump-method="))
				{
					this.methodToDump = parser.getLastValue();
					this.jarOutput = false;
				}
				else if (parser.isArg("--positions="))
				{
					String pstr = parser.getLastValue().intern();
					if (pstr == "none")
					{
						this.positionInfo = 1;
					}
					else if (pstr == "important")
					{
						this.positionInfo = 3;
					}
					else if (pstr == "lines")
					{
						this.positionInfo = 2;
					}
					else
					{
						this.context.err.println("unknown positions option: " + pstr);

						throw new UsageException();
					}
				}
				else if (parser.isArg("--no-locals"))
				{
					this.localInfo = false;
				}
				else if (parser.isArg("--num-threads="))
				{
					this.numThreads = Integer.parseInt(parser.getLastValue());
				}
				else if (parser.isArg("--incremental"))
				{
					this.incremental = true;
				}
				else if (parser.isArg("--force-jumbo"))
				{
					this.forceJumbo = true;
				}
				else if (parser.isArg("--multi-dex"))
				{
					this.multiDex = true;
				}
				else if (parser.isArg("--main-dex-list="))
				{
					this.mainDexListFile = parser.getLastValue();
				}
				else if (parser.isArg("--minimal-main-dex"))
				{
					this.minimalMainDex = true;
				}
				else if (parser.isArg("--set-max-idx-number="))
				{
					this.maxNumberOfIdxPerDex = Integer.parseInt(parser.getLastValue());
				}
				else if (parser.isArg("--input-list="))
				{
					File inputListFile = new File(parser.getLastValue());
					try
					{
						this.inputList = new ArrayList();
						Main.readPathsFromFile(inputListFile.getAbsolutePath(), this.inputList);
					}
					catch (IOException e)
					{
						this.context.err.println("Unable to read input list file: " + inputListFile
							.getName());

						throw new UsageException();
					}
				}
				else if (parser.isArg("--min-sdk-version="))
				{
					String arg = parser.getLastValue();
					int value;
					try
					{
						value = Integer.parseInt(arg);
					}
					catch (NumberFormatException ex)
					{
						int value;
						value = -1;
					}
					if (value < 1)
					{
						System.err.println("improper min-sdk-version option: " + arg);
						throw new UsageException();
					}
					this.minSdkVersion = value;
				}
				else if (parser.isArg("--allow-all-interface-method-invokes"))
				{
					this.allowAllInterfaceMethodInvokes = true;
				}
				else
				{
					this.context.err.println("unknown option: " + parser.getCurrent());
					throw new UsageException();
				}
			}
		}
	}
}







