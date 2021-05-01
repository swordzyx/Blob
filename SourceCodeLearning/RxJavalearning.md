RxJava 原理
1. Single/Observerable：发送数据的对象
2. RxJavaPlugins.setXxxxx：设置钩子，在实现在 Single/Observerable 创建时或。者被订阅时执行一些操作。
3. Observer：接受结果的回调。


实例一：
```kotlin
fun sourceAnalyse() {
    //RxJava 最简单的实现
    //立刻发送一个字符串“1”，根本会感受不到 onSubscribe 的执行。
    //这里其实返回的是一个 SingleJust 对象，RxJavaPlugin 会对这个 SingleJust 执行一个钩子函数，默认为 null，可以通过 “RxJavaPlugins.setOnSingleAssembly {...}” 设置
    val single = Single.just("1")
    //此处实际执行的是 SingleJust 的 subscribe 函数。 这里面也是首先对传进去的 SingleJust 和 Observerable 实例执行钩子函数（通过 RxJavaPlugins.setOnSingleSubscribe 设置），然后执行 SingleJust.subscribeActual() 函数。 subscribeActual 中就是执行了此处传进去的 Observer 中的回调方法，首先执行 onSubscribe，接着在数据发送成功时执行 onSuccess，出现错误时执行 onError
    single.subscribe(object : SingleObserver<String?> {
        override fun onSubscribe(d: Disposable?) {
        }

        override fun onSuccess(t: String?) {
            textview.text = t
        }

        override fun onError(e: Throwable?) {

        }
    })
}
```
    
```java
just {
    //这里返回一个 Single 对象 
    @CheckReturnValue
    @SchedulerSupport(SchedulerSupport.NONE)
    @NonNull
    public static <@NonNull T> Single<T> just(T item) {
        //判空，传进来的 item 不能为 null
        Objects.requireNonNull(item, "item is null");
        //new 一个 SingleJust 对象，将 item 保存起来，执行 onAssembly 方法，默认 onAssembly 是不执行任何操作，原封不动的将传进去的对象返回的，因此这里返回的就是一个 SingleJust 对象。
        return RxJavaPlugins.onAssembly(new SingleJust<>(item));
    }


    /**
     * 执行一个钩子函数(保存在 onSingleAssembly 中)，其实就是对 source 执行一些操作（通过 apply 方法将钩子函数 f 应用到 source 中）
     * @param <T> the value type
     * @param source the hook's input value
     * @return the value returned by the hook
     */
    @SuppressWarnings({ "rawtypes", "unchecked" })
    @NonNull
    public static <T> Single<T> onAssembly(@NonNull Single<T> source) {
        Function<? super Single, ? extends Single> f = onSingleAssembly;
        if (f != null) {//一般情况下不用管它，默认情况下 f 是为 null 的，也就是什么都不干
            //对 source 执行钩子函数
            return apply(f, source);
        }
        //如果钩子函数为 null，就是原封不动的将 souce 返回
        return source;
    }
}

```

```java
//SingleJust.subcribe，SingleJust 继承自 Single，subcribe 具体实现在 Single 中。
subscribe {

    @CheckReturnValue
    @NonNull
    @SchedulerSupport(SchedulerSupport.NONE)
    public final Disposable subscribe(@NonNull Consumer<? super T> onSuccess, @NonNull Consumer<? super Throwable> onError) {
        //判空操作
        Objects.requireNonNull(onSuccess, "onSuccess is null");
        Objects.requireNonNull(onError, "onError is null");

        //创建 ConsumerSingleObserver ，用于 Single 发送 item 时的回调，包括发射成功以及发生错误时的回调处理
        ConsumerSingleObserver<T> observer = new ConsumerSingleObserver<>(onSuccess, onError);
        subscribe(observer);
        return observer;
    }


    @SchedulerSupport(SchedulerSupport.NONE)
    @Override
    public final void subscribe(@NonNull SingleObserver<? super T> observer) {
        //判空
        Objects.requireNonNull(observer, "observer is null");

        //this 指代 Single 实例，对 Single 和 Observer 对象执行钩子函数（其实也是执行一些操作，然后将其返回，默认是不执行的。即原封不动的返回）
        observer = RxJavaPlugins.onSubscribe(this, observer);

        Objects.requireNonNull(observer, "The RxJavaPlugins.onSubscribe hook returned a null SingleObserver. Please check the handler provided to RxJavaPlugins.setOnSingleSubscribe for invalid null returns. Further reading: https://github.com/ReactiveX/RxJava/wiki/Plugins");

        try {
            //执行 SingleJust 的 subscribeActual 函数
            subscribeActual(observer);
        } catch (NullPointerException ex) {
            throw ex;
        } catch (Throwable ex) {
            Exceptions.throwIfFatal(ex);
            NullPointerException npe = new NullPointerException("subscribeActual failed");
            npe.initCause(ex);
            throw npe;
        }
    }


    //SingleJust.java
    @Override
    protected void subscribeActual(SingleObserver<? super T> observer) {
        //observer 就是执行 subscribe 时传进来的观察者，这里面直接执行订阅回调，已经发送成功回调，并将数据发送出去
        //Disosable 返回的是一个 EmptyDisposable.INSTANCE 的枚举，是一个状态值，表示此次发送已被丢弃，因为 Single.just 是一个瞬时完成的订阅和发送，是不存在中间状态的，开始即结束。
        observer.onSubscribe(Disposable.disposed());
        observer.onSuccess(value);
    }
}

```


