Retrofit Sample {
  MainActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl("https://api.github.com/")
                .build();

        RetrofitService retrofitService = retrofit.create(RetrofitService.class);

        Call<List<Repo>> call = retrofitService.getRepos("zerolans");

        call.enqueue(mCallback);
    }

    Callback mCallback = new Callback() {
        @Override
        public void onResponse(Call call, Response response) {
            System.out.println("onResponse");
            System.out.println("result: " + response.toString());
        }

        @Override
        public void onFailure(Call call, Throwable t) {

        }
    };
  }

  public interface RetrofitService {
      @GET("/users/{user}/repos")
      Call<List<Repo>> getRepos(@Path("user") String user);
  }
}




Retrofit.build {
  //Retrofit.java
  public Retrofit build() {
    if (baseUrl == null) {
      throw new IllegalStateException("Base URL required.");
    }

    //保存 CallFactory，可以通过 Builder.callFactory() 进行赋值，也就是说这是可以自定义的。
    okhttp3.Call.Factory callFactory = this.callFactory;
    if (callFactory == null) {
      callFactory = new OkHttpClient();
    }

    //保存 callbackExecutor，也可以通过 Builder.callbackExecutor 进行赋值，也是可以由开发者自定义的。
    Executor callbackExecutor = this.callbackExecutor;
    if (callbackExecutor == null) {
      //defauCallbackExecutor 在 Android 端返回的是 MainThreadExecutor ，位于 Platform.java
      //猜测是指定当收到响应时，在哪个线程中进行处理，默认情况下就是主线程了。
      callbackExecutor = platform.defaultCallbackExecutor();
    }

    // 制作 callAdapterFactories 的副本，并添加默认的 CallAdapterFactory，类型为 DefaultCallAdapterFactory
    List<CallAdapter.Factory> callAdapterFactories = new ArrayList<>(this.callAdapterFactories);
    callAdapterFactories.addAll(platform.defaultCallAdapterFactories(callbackExecutor));

    //制做一个 ConverterFactories 的副本
    //converterFactories 初始大小为 0
    //defaultConverterFactoriesSize 在 Android 平台下返回的是 0，而 java 平台下返回的是 1
    List<Converter.Factory> converterFactories =
        new ArrayList<>(
            1 + this.converterFactories.size() + platform.defaultConverterFactoriesSize());

    //首先添加一个内置的 BuiltInConverters，这是一个转换器，converterFactories 初始为空
    //defaultConverterFactories 在 java 平台下会返回一个只包含一个 OptionalConverterFactory 对象的不可变列表列表，即
    converterFactories.add(new BuiltInConverters());
    converterFactories.addAll(this.converterFactories);
    converterFactories.addAll(platform.defaultConverterFactories());

    //这里就是将上面获取的实例都传进 Retrofit 的构造函数中，进行保存
    return new Retrofit(
        callFactory,
        baseUrl,
        unmodifiableList(converterFactories),
        unmodifiableList(callAdapterFactories),
        callbackExecutor,
        validateEagerly);
  }
}

Retrofit 中的动态代理的实现原理是什么？它代理的是我们创建的接口，会为我们的接口创建一个实现类，每当到调用接口中的方法是，就会通过一个回调接口来反射调用接口中的具体实现方法。

