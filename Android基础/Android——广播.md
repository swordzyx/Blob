### 1. 广播机制简介

Android 中的广播主要是用来在不同的组件间传递消息用的。 app 能够接收来自 Android 系统或者其他 app 所发送的广播。也能像其发送系统提供的或者自定义的广播。例如，Android系统启动时就会发送一个开机广播，如果想要在开机是处理一些逻辑，可以监听该广播。

**应用场景如下：**
* 同一个app内部的同一个组件内的消息通信（单个或多个线程）;
* 同一个app内部的不同组件之间的消息通信（单个或多个进程）
* 不同app之间的组件之间的消息通信
* Android系统在特定情况下与App之间的消息通信



### 2. 广播的接收
想要接收系统或者app发送的广播，需要提前对广播接收器（`BroadcastReceiver`）进行注册。Android提供了两种注册的方式：静态注册和动态注册

#### 2.1 静态注册广播接收器
静态注册是通过` <receiver> `标签在 AndroidManifest.xml 进行声明实现的。通过这种注册方式，再接收到广播时系统会启动应用，即使这个应用没有运行

通过以下步骤可以实现静态注册一个广播接收器
1. 在 AndroidManifest.xml 中定义一个 `<receiver>` 标签：
```xml
    <receiver
        android:name=".CustomBroadcastReceiver"
        android:exported="true">
        
        <intent-filter>
            <action name="android.intent.action.BOOT_COMPLETED"/> 接收开机广播
            <action name="com.zero.test.CUSTOM_ACTION"/> 自定义广播
        </intent-filter>
    </receiver>
    
    
    //监听开机广播需要指定的权限，要加上下面这一句
    <uses-permission android:name="permission.RECEIVER_BOOT_COMPLETED"/>
```
**`<receiver>`的常用属性：**
* `android:enable`：是否启用广播接收器。
* `android:exported`：设置此广播接收器是否可以接收其他应用所发出的广播。它的默认值取决于是否包含过滤器（`<intent-filter>`），如果没有定义`<intent-filter>`标签，则默认为 false ，如果有则为 true。
* `android:permission`：外部的 app 只有声明了该属性所指定的权限，才能向这个广播接收器发送消息。
* `android:process`：指定该广播接收器运行的进程。若没有指定则默认运行在 app 的主进程或者 <application> 所指定的进程。如果该值以`:`开头，则表示开启一个子进程，这个广播接收器就是运行在其中的。

2. 接着创建一个继承自 BroadcastReceiver 的子类，并重写 `onReceiver()` 方法
```java
    public class CustomBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            //弹出一个提示
            Toast.makeText(context, "接收到" + intent.getAction() + "广播", Toast.LENGTH_SHORT).show();
        }
        
    }
```
通过以上两步，系统会在 app 安装时就将广播接收器注册完成，相当于这个广播是跟 app 分离的，即使 app 没有运行，通过发送指定的广播也可以启动这个 app。


#### 2.2 动态注册广播接收器
通过以下步骤可以注册一个动态的广播接收器
1. 创建一个 BroadcastReceiver 或者其子类的实例，拿上面定义个 CustomBroadcastReceiver 为例
```java
    CustomBroadcastReceiver receiver = new CustomBroadcastReceiver();
```

2. 创建一个过滤器（IntentFilter），然后注册
```java
    IntentFilter filter = new IntentFilter();
    filter.addAction("android.intent.action.BOOT_COMPLETED");
    filter.addAction("com.zero.test.CUSTOM_ACTION");
    
    this.registerReceiver(receiver, filter);
```
上面的 this 表示的是一个 Context 对象，可以理解为组件运行的环境。这个 Context 决定了这个广播接收器的生命周期。仅仅当注册广播接收器的 Context 对象有效时，该 BroadcastReceiver 才是有效的。例如，使用 activity 的 context 对象来注册，则当这个 Activity 运行时，这个广播接收器才能接受到广播。

3. 动态注册的广播接收器必须在不需要时取消，否则会发生内存泄漏的情况
```java
    unregisterReceiver(receiver);
```
一般会在 onCreate() 中注册广播接收器，在 onDestroy() 中取消，或者在 onResume() 中注册，在 onPause() 中取消，要看具体的情况。


#### 2.3 静态广播与动态广播的区别

1. 静态广播也叫常驻广播，即使程序没有运行，这个广播也还是存在于 Android 系统之中的，脱离于应用的生命周期。而动态广播则是跟随注册它的组件的生命周期，如果在 Activity 中注册，那么当 Activity 被销毁时，这个广播也就失效了。
2. 静态广播既可以通过显式 Intent 启动（即通过在 Intent 中指定类名），又可以通过隐式 Intent 启动。而动态广播只能通过隐式广播启动。


#### 2.4 广播接收器的状态对进程的影响

当正在运行 BroadcastReceiver 的 onReceive() 中的代码时，此 BroadcastReceiver 所在的进程被认为是前台进程。一般情况下，系统不会将其杀死