实例二：
1. Single.just(value)：保存 value
1. single.map: SingleJust -> SingleMap(singleJust, function)
2. singleMap.subscribe(SingleObserver): 
    - SingleObserver -> MapSingleObserver(singleObserver, function)：将 SingleObserver 转成 MapSingleObserver
    - singleJust.subscribe(mapSingleObserver)：开始订阅。 SingleJust 没有中间过程，因此开始即结束，没有中间过程
    - mapSingleObserver.onSubcribe(dispose) -> singleObserver.onSubcribe(dispose)
    - mapSingleObserver.onSuccess(value) -> function.apply(value): newValue -> singleObserver.onSuccess(newValue)

特定的被观察者其实对应了特定的观察者，例如 SingleJust 对应 SingleObserver，SingleMap 对应 MapSingleObserver。
SingJust 是上游

SingleMap 是桥梁，执行一个转换操作。当上游开始发送事件了，SingleMap 会将这个事件承接过来，如果是 onSubscribe 或者 onError 事件则直接转发到下游，如果是 onSuccess 事件则执行一个转换操作，具体的操作在 SingleMap.mapSingleObserver 对象中定义，执行完成之后在发送给下游

SingleObserver 是下游

因此 SingleMap 从上游承接事件，MapSingleObserver 处理 SingleMap 从上游承接的事件，处理完成之后再转发给下游


```java
//将 SingleJust<Int> 转成了 SingleMap<String>，SingleJust 和 SingleMap 都是 Single 的子类
val single: Single<Int> = Single.just(1)

val singleMap = single.map(object: Function<Int, String> {
    override fun apply(t: Int): String {
        return t.toString()
    }
})

singleMap.subscribe(object: SingleObserver<String> {
    override fun onSubscribe(d: Disposable?) {
        
    }

    override fun onSuccess(t: String?) {

    }

    override fun onError(e: Throwable?) {
    }

})
```

```java
map {
    @CheckReturnValue
    @NonNull
    @SchedulerSupport(SchedulerSupport.NONE)
    public final <@NonNull R> Single<R> map(@NonNull Function<? super T, ? extends R> mapper) {
        //判空
        Objects.requireNonNull(mapper, "mapper is null");
        //首先创建 SingleMap 对象，将 SingleJust 参数和 Function 作为参数传到 SingleMap 的构造函数中，而 SingMap 则是保存了这两个对象，分别保存在 source 和 mapper 成员中
        return RxJavaPlugins.onAssembly(new SingleMap<>(this, mapper));
    }
}


subscribe {

    //SingleMap.java
    @Override
    protected void subscribeActual(final SingleObserver<? super R> t) {
        //t 是原始的观察者，这里会将原始的观察者（SingleObserver）转换成一个新的（ MapSingleObserver ），MapSingleObserver 构造函数将传进去的原始观察者 t 和映射函数 mapper 保存到了成员变量中
        //source.subscribe 实际会执行 SingleJust.subscribeActual 方法，最终执行 MapSingleObserver 的 onSubscribe 和 onSuccess 方法
        source.subscribe(new MapSingleObserver<T, R>(t, mapper));
    }

    //MapSingleObserver
    //执行原始观察者 t 的 onSubcribe，t 是调用 SingleMap.subcribe() 时传进来的 Observer 对象。
    @Override
    public void onSubscribe(Disposable d) {
        t.onSubscribe(d);
    }


    //将 SingleJust.map 传进来的 Function（mapper）应用到 value 上面。就是执行映射函数，这其实也是开始即结束，没有中间过程。
    //MapSingleObserver
    @Override
    public void onSuccess(T value) {
        R v;
        try {
            v = Objects.requireNonNull(mapper.apply(value), "The mapper function returned a null value.");
        } catch (Throwable e) {
            Exceptions.throwIfFatal(e);
            onError(e);
            return;
        }

        t.onSuccess(v);
    }
}
```