//这里返回的是一个代理类，只调用了 Proxy.newProxyInstance 方法，这个函数是 Java 的反射类 java.lang.reflect.Proxy 提供的。官方介绍：https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/reflect/Proxy.html#newProxyInstance(java.lang.ClassLoader,java.lang.Class%5B%5D,java.lang.reflect.InvocationHandler)
//该函数的作用就是返回一个指定接口的代理对象，并且会将对于接口中方法的调用分发到指定的 InvocationHandler 中
//也就是说每次通过代理对象调用接口中的方法，其实就会调用 InvocationHandler 中的 invoke 方法，参数 Method 其实就是要调用的接口函数的方法实例
Retrofit.create {
  public <T> T create(final Class<T> service) {

      /* validateServiceInterface 函数作用：
        service 就是调用 Retrofit.create(...) 时传进来的 RetrofitService.class，对 RetrofitService 接口进行校验
          1. 检查 RetrofitService 是否是一个接口，如果不是接口，就抛异常 
          2. 检查 RetrofitService 是否是泛型接口，如果是泛型接口，抛异常
          3. 检查 RetrofitService 是否直接或间接实现了泛型接口，如果是，则抛异常。
          4. 如果开启了积极验证，加载 RetrofitService 接口中的所有的方法，检查接口方法是否正确
      */
      validateServiceInterface(service);
      //动态代理分两个词，一个是动态，一个是代理。代理就是创建一个类，然后这个类会生成一个对象，这个类实现了一些指定的接口，然后这个类就会代理这些接口的实现，这个对象就是那个实际的代理，它代理了那些指定的方法。动态代理指的是这个类是在运行时生成的，不是在编译时生成的。
      //Proxy.newProxyInstance 就是在运行是创建类。
      //这里返回的是我们创建的服务接口的实例类，这是 Retrofit 的核心代码
      return (T) Proxy.newProxyInstance(
          service.getClassLoader(), //获取类加载器，这是没有任何特殊之处
          new Class<?>[] {service}, //为什么要放到一个数组中？其实这里是可以接受多个接口的，但是 Retrofit 只提供了一个接口
          new InvocationHandler() { //创建一个 InvocationHandler，这实际上是一个回调
              private final Platform platform = Platform.get(); //获取当前的平台，Android 还是 Java，Retrofit 对于不同的平台会有不同的行为。
              private final Object[] emptyArgs = new Object[0]; 

              //invoke 函数时反射进行方法调用的时候会用到的。猜测肯定是在 newProxyInstance 方法中会使用到的
              //这里猜测
              @Override
              public @Nullable Object invoke(Object proxy, Method method, @Nullable Object[] args) throws Throwable {
                // 检查当前调用的方法是否是 Object 中的方法，如果是，则直接调用这个对象中相应的方法。
                // getDeclaringClass 用于查看在哪个类中声明的这个方法
                if (method.getDeclaringClass() == Object.class) {
                    return method.invoke(this, args);
                }

                //到这里就表明要反射的方法是接口中的方法。
                args = args != null ? args : emptyArgs;
                //isDefaultMethod 用于判断当前方法是否默认实现的 Java 方法（Java 平台），这是 Java 8 新加的一个特性，Java 8 之后可以在接口中实现方法，之前是只能在实现类中实现
                //回调用 loadServiceMethod 方法，这个方法返回一个 ， HttpServiceMethod 类型。
                //所以调用的是 HttpServiceMethod.invoke 方法。
                return platform.isDefaultMethod(method)
                    ? platform.invokeDefaultMethod(method, service, proxy, args)
                    : loadServiceMethod(method).invoke(args);//这里调用的是 ServiceMethod.invoke 
              }
          });
  }

  /*
    service 就是调用 Retrofit.create(...) 时传进来的 RetrofitService.class，对 RetrofitService 接口进行校验
      1. 检查 RetrofitService 是否是一个接口，如果不是接口，就抛异常
      2. 检查 RetrofitService 是否是泛型接口，如果是泛型接口，抛异常
      3. 检查 RetrofitService 是否直接或间接实现了泛型接口，如果是，则抛异常。
      4. 如果开启了积极验证，加载 RetrofitService 接口中的所有的方法，检查接口方法是否正确
  */
  private void validateServiceInterface(Class<?> service) {
    //检查传入 Retrofit.create() 方法中的类型是不是一个接口，如果不是接口，就报一个异常。
    if (!service.isInterface()) {
      throw new IllegalArgumentException("API declarations must be interfaces.");
    }

    //Deque 是一个双向队列
    Deque<Class<?>> check = new ArrayDeque<>(1);
    check.add(service);
    //检查传给 Retrofit.create() 的 class 对象，及其实现的父接口（比如，如果 RetrofitService 实现了一个父接口 Serializable，那么首先会将 RetrofitService 添加到双向队列中，然后再将 RetrofitServce 的父接口 Serialiable 也添加到双向队列中，进行检查，如果 Serializable 还实现了父接口，则会继续检查 Serializable 的父接口。）
    while (!check.isEmpty()) {
      Class<?> candidate = check.removeFirst();
      //getTypeParameters 返回类的泛型类型（比如 ArrayList<String> 返回的就是 String），以当前示例为例，如果 RetrofitService 是一个泛型接口，就抛异常。即 RetrofitService 不能是一个泛型的接口，也不能继承泛型接口。
      if (candidate.getTypeParameters().length != 0) {
        StringBuilder message = new StringBuilder("Type parameters are unsupported on ").append(candidate.getName());
        if (candidate != service) {
          message.append(" which is an interface of ").append(service.getName());
        }
        throw new IllegalArgumentException(message.toString());
      }
      //将 RetrofitService 继承的父接口也添加到双向队列中，进行检查。
      Collections.addAll(check, candidate.getInterfaces());
    }

    //用 Retrofit 来做 API 接口，我们写的 Service 接口类（例如 RetrofitService 接口）在调用的时候会有一些初始化操作，这些初始化只会在第一次调用接口 API 时候发生，这些初始化操作会对接口中的方法进行验证，验证接口方法写的是不是有问题。如果接口方法有问题，就会报错，然后应用就会崩溃。
    // Retrofit 提供了一个选项，提供了在调用 Retrofit.create 方法创建接口实例时（即 Retrofit 的初始化过程中）就对接口中的方法进行检查，这样可以尽快暴露接口中存在的问题，快速暴露的好处就是方便测试，方便开发人员去调试，一打开软件就能知道方法是否有问题，这对于开发很方便。
    //不过快速暴露是有代价的，会让性能比较差，每个方法的初始化过程会用到一点反射，这一点反射对于性能的影响不大，但如果所有的方法在软件刚刚打开时，一起做反射进行验证，这个耗时就会有些长，比如一个方法验证是 1ms，100 个方法同时做验证就是 100ms，这个时间就会比较长。所以 Retrofit 一般时在用到某个方法时，再去执行对这个方法的验证，这样就是将验证所需要的时间摊开了，用户就感觉不到这种卡顿。而在开发的时候则会希望做积极验证（validateEagerly 就是积极验证的意思），这样能够更快的暴露问题。
    //对接口中没有默认实现，且非静态的方法都加载一遍，加载完成之后，接口方法的初始化操作也就完成了，如果接口方法有问题，在 loadServiceMethod(method) 中就会被检查出来。
    if (validateEagerly) {
      Platform platform = Platform.get();
      //遍历接口中的所有方法。isDefaultMethod(method) 返回方法是否有默认实现，Modifier.isStatic() 返回方法是否为静态方法，Java 8 以前接口中的方法是不允许有默认实现的，且接口中也不允许有静态方法，而 Java 8 开始，接口中支持方法有默认实现，以及支持静态方法
      //Retrofit 则直接过滤掉了有默认实现和静态的方法，这样更快一些。
      for (Method method : service.getDeclaredMethods()) {
        if (!platform.isDefaultMethod(method) && !Modifier.isStatic(method.getModifiers())) {
          loadServiceMethod(method);
        }
      }
    }
  }
}

