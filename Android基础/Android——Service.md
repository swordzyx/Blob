### Service 概述
关于 Service 的介绍，官方文档里面有给详细的说明，它是一个可以再用户看不到的情况下长时间执行某项任务的应用组件。它可以由 Activity 通过调用 `startService()` 方法来启动，也可以跟 Activity 进行绑定，Activity 也可以通过绑定到 Service 来与其进行交互。

一般情况下，Service 会有两种状态：
* **启动状态**：Activity 调用 startService 启动服务之后， Service 便位于这个状态。这种状态下，服务会在后台无限期的运行，知道服务的任务执行完毕，一般我们启动服务用于执行某项任务，任务执行完了之后便会退出，且不会反悔结果。
* **绑定状态**：应用组件调用了bindService之后，服务就会处于该状态，此时服务会提供一个客户端的示例，通过该示例可以与Service进行交互，一般用于跨进程通信。一个服务可以同时被多个组件绑定，但是只有当所有绑定的组件都销毁是，这个服务才会销毁。

上面的两种状态在 Service 中是可以共存的，即一个服务启动的同时也可以被绑定。区别是启动服务会回调 `Service` 中的 `onStartCommand` 方法，而绑定则会回调 `onBind()` 方法。在 Android 中的任何组件都可以通过 Intent 来使用服务，即使在不同的应用中，当然这需要你定义的 Service 是对其他应用开放的。

Android 中的四大组件都是需要在 AndroidManifest.xml 中进行声明了才可以可使用，因此当你创建一个服务时，在清单文件也需要使用` <service> `标签来声明。

>默认情况下，Service是在启动它的应用程序进程中运行的，它不会创建自己的线程，因此如果要执行耗时的任务时，为了不阻塞应用程序的主进程，我们应该在 Service 中开启一个子线程。

### Service 的使用
#### 继承 Service 类
要创建一个自己的服务很简单，只需继承 Service 类并重写里面的方法即可。示例如下：
```java
public class MyService extends Service{
	public MyService(){
	
	}
	
	//若没有重写该方法，服务将无法绑定到其他组件
	@Override
	public IBinder onBind(Intent intent){
		throw new UnsupportedOperationException("Not yet implemented");
	}
	
	@Override
	public void onCreate(){
		super.onCreate();
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId){
		return super.onStartCommand(intent, flags, startId);
	}
	
	@Override
	public void onDestory(){
		super.onDestory();
	}
}

```
上面各个方法的含义如下：
* **onCreate()：** 只有第一次创建服务的时候会调用。
* **onBind()：** 前面说过其他组件可以可通过调用 `bindService()` 来绑定服务，该方法就是在服务被绑定的时候调用，这个方法会返回一个 IBinder 接口的实例，其他组件就是通过这个组件来与服务进行通信。
*  **onDestory()：** 当服务被销毁时被调用，一般在这个回调中进行资源的清理。
*   **onStartCommand()：** 当别的组件通过 `startService()` 方法来启动服务的时候，会回调这个方法。一般在这个方法中执行任务的逻辑，注意执行完了之后记得在该方法中调用 `stopSelf()` 或者 `stopService()` 来停止服务。该函数必须返回整型值，用于描述当服务终止时，如何继续运行服务，必须是以下常量之一：
    * **```START_NOT_STICKY```**：表示如果在 `onStartCommand()`  返回后才停止服务，则系统不会重新创建服务，除非有新的Intent传递到该服务
    * **```START_STICKY```**：表示如果该服务被系统 killed 了，该服务会一直保留启动的状态，在下次创建服务时则一定会重新调用 `onStartCommand()` 方法，即使没有启动服务的命令。
    * **```START_REDELIVER_INTENT```**：如果该服务给系统 killed ，将会重新启动，并且最后一次用于启动服务的 Intent 会被重新传递给它。

#### 在AndroidManifest.xml中声明
创建了 Service 的子类以后还要将该服务在清单文件（AndroidManifest.xml）中声明，代码如下：
```xml
<manifest>
    ......
    <service
    	android:name=".MyService"
    	android:enabled="true"
    	android:exported="true">
    </service>
    ........
</manifest>
```
几个属性的含义如下：
* **android:name:** 用于指定服务的类名
* **android:enabled:** 是否启用该服务，默认为是（true）
* **android:exported:** 指定服务是否能被其他应用程序所调用。

