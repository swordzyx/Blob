RxJava 原理
1. Single/Observerable：发送数据的对象
2. RxJavaPlugins.setXxxxx：设置钩子，实现在 Single/Observerable 创建时或者被订阅时执行一些操作。
3. Observer：接受结果的回调。

dispose：
（1）查看有没有来自上游的后续的任务，如果有，通知上游不要再发送之后的任务了，即当前是一个操作符，而且上游会发送多个消息
（2）有没有自己生产的后续任务（操作符），如果有，则终止后续任务
    - 自己是上游，自己生产任务
    - 有延时任务，自己额外生产的任务。

Disposable 本质上就是一个桥接器，桥接上游与下游






实例一：
```java
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

        //创建 ConsumerSingleObserver ，用于 Single 发送 item 时的回调，包括数据发送成功以及发送错误时的回调处理
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
        //observer 就是执行 subscribe 时传进来的 SingleObserver（下游 0），发送 onSubscribe 事件给下游 0
        //Disosable.disposed 返回的是一个 EmptyDisposable.INSTANCE 的枚举，是一个状态值，表示此次发送已被丢弃，因为 Single.just 是一个瞬时完成的订阅和发送，是不存在中间状态的，开始即结束。
        observer.onSubscribe(Disposable.disposed());
        observer.onSuccess(value);
    }

    //Disposable.java
    @NonNull
    static Disposable disposed() {
        //EmptyDisposable 是一个枚举类，它实现了 QueueDisposable<Object> 接口，是一个可取消的对象。
        return EmptyDisposable.INSTANCE;
    }
}

```


实例二
1. Single.just(value)：保存 value
2. single.map: SingleJust -> SingleMap(singleJust, function)
3. singleMap.subscribe(SingleObserver): 
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
//将 SingleJust<Int>（上游 0） 转成了 SingleMap<String>（上游 1），SingleJust 和 SingleMap 都是 Single 的子类
val single: Single<Int> = Single.just(1)
val singleMap = single.map(object: Function<Int, String> {
    override fun apply(t: Int): String {
        return t.toString()
    }
})

//执行上游 1 的 subscribe(SingleObserver) 函数
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
//SingleJust 是 Single 的子类，map 方法的具体实现在 Single.java 中
//map 方法将 SingleJust 封装到了 SingMap 中，然后返回 SingleMap 对象
//将 SingleJust 设为上游 0，被封装之后的 SingleMap 设为上游 1
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

