//创建 OkHttpClient 实例
OkHttpClient.Builder().build() {
  //OkHttpClient.kt
  fun build(): OkHttpClient = OkHttpClient(this)
}

//创建 RealCall 实例并发送异步请求，newCall 返回的是一个 RealCall 实例
//做了一件事，检查这个 Call 是否执行过，通知 EventListener 开始执行请求了。这里可以看出每个 Call 只能执行一次。
OkHttpClient.newCall().enqueue() {
  OkHttpClient.kt
  //创建一个 RealCall，RealCall 是 Call 的子类
  override fun newCall(request: Request): Call = RealCall(this, request, forWebSocket = false)

  
  RealCall.kt
  override fun enqueue(responseCallback: Callback) {
    //检查当前是否已经有请求再执行了
    check(executed.compareAndSet(false, true)) { "Already Executed" }

    //通知事件监听器，开始发送请求了。
    callStart()
    //dispatcher 是 Dispatcher 或其子类的实例，在 OkHttpClient.Builder() 中初始化，可以通过 Builder().dispatcher() 设置
    //创建一个 AsyncCall ，然后通过 Dispatcher 进行派发
    client.dispatcher.enqueue(AsyncCall(responseCallback))
  }

  //eventListener 通过 client.eventListenerFactory.create 创建，eventListenerFactory 在 OkHttpClient.Builder 中赋值，也就是在，也就是一个事件监听器，这里可以看出当开始发送请求时会回调 callStart 方法。
  //默认的 EventListener 是 EventListener.NONE ，在 Builder() 中初始化
  RealCall.kt
  private fun callStart() {
    this.callStackTrace = Platform.get().getStackTraceForCloseable("response.body().close()")
    eventListener.callStart(this)
  }
}

//Dispatcher 主要是用来管理最多能同时进行的请求数。也就是用来管理并发的。
Dispatcher.enqueue() {
  //Dispathcer.kt
  //将 AsyncCall 保存到一个可变数组中，接着调用 promoteAndExecute()
  internal fun enqueue(call: AsyncCall) {
    synchronized(this) {
      //readyAsyncCalls 是一个 ArrayDeque 实例，所以异步执行请求是将 Call 放到一个可变数组中。
      //ArrayDeque 是一个可变数组，基于数组和双指针实现，它的大部分操作都是线性时间复杂读。即可以用作栈，也可以用作队列，不是线程安全的。
      //ArrayDeque 官方介绍：https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/util/ArrayDeque.html
      readyAsyncCalls.add(call)

      //检查当前是否存在对于同一主机的请求，如果有则直接重用。
      if (!call.call.forWebSocket) {
        val existingCall = findExistingCallWithHost(call.host)
        if (existingCall != null) call.reuseCallsPerHostFrom(existingCall)
      }
    }
    promoteAndExecute()
  } 

  //Dispatcher.kt
  //判断请求数目是否已经超了，如果没超，通过 AsyncCall.executeOn() 执行请求，然后记录状态，只要一个 Call 处于 Running 状态，那么当前就是 Running 状态。
  //OkHttp 是可以接收多个请求同时执行的，官方文档定义的是每个 Call 只能使用一次，但是可以执行多次 newCall 来创建，分别调用 enqueue()，发起多个请求
  private fun promoteAndExecute(): Boolean {
    this.assertThreadDoesntHoldLock()

    val executableCalls = mutableListOf<AsyncCall>()
    val isRunning: Boolean
    synchronized(this) {
      //遍历保存了 AsyncCall 的数组，即遍历所有的异步请求。
      val i = readyAsyncCalls.iterator()
      while (i.hasNext()) {
        val asyncCall = i.next()

        //maxRequests 和 maxRequestsPerHost 在 Dispatcher 构造函数中初始化，默认是 64 和 5，可以通过 set 方法进行设置
        //判断当前正在运行的请求是否超过了设置的最大请求数。
        if (runningAsyncCalls.size >= this.maxRequests) break // Max capacity.
        //检查针对主机的请求数目是否超过了主机所能接收的最大数目
        if (asyncCall.callsPerHost.get() >= this.maxRequestsPerHost) continue // Host max capacity.

        //总请求数和每个主机的请求数都没有超，可以执行这个请求，将 AsyncCall 添加到 executableCalls 和 runningAsyncCalls 中，这都是可变数组
        //OkHttp 是将 Call 按照状态进行分配了？
        i.remove()
        asyncCall.callsPerHost.incrementAndGet()
        executableCalls.add(asyncCall)
        runningAsyncCalls.add(asyncCall)
      }
      //记录状态。
      isRunning = runningCallsCount() > 0
    }

    //这里是可以同时执行多个请求的，数量由 maxRequests 和 maxRequestsPerHost 指定
    for (i in 0 until executableCalls.size) {
      val asyncCall = executableCalls[i]
      //最终调用 AsyncCall 中的 executeOn.
      asyncCall.executeOn(executorService)
    }

    return isRunning
  }
}