#### 启动服务
一般通过将要启动的服务的类名传递到 Intent 中，在将该 Intent 作为 `startService()` 的参数来启动服务的。示例如下：
``` java
    Intent intent = new Intent(MainActivity.this, MyService.class);
    startService(intent);
```
 `startService()` 方法调用了之后会执行 Service 里面的 `onStartCommand()` 方法。如果多次发起启动服务的请求，则会调用多次该方法。启动服务之后会无限制的运行，即使启动服务的组件已经被销毁

#### 停止服务
前面也说过一旦服务启动了之后便会一直运行，因此要么会在组件销毁时调用 ```stopService()``` 停止服务，要么在Service的任务执行完毕之后，调动```stopSelf()``` 自行停止，依次来保证系统的资源能够被释放。如果服务同时处理多个 ```onStartCommand()``` ，则应该调用 `stopSelf(int)` 来停止最近的服务请求。

#### 绑定服务
 Android 提供了 ```bindService()``` 来进行服务的绑定，以便于长期连接。服务必须实现 ```onBind()``` 方法才能进行服务的绑定，该方法返回一个 IBinder 接口实例，用于与服务进行通信。当没有组件绑定到服务时，该服务则会被系统销毁。多个客户端可以同时绑定到一个服务，并且可以通过 ```unbindService()``` 来解绑。

### 前台服务
前台服务时用户主动意识到的服务，因此在内存不足时，系统也不会销毁它。当然前台服务必须以在状态栏上提供通知。 Android 提供了 startForeground(int id,  Notification notification) 方法来将一个服务变成前台服务，该方法有两个参数：
* ```id```：通知的唯一标识符，不能为0，整型
* ```notification```：状态栏的notification 

移除前台服务调用 ```stopForeground()``` 

### 服务的生命周期
官方给出的服务生命周期图如下：

![alt](https://note.youdao.com/yws/public/resource/d51de0a5dfeb9187f65ed4baa90e9499/xmlnote/B2BB194C778642D2A403B78E7F871A65/1992)


* 正常生命周期从 ```onCreate()``` 到 ```onDestory()``` ，与 Activity 差不多
* 服务的有效生命周期则是从 ```onStartCommand()``` 或者 ```onBind()``` ，两者分别对应启动服务和绑定服务，启动服务的有效生命周期与整个生命周期同时结束（即回调 onDestory() 时）。而绑定服务的有效生命周期则在 ```onUnbind()``` 返回时结束。

>不管是以何种方式启动服务的，均有可能与客户端进行绑定，因此即使已经使用 onStartCommand() （即客户端调用 startService ）启动的服务仍可以接收对 onBind() 的调用（客户端调用 bindService() ）

### IntentService
 IntentService 也是Service的子类，对 Service 做了一定的封装，前面说了如果不手动给 Service 开启一个线程的话，默认会运行在主线程中，这会降低正在运行的Activity的性能。因此 Android 提供了 IntentService ，它会创建独立的 Worker 线程来处理请求。

 IntentService 的使用方法也很简单，只需继承 IntentService 类并重写 `onHandleIntent()` 方法即可

 IntnetService 的主要执行了以下操作：
 * 创建独立的工作线程来处理原本交给 `onStartCommand()` 的 Intent 
 * 创建工作队列，将请求逐一传递给 `onHandleIntent()` 去处理。
 * 处理完请求之后会自动停止服务，不需要手动执行 `stopSelf()`
 * 默认实现 `onBind()`，返回 null
 * 默认实现 `onStartCommand()`，将请求一次发送到工作队列然后在 `onHandleIntent()` 中去处理

```java
public class HelloIntentService extends IntentService {
    public HelloIntentService(){
        super("HelloIntentService");
    }
    
    @Override
    protected void onHandleIntent(Intent intent){
        try{
            Thread.sleep(5000);
        }catch(InterruptedException e){
            Thread.currentThread().interrupt();
        }
    }
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId){
        Toast.makeText(this, "service starting", Toast.LENGTH_SHORT).show();
        return super.onStartCommand(Intent intent, int flags);
    }
}

```


###参考文档
* [Android Developers Service]: https://developer.android.com/guide/components/services
* 《第一行代码》 郭霖著