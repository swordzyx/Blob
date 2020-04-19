## 一、简介
内容提供程序是应用程序向外部应用公开数据的一个组件。它本身是不提供数据存储的实现的，只是为应用中的数据提供一套接口来方便别的应用访问。

它将可以被公开的数据以数据库的表的形式向外部应用程序公开。每一列代表一个字段或者属性，每一行表示一条记录或者一个实体信息。如果某些数据不想公开，可以不提供对于这些数据的访问接口即可。

并且在内容提供程序中可以为数据设定权限，只有具有该权限的应用才能够请求数据，这样可以提高数据的安全性。

#### 1. 支持的数据类型
内容提供程序支持对下面的数据类型的访问
- 整型
- 长整型
- 浮点型
- 长浮点型（双精度）
- BLOB：该类型用于存储二进制的大型对象

#### 2. 作用
内容提供程序主要被用来管理跨应用之间数据的访问。

有两个特点来支持应用之间的数据交互
- 一套标准的接口。有两个角色参与交互，一个是提供程序，一个是请求数据的客户端。提供程序实现一套标准的接口，客户端通过这个标准的接口进行数据请求
- 跨进程。ContentProvider 本身就是一个跨进程的组件，可以非常方便的进行跨进程的数据交互。

#### 3. 它的意义
个人猜测，Android 的大部分组件和 API 都要对数据进行访问，并且这些组件和 API 未必会在同一个进行，使用单纯的数据库或者文件形式很容易产生线程同步问题，而且这个组件用这个接口访问数据，那个组件用那个接口访问数据，代码难以维护，也会造成代码的冗余。因此就需要将数据的访问单独拎出来，这样方便维护而且增加了组件的复用



## 二、通过 ContentResolver 操作数据
ContentResolver 提供给客户端的用来与内容提供程序交互的类。客户端通过 ContentResolver 发送数据请求，内容提供程序返回的数据会通过 ContentResolver 给到客户端。

#### 1. 查询数据

##### （1）发送查询请求
ContentResolver 中有三个方法用于查询数据：
- `public final Cursor query (Uri uri, String[] projection, Bundle queryArgs, CancellationSignal cancellationSignal)`
    - `uri`：用于标识要查询的是哪一个数据源。
    - `projection`：需要从数据源中获取哪些列。前面说过内容提供程序是以表的形式对外提供数据。
    - `cancellationSignal`：可以理解为操作被取消的信号。当执行这个查询操作的时候，如果有操作正在执行，则会取消正在执行的操作，然后抛出一个 OperationCanceledException
- `public Cursor query (Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder, CancellationSignal cancellationSignal)`
    - `selection`：查询的条件，也就是设置一些过滤指标，每个指标的值用占位符标识，一般是“?”
    - `selectionArgs`：上面的每一个占位符，都对应着这里面的一个值。按顺序一一对应。
    - `sortOrder`：所有查询出来的数据行按照 sortOrder 指定的值进行排序，该参数可以为 null。
- `public abstract Cursor query (Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder)`


官方对于 cancellationSignal 的定义如下：
> A signal to cancel the operation in progress, or null if none. If the operation is canceled, then OperationCanceledException will be thrown when the query is executed. This value may be null.

> 目前也不太懂这是啥意思，所以还是贴上英文的解释保险一些。

##### （2）获取查询结果
通过 query 查询返回的结果会保存在 Cursor 中，可以通过 Cursor 中提供的方法方便的遍历里面的数据，常用的获取数据的步骤如下
- 通过 getCount() 来获取 Cursor 中数据的数目，确保数据不为空
- 处理数据
    - 可以通过 Cursor 的 moveToNext() 方法一条一条的遍历和处理数据。
    - 可以将 Cursor 这个数据容器传到 SimpleCursorAdapter 中，SimpleCursorAdapter 是一个辅助 ListView 显示数据的适配器。这种方式适用于需要用 ListView 显示数据的情况，省去了获取数据再重新封装的操作。



Cursor 常用的方法如下：

|方法|作用|
|--|--|
|getColumnName(int columnIndex)|获取指定索引的列名，索引从 0 开始|
|getCount()|返回获取的数据的行数|
|getXxx(int columnIndex)|Xxx 表示基本数据类型，比如 Int，Float等等，获取单个数据行中列索引对应的值|
|moveToNext()|将 cursor 的游标指向下一行数据，其实就是读取下一行数据|

