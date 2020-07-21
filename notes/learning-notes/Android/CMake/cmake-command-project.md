[官方文档](https://cmake.org/cmake/help/latest/command/project.html)

该命令用于设置项目的名称。

## 一、语法摘要

```
project(<PROJECT-NAME> [<language-name>...])
project(<PROJECT-NAME>
        [VERSION <major>[.<minor>[.<patch>[.<tweak>]]]]
        [DESCRIPTION <project-description-string>]
        [HOMEPAGE_URL <url-string>]
        [LANGUAGES <language-name>...])
```
设置项目的名称，并且会将其保存在 `PROJECT_NAME` 变量中，在 CMakeLists.txt 的顶层调用该命令时，设置的项目名也会保存在 `CMAKE_PROJECT_NAME` 变量中。 

可以使用该命令同时设置以下变量
- `PROJECT_SOURCE_DIR, <PROJECT-NAME>_SOURCE_DIR`
- `PROJECT_BINARY_DIR, <PROJECT-NAME>_BINARY_DIR`

## 二、操作选项

可以通过下面的操作参数来设置更多的变量，这些变量的默认值是空字符串。

- **`VERSION <version>`**：可选操作，除非策略 `CMP0048` 设置为NEW，否则不能使用。`<version>` 由非负整数构成，即 `<major>[.<minor>[.<patch>[.<tweak>]]]` 形式的值，并且会设置以下变量
    - `PROJECT_VERSION, <PROJECT-NAME>_VERSION`
    - `PROJECT_VERSION_MAJOR, <PROJECT-NAME>_VERSION_MAJOR`
    - `PROJECT_VERSION_MINOR, <PROJECT-NAME>_VERSION_MINOR`
    - `PROJECT_VERSION_PATCH, <PROJECT-NAME>_VERSION_PATCH`
    - `PROJECT_VERSION_TWEAK, <PROJECT-NAME>_VERSION_TWEAK.`
在 CMakeList.txt 时顶层被调用时，版本号将会被保存到 `CMAKE_PROJECT_VERSION` 变量中

- **`DESCRIPTION <project-description-string>`**：可选操作，设置 `PROJECT_DESCRIPTION, <PROJECT-NAME>_DESCRIPTION` 变量，建议使用相对短的字符串，通常不超过几个字符串。
使用了该操作项时，设置的描述字符串将会被保存到 `CMAKE_PROJECT_DESCRIPTION` 变量中

- **`HOMEPAGE_URL <url-string>`**：可选操作，设置 `PROJECT_HOMEPAGE_URL, <PROJECT-NAME>_HOMEPAGE_URL` 到 `<url-string>` 中，建议为项目的主页 URL。使用了该操作项的 `project()` 被调用时，此处设置的 url 将会被保存到  CMAKE_PROJECT_HOMEPAGE_URL 变量中。

- **`LANGUAGES <language-name>...`**：可选操作，在第一个简短签名中可以不使用 `LANGUAGES` 关键字，这个操作先将会选择在构建过程中使用哪一种语言，支持`C`，`CXX`（即 C++），`CUDA`，`OBJC`（即 Objective-C），`OBJCXX`，`Fortran`，以及 `ASM`。如果没有指定任何语言默认会启用 `C` 和 `CXX`。将 `language` 指定为 `NONE`，或者使用了 `LANGUAGE` 关键字但是并不指定任何语言，则会跳过关于语言的选择。
<br> 如果启用了 `ASM`，（原文：If enabling ASM, list it last so that CMake can check whether compilers for other languages like C work for assembly too.）

通过VERSION，DESCRIPTION和HOMEPAGE_URL选项设置的变量旨在用作包元数据和文档中的默认值。


## 三、代码注入
如果设置了`CMAKE_PROJECT_INCLUDE_BEFORE` 或者 `CMAKE_PROJECT_<PROJECT-NAME>_INCLUDE_BEFORE` 变量，那么它们所指向的文件将在 `project()` 的第一步包含进来。如果同时设置了两个，`CMAKE_PROJECT_INCLUDE_BEFORE` 会比 `CMAKE_PROJECT_<PROJECT-NAME>_INCLUDE_BEFORE` 先包含进来

如果设置了 `CMAKE_PROJECT_INCLUDE` 或者 `CMAKE_PROJECT_<PROJECT-NAME>_INCLUDE` ,它们所指向的文件会在 `project()` 命令的最后一步被包含进来，如果同时设置了两者，那么 `CMAKE_PROJECT_INCLUDE` 会比 `CMAKE_PROJECT_<PROJECT-NAME>_INCLUDE` 先被包含进来


## 四、用法
CMakeList.txt 文件的顶层必须包含对于 `project()` 的直接调用，仅仅通过 `include()` 命令加载一个是不够的，如果不存在这样的调用，CMake 会发出一个警告，并且会自动认为已经在顶部调用了 `project(project)` ，以启用默认语言（C 或者 CXX）