实例三：
```java
//有延迟有后续的新创建事件
//每隔 1s 发送当前时间给观察者
//返回 ObservableInterval 作为上游（原始观察者）
Observable.interval(1, 1, TimeUnit.SECONDS)
    .subscribeOn(AndroidSchedulers.mainThread())
    //这里实际调用的就是 ObservableInterval 的 subscribeActual 方法
    .subscribe(object: Observer<Long?> {
        override fun onSubscribe(d: Disposable?) {

        }

        override fun onNext(t: Long?) {
            textview.text = t.toString()
        }

        override fun onError(e: Throwable?) {
        }

        override fun onComplete() {
        }

    })
```

```java
interval {
    @CheckReturnValue
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    @NonNull
    public static Observable<Long> interval(long initialDelay, long period, @NonNull TimeUnit unit) {
        //Schedulers.computation() 返回 ComputationTask，通过此对象来切换线程。
        return interval(initialDelay, period, unit, Schedulers.computation());
    }


    @CheckReturnValue
    @NonNull
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public static Observable<Long> interval(long initialDelay, long period, @NonNull TimeUnit unit, @NonNull Scheduler scheduler) {
        //判空处理
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        //创建一个 ObservableInterval 对象，也就是被观察者，对这个被观察者执行钩子函数
        //ObservableInterval 保存了 initialDelay 作为初始延迟，period 作为每次发送的时间间隔，unit 作为时间单位，scheduler
        return RxJavaPlugins.onAssembly(new ObservableInterval(Math.max(0L, initialDelay), Math.max(0L, period), unit, scheduler));
    }
}

subscribe {
    //ObservableInterval.java
    @Override
    public void subscribeActual(Observer<? super Long> observer) {
        //创建桥梁观察者，与下游对接，它实现了 Runnable 和 Disposable 接口
        IntervalObserver is = new IntervalObserver(observer);
        //将 onSubscribe 直接转发给下游
        observer.onSubscribe(is);

        Scheduler sch = scheduler;

        //走 else 分支，sch 是 ComputationTask 的实例，并不是 TrampolineScheduler 的子类
        if (sch instanceof TrampolineScheduler) {
            Worker worker = sch.createWorker();
            is.setResource(worker);
            worker.schedulePeriodically(is, initialDelay, period, unit);
        } else {
            Disposable d = sch.schedulePeriodicallyDirect(is, initialDelay, period, unit);
            is.setResource(d);
        }
    }


    @NonNull
    public Disposable schedulePeriodicallyDirect(@NonNull Runnable run, long initialDelay, long period, @NonNull TimeUnit unit) {
        //createWorker 具体实现在 ComputationScheduler 中，创建一个 EventLooperWorker 对象并返回
        final Worker w = createWorker();

        //钩子函数，线程调度时执行
        final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

        PeriodicDirectTask periodicTask = new PeriodicDirectTask(decoratedRun, w);

        Disposable d = w.schedulePeriodically(periodicTask, initialDelay, period, unit);
        if (d == EmptyDisposable.INSTANCE) {
            return d;
        }

        return periodicTask;
    }

    @NonNull
    public Disposable schedulePeriodically(@NonNull Runnable run, final long initialDelay, final long period, @NonNull final TimeUnit unit) {
        final SequentialDisposable first = new SequentialDisposable();

        final SequentialDisposable sd = new SequentialDisposable(first);

        final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

        //时间间隔
        final long periodInNanoseconds = unit.toNanos(period);
        //初始延迟
        final long firstNowNanoseconds = now(TimeUnit.NANOSECONDS);
        //Runnable 首次执行的时间
        final long firstStartInNanoseconds = firstNowNanoseconds + unit.toNanos(initialDelay);

        Disposable d = schedule(new PeriodicTask(firstStartInNanoseconds, decoratedRun, firstNowNanoseconds, sd, periodInNanoseconds), initialDelay, unit);

        if (d == EmptyDisposable.INSTANCE) {
            return d;
        }
        first.replace(d);

        return sd;
    }

    @NonNull
    @Override
    public Disposable schedule(@NonNull Runnable action) {
        if (disposed) {
            return EmptyDisposable.INSTANCE;
        }

        return poolWorker.scheduleActual(action, 0, TimeUnit.MILLISECONDS, serial);
    }

    @NonNull
    public ScheduledRunnable scheduleActual(final Runnable run, long delayTime, @NonNull TimeUnit unit, @Nullable DisposableContainer parent) {
        Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

        ScheduledRunnable sr = new ScheduledRunnable(decoratedRun, parent);

        if (parent != null) {
            if (!parent.add(sr)) {
                return sr;
            }
        }

        Future<?> f;
        try {
            if (delayTime <= 0) {
                f = executor.submit((Callable<Object>)sr);
            } else {
                f = executor.schedule((Callable<Object>)sr, delayTime, unit);
            }
            sr.setFuture(f);
        } catch (RejectedExecutionException ex) {
            if (parent != null) {
                parent.remove(sr);
            }
            RxJavaPlugins.onError(ex);
        }

        return sr;
    }
}
```