0. 返回 Call<List<Repo>>
/*retrofitService 就是 Retrofit.create 里面创建的接口的代理对象，调用其 getRepos() 时其实就会回调到 InvocationHandler.invoke() 函数，而这个函数主要是调用 loadServiceMethod 并返回其结果*/
retrofitService.getRepos() {

  0.1 
  /*retrofitService.getRepos = loadServiceMethod(getRepos).invoke，
  主要做了一下事情：
  （1）调用 RequestFactory.parseAnnotations 解析接口方法的注解，并将解析结果保存起来，返回的是一个 RequestFactory 对象，用于创建 okhttp3.Request
  （2）调用 createCallAdapter 创建 CallAdapter 并保存。返回的是一个新构造的 CallAdapter 对象，用于将 okhttp3 在 Retrofit 的代理类 OkHttpCall 转换成 ExecutorCallbackCall，这是 loadServiceMethod 返回的对象，实际开发中调用它的 enqueue 来发送网络请求。
  （3）调用 createResponseConverter 创建 ResponseConverter 并保存，这用于将 okhttp 中的 Response 对象转成我们需要的 Response 。
  （4）保存 Retrofit 中的 callFactory 对象并保存，这是一个 OkHttpClient 类型的实例，通过它创建 okhttp3 的 Call。
  （5）new 一个 SuspendForBody 对象并返回，之后调用的就是它的 invoke 方法 */
  loadServieMethod(getRepos) {

    0.1.1
    //这里面完成了 RequestFactory 的创建，然后剩下的操作就转到 HttpServiceMthod 中处理了
    ServiceMethod.loadServieMethod() {
      //ServiceMethod
      ServiceMethod<?> loadServiceMethod(Method method) {
        //所以我们所声明的接口中的方法其实是在解析完之后，保存到一个缓存区中，每当要加载新的方法时，先从这里面获取
        ServiceMethod<?> result = serviceMethodCache.get(method);
        if (result != null) return result;

        synchronized (serviceMethodCache) {
          result = serviceMethodCache.get(method);
          if (result == null) {
            //加载新的方法时，会解析该方法上面的注解，比如 @GET 等等。解析的结果保存起来，返回的是 ServiceMethod
            result = ServiceMethod.parseAnnotations(this, method);
            serviceMethodCache.put(method, result);
          }
        }
        return result;
      }

      //ServiceMethod.java
      //返回 ServiceMethod
      static <T> ServiceMethod<T> parseAnnotations(Retrofit retrofit, Method method) {
          //1. 这里主要是解析方法的注释，参数，以及参数中的注释并保存起来，主要是验证方法的合法性
          //调用 Builder.build() 方法
          RequestFactory requestFactory = RequestFactory.parseAnnotations(retrofit, method);

          //获取返回值类型
          Type returnType = method.getGenericReturnType();
          if (Utils.hasUnresolvableType(returnType)) {
            ..... //检查返回类型是否合法
          }
          if (returnType == void.class) {//定义的接口方法必须要有返回值，也就是用于发送 HTTP 请求的接口方法。
            throw methodError(method, "Service methods cannot return void.");
          }

          //确定方法的返回类型没有问题了，就开始解析方法的注解，例如 @GET 等
          return HttpServiceMethod.parseAnnotations(retrofit, method, requestFactory);
      }

      //HttpServiceMethod.parseAnnotations
      //猜测 ResponseT 和 ReturnT 分别为从 HTTP 获取的响应类型以及该方法实际返回的数据类型。
      static <ResponseT, ReturnT> HttpServiceMethod<ResponseT, ReturnT> parseAnnotations(Retrofit retrofit, Method method, RequestFactory requestFactory) {
          boolean isKotlinSuspendFunction = requestFactory.isKotlinSuspendFunction;
          boolean continuationWantsResponse = false;
          boolean continuationBodyNullable = false;

          Annotation[] annotations = method.getAnnotations();
          Type adapterType; //接口方法的返回类型
          if (isKotlinSuspendFunction) {
            //如果是 Kotlin 方法
          } else {
            //获取接口方法的返回的泛型类型，示例代码中的返回类型是 Call<List<Repo>>。
            adapterType = method.getGenericReturnType();
          }

          //2. 创建 CallAdapter
          CallAdapter<ResponseT, ReturnT> callAdapter = createCallAdapter(retrofit, method, adapterType, annotations);

          Type responseType = callAdapter.responseType();

          /*------主要检查请求的响应类型是否合法-------*/
          if (responseType == okhttp3.Response.class) {
            //这里回报一个错误，所以需要的响应类型是不能为 okhttp3.Response 类型的
          }

          if (responseType == Response.class) {
            throw methodError(method, "Response must include generic type (e.g., Response<String>)");
          }

          if (requestFactory.httpMethod.equals("HEAD") && !Void.class.equals(responseType)) {
            throw methodError(method, "HEAD method must use Void as response type.");
          }
          /*-------------------end-----------------------*/

          //3. 创建 Converter
          Converter<ResponseBody, ResponseT> responseConverter = createResponseConverter(retrofit, method, responseType);

          //4. 保存 Retrofit 中的 CallFactory
          okhttp3.Call.Factory callFactory = retrofit.callFactory;

          if (!isKotlinSuspendFunction) {
            return new CallAdapted<>(requestFactory, callFactory, responseConverter, callAdapter);
          } else if (continuationWantsResponse) {
            //这还是 kotlin ，不看这里
          } else {
            //5. 返回 SuspendForBody()
            //SuspendForBody 是 HttpServiceMethod 的子类，构造方法就是保存传进去的参数。
            return (HttpServiceMethod<ResponseT, ReturnT>)
                new SuspendForBody<>(
                    requestFactory,
                    callFactory,
                    responseConverter,
                    (CallAdapter<ResponseT, Call<ResponseT>>) callAdapter,
                    continuationBodyNullable);
          }
      }
    }

    0.1.2
    /*这里做的工作是解析我们定义的用于 HTTP 请求的接口的信息，包括返回类型，参数类型，参数注解，方法的注解，解析出来之后保存起来，稍后会用这些信息创建一个 okhttp3.Request，然后这个 Request 会被传到 OkHttpClient 中，用于创建 Call 实例。*/
    RequestFactory.parseAnnotations {
      //RequestFactory.java
      static RequestFactory parseAnnotations(Retrofit retrofit, Method method) {
        //Builder() 只是对传进去的 retrofit，method 参数做了保存，接着保存了方法的注解信息，方法参数的实际类型，以及参数的注解，这里的方法就是调用的接口方法 getRepos
        //并不明白参数化类型是什么意思。
        return new Builder(retrofit, method).build();
      }

      //主要是验证接口方法所配置的信息是否正确，然后将解析出来的方法信息通过保存到 RequestFactory 中，并将其返回
      RequestFactory build() {
        for (Annotation annotation : methodAnnotations) {
          //解析注解，主要是将其解析成对应的 HTTP 操作方法
          parseMethodAnnotation(annotation);
        }

        if (httpMethod == null) {
          throw methodError(method, "HTTP method annotation is required (e.g., @GET, @POST, etc.).");
        }

        //一些情况下请求必须要有请求体
        if (!hasBody) {
          if (isMultipart) {
            throw methodError(
                method,
                "Multipart can only be specified on HTTP methods with request body (e.g., @POST).");
          }
          if (isFormEncoded) {
            throw methodError(
                method,
                "FormUrlEncoded can only be specified on HTTP methods with "
                    + "request body (e.g., @POST).");
          }
        }

        //获取参数的数目
        int parameterCount = parameterAnnotationsArray.length;
        //解析参数以及参数上的注解
        parameterHandlers = new ParameterHandler<?>[parameterCount];
        for (int p = 0, lastParameter = parameterCount - 1; p < parameterCount; p++) {
          parameterHandlers[p] =
              parseParameter(p, parameterTypes[p], parameterAnnotationsArray[p], p == lastParameter);
        }

        if (relativeUrl == null && !gotUrl) {
          throw methodError(method, "Missing either @%s URL or @Url parameter.", httpMethod);
        }
        if (!isFormEncoded && !isMultipart && !hasBody && gotBody) {
          throw methodError(method, "Non-body HTTP method cannot contain @Body.");
        }
        if (isFormEncoded && !gotField) {
          throw methodError(method, "Form-encoded method must contain at least one @Field.");
        }
        if (isMultipart && !gotPart) {
          throw methodError(method, "Multipart method must contain at least one @Part.");
        }

        return new RequestFactory(this);
      }
    }

    0.1.3
    HttpServiceMethod.loadServiceMethod() {
      0.1.3.1
      /*
      1. 这里接收接口方法的返回值类型，还有在 Retrofit 初始化是保存的线程执行器（默认是 MainThreadExecutor）
      2. new 一个 CallAdapter 对象并将其返回，这里面重写了两个方法
        （1）responseType：这里返回需要的响应类型，是根据接口函数的返回值类型的到的，估计是要把从 HTTP 的响应转换成接口所需的类型
        （2）adapt：这里就是 new 了一个 ExecutorCallbackCall 对象并返回，猜测通过这个进行线程切换还有类型转换
      3. 所以这里主要是创建了一个 CallAdapter，这是用来将特定类型的 call 转换成 ExecutorCallbackCall 的，代码中是将 OkHttpCall 转换成 ExecutorCallbackCall，OkHttpCall 是在 Retrofit 中定义的一个类，它里面是对 okhttp3.call 的一个代理，所以 CallAdapter 其实就是一个适配器，我觉得也可以叫做转换器，将 okhttp 中的 Call 转成 Retrofit 中所需要的 Call ，并返回。
      */
      createCallAdapter {
        //HttpServiceMethod.java
        private static <ResponseT, ReturnT> CallAdapter<ResponseT, ReturnT> createCallAdapter(Retrofit retrofit, Method method, Type returnType, Annotation[] annotations) {
          try {
            //这里面只调用了 Retrofit 中的 nextCallAdapter 方法
            return (CallAdapter<ResponseT, ReturnT>) retrofit.callAdapter(returnType, annotations);
          } catch (RuntimeException e) { // Wide exception range because factories are user code.
            throw methodError(method, e, "Unable to create call adapter for %s", returnType);
          }
        }

        //Retrofit.java
        public CallAdapter<?, ?> callAdapter(Type returnType, Annotation[] annotations) {
          return nextCallAdapter(null, returnType, annotations);
        }

        //Retrofit.java
        public CallAdapter<?, ?> nextCallAdapter(@Nullable CallAdapter.Factory skipPast, Type returnType, Annotation[] annotations) {
          Objects.requireNonNull(returnType, "returnType == null");
          Objects.requireNonNull(annotations, "annotations == null");

          int start = callAdapterFactories.indexOf(skipPast) + 1;
          for (int i = start, count = callAdapterFactories.size(); i < count; i++) {
            //调用 DefaultCallAdapterFactory.get(returnType, annotations, this)
            CallAdapter<?, ?> adapter = callAdapterFactories.get(i).get(returnType, annotations, this);
            if (adapter != null) {
              return adapter;
            }
          }

          StringBuilder builder = new StringBuilder("Could not locate call adapter for ").append(returnType).append(".\n");

          if (skipPast != null) {
            builder.append("  Skipped:");
            for (int i = 0; i < start; i++) {
              builder.append("\n   * ").append(callAdapterFactories.get(i).getClass().getName());
            }
            builder.append('\n');
          }
          builder.append("  Tried:");
          for (int i = start, count = callAdapterFactories.size(); i < count; i++) {
            builder.append("\n   * ").append(callAdapterFactories.get(i).getClass().getName());
          }
          throw new IllegalArgumentException(builder.toString());
        }

        //DefaultCallAdapterFactory.java
        @Override
        public @Nullable CallAdapter<?, ?> get(Type returnType, Annotation[] annotations, Retrofit retrofit) {
          if (getRawType(returnType) != Call.class) {
            return null;
          }
          if (!(returnType instanceof ParameterizedType)) {
            throw new IllegalArgumentException(
                "Call return type must be parameterized as Call<Foo> or Call<? extends Foo>");
          }

          //根据接口方法所需要的返回类型，获取需要返回的请求响应类型
          //ParameterizedType 是类似 Collection<String> 这样的参数类型，也就是带有泛型的类型。
          //ParameterizedType 的官方介绍：https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/lang/reflect/ParameterizedType.html
          //那么按照这里的例子的话，返回的就是 Call<List<Repo>>。
          //getParameterUpperBound 返回的是 ParameterizedType 的实际类型，这里返回的就是 Call<List<Repo>>
          final Type responseType = Utils.getParameterUpperBound(0, (ParameterizedType) returnType);

          //如果接口方法的注解中有 SkipCallbackExecutor 这个注解，则不创建 Executor，目前是没有的，会返回一个 callbackExecutor，这是在 Retrofit.build 中初始化的
          //callbackExecutor 默认类型是 MainThreadExecutor，主线程执行器
          final Executor executor = Utils.isAnnotationPresent(annotations, SkipCallbackExecutor.class) ? null : callbackExecutor;

          return new CallAdapter<Object, Call<?>>() {
            @Override
            public Type responseType() {
              return responseType;
            }

            //executor 是不为空的，因此会返回一个 ExecutorCallbackCall，猜测在这个类中进行线程切换。
            @Override
            public Call<Object> adapt(Call<Object> call) {
              return executor == null ? call : new ExecutorCallbackCall<>(executor, call);
            }
          };
        }

        //delegate 是通过 adapt 方法传进来的。
        ExecutorCallbackCall(Executor callbackExecutor, Call<T> delegate) {
          this.callbackExecutor = callbackExecutor;
          this.delegate = delegate;
        }

        //Utils.java
        //ParameterizedType 官方定义是参数化类型，类似于 Collection<String> 这样子的，getActualTypeArguments 会返回参数化类型的实际类型，例如 Collection<String> 就会返回 String。
        static Type getParameterUpperBound(int index, ParameterizedType type) {
          Type[] types = type.getActualTypeArguments();
          if (index < 0 || index >= types.length) {
            throw new IllegalArgumentException(
                "Index " + index + " not in range [0," + types.length + ") for " + type);
          }
          Type paramType = types[index];
          //WildcardType 表示一个通配符类型的表达式，例如 ?、? extends Number 或 ? super Integer。
          if (paramType instanceof WildcardType) {
            return ((WildcardType) paramType).getUpperBounds()[0];
          }
          return paramType;
        } 
      }

      0.1.3.2
      /*这里主要创建一个 ResponseConverter ，但是具体返回的是哪个 Converter 我还不知道，不过它的作用就是将 OkHttp 中的 Response 转换成我们需要的 Response 类型。这其实就是一个转化器。*/
      createResponseConverter {
        //HttpServiceMethod.java
        //responseType 是从 CallAdapter 中获取的。自定义接口方法返回的都是 Call 实例，它是一个泛型类型，responseType 就是这个泛型类型的实际类型，在目前的示例中是 List<Repo> 
        private static <ResponseT> Converter<ResponseBody, ResponseT> createResponseConverter(Retrofit retrofit, Method method, Type responseType) {
          Annotation[] annotations = method.getAnnotations();
          try {
            return retrofit.responseBodyConverter(responseType, annotations);
          } catch (RuntimeException e) { // Wide exception range because factories are user code.
            throw methodError(method, e, "Unable to create converter for %s", responseType);
          }
        }

        //Retorfit.java
        public <T> Converter<ResponseBody, T> responseBodyConverter(Type type, Annotation[] annotations) {
          return nextResponseBodyConverter(null, type, annotations);
        }

        //Retrofit.java
        public <T> Converter<ResponseBody, T> nextResponseBodyConverter(@Nullable Converter.Factory skipPast, Type type, Annotation[] annotations) {
          Objects.requireNonNull(type, "type == null");
          Objects.requireNonNull(annotations, "annotations == null");

          int start = converterFactories.indexOf(skipPast) + 1;
          for (int i = start, count = converterFactories.size(); i < count; i++) {
            //这里会调用 BuiltInConverters 中的 responseBodyConverter
            Converter<ResponseBody, ?> converter = converterFactories.get(i).responseBodyConverter(type, annotations, this);
            if (converter != null) {
              return (Converter<ResponseBody, T>) converter;
            }
          }

          StringBuilder builder =
              new StringBuilder("Could not locate ResponseBody converter for ")
                  .append(type)
                  .append(".\n");
          if (skipPast != null) {
            builder.append("  Skipped:");
            for (int i = 0; i < start; i++) {
              builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
            }
            builder.append('\n');
          }
          builder.append("  Tried:");
          for (int i = start, count = converterFactories.size(); i < count; i++) {
            builder.append("\n   * ").append(converterFactories.get(i).getClass().getName());
          }
          throw new IllegalArgumentException(builder.toString());
        }

        //BuiltInConverters ，传进来的 type 是 List<String> 类型，所以是会返回 null ?
        @Override
        public @Nullable Converter<ResponseBody, ?> responseBodyConverter(Type type, Annotation[] annotations, Retrofit retrofit) {
          if (type == ResponseBody.class) {
            return Utils.isAnnotationPresent(annotations, Streaming.class)
                ? StreamingResponseBodyConverter.INSTANCE
                : BufferingResponseBodyConverter.INSTANCE;
          }
          if (type == Void.class) {
            return VoidResponseBodyConverter.INSTANCE;
          }
          //checkForKotlinUnit 默认为 true，
          if (checkForKotlinUnit) {
            try {
              //Unit 是 Kotlin 中的类型，类似于 Java 中的 Void 类型。
              if (type == Unit.class) {
                return UnitResponseBodyConverter.INSTANCE;
              }
            } catch (NoClassDefFoundError ignored) {
              checkForKotlinUnit = false;
            }
          }
          return null;
        }
      }

      0.1.3.3
      retrofit.callFactory ：默认情况下是 OkHttpClient，这个就是 OKhttp 里面的 OkHttpClient ，稍后会被用来创建 okhttp3.Call.
    }
    
  }

  0.2 
  /*
  当调用接口中的函数是，实际回调用 SuspendForBody 中 invoke 函数。 SuspendForBody 是 HttpServiceMethod 的实现类。这里面

  首先将 loadServiceMethod 中保存 RequestFactory，CallAdapter，ResponseConverter，callFactory 传入 OkHttpCall 的构造函数中，构造出一个 OkHttpCall 对象

  然后通过 callAdapter 将 OkHttpCall 适配成成一个 ExecutorCallbackCall 作为 retrofitService.getRepos() 调用的返回，ExecutorCallbackCall 在 OkHttpCall 的基础上使用 callbackExecutor 做了线程切换，默认是切换到主线程。

  这里主要就是将我们所定义的网络请求操作方法信息（包括请求类型，请求参数等）封装到 ExecutorCallbackCall 中，并返回，用于稍后的请求发送。
  */
  SuspendForBody.invoke {

    //HttpServiceMethod
    @Override
    final @Nullable ReturnT invoke(Object[] args) {
      //创建 OkHttpCall 实例对象，这里可以看出 Retrofit 是基于 OkHttp 的。
      Call<ResponseT> call = new OkHttpCall<>(requestFactory, args, callFactory, responseConverter);
      return adapt(call, args);
    }


    //SuspendForBody
    @Override
    protected Object adapt(Call<ResponseT> call, Object[] args) {

        //返回的是 ExecutorCallbackCall， ExecutorCallbackCall 继承自 Call<?> 
        call = callAdapter.adapt(call);

        //noinspection unchecked Checked by reflection inside RequestFactory.
        Continuation<ResponseT> continuation = (Continuation<ResponseT>) args[args.length - 1];

        try {
          //isNullable 是 loadServiceMethod 传进来的 continuationBodyNullable 参数，为false
          //因此回调用 KotExtensions.await 函数，这是 Kotlin 文件中的方法。看的不是很懂，目前看注释，我的理解是，这里返回的肯定是一个 Call 对象，通过 Kotlin 包装了一层，这样能够在发生异常的以后依然能够进行回调。但是为什么 Java 不行，我不知道。
          return isNullable
              ? KotlinExtensions.awaitNullable(call, continuation)
              : KotlinExtensions.await(call, continuation);
        } catch (Exception e) {
          return KotlinExtensions.suspendAndThrow(e, continuation);
        }
    }



    //这是一个扩展函数，对传过去的 Call<T> 接口做了扩展，不过不知道这个方法是干嘛用的。
    suspend fun <T : Any> Call<T>.await(): T {
      return suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation {
          cancel()
        }

        enqueue(object : Callback<T> {
          override fun onResponse(call: Call<T>, response: Response<T>) {
            if (response.isSuccessful) {
              val body = response.body()
              if (body == null) {
                val invocation = call.request().tag(Invocation::class.java)!!
                val method = invocation.method()
                val e = KotlinNullPointerException("Response from " +
                    method.declaringClass.name +
                    '.' +
                    method.name +
                    " was null but response body type was declared as non-null")
                continuation.resumeWithException(e)
              } else {
                continuation.resume(body)
              }
            } else {
              continuation.resumeWithException(HttpException(response))
            }
          }

          override fun onFailure(call: Call<T>, t: Throwable) {
            continuation.resumeWithException(t)
          }
        })
      }
    }
  }
}