当 onReceive() 方法执行结束，系统会判断 BroadcastReceiver 是否还有其他的应用程序的组件在运行，如果没有会被认为是低优先级的进程，则系统会在内存不足时将其杀死以释放内存。

因此不应该在 onReceive() 中开启后台线程，因为当 onReceiver() 运行结束，当前进程可能会因为优先级太低而被系统杀死，从而导致后台线程也被干掉，即便该线程还没有执行完。

如果希望在 onReceive() 执行完之后可以有时间继续执行未完成的线程，可以通过 goAsync() 方法或者在 onReceive() 方法中使用 JobSchedule 调度一个 JobService。不过这样可能会导致主线程的卡顿


### 3. 发送广播

Android 有三种发送广播的方式
- **sendOrderedBroadcast(Intent, String)**。用于发送有序广播。通过该方法发送的广播首先会被优先级最高的广播接收器执行，然后在传递到下一个广播。也可以通过 abortBroadcast() 来终止广播的传递。广播的优先级可以通过 <intent-filter> 标签的 android:priority 属性来指定。如果两个广播接收器有相同的优先级，对于不同类型的广播，会优先执行动态注册的广播接收器。如果同为动态的，则优先执行先注册的。同为静态广播则优先执行先扫描到的（类似于随机）。
- **sendBroadcast(Intent intent)**。向所有广播接收器发送广播，不带顺序。也叫作普通广播。该方法发送的广播不会被某一个广播接收器所终止
- **LocalBroadcastManager.sendBroadcast(Intent)**。该方法发送的广播仅限于在同一个应用中进行消息传递，也叫作本地广播。这种广播效率要比其他两种好，并且不需要担心由广播的发送和接收所带来的的安全问题。如果 app 中不需要像外界发送广播，建议使用本地广播。


### 4. 广播中的权限

#### 4.1 在发送广播时指定权限

可以通过在发送广播的方法中指定权限，来确保只有的 AndroidManifest.xml 中声明了该权限的广播接收器才能够接收到该广播。
```java
    sendBroadcast(new Intent("com.zero.test.CUSTOM_ACTION"), Manifest.permission.SEND_MSM);
    
    //接着你需要在广播接收器所在 app 的 AndroidManifest.xml 中声明该权限
    <uses-permission android:name="android.permission.SEND_MSM"/>
```
关于权限可以指定系统权限，也可以使用自定义的权限。但是自定义的权限是在应用安装时被注册的，因此在发送自定义权限的广播之前要确保定义该权限的应用程序已经被安装。

#### 4.2 在接收广播时指定权限

如果在注册广播接收器时指定了权限，则只有在 AndroidManifest.xml 中声明了相应权限的 app 可以向这个广播接收器发送广播。

1. 静态注册时指定权限
```xml
    <receiver
        android:name=".CustomBroadcastReceiver"
        android:exported="true"
        android:permission="android.permission.SEND_SMS">
        <intent-filter>
            <action android:name="com.zero.test.CUSTOM_ACTION"/>
        </intent-filter>  
    </receiver>
```

2. 动态注册广播时指定权限
```java
    CustomBroadcastReceiver receiver = new CustomBroadcastReceiver();

    IntentFilter filter = new IntentFilter("com.zero.test.CUSTOM_ACTION");
    registerReceiver(receiver, filter, Manifest.permission.SEND_MSM, null);
    
```


### 5. 广播中的一些注意点
- 如果不需要向应用程序以外的 app 或者组件发送广播，建议使用 LocalBroadcastManager 。因为使用 LocalBroadcastManager 的效率更高。能避免了由于向外部发布或者接收广播所导致的安全问题。另外，使用 LocalBroadcastManager 能减少系统的开销
- 尽量动态注册广播接收器。因为可能会存在多个不同的 app 注册了相同的广播的情况，此时可能会因为启动大量的 app 而对设备的性能造成重大影响。而且 Android 中有些广播只会发送给动态注册的广播接收器，比如 CONNECTIVITY_ACTION。
- 不要使用隐式的广播传递敏感的信息，这样可能会导致敏感信息的泄漏。可以通过指定权限、指定包名来控制谁可以接收该广播，或者直接使用 LocalBroadcastManager 来发送本地广播
- 为了防止受到别的 app 所发送的恶意广播，可以在注册广播接收器是指定权限，或者将 <receiver> 标签的 android:exported 属性置为false。来控制谁可以向你发送广播。也可以使用 `LocalBroadcastManager.registerReceiver(BroadcastReceiver receiver, IntentFilter filter) `来将广播接收器限制为仅可接收本地广播
- BroadcastReceiver 的 onReceive() 必须在很短的时间内执行完，否则会触发应用程序无响应。可以在 onReceive() 中方法中使用 goAsync() 或者 JobSchedule 调度一个作业来开启线程。开启线程需要谨慎的。也可以在 onReceive() 中开启一个后台服务
- 尽量不要在 onReceive() 中开启活动，想一想玩手机玩的好好地，突然给调到另一个界面，会不会很想卸载那个应用。可以采用显示通知的方式。


