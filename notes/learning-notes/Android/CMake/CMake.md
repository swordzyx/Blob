## 一、概要
#### 1. Generate a Project Buildsystem
- `cmake [<options>] <path-to-source>`
- `cmake [<options>] <path-to-existing-build>`
- `cmake [<options>] -S <path-to-source> -B <path-to-build>`

#### 2. Build a Project
- `cmake --build <dir> [<options>] [-- <build-tool-options>]`

#### 3. Install a Project
- `cmake --install <dir> [<options>]`

#### 4. Open a Project
- `cmake --open <dir>`

#### 5. Run a Script
- `cmake [{-D <var>=<value>}...] -P <cmake-script-file>`

#### 6. Run a Command-Line Tool
- `cmake -E <command> [<options>]`

#### 7. Run the Find-Package Tool
- `cmake --find-package [<options>]`

#### 8. View Help
- `cmake --help[-<topic>]`

## 二、描述
cmake可执行文件是跨平台构建系统生成器CMake的命令行界面。

CMake 通过生成构建系统来实现项目的构建，可以使用 cmake 命令或者直接运行相应的构建工具。

开发人员可以使用 CMake 语言来编写构建脚本，来实现项目的构建

CMake 也提供了相应的图形界面工具作为 cmake 命令的替代。命令行工具的用法参考[cmake](https://cmake.org/cmake/help/v3.17/manual/ccmake.1.html#manual:ccmake(1))。用户图形界面工具参考 [cmake-gui](https://cmake.org/cmake/help/v3.17/manual/cmake-gui.1.html#manual:cmake-gui(1))

CMake 也提供了打包和测试项目的命令行工具，参考 [ctest](https://cmake.org/cmake/help/v3.17/manual/ctest.1.html#manual:ctest(1)) 和 [cpack](https://cmake.org/cmake/help/v3.17/manual/cpack.1.html#manual:cpack(1))

## 三、CMake 构建系统的介绍
构建系统描述了如何自动执行从源代码到可执行文件或者库的生成过程，例如构建系统可以是和命令行工具一起使用的 Makefile 文件或者是 IDE（集成开发环境）中的工程文件。这些文件也被称为是构建系统的抽象，通过 CMake 语言编写，这些文件被 CMake 使用一个生成器（也被叫做后端）来生成本地的首选构建系统

下面列出的信息是生成一个构建系统所必须的
- **Source Tree**：用于指定包含了项目源码文件的顶级目录。该项目使用一个文件（也就是构建系统的抽象）来指定其构建系统，该文件使用 CMake 语言编写，文件名必须为 “CMakeLists.txt”，可以在代码的各个子模块中分别创建一个 “CMakeLists.txt” 文件，这样更方便维护。 构建开始时首先会解析顶级目录下的 “CMakeLists.txt” 文件。这些文件制定了项目的构建目标及其依赖项。

- **Build Tree**：一个顶级目录，该目录存储了构建系统文件以及构建输出的生成物。这个目录将会被 CMake 写入 CMakeCache.txt，以将该目录标识为构建树并存储持久性信息，例如buildsystem配置选项。<br>
建议为源码树和构建树创建不同的目录，以将它们分隔开来。

- **Generator**：这个指示了生成的构建系统的类型。运行 `cmake --help` 命令可以查看本地可用的生成器，通过 `-G` 参数来指定使用哪一个生成器，或者可以使用当前平台默认的生成器。
<br>当使用命令行工具生成器时，需要配置编译所需的环境。
<br>使用 IDE 工具生成器时，则不需要配置，因为这些工具中已经自带了编译环境。

## 四、生成项目构建系统
#### 1. 配置基本信息
运行下面列出的命令中的任何一个来指定源码树，构建树以及生成构建系统。

##### （1）`cmake [<options>] <path-to-source>`
该命令指定当前工作目录作为构建树。`<path-to-source>` 指定了源码树，这个指定的路径可以是绝对路径或者相对于当前工作目录的路径。源码目录下必须包含一个 `CMakeLists.txt`，并且不能包含 `CMakeCache.txt` 文件，因为后者用于标识一个已存在的构建树，也就是这个文件是在构建的过程中自动生成的。

示例：下面的命令指定了 build/src 目录作为源码目录。
```
$ mkdir build ; cd build
$ cmake ../src
```

##### （2）`cmake [<options>] <path-to-existing-build>`
使用 `<path-to-existing-build>` 作为构建树，从构建树目录下的 `CMakeCache.txt` 文件加载出源码树目录，这要求之前运行过 `cmake` 命令来生成这个文件。指定的构建树目录可以是绝对路径，也可以是相对于当前目录的相对路径

示例：
```
$ cd build
$ cmake .
```

> 也就是说如果之前已经构建过了，可以通过这个命令来直接再次构建，而不需要再次指定源码目录

##### （3）`cmake [<options>] -S <path-to-source> -B <path-to-build>`
使用 `<path-to-source>` 作为源码树路径，使用 `<path-to-build>` 作为构建树路径。源码目录下必须有一个 `CMakeLists.txt` 文件。如果指定的构建目录不存在，则构建过程中会自动生成构建目录

示例：
```
$ cmake -S src -B build
```

##### （4）构建
在构建系统生成了之后，要使用相应的本地构建工具来构建项目。例如，在使用了 Unix MakeFiles 生成器生成了构建系统之后可以直接运行 `make` 命令
```
$ make
$ make install
```

也可以使用 cmake 来自动选择合适的本地构建工具来生成项目。

#### 2. 操作
上面命令中的 `[options]` 可以由0个或者多个下面列出的操作组成
- **`-S <path-to-source>`**：源码的根目录

- **`-B <path-to-build>`**：构建目录。如果这个目录不存在，CMake 会自动生成，构建的产物存放在该目录中

- **`-C <initial-cache>`**：预加载一个脚本以填充缓存
<br>首次运行时，如果构建目录为空，CMake 会生成一个 CMakeCache.txt 文件，并且使用项目的自定义配置来填充它。
<br>在第一次解析项目的列表文件之前，通过这个操作项指定的文件来加载缓存条目。这些缓存会优先于项目的默认值。这个文件不是一个缓存格式的文件，而是一个包含了 `set()` 命令的脚本，并且命令中必须使用 `CACHE` 操作项。
<br>参考该脚本中的 `CMAKE_SOURCE_DIR` 和 `CMAKE_BINARY_DIR` 来获取源码树与构建树的路径

- **`-D <var>:<type>=<value>, -D <var>=<value>`**：此选项可用于指定优先于项目默认值的设置。 可以根据需要对尽可能多的CACHE条目重复该选项。
<br>如果指定了`:<type>`部分，则该部分必须是`set()`命令文档为其CACHE签名指定的类型之一。如果省略`:<type>`部分，则该条目将不创建任何类型（如果该类型尚不存在）。 如果项目中的命令将类型设置为 PATH 或 FILEPATH ，则`<value>`将转换为绝对路径。
<br>此选项也可以作为单个参数给出：`-D <var>:<type> = <value>`或`-D <var>=<value>`。

- **`-U <globbing_expr>`**：从CMake CACHE中删除匹配的条目。
<br>此选项可用于从CMakeCache.txt文件中删除一个或多个变量，并使用\*和 ？ 支持。 可以根据需要对尽可能多的`CACHE`条目重复该选项。
<br>使用这个操作项可能会使 CMakeCache.txt 不起作用。


- **`-G <generator-name>`**：指定一个构建系统的生成器。
<br>指定构建系统生成器。CMake可能在某些平台上支持多个本机构建系统。 生成器负责生成特定的构建系统。如果未指定，则CMake将检查CMAKE_GENERATOR环境变量，否则将使用内置的默认选择。

- **`-T <toolset-spec>`**：生成器的工具集规范（如果支持）。一些CMake生成器支持工具集规范，以告诉本机生成系统如何选择编译器。

- **`-A <platform-name>`**：指定平台名称，如果生成器支持。一些 CMake 生成器支持将平台名称提供给本机构建系统以选择编译器或SDK。

- **`-Wno-dev`**：Suppress developer warnings.
<br>Suppress warnings that are meant for the author of the `CMakeLists.txt` files. By default this will also turn off deprecation warnings.

- **`-Wdev`**：启用开发人员警告。
<br>这个选项主要是供 `CMakeLists.txt` 文件的作者使用的。
<br>By default this will also turn on deprecation warnings.

- **`-Werror=dev`**：Make developer warnings errors.
<br>Make warnings that are meant for the author of the `CMakeLists.txt` files errors. By default this will also turn on deprecated warnings as errors.

- **`-Wno-error=dev`**：Make developer warnings not errors.
<br>Make warnings that are meant for the author of the `CMakeLists.txt` files not errors. By default this will also turn off deprecated warnings as errors.

- **`-Wdeprecated`**：Enable deprecated functionality warnings.
<br>Enable warnings for usage of deprecated functionality, that are meant for the author of the `CMakeLists.txt` files.

- **`-Wno-deprecated`**：Suppress deprecated functionality warnings.
<br>Suppress warnings for usage of deprecated functionality, that are meant for the author of the `CMakeLists.txt` files.

更多参考[Options](https://cmake.org/cmake/help/v3.17/manual/cmake.1.html#options)

## 五、构建项目
CMake提供了一个命令行签名来构建一个已经生成的项目二进制树：`cmake --build <dir> [<options>] [-- <build-tool-options>]`

这将使用以下选项抽象出本机构建工具的命令行界面：
- **`--build <dir>`**：要构建的项目二进制目录。必须位于首行

- **`--parallel [<jobs>], -j [<jobs>]`**：构建时要使用的最大并发进程数。 如果省略`<jobs>`，则使用本机构建工具的默认编号。
<br>如果设置了CMAKE_BUILD_PARALLEL_LEVEL环境变量，则在未指定此选项时指定默认的并行级别。
<br>一些本地构建工具总是并行构建。 `<jobs>`值为1的使用可用于限制单个作业。

- **`--target <tgt>..., -t <tgt>...`**：使用指定的构建目标替代默认的构建目标，如果构建多个目标可以用空格分隔开。

- **`--config <cfg>`**：用于多配置工具的配置选择

- **`--clean-first`**：在构建时，首先清楚旧的构建信息，然后再开始构建（可以使用 `--target clean` 来仅仅清除构建信息）

- **`--use-stderr`**：忽略。CMake 3.0 及以上的版本默认指定了这个选项

- **`-verbose， -v`**：如果支持的话，该项会允许构建过程中的日志输出，包括构建过程中执行的命令
<br>如果设置了 `VERBOSE` 或者 `CMAKE_VERBOSE_MAKEFILE` 环境变量，该项可以被省略

- **`--`**：将其余选项传递给本机工具。

## 六、安装项目
CMake 提供了一个命令来安装一个已生成的项目二进制树：`cmake --install <dir> [<options>]`

使用可以命令可以在不使用构建系统或者本地构建工具的情况下执行安装过程，有如下操作选项
- **`--install <dir>`**：需要安装的二进制项目的目录。必须位于首行

- **`--config <cfg>`**：为多配置生成器选择配置

- **`--component <comp>`**：基本组件安装，仅仅只安装 `<comp>` 指定的组件

- **`--prefix <prefix>`**：覆盖 `CMAKE_INSTALL_PREFIX` 指定的安装前缀，

- **`--strip`**：在安装之前将其剥离。

- **`-v, --verbose`**：启用日志打印输出。如果设置了 `VERBOSE` 环境变量，该项可以省略

运行 `cmake --install` 可以获取快速帮助

## 七、打开项目
`cmake --open <dir>`

在应用程序中打开相关的生成项目，不过需要看生成器是否支持。

## 八、运行脚本
`cmake [{-D <var>=<value>}...] -P <cmake-script-file>`

将给定的cmake文件作为以CMake语言编写的脚本进行处理。 不执行配置或生成步骤，并且不修改缓存。 如果使用-D定义变量，则必须在-P参数之前完成。

## 九、运行命令行工具
CMake 提供了内置的命令行工具
```
cmake -E <command> [<options>]
```

运行 `cmake -E` 或者 `cmake -E help` 来获取命令的摘要，可用的命令如下
#### 1. capabilities
以 JSON 的格式报告出 CMake 的功能，输出的 JSON 对象包含以下键
- **`version`**：包含版本信息的 JSON 对象。有以下键
    - **`string`**：完整的版本信息的字符串，通过 `cmake --version`可以显示
    - **`major`**：整数形式的主版本号
    - **`minor`**：整数形式的次版本号
    - **`patch`**：整数形式的补丁级别
    - **`suffix`**：cmake 版本的后缀字符串
    - **`isDirty`**：布尔值，设置 cmake 构建是否来自于一个脏源码树

- **`generators`**：可用的生成器列表。每个代表生成器的 JSON 对象包含以下键
    - **`name`**：一个包含了生成器名字的字符串
    - **`toolsetSupport`**：如果生成器支持 toolset 则为 true，否则为 false
    - **`platformSupport`**：如果生成器支持当前平台，则为 true，否则为 false
    - **`extraGenerators`**：字符串列表，包含了所有可兼容当前生成器的扩展生成器

- **`fileApi`**：当 `cmake-file-api` 可用时存在可选成员。其值是包含了一个成员的 JSON 对象
    - **`requests`**：JSON 数组。包含了 0 个或多个支持的 `file-api` 请求，包含以下成员
        - **`kind`**：指定一个支持的[对象类型](https://cmake.org/cmake/help/v3.17/manual/cmake-file-api.7.html#file-api-object-kinds)
        - **`version`**：一个JSON数组，其元素都是一个JSON对象，其中包含主要成员和次要成员，这些成员指定了非负整数版本组件。
    - **`serverMode`**：如果 cmake 支持 server-mode 则为 true，否则为 false

-  **`chdir <dir> <cmd> [<arg>...]`**：更改当前的工作目录，同时运行 `<cmd>` 指定的命令

- **`compare_files [--ignore-eol] <file1> <file2>`**：检查 `<file1>` 和 `<file2>` 指定的文件是否相同，如果相同则返回 0 ，否则返回 1 。 `--ignore-eol` 选项指示逐行比较以及忽略 LF/CRLF 差异

- **`copy_directory <dir>... <destination>`**：将 `<dir>` 指定的目录下的内容复制到 `<destination>` 目录下。如果目标目录不存在则会自动创建。

- **`copy_if_different <file>... <destination>`**：如果文件发生了更改，将文件复制到 `<destination>` 指定目录下，如果 `<file>` 指定多个文件，则 `<destination>` 所指定的目录必须存在。

- **`create_symlink <old> <new>`**：为 `<old>` 指定的目录或文件创建一个以 `<new>` 命名的符号链接。
<br>`<new>` 链接的目录必须是预先已经被创建好了的。

- **`echo [<string>...]`**：以文本的形式显示参数

- **`echo_append [<string>...]`**：将参数显示为文本，但不显示新行。

- **`env [--unset=NAME]... [NAME=VALUE]... COMMAND [ARG]...`**：修改指定的环境变量，然后再修改的环境中运行命令

- **`environment`**：显示当前环境变量

- **`false`**：不执行任何操作，退出代码为1。

- **`make_directory <dir>...`**：创建一个以 `<dir>` 命名的目录，同时会创建其父目录。如果目录已存在，则该命令会被忽略

- **`md5sum <file>...`**：为 `<file>` 指定的文件创建兼容 `md5sum` 格式的 MD5 校验和。
<br> 示例
```
351abe79cd3800b38cdfb25d45015a15  file1.txt
052f86c15bbde68af55c7f7b340ab639  file2.txt
```

- **`sha1sum <file>...`**：为 `<file>` 指定的文件创建兼容 `sha1sum` 格式的 SHA1 校验和。
<br>示例：
```
4bb7932a29e6f73c97bb9272f2bdc393122f86e0  file1.txt
1df4c8f318665f9a5f2ed38f55adadb7ef9f559c  file2.txt
```

- **`sha224sum <file>...`**：为 `<file>` 指定的文件创建兼容 `sha224sum` 格式的 SHA224 校验和。

- **`sha256sum <file>...`**：为 `<file>` 指定的文件创建兼容 `sha256sum` 格式的 SHA256 校验和。

- **`sha384sum <file>...`**：为 `<file>` 指定的文件创建兼容 `sha384sum` 格式的 SHA384 校验和。

- **`sha512sum <file>...`**：为 `<file>` 指定的文件创建兼容 `sha512sum` 格式的SHA512 校验和。

- **`remove [-f] <file>...`**：这个命令在 3.17 版本被丢弃。

- **`remove_directory <dir>...`**：这个命令在 3.17 版本被丢弃。

- **`rename <oldname> <newname>`**：将 `<oldname>` 指定的文件重命名为 `<newname>` ，如果 `<newname>` 指定的名字已存在，那么该命令会被忽略

- **`rm [-rRf] <file> <dir>...`** 删除 `<file>` 指定的文件，或者删除 `<dir>` 指定的目录。使用 `-r` 或者 `-R` 递归的删除目录下的内容，可以指定多个文件或者目录。如果指定的目录或者文件中某一个不存在，那么会返回一个非 0 退出码，但是不会记录到日志信息中。使用 `-f` 选项可以在想听情况下改变这个行为，比如返回一个 0 退出码表示成功。

- **`server`**：启动 `cmake-server` 模式

- **`sleep <number>...`**：睡眠 `<number>` 指定的时长

- **`tar [cxt][vf][zjJ] file.tar [<options>] [--] [<pathname>...]`**：生成一个 tar 或者 zip 形式的文件，支持一下操作项
	- `c`：创建一个包含了 `<pathname>` 指定的文件的生成物（tar 或者 zip 格式）
	- `x`：将指定的 archive 中的内容提取到磁盘，`<pathname>` 指示了需要被提取的
	- `t`：列出存档文件中的内容，使用 `<pathname>` 列出选择的文件或者目录
	- `v`：输出生成过程中的详细信息
	- `z`：将归档文件压缩成 gzip 格式
	- `j`：将归档文件压缩成 bzip2 格式
	- `J`：将归档文件压缩成 XZ 格式
	- `--zstd`：将归档文件压缩成 Zstandard 格式
	- `--files-from=<file>`：逐行输出 `<file>` 中指定的文件名，忽略空行，除了在使用 `--add-file=<name>` 的时候可以使用

> 查了一下网上，archive 的意思是存档文件，存档的意思。那就称之为存档文件吧。
