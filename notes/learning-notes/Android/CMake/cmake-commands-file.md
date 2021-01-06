[文件操作命令](https://cmake.org/cmake/help/latest/command/file.html)


## 一、概要
- `Reading`
    - `file(READ <filename> <out-var> [...])`
    - `file(STRINGS <filename> <out-var> [...])`
    - `file(<HASH> <filename> <out-var>)`
    - `file(TIMESTAMP <filename> <out-var> [...])`
    - `file(GET_RUNTIME_DEPENDENCIES [...])`
- `Writing`
    - `file({WRITE | APPEND} <filename> <content>...)`
    - `file({TOUCH | TOUCH_NOCREATE} [<file>...])`
    - `file(GENERATE OUTPUT <output-file> [...])`
- `Filesystem`
    - `file({GLOB | GLOB_RECURSE} <out-var> [...] [<globbing-expr>...])`
    - `file(RENAME <oldname> <newname>)`
    - `file(MAKE_DIRECTORY [<dir>...])`
    - `file({COPY | INSTALL} <file>... DESTINATION <dir> [...])`
    - `file(SIZE <filename> <out-var>)`
    - `file(READ_SYMLINK <linkname> <out-var>)`
    - `file(CREATE_LINK <original> <linkname> [...])`
- `Path Conversion`
    - `file(RELATIVE_PATH <out-var> <directory> <file>)`
    - `file({TO_CMAKE_PATH | TO_NATIVE_PATH} <path> <out-var>)`
- `Transfer`
    - `file(DOWNLOAD <url> <file> [...])`
    - `file(UPLOAD <file> <url> [...])`
- `Locking`
    - `file(LOCK <path> [...])`

## 二、[Reading](https://cmake.org/cmake/help/latest/command/file.html#reading)

> **file([READ](https://cmake.org/cmake/help/latest/command/file.html#read)\<filename\> \<out-var\> [...])**

### 1. 命令一
```
file(READ <filename> <variable>
     [OFFSET <offset>] [LIMIT <max-in>] [HEX])
```
从 `<filename>` 指定的文件中读取内容，并保存到 `<variable>` 指定的变量中。`<offset>` 指定了读取的起始位置的偏移量，`<max-in>` 指定了读取的字节数。使用 `[HEX]` 可以将数据转换为十六进制的形式（可用于二进制数据。）



### 2. 命令二
```
file(STRINGS <filename> <variable> [<options>...])
```
解析 `<filename>` 文件中的 ASCII 字符串并保存到 `<variable>` 变量中，文件中的二进制数据将会被忽略，回车符（\r，CR）也会被忽略，该命令包含了以下选项
- `LENGTH_MAXIMUM <max-len>`：Consider only strings of at most a given length.
- `LENGTH_MINIMUM <min-len>`：Consider only strings of at least a given length.
- `LIMIT_COUNT <max-num>`：限制要提取的不同字符串的数量。
- `LIMIT_INPUT <max-in>`：限制要从文件读取的最大字节数
- `LIMIT_OUTPUT <max-out>`：限制要存储在`<variable>`中的总字节数。
- `NEWLINE_CONSUME`：将换行符（\n, LF）视为文本内容的一部分。
- `NO_HEX_CONVERSION`：默认情况下会将 Intel Hex 和 Motorola S-record 文件转为二进制文件，配置该选项将关闭这个行为
- `REGEX <regex>`：仅读取与给定正则表达式匹配的字符串。
- `ENCODING <encoding-type>`：仅读取给定编码的字符。当前支持的编码有：UTF-8，UTF-16LE，UTF-16BE，UTF-32LE，UTF-32BE。 如果未提供 `ENCODING`选项，并且文件具有字节顺序标记，则默认情况下ENCODING选项将遵循字节顺序标记。

### 3. 命令三
```
file(<HASH> <filename> <variable>)
```
计算 `<filename>` 文件内容的加密哈希值，并将其保存到 `<variable>` 变量中。通过 `string(HASH)` 可以列出所有支持的 Hash 算法

### 4. 命令四
```
file(TIMESTAMP <filename> <variable> [<format>] [UTC])
```
将 `<filname>` 文件的最后修改时间以字符串的形式表现出来，并保存到 `<variable>` 变量中。该命令无法获取时间戳时将会被设为空字符串（""）。

通过 `string(TIMESTAMP)` 可以查看 `<format>` 和 `<URC>` 相关的信息

### 5. 命令五
```
file(GET_RUNTIME_DEPENDENCIES
  [RESOLVED_DEPENDENCIES_VAR <deps_var>]
  [UNRESOLVED_DEPENDENCIES_VAR <unresolved_deps_var>]
  [CONFLICTING_DEPENDENCIES_PREFIX <conflicting_deps_prefix>]
  [EXECUTABLES [<executable_files>...]]
  [LIBRARIES [<library_files>...]]
  [MODULES [<module_files>...]]
  [DIRECTORIES [<directories>...]]
  [BUNDLE_EXECUTABLE <bundle_executable_file>]
  [PRE_INCLUDE_REGEXES [<regexes>...]]
  [PRE_EXCLUDE_REGEXES [<regexes>...]]
  [POST_INCLUDE_REGEXES [<regexes>...]]
  [POST_EXCLUDE_REGEXES [<regexes>...]]
  )
```
递归获取给定文件所依赖的库列表。不过该命令应该用在 `install(CODE)` 或 `install(SCRIPT)` 命令块中，如下
```
install(CODE [[
  file(GET_RUNTIME_DEPENDENCIES
    # ...
    )
  ]])
```
可以使用以下参数
#### （1） `RESOLVED_DEPENDENCIES_VAR <deps_var>`

存储已解析依赖项列表的变量的名称。（原文：Name of the variable in which to store the list of resolved dependencies.）
#### （2）`UNRESOLVED_DEPENDENCIES_VAR <unresolved_deps_var>`

存储未解析依赖项列表中的变量名称，如果没有指定该变量，并且存在未解析的依赖项，会报出一个错误。

#### （3）`CONFLICTING_DEPENDENCIES_PREFIX <conflicting_deps_prefix>`

存储冲突依赖项的名称前缀，如果在两个不同的目录中找到两个具有相同名称的文件，则依赖关系会发生冲突。 冲突的文件名列表存储在`<conflicting_deps_prefix>_FILENAMES`中。对于每个文件名，为该文件名找到的路径列表存储在`<conflicting_deps_prefix>_<filename>` 中。（原文：Variable prefix in which to store conflicting dependency information. Dependencies are conflicting if two files with the same name are found in two different directories. The list of filenames that conflict are stored in `<conflicting_deps_prefix>_FILENAMES`. For each filename, the list of paths that were found for that filename are stored in `<conflicting_deps_prefix>_<filename>.`）


#### (4) `EXECUTABLES <executable_files>`


#### (5) `MODULES <module_files>`：

#### (6) `DIRECTORIES <directories>`：

#### (7) `BUNDLE_EXECUTABLE <bundle_executable_file>`：

#### (8) `PRE_INCLUDE_REGEXES <regexes>`

#### (9) `POST_INCLUDE_REGEXES <regexes>`

#### (10)`POST_EXCLUDE_REGEXES <regexes>`：

List of post-exclude regexes through which to filter the names of resolved dependencies.


## 三、[Writing](https://cmake.org/cmake/help/latest/command/file.html#writing)

## 四、[Filesystem](https://cmake.org/cmake/help/latest/command/file.html#filesystem)

### 1. 命令一

```
file(GLOB <variable>
     [LIST_DIRECTORIES true|false] [RELATIVE <path>] [CONFIGURE_DEPENDS]
     [<globbing-expressions>...])

file(GLOB_RECURSE <variable> [FOLLOW_SYMLINKS]
     [LIST_DIRECTORIES true|false] [RELATIVE <path>] [CONFIGURE_DEPENDS]
     [<globbing-expressions>...])
```

生成与 `<globbing-expressions>` 匹配的文件列表，并将其存储到 `<variable>` 中。通配表达式与正则表达式相似，但更为简单。 如果指定了RELATIVE标志，则结果将作为相对路径返回给定路径。 结果将按字典顺序排序。

在 Window 和 macOS 上，表达式是不区分大小写的（在匹配之前，文件名和 globlob 表达式都转换为小写）。在其他平台上则区分大小写

如果制定了 CONFIGURE_DEPENDS 标志，则 CMake 会向主构建系统的检查目标中添加一段逻辑，用于在构建时重新运行被标记的 GLOB 命令。

默认情况下 GLOB 会列出目录，将 LIST_DIRECTORIES 设为 false ，则会省略目录输出。

> 不建议使用 GLOB 命令从源码树中收集源代码文件列表。如果在源码树中增加或者删除文件，却没有在 CMakeLists.txt 中更改，则已生成的构建系统将不会通知 CMake 重新生成。CONFIGURE_DEPENDS 不是兼容所有的生成器的，如果未来增加了不兼容该属性的生成器，使用该生成器的项目将被卡住。即使CONFIGURE_DEPENDS能够可靠地工作，仍然需要对每个重建执行检查。

通配符表达式示例：
```
*.cxx      - match all files with extension cxx
*.vt?      - match all files with extension vta,...,vtz
f[3-5].txt - match files f3.txt, f4.txt, f5.txt
```

GLOB_RECURSE 模式下会遍历匹配目录的所有子目录并匹配文件，如果设置了 FOLLOW_SYMLINKS 属性或者 CMP0009 未设置未 NEW 时，CMake 将遍历作为符号链接的子目录

默认情况下，GLOB_RECURSE 会删除结果列表中的目录，可以将 LIST_DIRECTORIES 设为 true 以将目录添加到结果列表中。如果设置了 FOLLOW_SYMLINKS 属性或者 CMP0009 未设置未 NEW 时，作为符号链接的目录也会添加到结果列表中。

包含子目录的通配符表达式示例：
```
/dir/*.py  - match all python files in /dir and subdirectories
```

### 2. 命令二
```
file(RENAME <oldname> <newname>)
```