1. 
/*这里了入口就是调用 ExecutorCallbackCall.enqueue() 函数，主要执行的工作就是调用 OkHttp3.Call.enqueue 发送网络请求，接着在接受到响应消息时，转换成需要的 Response<T> 类型之后，回调给 ExecutorCallbackCall ，这里面会通过 callbackExecutor 将线程切换到主线程，最终在主线程中处理响应消息*/
call.enqueue(mCallback){
  //在 ExecutorCallbackCall 中重写了 enqueue 方法，因此猜测 call.enqueue 最终会调用到 ExecutorCallbackCall 中
  @Override
  public void enqueue(final Callback<T> callback) {
    Objects.requireNonNull(callback, "callback == null");

    //delegate 是调用 SuspendForBody.invoke 时调用了 callAdapter.adapt() 方法传进去的，是一个 OkHttpCall 对象。这是 Retrofit 中的 OkHttpCall 对象
    //callbackExecutor 在 ExecutorCallbackCall 的构造方法中被保存。它是 MainThreadExecutor
    delegate.enqueue(
        new Callback<T>() {
          @Override
          public void onResponse(Call<T> call, final Response<T> response) {
            //在得到响应的消息时通过 MainThreadExecutor 切换到主线程。
            callbackExecutor.execute(
                () -> {
                  if (delegate.isCanceled()) {
                    // 调用 mCallback 中的方法
                    callback.onFailure(ExecutorCallbackCall.this, new IOException("Canceled"));
                  } else {
                    callback.onResponse(ExecutorCallbackCall.this, response);
                  }
                });
          }

          @Override
          public void onFailure(Call<T> call, final Throwable t) {
            callbackExecutor.execute(() -> callback.onFailure(ExecutorCallbackCall.this, t));
          }
        }); }

  //OkHttpCall.java
  @Override
  public void enqueue(final Callback<T> callback) {
    Objects.requireNonNull(callback, "callback == null");

    okhttp3.Call call;
    Throwable failure;

    synchronized (this) {
      if (executed) throw new IllegalStateException("Already executed.");
      executed = true;

      call = rawCall;
      failure = creationFailure;
      if (call == null && failure == null) {
        try {
          //通过 RequestFactory 将接口方法的信息封装成一个 Okhttp 的 Request
          //通过  callFactory（OkHttpClient）构造一个 Call ，传入 Request
          //这里返回的是 okhttp3.call
          call = rawCall = createRawCall();
        } catch (Throwable t) {
          throwIfFatal(t);
          failure = creationFailure = t;
        }
      }
    }

    if (failure != null) {
      callback.onFailure(this, failure);
      return;
    }

    if (canceled) {
      call.cancel();
    }

    //通过 OkHttp3.Call 发送请求 
    call.enqueue(
        //构造一个 OkHttp3 中的回调，里面在收到响应时会先解析，然后调用我们传进来的回调接口
        new okhttp3.Callback() {
          @Override
          public void onResponse(okhttp3.Call call, okhttp3.Response rawResponse) {
            Response<T> response;
            try {
              //猜测是将 okhttp 中的 Response 解析成 retrofit 需要的 Response 类型。
              response = parseResponse(rawResponse);
            } catch (Throwable e) {
              throwIfFatal(e);
              callFailure(e);
              return;
            }

            try {
              //callback 就是在实际代码中传进来的 Callback
              callback.onResponse(OkHttpCall.this, response);
            } catch (Throwable t) {
              throwIfFatal(t);
              t.printStackTrace(); // TODO this is not great
            }
          }

          @Override
          public void onFailure(okhttp3.Call call, IOException e) {
            callFailure(e);
          }

          private void callFailure(Throwable e) {
            try {
              callback.onFailure(OkHttpCall.this, e);
            } catch (Throwable t) {
              throwIfFatal(t);
              t.printStackTrace(); // TODO this is not great
            }
          }
        });
  }


  private okhttp3.Call createRawCall() throws IOException {
    //callFactory 在 初始化 Retrofit 时被赋值，是 OkHttpClient ，而这个 OkHttpClient 是 Retrofit 中的 OkHttpClient
    //requestFactory.create 返回的是一个 okhttp3.Request，从这里可以看出 RequestFactory 是先解析接口方法的信息，然后会将这些信息重新封装成一个 OkHttp 中的请求
    //所以这里做的实际操作就是使用 OkHttpClient 通过一个 Okhttp Request 构造一个 Call，这个是 OkHttp 中用于发送请求的。
    //所以这里返回的是 OkHttp 中的 Call。
    okhttp3.Call call = callFactory.newCall(requestFactory.create(args));
    if (call == null) {
      throw new NullPointerException("Call.Factory returned null.");
    }
    return call;
  }

  //解析响应消息体，将其转成我们所需要的 Response 类型。在这里就会转成 List<Repo> 类型，也就是说这里会返回 Response<List<Repo>> 类型
  Response<T> parseResponse(okhttp3.Response rawResponse) throws IOException {
    //获取响应消息体
    ResponseBody rawBody = rawResponse.body();

    // Remove the body's source (the only stateful object) so we can pass the response along.
    rawResponse =
        rawResponse
            .newBuilder()
            .body(new NoContentResponseBody(rawBody.contentType(), rawBody.contentLength()))
            .build();

    //获取响应码
    int code = rawResponse.code();
    if (code < 200 || code >= 300) {
      try {
        // Buffer the entire body to avoid future I/O.
        ResponseBody bufferedBody = Utils.buffer(rawBody);
        return Response.error(bufferedBody, rawResponse);
      } finally {
        rawBody.close();
      }
    }

    if (code == 204 || code == 205) {
      rawBody.close();
      return Response.success(null, rawResponse);
    }

    //这里是将 okhttp3 获取的响应消息题重新封装成了一个 ExceptionCatchingResponseBody，这里面就两个成员，一个是传进去的消息体（rawBody），另一个是一个 BufferedSource ，这里面有一个缓冲区，缓冲的是消息体的内容。
    ExceptionCatchingResponseBody catchingBody = new ExceptionCatchingResponseBody(rawBody);
    try {
      //responseConverter 的创建是在 responseBodyConverter (BuiltInConverters.java) 中实现的。但是具体返回的是哪一个 Converter 我还不知道，总之是在这里将获取到的原始的 Response 转换成我们需要的 Response 类型
      T body = responseConverter.convert(catchingBody);
      //最后是返回一个 retrofit.Response 类型的实例交给回调函数
      return Response.success(body, rawResponse);
    } catch (RuntimeException e) {
      // If the underlying source threw an exception, propagate that rather than indicating it was
      // a runtime exception.
      catchingBody.throwIfCaught();
      throw e;
    }
  }
}



































