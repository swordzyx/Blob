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

  //RealInterceptorChain.kt
  override fun proceed(request: Request): Response {
    check(index < interceptors.size)

    calls++

    if (exchange != null) {
      check(exchange.finder.sameHostAndPort(request.url)) {
        "network interceptor ${interceptors[index - 1]} must retain the same host and port"
      }
      check(calls == 1) {
        "network interceptor ${interceptors[index - 1]} must call proceed() exactly once"
      }
    }

    // 所有的拦截器以链式结构存储，这里获取到下一个拦截器，index 的初始值是 0.
    val next = copy(index = index + 1, request = request)
    val interceptor = interceptors[index]

    @Suppress("USELESS_ELVIS")
    //调用拦截器的 intercept 方法。每一个拦截器中都有对应的 intercept 方法。
    val response = interceptor.intercept(next) ?: throw NullPointerException(
        "interceptor $interceptor returned null")

    if (exchange != null) {
      check(index + 1 >= interceptors.size || next.calls == 1) {
        "network interceptor $interceptor must call proceed() exactly once"
      }
    }

    check(response.body != null) { "interceptor $interceptor returned a response with no body" }

    return response
  }


  //RetryAndFollowUpInterceptor
  //这里主要负责 Request 被重定向，或者请求超时等情况的请求跟随和请求重试
  RetryAndFollowUpInterceptor {

    //RetryAndFollowUpInterceptor
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
      val realChain = chain as RealInterceptorChain
      var request = chain.request
      val call = realChain.call
      var followUpCount = 0
      var priorResponse: Response? = null
      var newExchangeFinder = true
      var recoveredFailures = listOf<IOException>()
      while (true) {
        call.enterNetworkInterceptorExchange(request, newExchangeFinder)

        var response: Response
        var closeActiveExchange = true
        try {
          if (call.isCanceled()) {
            throw IOException("Canceled")
          }

          try {
            //在此处将 Request 往下一个 Inerceptor 传递，也就是 BridgeInterceptor 所以这个拦截器主要做的是后置工作。
            response = realChain.proceed(request)
            newExchangeFinder = true
          } catch (e: RouteException) {
            // The attempt to connect via a route failed. The request will not have been sent.
            if (!recover(e.lastConnectException, call, request, requestSendStarted = false)) {
              throw e.firstConnectException.withSuppressed(recoveredFailures)
            } else {
              recoveredFailures += e.firstConnectException
            }
            newExchangeFinder = false
            continue
          } catch (e: IOException) {
            // An attempt to communicate with a server failed. The request may have been sent.
            if (!recover(e, call, request, requestSendStarted = e !is ConnectionShutdownException)) {
              throw e.withSuppressed(recoveredFailures)
            } else {
              recoveredFailures += e
            }
            newExchangeFinder = false
            continue
          }

          // Attach the prior response if it exists. Such responses never have a body.
          if (priorResponse != null) {
            response = response.newBuilder()
                .priorResponse(priorResponse.newBuilder()
                    .body(null)
                    .build（())
                .build()
          }

          val exchange = call.interceptorScopedExchange
          //这里里面根据响应码，判断是否需要跟随请求，例如重定向请求，请求超时时的重试等等
          val followUp = followUpRequest(response, exchange)

          if (followUp == null) {
            if (exchange != null && exchange.isDuplex) {
              call.timeoutEarlyExit()
            }
            closeActiveExchange = false
            return response
          }

          val followUpBody = followUp.body
          if (followUpBody != null && followUpBody.isOneShot()) {
            closeActiveExchange = false
            return response
          }

          response.body?.closeQuietly()

          if (++followUpCount > MAX_FOLLOW_UPS) {
            throw ProtocolException("Too many follow-up requests: $followUpCount")
          }

          //这是一个死循环，因此这里将 followUp 赋值给 request 之后，会重新进行 request 的传递。
          request = followUp
          priorResponse = response
        } finally {
          call.exitNetworkInterceptorExchange(closeActiveExchange)
        }
      }
    }

    @Throws(IOException::class)
    private fun followUpRequest(userResponse: Response, exchange: Exchange?): Request? {
      val route = exchange?.connection?.route()
      val responseCode = userResponse.code

      val method = userResponse.request.method
      when (responseCode) {
        HTTP_PROXY_AUTH -> {
          val selectedProxy = route!!.proxy
          if (selectedProxy.type() != Proxy.Type.HTTP) {
            throw ProtocolException("Received HTTP_PROXY_AUTH (407) code while not using proxy")
          }
          return client.proxyAuthenticator.authenticate(route, userResponse)
        }

        //需要进行授权认证
        HTTP_UNAUTHORIZED -> return client.authenticator.authenticate(route, userResponse)

        //重定向
        HTTP_PERM_REDIRECT, HTTP_TEMP_REDIRECT, HTTP_MULT_CHOICE, HTTP_MOVED_PERM, HTTP_MOVED_TEMP, HTTP_SEE_OTHER -> {
          return buildRedirectRequest(userResponse, method)
        }

        //客户端请求超时
        HTTP_CLIENT_TIMEOUT -> {
          // 408's are rare in practice, but some servers like HAProxy use this response code. The
          // spec says that we may repeat the request without modifications. Modern browsers also
          // repeat the request (even non-idempotent ones.)
          if (!client.retryOnConnectionFailure) {
            // The application layer has directed us not to retry the request.
            return null
          }

          val requestBody = userResponse.request.body
          if (requestBody != null && requestBody.isOneShot()) {
            return null
          }
          val priorResponse = userResponse.priorResponse
          if (priorResponse != null && priorResponse.code == HTTP_CLIENT_TIMEOUT) {
            // We attempted to retry and got another timeout. Give up.
            return null
          }

          if (retryAfter(userResponse, 0) > 0) {
            return null
          }

          return userResponse.request
        }

        //服务器超时
        HTTP_UNAVAILABLE -> {
          val priorResponse = userResponse.priorResponse
          if (priorResponse != null && priorResponse.code == HTTP_UNAVAILABLE) {
            // We attempted to retry and got another timeout. Give up.
            return null
          }

          if (retryAfter(userResponse, Integer.MAX_VALUE) == 0) {
            // specifically received an instruction to retry without delay
            return userResponse.request
          }

          return null
        }

        //HTTP 版本不一样，无法直接发送请求？猜的
        HTTP_MISDIRECTED_REQUEST -> {
          // OkHttp can coalesce HTTP/2 connections even if the domain names are different. See
          // RealConnection.isEligible(). If we attempted this and the server returned HTTP 421, then
          // we can retry on a different connection.
          val requestBody = userResponse.request.body
          if (requestBody != null && requestBody.isOneShot()) {
            return null
          }

          if (exchange == null || !exchange.isCoalescedConnection) {
            return null
          }

          exchange.connection.noCoalescedConnections()
          return userResponse.request
        }

        else -> return null
      }
    }
  }



  //这里对请求的 Header 做一些额外的处理，设置压缩格式，传输的格式，传输的内容类型，Cookie 和用户代理等。
  //BridgeInterceptor
  BridgeInterceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
      //获取要发送的请求
      val userRequest = chain.request()
      val requestBuilder = userRequest.newBuilder()

      val body = userRequest.body
      if (body != null) {
        //获取请求体的内容类型
        val contentType = body.contentType()
        if (contentType != null) {
          requestBuilder.header("Content-Type", contentType.toString())
        }
        //长度
        val contentLength = body.contentLength()
        //添加 Header
        if (contentLength != -1L) {
          requestBuilder.header("Content-Length", contentLength.toString())
          requestBuilder.removeHeader("Transfer-Encoding")
        } else {
          requestBuilder.header("Transfer-Encoding", "chunked")
          requestBuilder.removeHeader("Content-Length")
        }
      }

      if (userRequest.header("Host") == null) {
        requestBuilder.header("Host", userRequest.url.toHostHeader())
      }

      if (userRequest.header("Connection") == null) {
        requestBuilder.header("Connection", "Keep-Alive")
      }

      // 默认情况下会添加一个压缩编码格式，表示接收压缩的响应内容，OkHttp 会自动进行解压缩。
      var transparentGzip = false
      if (userRequest.header("Accept-Encoding") == null && userRequest.header("Range") == null) {
        transparentGzip = true
        requestBuilder.header("Accept-Encoding", "gzip")
      }

      //如果保存了该请求对应的 Cookie
      val cookies = cookieJar.loadForRequest(userRequest.url)
      if (cookies.isNotEmpty()) {
        requestBuilder.header("Cookie", cookieHeader(cookies))
      }

      if (userRequest.header("User-Agent") == null) {
        requestBuilder.header("User-Agent", userAgent)
      }

      //将 Request 递交到下一个 Interceptor，
      val networkResponse = chain.proceed(requestBuilder.build())

      //保存新的 cookie 
      cookieJar.receiveHeaders(userRequest.url, networkResponse.headers)

      val responseBuilder = networkResponse.newBuilder()
          .request(userRequest)

      //如果在 Request 中设置了 “Accept-Encoding” 为 “gzip”，这里对响应的正文进行解压缩，然后将解压缩后的内容重新打包成一个 Response 并返回。
      if (transparentGzip &&
          "gzip".equals(networkResponse.header("Content-Encoding"), ignoreCase = true) &&
          networkResponse.promisesBody()) {
        val responseBody = networkResponse.body
        if (responseBody != null) {
          val gzipSource = GzipSource(responseBody.source())
          val strippedHeaders = networkResponse.headers.newBuilder()
              .removeAll("Content-Encoding")
              .removeAll("Content-Length")
              .build()
          responseBuilder.headers(strippedHeaders)
          val contentType = networkResponse.header("Content-Type")
          responseBuilder.body(RealResponseBody(contentType, -1L, gzipSource.buffer()))
        }
      }

      return responseBuilder.build()
    }
  }
    

  //CacheInterceptor
  //执行缓存相关的处理，它在 NetworkInterceptor 之前，可以确保如果有缓存可用，那么在发生实质的网络请求之前能够将缓存作为 Resposne 返回。
  CacheInterceptor {
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
      //获取 Call
      val call = chain.call()
      val cacheCandidate = cache?.get(chain.request())

      val now = System.currentTimeMillis()

      //获取该请求在本地是否有缓存，如果有提取缓存，并计算该缓存是否可用。
      //这里会根据设置的缓存策略来判断是使用缓存，还是向网络发送请求，如果二者皆不可用，networkRequest 和 cacheResposne 均为 null
      val strategy = CacheStrategy.Factory(now, chain.request(), cacheCandidate).compute()
      val networkRequest = strategy.networkRequest
      val cacheResponse = strategy.cacheResponse

      cache?.trackResponse(strategy)
      val listener = (call as? RealCall)?.eventListener ?: EventListener.NONE

      if (cacheCandidate != null && cacheResponse == null) {
        // The cache candidate wasn't applicable. Close it.
        cacheCandidate.body?.closeQuietly()
      }

      // If we're forbidden from using the network and the cache is insufficient, fail.
      if (networkRequest == null && cacheResponse == null) {
        return Response.Builder()
            .request(chain.request())
            .protocol(Protocol.HTTP_1_1)
            .code(HTTP_GATEWAY_TIMEOUT)
            .message("Unsatisfiable Request (only-if-cached)")
            .body(EMPTY_RESPONSE)
            .sentRequestAtMillis(-1L)
            .receivedResponseAtMillis(System.currentTimeMillis())
            .build().also {
              listener.satisfactionFailure(call, it)
            }
      }

      // 如果缓存可用，将缓存作为 Response 返回，不需要进行网络请求。
      if (networkRequest == null) {
        return cacheResponse!!.newBuilder()
            .cacheResponse(stripBody(cacheResponse))
            .build().also {
              listener.cacheHit(call, it)
            }
      }

      if (cacheResponse != null) {
        listener.cacheConditionalHit(call, cacheResponse)
      } else if (cache != null) {
        listener.cacheMiss(call)
      }

      var networkResponse: Response? = null
      try {
        //将请求递交下一个拦截器，并等待响应
        networkResponse = chain.proceed(networkRequest)
      } finally {
        // If we're crashing on I/O or otherwise, don't leak the cache body.
        if (networkResponse == null && cacheCandidate != null) {
          cacheCandidate.body?.closeQuietly()
        }
      }

      //如果 WebServer 返回它的内容与本地缓存相比，内容并未更改，那么直接返回缓存的 Response，然后更新本地缓存
      if (cacheResponse != null) {
        if (networkResponse?.code == HTTP_NOT_MODIFIED) {
          val response = cacheResponse.newBuilder()
              .headers(combine(cacheResponse.headers, networkResponse.headers))
              .sentRequestAtMillis(networkResponse.sentRequestAtMillis)
              .receivedResponseAtMillis(networkResponse.receivedResponseAtMillis)
              .cacheResponse(stripBody(cacheResponse))
              .networkResponse(stripBody(networkResponse))
              .build()

          networkResponse.body!!.close()

          // Update the cache after combining headers but before stripping the
          // Content-Encoding header (as performed by initContentStream()).
          cache!!.trackConditionalCacheHit()
          //更新缓存
          cache.update(cacheResponse, response)
          return response.also {
            listener.cacheHit(call, it)
          }
        } else {
          cacheResponse.body?.closeQuietly()
        }
      }

      val response = networkResponse!!.newBuilder()
          .cacheResponse(stripBody(cacheResponse))
          .networkResponse(stripBody(networkResponse))
          .build()

      if (cache != null) {
        if (response.promisesBody() && CacheStrategy.isCacheable(response, networkRequest)) {
          // Offer this request to the cache.
          val cacheRequest = cache.put(response)
          return cacheWritingResponse(cacheRequest, response).also {
            if (cacheResponse != null) {
              // This will log a conditional cache miss only.
              listener.cacheMiss(call)
            }
          }
        }

        if (HttpMethod.invalidatesCache(networkRequest.method)) {
          try {
            cache.remove(networkRequest)
          } catch (_: IOException) {
            // The cache cannot be written.
          }
        }
      }

      return response
    }
  }
  


  //ConnectInterceptor
  //这里的初始化连接实质是初始化了一个 Exchange 对象。
  ConnectInterceptor {

    //ConnectionInterceptor.kt
    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
      val realChain = chain as RealInterceptorChain
      val exchange = realChain.call.initExchange(chain)
      val connectedChain = realChain.copy(exchange = exchange)
      //将请求递交给下一个 Interceptor
      return connectedChain.proceed(realChain.request)
    }


    //RealCall.kt
    internal fun initExchange(chain: RealInterceptorChain): Exchange {
      synchronized(this) {
        check(expectMoreExchanges) { "released" }
        check(!responseBodyOpen)
        check(!requestBodyOpen)
      }

      val exchangeFinder = this.exchangeFinder!!
      //HTTP 编解码器
      val codec = exchangeFinder.find(client, chain)
      //初始化连接，Exchange 用于发送单个 HTTP 请求和一个响应对。
      val result = Exchange(this, eventListener, exchangeFinder, codec)
      this.interceptorScopedExchange = result
      this.exchange = result
      synchronized(this) {
        this.requestBodyOpen = true
        this.responseBodyOpen = true
      }

      if (canceled) throw IOException("Canceled")
      return result
    }

  }


  //CallServerInterceptor
  //这里主要就是向 WebServer 发送请求和获取响应了。这是最后一个 Interceptor，在这里接收到响应之后就是一层一层往前回传
  CallServerInterceptor {

    @Throws(IOException::class)
    override fun intercept(chain: Interceptor.Chain): Response {
      val realChain = chain as RealInterceptorChain
      val exchange = realChain.exchange!!
      val request = realChain.request
      val requestBody = request.body
      val sentRequestMillis = System.currentTimeMillis()

      var invokeStartEvent = true
      var responseBuilder: Response.Builder? = null
      var sendRequestException: IOException? = null
      //发送请求，即向 Exchange 中写入 Request Headers 和 Request Body。
      try {
        //exchange 就是上一个请求创建的用来发送请求和接收响应的 Exchange 实例
        //写入 Request Headers
        exchange.writeRequestHeaders(request)

        //发送 Request Body
        if (HttpMethod.permitsRequestBody(request.method) && requestBody != null) {
          //判断是否发送要给临时消息，并等待服务器返回 100 响应码，“100” 表示可以继续发送
          if ("100-continue".equals(request.header("Expect"), ignoreCase = true)) {
            exchange.flushRequest()
            responseBuilder = exchange.readResponseHeaders(expectContinue = true)
            exchange.responseHeadersStart()
            invokeStartEvent = false
          }
          if (responseBuilder == null) {
            if (requestBody.isDuplex()) {
              // Prepare a duplex body so that the application can send a request body later.
              exchange.flushRequest()
              val bufferedRequestBody = exchange.createRequestBody(request, true).buffer()
              requestBody.writeTo(bufferedRequestBody)
            } else {
              // Write the request body if the "Expect: 100-continue" expectation was met.
              val bufferedRequestBody = exchange.createRequestBody(request, false).buffer()
              requestBody.writeTo(bufferedRequestBody)
              bufferedRequestBody.close()
            }
          } else {
            exchange.noRequestBody()
            if (!exchange.connection.isMultiplexed) {
              // If the "Expect: 100-continue" expectation wasn't met, prevent the HTTP/1 connection
              // from being reused. Otherwise we're still obligated to transmit the request body to
              // leave the connection in a consistent state.
              exchange.noNewExchangesOnConnection()
            }
          }
        } else {
          exchange.noRequestBody()
        }

        if (requestBody == null || !requestBody.isDuplex()) {
          exchange.finishRequest()
        }
      } catch (e: IOException) {
        if (e is ConnectionShutdownException) {
          throw e // No request was sent so there's no response to read.
        }
        if (!exchange.hasFailure) {
          throw e // Don't attempt to read the response; we failed to send the request.
        }
        sendRequestException = e
      }

      //获取 Response，并对 Response 做一些检查，然后将 Resposne 返回给上一个 Interpector。
      try {
        if (responseBuilder == null) {
          //从 Exchange 中读取响应
          responseBuilder = exchange.readResponseHeaders(expectContinue = false)!!
          if (invokeStartEvent) {
            exchange.responseHeadersStart()
            invokeStartEvent = false
          }
        }

        var response = responseBuilder
            .request(request)
            .handshake(exchange.connection.handshake())
            .sentRequestAtMillis(sentRequestMillis)
            .receivedResponseAtMillis(System.currentTimeMillis())
            .build()
        //获取响应码，响应码为 100
        var code = response.code
        if (code == 100) {
          // Server sent a 100-continue even though we did not request one. Try again to read the
          // actual response status.
          responseBuilder = exchange.readResponseHeaders(expectContinue = false)!!
          if (invokeStartEvent) {
            exchange.responseHeadersStart()
          }
          response = responseBuilder
              .request(request)
              .handshake(exchange.connection.handshake())
              .sentRequestAtMillis(sentRequestMillis)
              .receivedResponseAtMillis(System.currentTimeMillis())
              .build()
          code = response.code
        }

        exchange.responseHeadersEnd(response)

        //响应码为 101
        response = if (forWebSocket && code == 101) {
          // Connection is upgrading, but we need to ensure interceptors see a non-null response body.
          response.newBuilder()
              .body(EMPTY_RESPONSE)
              .build()
        } else {
          response.newBuilder()
              .body(exchange.openResponseBody(response))
              .build()
        }
        if ("close".equals(response.request.header("Connection"), ignoreCase = true) ||
            "close".equals(response.header("Connection"), ignoreCase = true)) {
          exchange.noNewExchangesOnConnection()
        }
        if ((code == 204 || code == 205) && response.body?.contentLength() ?: -1L > 0L) {
          throw ProtocolException(
              "HTTP $code had non-zero Content-Length: ${response.body?.contentLength()}")
        }
        //返回 Resposne 。
        return response
      } catch (e: IOException) {
        if (sendRequestException != null) {
          sendRequestException.addSuppressed(e)
          throw sendRequestException
        }
        throw e
      }
    }

  }
}
