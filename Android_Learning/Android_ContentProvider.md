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
- `values`：











