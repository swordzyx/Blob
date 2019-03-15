### Android 四大组件 —— Activity ###
Actvity是Android中的四大组件之一，平常我们在手机一个用程序上所看的界面就是 Activity 的表现形式。而且 Activity 也是用户唯一能够感知到的组件，每一个UI的界面就是通过 Activity 显示在屏幕上的，平常我们在软件里进行的各种页面的切换很大一部分是不同 Activity 的跳转。

我们在日常生活中不可避免的打开很多个不同的应用， Activity 的生命周期便是从我们点开图标的那一瞬间开始的。，Google官方给出了Activity的生命周期图，如下：<br />
![alt text](https://note.youdao.com/yws/public/resource/66a1078a2b65fd9f105b4d5efe49298c/xmlnote/2B754B4548A7471F918C05C9ADDF2C21/635)     <br/>
先来看看它的几个回调方法:
* **onCreate()**：此方法在活动创建时被触发，此时活动处于创建状态，一般 Activity 的一些初始化的工作在该方法中执行，且在生命周期内只会执行一次。在该方法调用 setContentView 中进行 View 的创建。
* **onStart()**：这个方法紧接着 onCreate ，此时已经是可以看到活动的了，但是无法进行交互。此时 Activity 处于启动的状态。紧接着会回调 onResume() 。
* **onResume()**：此时 Activity 处于 Resume 状态，位于前台完全可见也可以进行交互，我们看到的界面便是 Activity 保持在 onResume() 状态。该方法之后是 onPause() 方法。
* **onPause()**：该方法是在 onResume 之后执行的，此时活动处于暂停状态，不在位于前台，可见但是无法交互。这个方法的执行时间非常短，因此不应该在此方法中执行重量级的资源释放。
* **onStop()**：此时活动对用户已经不可见了。紧接着会回调 onRestart() 或者 onDestory()。此时 Activity 处于 Stopped 状态，活动的控件实例还是存在的，只是没有被显示。此方法中可以释放对用户不可见时的资源。也可以在此方法中进行数据的保存。
* **onRestart()**：表示 Activity 从停止状态重新启动。后面会回调 onStart()
* **onDestroy()**：活动被销毁，这是系统生命周期的最后一个回调。一般在这个回调中进行释放所有还未释放的资源。

用户打开和关闭应用程序的过程其实就是 Activity 在不同的状态之间进行切换的过程。我们所做的就是在正确的状态下执行需要的逻辑。

Activity 的生命周期分为两种情况：一种是正常情况下，一种是异常情况下。
* 正常情况下就是用户正常打开应用，是用完之后按返回键退出，以上的回调方法执行顺序如下：
`onCreate -> onStart -> onResume -> 用户操作 -> onPause -> onStop -> onDestory`
* 当应用在启动或者用户使用的过程中发生了什么意外，就是异常情况下的生命周期，异常情况下的生命周期要分情况讨论：
    * 系统配置发生改变：比如屏幕的方向发生旋转、该改变语言环境，或者输入设备发生更改。当这些发生时， Activity 会被销毁和重建， Activity 会执行`onPause -> onStop -> onDestory -> onCreate -> onStart -> onResume`
    * 手机变为多窗口：Android 7.0 以后，增加了一个“多窗口”模式，应用程序进入多窗口模式或者在多窗口模式中改变大小都会引发系统配置的更改，可以在代码中自行处理配置更改，也可以对 Activity 进行销毁和重建
    * 当前的 Actiivty 被新的 Activity 或者 Dialog 所部分覆盖。这种情况下当前的 Activity 会回调 onPause 方法。当新的 Activity 退出或者 Dialog 消失使会重新调用 onResume 方法。如果完全覆盖则会调用 onStop 方法，再回到该界面时则会回调 onStart 。
    * 系统的内存不足时，会杀死后台的进程来确保前台应用的正常运行。

关于系统配置改变时 Activity 重建的情况，Android 提供了一个属性用于在指定属性发生改变时不重建 Activity，该属性为： android:configChanges ，不同的值用“|”连接。该属性的值如下：
* **mcc**：SIM卡唯一标识IMSI（国际移动用户识别码）中的代码，有三位数组成，中国为460，此项标识mcc代码发生了改变
* **mnc**：SIM卡唯一标识了IMSI（国际移动用户识别码）中的运营商代码，由两位数组成，中国移动TD系统为00，中国联通为01，中国电信为03，此项标识mnc发生了改变
* **locale**：设备本地位置发生了改变，一般指切换了系统语言
* **touchscreen**：触摸屏发生了改变，这个很费解，正常情况下无法发生，可以忽略它
* **keyboard**：键盘类型发生了改变，比如用户使用了外插键盘
* **keyboardHidden**：键盘的可问性发生了改变，比如用户调出了键盘
* **navigation**：系统导航方式发生了改变，比如采用了轨迹球导航，这个很难发生，可以忽略它
* **screenLayout**：屏幕布局发生了改变，很可能是用户激活了另一个显示设备
* **fontStyle**：系统的字体缩放比例发生了改变，比如用于选择了一个新的字号
* **uiMode**：用户界面模式发生了改变，比如是否开启了夜间模式（API 8添加）
* **orientation**：屏幕方向发生了改变，这个是最常用的，比如旋转了手机屏幕
* **screenSize**：当屏幕的尺寸信息发生了改变，当旋转设备屏幕时，屏幕尺寸会发生变化，这个选项比较特殊，它和编译选项有关，当编译选项中的minSdkVersion和targetSdkVersion均低于13时，此选项不会导致Activity重启，否则会导致Activity重启（API 13重新添加）
* **smallestScreenSize**：设备的物理尺寸发生了改变，这个项目和屏幕的方向没有关系，仅仅在实际的物理屏幕尺寸改变时的时候发生，比如用户切换到了外部的显示设备，这个选项和screenSize一样，这个选项不会导致Activity重启否则会导致Activity重启
* **layoutDirection**：当布局发现发生变化，这个属性用的比较少，正常情况下无需修改布局的layoutDirection属性（API 17时添加）

我们常用的只有 locale 、orientation 和 keyboardHidden 这三个选项。当系统配置发生改变时，系统会回调onConfigurationChanged方法。另外，Android 3.2 之后，当设备的方向发生改变时，同时也会触发屏幕的尺寸信息改变，因此 “orientation” 要与 “screenSize” 一起使用才会生效

关于系统进程的优先级，Android 根据进程中运行的组件以及其所处的状态对进程做了一个优先级的划分（从高到低）：
* **前台进程**：用户当前正在使用的进程。一个前台进程需要具备以下条件的任意一个或者几个：
    * 有一个显示在当前用户屏幕的最上面的 Activity ，并且正在与用户进行交互（此时 onResume 已经被调用了）
    * 进程中的有一个广播接收器正在执行 onReceive 方法。
    * 进程中有一个服务正在执行它的某一个回调（ onCreate，  onStart() 或者 onDestory()）
* **可见进程**：正在执行用户感知得到的任务。它需要具备以下条件的一个或几个：
    * 该进程中运行的一个 Activity 对用户可见但不处于交互状态。例如 Activity 被部分覆盖的情况
    * 该进程中有一个运行在前台的服务（正在执行 Service.startForeground() 方法。）
    * 承载一个用于执行特定功能的系统服务，比如动态壁纸，输入法等等，这些服务是能够被用户感知的。
* **服务进程**：该进程包含一个已启动的后台服务，这些服务一般在后台执行一些用户所关心的任务（比如网络数据的上传和下载）。在 Android 会对运行时间过长的服务进行降级以避免服务长时间运行而导致内存泄漏或者内存被大量占用。
* **缓存进程**：该进程在系统中是可有可无的，因此在系统需要内存的时候会杀死。这些进程一般包含大量对用户不可见的 Activity 。系统有一个列表用于保存这些缓存进程，该列表中的最后一个进程是最先被系统回收的进程。

> 注：进程之间的依赖性对进程的优先级也会有影响。比如进程 A 绑定到带有 Context.bind_auto_create标志的服务，那么该服务所在的进程的优先级至少是和 A 进程的优先级一样。

参考：
《Android 开发艺术探索》第一章
[Android Developer Activity](https://developer.android.com/guide/components/activities)



<img width="200" height="200" src="https://note.youdao.com/yws/public/resource/123c1bcad5b727fdc43fc19abcdb46f6/xmlnote/BDC8C5FEC02F4B2B865F496EEBD8DFE0/1281"/>