//执行上游 1 的 subscribe 方法
subscribe {

    //SingleMap.java
    @Override
    protected void subscribeActual(final SingleObserver<? super R> t) {
        //t 是下游 0，mapper 是映射函数，将二者封装到 MapSingleObserver 中，作为一个新的下游（下游 1）
        //source.subscribe 最后会执行 SingleJust.subscribeActual 方法，subscribeActual 是所有的被观察者实际订阅并开始发送事件的地方，这里面发送的事件则由传进 subscribe 的观察者（Observer）接受， 观察者接收到事件之后会回调相应的函数（onSubscribe，onSuccess 等）
        //因此此处调用了 (上游 0)SingleJust.subscribe 之后将触发 (下游 1)MapSingleObserver 的 onSubscribe 和 onSuccess 方法，因为 SingleJust 一旦开始被订阅（subscribe 方法被执行）就会立刻发送 onSubscribe 和 onSuccess 函数
        source.subscribe(new MapSingleObserver<T, R>(t, mapper));
    }



    //MapSingleObserver.java 下游 1
    //MapSingleObserver（下游1）在收到 onSubscribe 事件之后直接传递给了下游0（t）
    //Disposable 对象是由 SingleJust（上游 0）通过 Disposable.disposed() 创建并传过来的，用于做取消任务操作的。 Disposable.disposed() 返回的是一个 EmptyDisposable.INSTANCE 枚举（EmptyDisposable 对象），EmptyDisposable 它的 dispose() 是一个空实现，表示并不需要执行任何额外的取消操作。
    @Override
    public void onSubscribe(Disposable d) {
        t.onSubscribe(d);
    }


    //MapSingleObserver.java 下游 1
    //MapSingleObserver（下游1）收到上游 0 传过来的 onSuccess 事件，该事件携带了上游 0 发过来的数据。 
    //MapSingleObserver 会将映射函数（mapper）应用到上游 0 传过来的 value 上，也就是对 value 执行映射操作，将映射后的 value 通过 onSuccess 事件传递给下游 0。
    //mapper 是调用 Single.map(...) 方法是传进去的 Function 对象，是由开发者自定义的
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
//interval 返回 ObservableInterval 作为原始被观察者（上游 0）。其内部会通过 Schedulers.computation 创建一个 ComputationTask 对象，它是用来执行线程的创建和切换的。
interval {
    //Observable.java
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
    //ObservableInterval.java  上游 0
    //ObservableInterval.subscribe 方法被调用时，实际执行的是 subscribeActual （被观察者被订阅时，在该函数中发送）。
    @Override
    public void subscribeActual(Observer<? super Long> observer) {
        //将 observer（下游 0）封装到 IntervalObserver 中，它继承自 AtomicReference 类，并实现了 Runnable 和 Disposable 接口，但它并没有实现 Observer 接口。因此它只是对下游 0 做了一层包装，将其包装成了一个可以被取消的任务，并且在任务执行完成之后自动会将结果发送给观察者（下游 0）（所以才要将 observer 封装到其内部）。
        //可以认为 IntervalObserver 是一个线程安全的 Disposable，它实现了 Disposable 接口表示它是一个可取消的对象，不过它继承了 AtomicReference<Disposable> 又表示实际执行 dispose 操作的是它所指向的那个 Disposable。也就是当调用 IntervalObserver 的 dispose 方法时，它实际会调用 IntervalObserver 所指向的那个 Disposable 的 dispose 操作。
        IntervalObserver is = new IntervalObserver(observer);
        //上游 0 发送 onSubscribe 事件给下游 0（observer），然后传递 IntervalObserver 过去，让调用者可以通过此对象来取消订阅。
        observer.onSubscribe(is);

        //scheduler 是执行 Observable.interval 时内部创建的 ComputationTask 对象。
        Scheduler sch = scheduler;

        //走 else 分支，sch 是 ComputationTask 的实例，并不是 TrampolineScheduler 的子类
        if (sch instanceof TrampolineScheduler) {
            Worker worker = sch.createWorker();
            is.setResource(worker);
            worker.schedulePeriodically(is, initialDelay, period, unit);
        } else {
            //is 就是 IntervalObserver 对象，它是一个 Runnable。这里将创建一个定时任务，在延迟 initialDelay 之后，以指定的频率（每 period 个 unit 执行一次，unit 是时间单位（例如 SECOND 表示秒））执行 is。而 IntervalObserver 的 run 方法则是不断的往下游发送 onNext 事件。
            //最终就是在延迟 initialDelay 之后，每 period 个 unit 向下游 0 发送 onNext 事件，携带对应的数据
            //也可以理解为创建了一个可取消的定时器，只要这个定时器没有被取消，就会按照指定的频率执行传进去的 is（也就是 IntervalObserver）。schedulePeriodicallyDirect 返回的是一个 ScheduledRunnable 对象，顾名思义，这是一个已被安排的任务，也就是将要最近将要被执行的任务。将此对象传到 IntervalObserver 内部作为 Disposable，表示用户可以调用 ScheduledRunnable 的 dispose 方法来取消任务。
            Disposable d = sch.schedulePeriodicallyDirect(is, initialDelay, period, unit);
            //设置 IntervalObserver 的实际 Disposable，其实就是将上面获取的 ScheduleRunnable 放到 IntervalObserver 里面去，当 IntervalObserver 被取消时，实际执行取消操作的是 IntervalObserver 内部的定时任务
            //Disposable 是需要传递给下游的，这样设计的好处是在 onSubscribe 方法中给下游设置了 Disposable 之后，之后即使 Disposable 发生了变化，也不必再重新给下游设置 Disposable 了，只需更改 IntervalObserver 内部的 Disposable 即可，因此 IntervalObserver 其实是将下游与内部的 Disposable 挂接起来了，而 IntervalObserver 内部的 Disposable 是可以神不知鬼不觉的被替换的。
            is.setResource(d);
        }
    }

    //定时器内部执行的任务，就是给下游发送 onNext 事件
    ObservableInterval.run {
        @Override
        public void run() {
            //get 方法获取 ObserverableInterval 自身的状态，以此检查调用者有没有取消定时器。
            //downstream 就是创建 IntervalObserver 时传进来的 Observer，也就是下游 0，也就是在任务在执行完成之后会自动将执行结果发送给下游。
            if (get() != DisposableHelper.DISPOSED) {
                downstream.onNext(count++);
            }
        }
    }

    //取消定时任务。
    IntervalObserver.dispose {
        //IntervalObserver.java
        @Override
        public void dispose() {
            //取消 IntervalObserver 内部引用的真正工作的 Disposable
            DisposableHelper.dispose(this);
        }

        //DisposableHelper.java
        //DisposableHelper 是一个枚举类。
        public static boolean dispose(AtomicReference<Disposable> field) {
            //获取 IntervalObserver 所指向的那个 Disposeable，IntervalObserver 继承了 AtomicReference 类，即它只是一个引用，它内部指向的 Disposable 才是真正工作的 Disposable
            Disposable current = field.get();
            //DISPOSED 表示订阅已取消。
            Disposable d = DISPOSED;
            //检查当前是否是已取消的状态，如果已经被取消了，则不再执行取消操作。
            if (current != d) {
                //这是一个原子操作，获取当前指向的 Disposable，并将当前指向的 Disposable 替换为 DISPOSED，表示任务已取消订阅。
                current = field.getAndSet(d);
                //再一次判断当前订阅是否已被取消，这是防止重复取消的情况。
                //例如线程 A 再第一次执行 if(current != d) 时为真，但 "current = field.getAndSet(d)" 还没执行完，线程 B 也执行到第一句 “if(current != d)” 了，此时也为真，但由于线程 A 正在执行 “current = field.getAndSet(d)” 因此 B 进入等待状态，当 A 执行完毕了，此时 IntervalObserver 内部指向的 Disposable 已经是 DISPOSED 了。
                //线程 B 继续执行 “field.getAndSet(d)” ，返回的 current 就是 DISPOSED ，第二次 if(current != d) 就会返回 false，避免重复执行 dispose() 操作。
                if (current != d) { 
                    if (current != null) {
                        current.dispose();
                    }
                    return true;
                }
            }
            return false;
        }

        //IntervalObserver.java
        //执行 ObservableInterval 的 subscribeActual 时会调用此方法，将参数 d 作为 IntervalObserver 真正引用的 Disposable。首次调用时传进来的 Disposable 是一个可被取消定时任务
        public void setResource(Disposable d) {
            DisposableHelper.setOnce(this, d);
        }

        //向 IntervalObserver 设置 Disposable
        public static boolean setOnce(AtomicReference<Disposable> field, Disposable d) {
            Objects.requireNonNull(d, "d is null");
            //
            if (!field.compareAndSet(null, d)) {
                d.dispose();
                if (field.get() != DISPOSED) {
                    reportDisposableSet();
                }
                return false;
            }
            return true;
        }
    }

    schedulePeriodicallyDirect {
        //Scheduler.java
        @NonNull
        //传进来的 Runnable 是 IntervalObserver 的对象
        public Disposable schedulePeriodicallyDirect(@NonNull Runnable run, long initialDelay, long period, @NonNull TimeUnit unit) {
            //createWorker 具体实现在 ComputationScheduler 中，创建一个 EventLooperWorker 对象并返回
            final Worker w = createWorker();

            //钩子函数，线程调度时执行
            final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

            //PeriodicDirectTask 实现了 Disposable ，Runnable，SchedulerRunnableIntrospection 接口，它是对 Runnable 的一个包装，可以执行和取消它里面真正的 Runnable
            PeriodicDirectTask periodicTask = new PeriodicDirectTask(decoratedRun, w);

            //安排一个周期任务。
            Disposable d = w.schedulePeriodically(periodicTask, initialDelay, period, unit);
            if (d == EmptyDisposable.INSTANCE) {
                return d;
            }

            return periodicTask;
        }

        //Scheduler.java
        @NonNull
        //run 参数是一个 PeriodicDirectTask 对象，它实现了 Runnable 接口。
        public Disposable schedulePeriodically(@NonNull Runnable run, final long initialDelay, final long period, @NonNull final TimeUnit unit) {
            //SequentialDisposable 是一个 Disposable 容器，它允许以原子的方式更新和替换 Disposable，也可以 dispose 其本身
            final SequentialDisposable first = new SequentialDisposable();

            //将 first 放入 sd 容器里面
            final SequentialDisposable sd = new SequentialDisposable(first);

            final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

            //时间间隔，将时间转成以毫秒为单位。
            final long periodInNanoseconds = unit.toNanos(period);
            //初始延迟
            final long firstNowNanoseconds = now(TimeUnit.NANOSECONDS);
            //Runnable 首次执行的时间
            final long firstStartInNanoseconds = firstNowNanoseconds + unit.toNanos(initialDelay);

            //安排一个可取消的延时任务，并将该延时任务作为 Disposable 返回。（返回的实际上是一个 ScheduleRunnable 对象）
            Disposable d = schedule(new PeriodicTask(firstStartInNanoseconds, decoratedRun, firstNowNanoseconds, sd, periodInNanoseconds), initialDelay, unit);

            if (d == EmptyDisposable.INSTANCE) {
                return d;
            }
            //将 first 替换成 d，也就是替换了 sd 容器中的 Disposable
            first.replace(d);

            return sd;
        }

        //ComputationScheduler.java
        //schedule 由 Scheduler 的子类实现。Schedulers.IO 返回的是 ComputationTask ，它是 ComputationScheduler 对象。
        @NonNull
        @Override
        public Disposable schedule(@NonNull Runnable action) {
            if (disposed) {
                return EmptyDisposable.INSTANCE;
            }
            //poolWorker 是在调用 createWorker 方法时进行实例化的。poolWorker 是一个 PoolWorker 对象，PoolWorker 是 NewThreadWorker 的子类，NewThreadWorker 里面封装了 ExecutorService，本质上是通过 ExecutorService 来切换线程。scheduleActual 具体实现在 NewThreadWorker 中。
            return poolWorker.scheduleActual(action, 0, TimeUnit.MILLISECONDS, serial);
        }

        //NewThredWorker.java
        @NonNull
        public ScheduledRunnable scheduleActual(final Runnable run, long delayTime, @NonNull TimeUnit unit, @Nullable DisposableContainer parent) {
            Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

            //ScheduledRunnable 是 AtomicReferenceArray 的子类，实现了 Runnable, Callable<Object>, Disposable 接口。
            //传进来的 run 是 PeriodicTask 对象，PeriodicTask 实现了 Runnable 和 SchedulerRunnableIntrospection 接口。
            ScheduledRunnable sr = new ScheduledRunnable(decoratedRun, parent);

            if (parent != null) {
                if (!parent.add(sr)) {
                    return sr;
                }
            }

            //通过线程池来执行后台任务，使用 Future 获取线程计算的结果。
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
}
```


实例四：
对于 Single.delay 这种只有一个，没有后续但是有延时，需要做一次置换，在上游调用观察者的 onSubscribe 时要给 SequentialDisposable 内部赋个值，此时是将上游传递过来的 Disposable 赋值给了 SequentialDisposable 内部，这样当下游想取消时，可以通知到上游。
而之后的 onSuccess 和 onError 则会置换 SequentialDisposable 内部的 Disposable，因为要启动一个延时器，将这个延时器作为 Disposable 置换到 SequentialDisposable 内部，这样当下游要取消任务时，直接取消这个延时器即可，也就是当上游发送了 onSuccess 事件之后，整个事件就和上游没有关系了，具体的延时是在 SingleDelay#Delay 中完成的，这是一个 Observer。

SingleDelay.subscribe(SingleObserver) -> SingleObserver.onSubcribe()（下游） -> SingleJust.subscribe(Delay)（上游） -> Delay.onSubscribe()（链接上游的 Disposable 到下游） -> SingleJust.onSuccess() -> Delay 执行延时操作 -> SingleObserver.onSuccess() 
```java
//有延迟无后续的新创建事件
Single.just(1).delay(1, TimeUnit.SECONDS).subscribe(object: SingleObserver<Int> {
    override fun onSubscribe(d: Disposable?) {

    }

    override fun onSuccess(t: Int?) {

    }

    override fun onError(e: Throwable?) {

    }
})
```

```java
delay {
    //Single.java
    //返回 SingleDelay 对象作为桥梁
    @CheckReturnValue
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    @NonNull
    public final Single<T> delay(long time, @NonNull TimeUnit unit) {
        return delay(time, unit, Schedulers.computation(), false);
    }

    @CheckReturnValue
    @NonNull
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Single<T> delay(long time, @NonNull TimeUnit unit, @NonNull Scheduler scheduler, boolean delayError) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");
        return RxJavaPlugins.onAssembly(new SingleDelay<>(this, time, unit, scheduler, delayError));
    }
}

subcribe {
    //SingleDelay.java
    @Override
    protected void subscribeActual(final SingleObserver<? super T> observer) {

        //SequentialDisposable 继承了 AtomicReference<Disposable> 类并实现了 Disposable 接口，即它也是一个可取消的 Disposable，不过实际执行任务的是它内部所指向的那个 Disposable
        final SequentialDisposable sd = new SequentialDisposable();
        //触发下游的 onSubscribe 事件，此时 SD 内部还没有设置 Disposable
        observer.onSubscribe(sd);
        //Delay 的构造函数中保存了 SD 作为 Disposable 和 observer 作为下游
        source.subscribe(new Delay(sd, observer));
    }


    //SingleDelay.Delay
    //delay 操作是先触发下游的 onSubscribe 事件，然后再上游执行 onSubscrive 时设置 Disposable，这个 Disposable 就是 SingleJust 调用 observer.onSubscribe 时传过来的 Disposable.disposed()。
    @Override
    public void onSubscribe(Disposable d) {
        sd.replace(d);
    }

    //SingleDelay.java
    @Override
    public void onSuccess(final T value) {
        //scheduler 是对 Java 的 Executor 做了一个包装，本质上是一个线程调度器，就是指定延时之后调用 onSuccess
        //在这个期间，如果上游想要取消，应该取消 scheduler.scheduleDirect(new OnSuccess(value), time, unit) 返回的任务。
        sd.replace(scheduler.scheduleDirect(new OnSuccess(value), time, unit));
    }

    //SingleDelay.java
    @Override
    public void onError(final Throwable e) {
        sd.replace(scheduler.scheduleDirect(new OnError(e), delayError ? time : 0, unit));
    }
}
```





实例五：
```java
//有延迟有后续
//延迟一秒之后，以每秒钟 1 次的频率向下游发送当前时间。
//interval 返回 ObservableInterval 对象，这是上游
//map 返回 ObservableMap 作为被观察者的桥梁，与上游对接
Observable.interval(1, 1, TimeUnit.SECONDS).map {
    it.toString()
}.subscribe(object: Observer<String>{
    override fun onSubscribe(d: Disposable?) {

    }

    override fun onNext(t: String?) {

    }

    override fun onError(e: Throwable?) {

    }

    override fun onComplete() {

    }
})
```

```java
//将 ObservableInterval(上游 0) 封装到 ObservableMap(上游 1) 中，返回 ObservableMap 对象
map {
    public final <R> Observable<R> map(@NonNull Function<? super T, ? extends R> mapper) {
        Objects.requireNonNull(mapper, "mapper is null");
        //将 ObservableInterval(上游 0) 封装到 ObservableMap(上游 1) 中，返回 ObservableMap 对象
        return RxJavaPlugins.onAssembly(new ObservableMap<>(this, mapper));
    }
}
事件传递顺序：ObservableInterval（上游 1） -> ObservableMap（上游 0） -> MapObserver（下游 1） -> Observer（下游 0）

订阅顺序：ObservableMap（上游 0， 将 Observer 封装到 MapObserver 中） -> ObservableInterval（上游 1， 将 MapObserver 封装到 IntervalObserver 中，传递给 MapObserver） -> MapObserver（下游 1） -> Observer（下游 0）

事件发送顺序： IntervalObserver.run -> MapObserver.onNext() -> Observer.onNext()


subscribe {
    

    //上游 1，ObservableMap.java
    @Override
    public void subscribeActual(Observer<? super U> t) {
        //将传进来的 Observer(下游 0) 封装到 MapObserver(下游 1) 中，将 ObservableInterval(上游 0) 与 MapObserver(下游 1) 关联起来，之后上游 0 发送的事件将由下游 1 接收
        //ObservableInterval(上游 0) 发送 onSubscribe 事件给 MapObserver(下游 1)，onSubscribe 的具体实现位于 MapObserver 的父类 BasicFuseableObserver 中。
        source.subscribe(new MapObserver<T, U>(t, function));
    }

    //ObservableInterval.Java  
    //里面的 observer 是下游 1
    @Override
    public void subscribeActual(Observer<? super Long> observer) {
        //创建 IntervalObserver 对象，它继承自 AtomicReference 类，并实现了 Runnable 和 Disposable 接口。是一个任务对象
        IntervalObserver is = new IntervalObserver(observer);
        //将 onSubscribe 事件传递给 MapObserver（下游 1），onSubscribe 的具体实现在 MapObserver 的父类 BasicFuseableObserver 中
        observer.onSubscribe(is);

        Scheduler sch = scheduler;

        if (sch instanceof TrampolineScheduler) {
            .... //一般不会走这里
        } else {
            //通过 Scheduler 安排再指定初始延迟后，按给定的频率间隔执行 is，返回的 Disposable 其实还是 IntervalObserver，它实现了 Runnable 和 Disposable 接口，可以看作是一个可取消的任务。
            Disposable d = sch.schedulePeriodicallyDirect(is, initialDelay, period, unit);
            //设置实际工作的 Disposable
            is.setResource(d);
        }
    }


    //BasicFuseableObserver.java（MapObserver.onSubscribe），BasicFuseableObserver 是一个有上游有下游的 Observer，Fuseable 是可熔断的意思，即熔断上游的 Observer 和 下游的 Observer
    //参数里面的 d 就是上面传过来的 IntervalObserver
    @Override
    public final void onSubscribe(Disposable d) {
        //如果 MapObserver 已经有上游，通知这个上游“我要与你脱钩了”，然后使用 d 作为新的上游
        if (DisposableHelper.validate(this.upstream, d)) {

            this.upstream = d;
            if (d instanceof QueueDisposable) {
                this.qd = (QueueDisposable<T>)d;
            }

            //在订阅前执行的操作，beforeDownstream 返回 true
            if (beforeDownstream()) {

                //发送 onSubscribe 事件给下游 0，将 MapObserver 本身一起发给了下游
                //BasicFuseableObserver 实现了 QueueDisposable<R> 接口
                downstream.onSubscribe(this);

                //订阅完成之后执行的操作，默认不执行任何操作
                afterDownstream();
            }

        }
    }


    //ObservableMap#MapObserver.java
    //将 onNext 事件传给最终的下游
    @Override
    public void onNext(T t) {
        if (done) {
            return;
        }

        if (sourceMode != NONE) {
            downstream.onNext(null);
            return;
        }

        U v;

        try {
            //执行转换操作，然后将转换之后的数据传给最终的下游。
            v = Objects.requireNonNull(mapper.apply(t), "The mapper function returned a null value.");
        } catch (Throwable ex) {
            fail(ex);
            return;
        }
        downstream.onNext(v);
    }


    //BasicFuseableObserver.java
    //直接调用上游的 dispose，MapObserver 的上游是 IntervalObserval
    @Override
    public void dispose() {
        upstream.dispose();
    }
}

```





实例六：有延迟无后续
```java
//有延迟无后续
Observable.interval(1, 1, TimeUnit.SECONDS).delay(1, TimeUnit.SECONDS).subscribe(object: Observer<Long>{
    override fun onSubscribe(d: Disposable?) {

    }

    override fun onNext(t: Long?) {

    }

    override fun onError(e: Throwable?) {

    }

    override fun onComplete() {

    }
})
```

```java

//将 Observable 封装成 ObservableDelay 对象，并将其返回
delay {
    @CheckReturnValue
    @SchedulerSupport(SchedulerSupport.COMPUTATION)
    @NonNull
    public final Observable<T> delay(long time, @NonNull TimeUnit unit) {
        return delay(time, unit, Schedulers.computation(), false);
    }

    @CheckReturnValue
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    @NonNull
    public final Observable<T> delay(long time, @NonNull TimeUnit unit, @NonNull Scheduler scheduler, boolean delayError) {
        Objects.requireNonNull(unit, "unit is null");
        Objects.requireNonNull(scheduler, "scheduler is null");

        return RxJavaPlugins.onAssembly(new ObservableDelay<>(this, time, unit, scheduler, delayError));
    }
}

subscribe {
    //ObservableDelay.java
    //t 为下游 0，将 t 封装到 SerializedObserver(下游 1) 中，再将 SerializedObserver 封装到 DelayObserver(下游 2) 中
    //SerializedObserver 实现了 Observer<T> 和 Disposable 接口，它也是一个观察者。
    @Override
    @SuppressWarnings("unchecked")
    public void subscribeActual(Observer<? super T> t) {
        Observer<T> observer;
        if (delayError) {//delayError 为 false
            observer = (Observer<T>)t;
        } else {
            //它主要是防止事件的同时发送，有多个事件需要发送时，会将这些事件放入一个列表中，一个一个发送
            observer = new SerializedObserver<>(t);
        }

        //创建一个调度器，执行延时操作
        Scheduler.Worker w = scheduler.createWorker();

        //创建 DelayObserver 对象(下游 2)
        //将 DelayObserver(下游 2) 封装到 IntervalObserver(Disposable 对象) 中，IntervalObserver 是一个可被取消的 Runnable，这个 Runnable 会通过 onSubscribe 事件传给 DelayObserver(下游 2)
        source.subscribe(new DelayObserver <>(observer, delay, unit, w, delayError));
    }


    //DelayObserver 实现了 Disposable 接口。
    //当 DelayObserver 收到 onNext，onError，onComplete 事件时，会通过上面的 Scheduler.Worker 执行一秒的延迟之后，再往下游发送事件
    @Override
    public void dispose() {
        //取消上游
        upstream.dispose();
        //取消延时任务
        w.dispose();
    }
}
```






实例六，线程切换（subscribeOn）
1. 使用 Scheduler 切线程
2. 在新的线程中执行对上游的订阅
3. 当下游调用 disposed 取消时，做两件事，通知上游取消任务，取消线程切换任务。
```java
//subscribeOn 切换的是下游执行的线程。
Single.subscribeOn {
    // Single.java
    // 创建并返回 SingleSubscribeOn 作为上游 n（假设调用了 subscribeOn 的 Observable 为上游 n-1）
    @CheckReturnValue
    @NonNull
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    public final Single<T> subscribeOn(@NonNull Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        return RxJavaPlugins.onAssembly(new SingleSubscribeOn<>(this, scheduler));
    }


    //SingleSubscribeOn.java
    //SubscribeOnObserver 做两件事，一件是作为 Runnable 用于执行任务，另一件事用作 Disposable 传递给下游，让下游能够取消任务。
    @Override
    protected void subscribeActual(final SingleObserver<? super T> observer) {
        //创建 SubscribeOnObserver(下游n) ，将传进来的 observer(下游n-1) 封装到 SubscribeOnObserver(下游 n) 中，source 是上游 n-1
        final SubscribeOnObserver<T> parent = new SubscribeOnObserver<>(observer, source);
        //发送 onSubscribe 事件给下游 n-1，并将 SubscribeOnObserver(一个 Disposable，用于终止订阅) 传递给下游。
        //在没有进行线程切换时，onSubscribe() 事件的发送是在下游 n 的 onSubscribe 中执行的。此时还是处于原始的线程
        //SubscribeOnObserver 之前的被观察者的 onSubscribe 事件在原始线程中被发送给观察者对应的被观察者，而 SubscribeOnObserver 之后的被观察者的 onSubscribe 事件在新线程中发送
        observer.onSubscribe(parent);

        //通过 scheduler.scheduleDirect 切换线程，SubscribeOnObserver 也是一个 Runnable，通过 scheduler 在另一个线程上执行 Runnable
        Disposable f = scheduler.scheduleDirect(parent);

        //parent.task 是一个 SequentialDisposable 对象，它继承了 AtomicReference 类并实现了 Disposable 接口，所以它是一个 Disposable 的外壳，它是一个可取消的线程切换任务。
        //设置 Disposable 到 SequentialDisposable 内部作为真正的 Disposable，
        parent.task.replace(f);

    }

    //新线程
    //SubscribeOnObserver.java
    @Override
    public void run() {
        //SingleSubscribeOn 的 subscribeActual 方法中，开启了一个新线程，在里面让上游 n-1 和下游 n 进行关联，触发上游 n-1 的 subscribeActual 方法。
        //也就是说，上游 0 的 subscribeActual 也是在新线程中，即上游 0 发送的所有事件（onSubscribe，onNext等）也都是在新线程中。
        //从 SingleSubscribeOn 的 subscribeActual 开始，之后的被观察者发送的的所有事件都会在新的线程中发送，不过 SingleSubscribeOn 的订阅依然是在原始线程，因此需要在原始线程中发送 onSubscribe 事件
        //触发上游 n-1 的 subscribeActual 方法，一直到上游 0 的 subscribeActual 被调用为止，上游 0 的 subscribeActual 中开始发送各个事件，此时处于新线程
        source.subscribe(this);
    }

    //SingleSubscribeOn 的 subscribeActual 中向下游 n-1 发送了 onSubscribe 事件，因此 SubscribeOnObserver(下游 n) 就无需向下游 n-1 发送 onSubscribe 事件了。
    @Override
    public void onSubscribe(Disposable d) {
        //设置真正的 Disposable，这是上游传过来的，
        DisposableHelper.setOnce(this, d);
    }

    //SubscribeOnObserver.java
    @Override
    public void dispose() {
        //通知上游取消任务。
        DisposableHelper.dispose(this);
        //取消线程切换任务
        task.dispose();
    }
}



Observable.subscribeOn {

    @CheckReturnValue
    @SchedulerSupport(SchedulerSupport.CUSTOM)
    @NonNull
    public final Observable<T> subscribeOn(@NonNull Scheduler scheduler) {
        Objects.requireNonNull(scheduler, "scheduler is null");
        return RxJavaPlugins.onAssembly(new ObservableSubscribeOn<>(this, scheduler));
    }
}
```




实例七：ObserveOn

1. 在事件达到之后才切线程
```java
//Single.java
//创建 SingleObserveOn(上游 n，假设调用了 observeOn 的观察者是上游 n-1) 实例
@CheckReturnValue
@NonNull
@SchedulerSupport(SchedulerSupport.CUSTOM)
public final Single<T> observeOn(@NonNull Scheduler scheduler) {
    Objects.requireNonNull(scheduler, "scheduler is null");
    return RxJavaPlugins.onAssembly(new SingleObserveOn<>(this, scheduler));
}


//SingleObserveOn.java
//创建 ObserveOnSingleObserver(下游 n)，让 ObserveOnSingleObserver(下游n) 订阅上游 n-1，触发上游 n-1 的 subscribeActual 方法，下游 n 会收到上游 n-1 发过来的 onSubscribe 事件，再往下（下游 n-1，下游 n-2，... ，下游 0）。这是在原始线程
@Override
protected void subscribeActual(final SingleObserver<? super T> observer) {
    source.subscribe(new ObserveOnSingleObserver<>(observer, scheduler));
}


//ObserveOnSingleObserver
@Override
public void onSubscribe(Disposable d) {
    //ObserveOnSingleObserver 继承了 AtomicReference<Disposable> 类，实现了 Disposable, Runnable 接口，因此此处就是设置 ObserveOnSingleObserver 内部引用的 Disposable
    //ObserveOnSingleObserver 实现了 SingleObserver，Disposable，Runnable 接口，且是 AtomicReference<Disposable> 子类
    //将 onSubscribe 事件传给下游 n-1
    if (DisposableHelper.setOnce(this, d)) {
        downstream.onSubscribe(this);
    }
}



//切换到一个新的线程，再新线程中执行 onError 和 onSuccess 方法。
//在一个新的线程中执行 Runnable（ObserveOnSingleObserver 实现了 Runnable 接口），返回的 Disposable 其实是一个 Worker，它里面封装了 ExecutoerService（线程池）
//Worker 的 dispose() 实际会关闭 ExecutorService
@Override
public void onSuccess(T value) {
    this.value = value;
    Disposable d = scheduler.scheduleDirect(this);
    DisposableHelper.replace(this, d);
}


//ObserveOnSingleObserver.run() 是在新线程中执行的，因此 onError() 和 onSuccess() 事件的下发也是在新线程中完成的。
//SingleObserveOn#ObserveOnSingleObserver.java
@Override
public void run() {
    Throwable ex = error;
    if (ex != null) {
        downstream.onError(ex);
    } else {
        downstream.onSuccess(value);
    }
}



//收到事件之后，ObserveOnSingleObserver 内部的 Disposable 是上游传过来的，因此此时若是下游取消则是直接通知上游去取消任务
//在收到事件之后 ObserveOnSingleObserver 内部的 Disposable 就会被置换成调用 scheduler.scheduleDirect(this) 时返回的 Disposable，这也是一个 Worker 对象，里面封装了 ExecutorService。此时若是下游取消了任务，取消的就是线程切换的任务了。
@Override
public void dispose() {
    DisposableHelper.dispose(this);
}
```




















切换线程 源码分析

IoScheduler 和 NewThreadScheduler 的区别在于，在多次使用时，IoScheduler 会重用 executor，而 newThread 每次会创建一个新的 Executor

各种线程库，它不可能自己做东西，基本用的都是原生的东西。只是封装的比较好用，而且可能会比自己写更省资源。

```java 
io {
    //Schedulers.java
    //io 实际返回的是 IoScheduler 实例
    @NonNull
    public static Scheduler io() {
        //IO = RxJavaPlugins.initIoScheduler(new IOTask());
        return RxJavaPlugins.onIoScheduler(IO);
    }


    static final class IOTask implements Supplier<Scheduler> {
        @Override
        public Scheduler get() {
            //static final Scheduler DEFAULT = new IoScheduler();
            return IoHolder.DEFAULT;
        }
    }

    public IoScheduler() {
        //WORKER_THREAD_FACTORY = new RxThreadFactory(WORKER_THREAD_NAME_PREFIX, priority);
        this(WORKER_THREAD_FACTORY);
    }

    public IoScheduler(ThreadFactory threadFactory) {
        this.threadFactory = threadFactory;
        this.pool = new AtomicReference<>(NONE);
        start();
    }

    @Override
    public void start() {
        //KEEP_ALIVE_TIME 默认为 60
        //KEEP_ALIVE_UNIT 默认为 TimeUnit.SECONDS
        CachedWorkerPool update = new CachedWorkerPool(KEEP_ALIVE_TIME, KEEP_ALIVE_UNIT, threadFactory);
        if (!pool.compareAndSet(NONE, update)) {
            update.shutdown();
        }
    }


    //CachedWorkerPool 实现了 Runnable 接口
    CachedWorkerPool(long keepAliveTime, TimeUnit unit, ThreadFactory threadFactory) {
        this.keepAliveTime = unit != null ? unit.toNanos(keepAliveTime) : 0L;
        this.expiringWorkerQueue = new ConcurrentLinkedQueue<>();
        this.allWorkers = new CompositeDisposable();
        this.threadFactory = threadFactory;

        ScheduledExecutorService evictor = null;
        Future<?> task = null;
        if (unit != null) {
            //创建 Executor，即线程池
            evictor = Executors.newScheduledThreadPool(1, EVICTOR_THREAD_FACTORY);
            task = evictor.scheduleWithFixedDelay(this, this.keepAliveTime, this.keepAliveTime, TimeUnit.NANOSECONDS);
        }
        evictorService = evictor;
        evictorTask = task;
    }
}


newThread {
    @NonNull
    public static Scheduler newThread() {
        //NEW_THREAD = RxJavaPlugins.initNewThreadScheduler(new NewThreadTask());
        return RxJavaPlugins.onNewThreadScheduler(NEW_THREAD);
    }

    static final class NewThreadTask implements Supplier<Scheduler> {
        @Override
        public Scheduler get() {
            //static final Scheduler DEFAULT = new NewThreadScheduler();
            return NewThreadHolder.DEFAULT;
        }
    }
}


AndroidSchedulers.mainThread {
    //AndroidSchedulers.java
    public static Scheduler mainThread() {
        //private static final Scheduler MAIN_THREAD = RxAndroidPlugins.initMainThreadScheduler(() -> MainHolder.DEFAULT);
        return RxAndroidPlugins.onMainThreadScheduler(MAIN_THREAD);
    }


    //创建一个主线程的 Handler，通过 scheduleDirect 切换线程时通过此 Handler 将消息推到主线程
    static final Scheduler DEFAULT = new HandlerScheduler(new Handler(Looper.getMainLooper()), true);


    @Override
    @SuppressLint("NewApi") // Async will only be true when the API is available to call.
    public Disposable scheduleDirect(Runnable run, long delay, TimeUnit unit) {
        if (run == null) throw new NullPointerException("run == null");
        if (unit == null) throw new NullPointerException("unit == null");

        run = RxJavaPlugins.onSchedule(run);
        ScheduledRunnable scheduled = new ScheduledRunnable(handler, run);
        Message message = Message.obtain(handler, scheduled);
        if (async) {
            message.setAsynchronous(true);
        }
        handler.sendMessageDelayed(message, unit.toMillis(delay));
        return scheduled;
    }

}



@NonNull
public Disposable scheduleDirect(@NonNull Runnable run) {
    return scheduleDirect(run, 0L, TimeUnit.NANOSECONDS);
}

//Scheduler.java
@NonNull
public Disposable scheduleDirect(@NonNull Runnable run, long delay, @NonNull TimeUnit unit) {
    // Worker 本质上是一个包了 Executors（线程池） 的调度器
    final Worker w = createWorker();

    //钩子
    final Runnable decoratedRun = RxJavaPlugins.onSchedule(run);

    //
    DisposeTask task = new DisposeTask(decoratedRun, w);

    //
    w.schedule(task, delay, unit);

    return task;
}

Schedulers.newThread {

    //NewThreadScheduler.java
    @NonNull
    @Override
    public Worker createWorker() {
        //threadFactory 默认为 THREAD_FACTORY ，THREAD_FACTORY = new RxThreadFactory(THREAD_NAME_PREFIX, priority);
        //RxThreadFactory 是一个线程工厂
        return new NewThreadWorker(threadFactory);
    }

    //NewThreadWorker.java
    public NewThreadWorker(ThreadFactory threadFactory) {
        executor = SchedulerPoolFactory.create(threadFactory);
    }

    //SchedulerPoolFactory.java
    //创建一个 Executors，即线程池
    public static ScheduledExecutorService create(ThreadFactory factory) {
        //核心线程数为 1，
        final ScheduledExecutorService exec = Executors.newScheduledThreadPool(1, factory);
        tryPutIntoPool(PURGE_ENABLED, exec);
        return exec;
    }
}


Schedulers.io {
    //IoScheduler.java
    @NonNull
    @Override
    public Worker createWorker() {
        //默认 pool = new AtomicReference<>(NONE);
        return new EventLoopWorker(pool.get());
    }

    //IoScheduler#EventLoopWorker
    EventLoopWorker(CachedWorkerPool pool) {
        this.pool = pool;
        this.tasks = new CompositeDisposable();
        //首先从一个 expiringWorkerQueue 里面获取，如果获取不到，则 new 一个 ThreadWorker 返回，ThreadWorker 是 NewThreadWorker 的子类。
        //这里的 threadWorker 是从 CachedWorkerPool 里面获取的
        this.threadWorker = pool.get();
    }

    //返回的是 ThreadWorker，所以 Schedulers.io 本质是使用了 Schedulers.newThread
    //IoScheduler#CachedWorkerPool.get()
    ThreadWorker get() {
        if (allWorkers.isDisposed()) {
            return SHUTDOWN_THREAD_WORKER;
        }
        //expiringWorkerQueue 是一个 ConcurrentLinkedQueue<ThreadWorker> 对象，一个并发队列，因此每次获取 ThreadWorker 时会先判断这个队列中是否有可重用的 ThreadWorker，如果有则返回，如果没有则创建一个新的 ThreadWorker
        while (!expiringWorkerQueue.isEmpty()) {
            ThreadWorker threadWorker = expiringWorkerQueue.poll();
            if (threadWorker != null) {
                return threadWorker;
            }
        }

        // No cached worker found, so create a new one.
        ThreadWorker w = new ThreadWorker(threadFactory);
        allWorkers.add(w);
        return w;
    }

    //IoScheduler#ThreadWorker extends NewThreadWorker
    //ThreadWorker 继承自 NewThreadWorker，NewThreadWorker 构造方法中创建了一个 Executor。
    ThreadWorker(ThreadFactory threadFactory) {
        super(threadFactory);
        this.expirationTime = 0L;
    }

    //NewThreadWorker.java
    public NewThreadWorker(ThreadFactory threadFactory) {
        executor = SchedulerPoolFactory.create(threadFactory);
    }

}

```


