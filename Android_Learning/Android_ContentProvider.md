## 一、简介
ContentProvider 是 Android 提供的一套标准接口，主要用于管理应用存储的数据，并且为外部应用提供数据访问的接口。可以通过 ContentProvider 方便的实现与其他应用程序数据的共享。并且 ContentProvider 提供了数据的安全机制，可以指定哪部分的数据对外公开，哪些数据隐藏。

ContentProvider 是 Android 系统数据访问的一个抽象，可以使用 ContentProvider 访问不同的数据源（比如音频，文件等等），同样如果开发者对应用的数据存储实现做了某些修改，对数据的访问接口不会有影响。

## 一、访问内容提供程序

Android 系统提供了内置的内容提供程序，比如联系人，通话记录等，可以通过 ContentResolver 对象访问这些数据。ContentResolver 提供了对内容提供程序的访问，应用通过 ContentResolver 提交数据访问请求，并且接收来自 ContentProvider 返回的结果，这些请求包括基本的 CURD （增删改查）操作。

#### 1. 查询数据
ContentResolver 提供了以下方法来访问数据：
```java
1. public final Cursor query (Uri uri, 
                String[] projection, 
                Bundle queryArgs, 
                CancellationSignal cancellationSignal)

2. public final Cursor query (Uri uri, 
                String[] projection, 
                String selection, 
                String[] selectionArgs, 
                String sortOrder, 
                CancellationSignal cancellationSignal)

3. public final Cursor query (Uri uri, 
                String[] projection, 
                String selection, 
                String[] selectionArgs, 
                String sortOrder)
```
上面三个函数的作用一样都是用于

## 二、创建一个内容提供程序

如果应用程序需向其他进程提供数据，使用内容提供程序会好一些，如果仅仅只是在应用内使用的话则不需要。