//RealCall.kt
//请求的执行
AsyncCall.executeOn() {

  //RealCall.kt
  //分配线程池：从线程池中分配一个线程来执行请求。
  fun executeOn(executorService: ExecutorService) {
    client.dispatcher.assertThreadDoesntHoldLock()

    var success = false
    try {
      //executorService 是一个线程池，可以在创建 Dispathcer 时，将线程池实例传入构造函数进行设置。
      //这其实是开启了一个线程执行请求。接下来应该是调用 AsyncCall.run 函数了。
      executorService.execute(this)
      success = true
    } catch (e: RejectedExecutionException) {
      val ioException = InterruptedIOException("executor rejected")
      ioException.initCause(e)
      noMoreExchanges(ioException)
      //resposneCallback 是执行 newCall().enqueue(callback) 时传入的 callback。
      responseCallback.onFailure(this@RealCall, ioException)
    } finally {
      if (!success) {
        client.dispatcher.finished(this) // This call is no longer running!
      }
    }
  }

  //RealCall.kt
  override fun run() {
    threadName("OkHttp ${redactedUrl()}") {
      var signalledCallback = false
      timeout.enter()
      try {
        //这里是核心方法，所有的 HTTP 相关的工作都在这里面做了，包括发送网络请求，获取响应。
        val response = getResponseWithInterceptorChain()
        signalledCallback = true
        //收到响应之后回调
        responseCallback.onResponse(this@RealCall, response)
      } catch (e: IOException) {
        if (signalledCallback) {
          // Do not signal the callback twice!
          Platform.get().log("Callback failure for ${toLoggableString()}", Platform.INFO, e)
        } else {
          responseCallback.onFailure(this@RealCall, e)
        }
      } catch (t: Throwable) {
        cancel()
        if (!signalledCallback) {
          val canceledException = IOException("canceled due to $t")
          canceledException.addSuppressed(t)
          responseCallback.onFailure(this@RealCall, canceledException)
        }
        throw t
      } finally {
        client.dispatcher.finished(this)
      }
    }
  }
}

//这里完成 HTTP 的相关的工作，是 OkHttp 的核心方法。
//通过 OkHttp 发送请求的流程，应用层传递到 OkHttp，再由 OkHttp 传递给 WebServer，
//addInterceptor() 添加的是应用层到 OkHttp 之间的拦截器，addNetworkInterceptor() 添加的是 OkHttp 到 WebServer 之间的拦截器。
//这里创建拦截器链，并将请求往拦截器链中一层层往下传递。
getResponseWithInterceptorChain() {

  //RealCall.kt
  @Throws(IOException::class)
  internal fun getResponseWithInterceptorChain(): Response {
    // Build a full stack of interceptors.
    val interceptors = mutableListOf<Interceptor>()
    //这里是通过 OkHttpClient.Builder().addInterceptor() 添加的 Interceptor。
    interceptors += client.interceptors
    //当请求出现错误时进行重试，以及请求被重定向后，进行跟随。
    interceptors += RetryAndFollowUpInterceptor(client)
    //cookieJar 是一个保存 Cookie 的容器
    interceptors += BridgeInterceptor(client.cookieJar)
    //判断针对该请求是否有已缓存的结果，如果有判断是否需要刷新缓存。保存缓存的 Cache 需自己实现。
    interceptors += CacheInterceptor(client.cache)
    //这里负责建立网络连接，这里会建立 TCP 连接，或者 TLS 连接 
    interceptors += ConnectInterceptor
    if (!forWebSocket) {
      //networkInterceptors 通过 Builder().addNetworkInterceptor() 添加，可以用于查看 OkHttp 和 WebServer 网络交互所产生的数据。
      interceptors += client.networkInterceptors
    }
    interceptors += CallServerInterceptor(forWebSocket)

    //OkHttp 中的拦截器是链式的，这里创建拦截器链。
    val chain = RealInterceptorChain(
        call = this,
        interceptors = interceptors,
        index = 0,
        exchange = null,
        request = originalRequest,
        connectTimeoutMillis = client.connectTimeoutMillis,
        readTimeoutMillis = client.readTimeoutMillis,
        writeTimeoutMillis = client.writeTimeoutMillis
    )

    var calledNoMoreExchanges = false
    try {
      //在拦截器链中，请求开始一层一层往下传，获取到响应之后，响应的结果在一层一层往上传。 proceed() 是 Interceptor 的关键方法。
      val response = chain.proceed(originalRequest)
      if (isCanceled()) {
        response.closeQuietly()
        throw IOException("Canceled")
      }
      return response
    } catch (e: IOException) {
      calledNoMoreExchanges = true
      throw noMoreExchanges(e) as Throwable
    } finally {
      if (!calledNoMoreExchanges) {
        noMoreExchanges(null)
      }
    }
  }
}