##### （3）demo
```java
MainActivity 中的代码。
```
上面的 SelectionBuilder 只是用于构造条件的，这其实是参考了 Google 给出的 [Demo](https://developer.android.google.cn/samples/BasicSyncAdapter)


下面是 SelectionBuilder 中的 where 和 query 函数
```java
SelectionBuilder 中的代码
```

#### 2. 删除数据
通过 ContentResolver 中的以下方法可删除数据
```java
public final int delete (Uri url,  String where, String[] selectionArgs)
```

##### demo
```java
MainActivity 中的代码
```

#### 3. 更新数据
通过 ContentResolver 中的 update 方法即可更新数据，方法具体声明如下
```java
public final int update (Uri uri, ContentValues values, String where, String[] selectionArgs)
```
- `values`：新数据以键值对的形式保存在这个参数中
- `where `：如果数据中某些属性为特定的值时，则更新该数据行。该参数指定了过滤的属性
- `selectionArgs`：这是一个数组，数组中的值则一一为上面的过滤属性赋值。

##### demo
```
MainActivity 中的代码
```

#### 4. 插入数据
通过 ContentResolver 提供的 insert() 方法插入数据
```java
public final Uri insert (Uri url, ContentValues values)
```
- `values`：数据以行的形式插入，要插入的数据以键值对的形式保存在 ContentValues 中，键表示一行数据中的列属性，值则代表属性对应的值。

##### demo
```
MainActivity 中的代码
```

#### 5. 批量访问数据
顾名思义，批量访问就是一次可以操作多行数据。

要执行的操作保存在一个 ContentProviderOperation 对象数组中。

然后通过 ContentResolver.applyBatch() 方法将这些请求发送给内容提供程序，由它进行处理。该函数的声明如下
```java
public ContentProviderResult[] applyBatch (String authority,  ArrayList<ContentProviderOperation> operations)
```
可以看到，里面有一个 ContentProviderOperation 数组列表的参数，也就是我们创建的 ContentProviderOperation 对象数组了。该函数会返回一个 ContentProviderResult 数组，内容提供程序返回的结果就保存在这个数组中

##### demo

## 三、通过 Intent 访问

直接发送给内容提供程序：这种方法通常用在不具备访问提供程序的权限时，可以通过申请临时权限的方式来访问数据，只需在 Intent 中设置相应的标志位（flag）即可。

发送给第三方应用：另外也可以发送一个请求数据的 Intent（字面意思为意图）到具有权限的应用，然后这个应用根据请求向内容提供程序发送相应的数据请求，然后将操作结果返回给请求的应用。

```java
MainActivity.java 中的代码。
```

## 四、创建内容提供程序
#### 1. 确定需求
首先需要确定是否真的需要在应用中使用内容提供程序，如果不满足下面条件中的任何一条，那么使用内部的数据存储就足够了
- 应用中的数据是否需要被别的进程所访问。换句话说就是别的应用是否被允许访问你应用中的数据。
- 官方提出的条件
    - 应用中需要使用搜索框架。
    - 向其他应用或者控件提供数据
    - 应用中有类需要实现 AbstractThreadedSyncAdapter 、CursorAdapter 或 CursorLoader 类。


#### 2. 前期准备
##### （1）内容提供程序提供对以下数据的存储
- 文件数据

以文件的形式存储在设备中的数据，比如图片，音频，视频等，文件一般会被存储在应用的私有空间中。外界如果要访问，则是向内容提供程序提交访问请求，获得文件句柄。

Android 提供了各种面向文件的 API。如要设计提供媒体相关数据（如音乐或视频）的提供程序，可以开发合并表数据和文件的提供程序。

- 结构化数据

指存储在数据库（Android 中一般使用的是 SQLite 数据库。数据库和文件是两个不同的东西）中的数据，它们通常以表的形式存在，每一行代表一个实体，每一列代表实体中的一个属性。

一般在 Android 中采用关系型数据库（如 SQLite）或非关系型键值数据存储区（如 LevelDB）来处理结构化数据。可以在内容提供程序中混合使用记中不同的数据存储技术


##### （2）Android 中的数据存储技术
- 使用 Room 持久化库，它提供对 SQLite 数据库 API 的访问权限，而 Android 自有提供程序可使用 SQLite 数据库 API 存储面向表格的数据。如果要使用 Room 创建数据库，需要将 RoomDatabase 的子类实例化。

- 通过使用 java.net 和 android.net 中的类来访问网络数据，也可以将网络数据同步到本地数据存储，以表格或者文件的形式存储

##### （3）注意事项


#### 3. 创建内容提供程序












