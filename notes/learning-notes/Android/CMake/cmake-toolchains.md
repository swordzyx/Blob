## 一、介绍
CMake使用实用程序的工具链来编译，链接库和创建档案，以及其他驱动构建的任务。 可用的工具链实用程序取决于启用的语言。 在常规版本中，CMake根据系统自省和默认值自动确定用于主机版本的工具链。在交叉编译方案中，可以使用有关编译器和实用程序路径的信息来指定工具链文件。

## 二、语言
语言由project()命令启用。 通过调用project()命令可以设置特定于语言的内置变量，例如CMAKE_CXX_COMPILER，CMAKE_CXX_COMPILER_ID等。 如果顶级 CMakeLists 文件中没有 project() 命令，则将隐式生成一个命令。

- 默认情况下，启用的语言为 C 和 CXX ：`project(C_Only C)`

- 在 project 命令中也可以使用特殊值 NONE 来不启用任何语言：`project(MyProject NONE)`

- 使用 `enable_language()` 可以在 `project()` 之后启用某一种语言：`enable_language(CXX)`

当启用了某种语言之后，CMake 会为该语言寻找对应的编译器，并且确定一些信息，比如编译器的版本以及厂商信息，目标架构平台，位宽（32位或者64位等等），以及实用程序所在的路径等等。

> 这里的语言是不是指的是源代码的编写语言。CMake 需要通过指定的语言来搭建构建的环境

全局属性 `ENABLED_LANGUAGES` 中声明了当前已启用的语言。

## 三、变量和属性
CMake 启用了工具链中与语言组件相关的多个环境变量。
- `CMAKE_<LANG>_COMPILER`：指定了`<Lang>`语言对应编译器的完整路径
- `CMAKE_<LANG>_COMPILER_ID`：指定了编译器在 CMake 中的标识
- `CMAKE_<LANG>_COMPILER_VERSION`：指定了编译器的版本号

在编译特定的语言文件时，`CMAKE_<LANG>_FLAGS` 和特定于配置的等效项包含的标志将会被添加到编译命令中

> the configuration-specific equivalents 这个单词不知道什么意思

> 特定于配置的等效项指的是什么？

Cmake 通过目标程序中源文件所使用的语言来计算使用哪一个编译器来调用链接器，静态库的链接则是由目标程序中的依赖库计算。计算出来的结果可以被 `LINKER_LANGUAGE` 目标属性所覆盖。

> 这里的目标程序就指的是需要使用 CMake 进行构建的程序。


## 四、工具链功能（toolchain features）
CMake 提供了 `try_compile()` 命令和包装宏，例如 `CheckCXXSourceCompiles`，`CheckCXXSymbolExists` 和`CheckIncludeFile`，以测试各种工具链功能的功能和可用性。 这些API以某种方式测试工具链并缓存结果，以便下次CMake运行时不必再次执行测试。

> features 在词典上搜明明是特征，特色的意思

某些工具链功能在CMake中具有内置处理功能，并且不需要编译测试。比如如果编译器支持，`POSITION_INDEPENDENT_CODE` 允许指定应将目标构建为与位置无关的代码。`<LANG>_VISIBILITY_PRESET` 和 `VISIBILITY_INLINES_HIDDEN` 可添加标志来隐藏可见性


## 五、交叉编译
如果使用 `DCMAKE_TOOLCHAIN_FILE=path/to/file` 参数来执行 `cmake` 命令，该文件将会被提前加载以设置编译器的值。当 `CMake` 交叉编译时，`CMAKE_CROSSCOMPILING` 变量设置为`true`。

不建议在工具链文件中 `CMAKE_SOURCE_DIR` 或者 `CMAKE_BINARY_DIR`，在不同的环境下，这些变量在使用工具链文件的上下文环境中会有不同的值（例如，作为try_compile()调用的一部分）。更推荐使用 `CMAKE_CURRENT_LIST_DIR`，因为它具有明确的，可预测的值。

> 也就是说，只有交叉编译需要用到工具链文件。那么我猜测工具链文件是用于告诉 CMake 生成的文件是要用在哪个平台上的（也就是目标平台）。

#### 1. Linux 平台上的交叉编译
典型的Linux交叉编译工具链包含以下内容：
```
set(Linux)
set(CMAKE_SYSTEM_PROCESSOR arm)

set(CMAKE_SYSROOT /home/devel/rasp-pi-rootfs)
set(CMAKE_STAGING_PREFIX /home/devel/stage)

set(tools /home/devel/gcc-4.7-linaro-rpi-gnueabihf)
set(CMAKE_C_COMPILER ${tools}/bin/arm-linux-gnueabihf-gcc)
set(CMAKE_CXX_COMPILER ${tools}/bin/arm-linux-gnueabihf-g++)

set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_PACKAGE ONLY)
```

- `CMAKE_SYSTEM_NAME` 是要构建的目标平台的 CMake 标识符。

- `CMAKE_SYSTEM_PROCESSOR` 是要构建的目标架构的 CMake 标识

- `CMAKE_SYSROOT`是可选的，在sysroot可用的情况下指定。

- `CMAKE_STAGING_PREFIX` 也是可选的。 它可用于指定主机上要安装的路径。

-  `CMAKE_INSTALL_PREFIX` 指定运行时的安装路径

- `CMAKE_ <LANG> _COMPILER。`：变量可以设置为完整路径，也可以设置为在标准位置搜索的编译器名称，对于不支持链接没有自定义标志或脚本的二进制文件的工具链，可以将 `CMAKE_TRY_COMPILE_TARGET_TYPE`变量设置为 STATIC_LIBRARY ，以告诉 CMake 在检查过程中不要尝试链接可执行文件。

在任何情况下，`CMake find_ *` 默认会在 sysroot 和 CMAKE_FIND_ROOT_PATH 目录下查找，并且也会在主机的根目录下查找。在编译时所用到的库和软件应在主机中的根目录下找到，而在运行时所要用到的库和软件应在目标平台的根目录下查找。

> 主机指的是运行 cmake 的平台吗？


#### 2. Cray Linux环境的交叉编译

[Cross Compiling for the Cray Linux Environment](https://cmake.org/cmake/help/v3.17/manual/cmake-toolchains.7.html#cross-compiling-for-the-cray-linux-environment)

#### 3. 使用Clang交叉编译

[Cross Compiling using Clang](https://cmake.org/cmake/help/v3.17/manual/cmake-toolchains.7.html#cross-compiling-using-clang)

#### 4. 使用 QNX 进行交叉编译
[Cross Compiling for QNX](https://cmake.org/cmake/help/v3.17/manual/cmake-toolchains.7.html#cross-compiling-for-qnx)

#### 5. Window CE 环境的交叉编译
[Cross Compiling for Windows CE](https://cmake.org/cmake/help/v3.17/manual/cmake-toolchains.7.html#cross-compiling-for-windows-ce)

#### 6. Windows 10通用应用程序的交叉编译
[Cross Compiling for Windows 10 Universal Applications](https://cmake.org/cmake/help/v3.17/manual/cmake-toolchains.7.html#cross-compiling-for-windows-10-universal-applications)

#### 7. Windows Phone的交叉编译
[Cross Compiling for Windows Phone](https://cmake.org/cmake/help/v3.17/manual/cmake-toolchains.7.html#cross-compiling-for-windows-phone)


#### 8. Windows应用商店的交叉编译

[Cross Compiling for Windows Store](https://cmake.org/cmake/help/v3.17/manual/cmake-toolchains.7.html#cross-compiling-for-windows-store)

#### 9. Android 下的交叉编译
通过将工具链文件中的 `CMAKE_SYSTEM_NAME` 变量设置为 `Android`，可以配置为在 Android 下进行交叉编译

对于 [Visual Studio Generators](https://cmake.org/cmake/help/v3.17/manual/cmake-generators.7.html#visual-studio-generators)，需要安装 [NVIDIA Nsight Tegra Visual Studio Edition](https://cmake.org/cmake/help/v3.17/manual/cmake-toolchains.7.html#cross-compiling-for-android-with-nvidia-nsight-tegra-visual-studio-edition) 。

对于 [Makefile Generators](https://cmake.org/cmake/help/v3.17/manual/cmake-generators.7.html#makefile-generators) 和 [Ninja](https://cmake.org/cmake/help/v3.17/generator/Ninja.html#generator:Ninja "Ninja") 生成器，CMake 需要安装下面的环境之一
- [NDK](https://cmake.org/cmake/help/v3.17/manual/cmake-toolchains.7.html#cross-compiling-for-android-with-the-ndk)
- [Standalone Toolchain](https://cmake.org/cmake/help/v3.17/manual/cmake-toolchains.7.html#cross-compiling-for-android-with-a-standalone-toolchain)

CMake 通过下面的步骤来选择使用哪一个环境
- 如果设置了 [`CMAKE_ANDROID_NDK`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_NDK.html#variable:CMAKE_ANDROID_NDK "CMAKE_ANDROID_NDK") 变量，将会使用这个变量值（也就是 NDK 的路径）所指定的 NDK
- 否则，如果设置了 [CMAKE_ANDROID_STANDALONE_TOOLCHAIN](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_STANDALONE_TOOLCHAIN.html#variable:CMAKE_ANDROID_STANDALONE_TOOLCHAIN "CMAKE_ANDROID_STANDALONE_TOOLCHAIN")，这个变量值所指定的独立工具链将会被使用
- 否则，如果将`CMAKE_SYSROOT`变量设置为`<ndk>/platforms/android-<api>/arch-<arch>`形式的目录，则`<ndk>`部分将用作`CMAKE_ANDROID_NDK`的值，CMake 将使用 NDK。
- 否则，如果将`CMAKE_SYSROOT`变量设置为`<standalone-toolchain>/sysroot`形式的目录，则`<standalone-toolchain>`部分将用作`CMAKE_ANDROID_STANDALONE_TOOLCHAIN`的值，并且将使用`Standalone Toolchain`。
- 否则，如果设置了 cmake 变量`ANDROID_NDK`，它将用作`CMAKE_ANDROID_NDK`的值，并且将使用`NDK`。
- 否则，如果设置了 ANDROID_STANDALONE_TOOLCHAIN 变量，那么它将会被作为 [`CMAKE_ANDROID_STANDALONE_TOOLCHAIN`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_STANDALONE_TOOLCHAIN.html#variable:CMAKE_ANDROID_STANDALONE_TOOLCHAIN "CMAKE_ANDROID_STANDALONE_TOOLCHAIN") 的值，此时会使用 独立工具链
- 否则，如果在环境变量中设置了 `ANDROID_NDK_ROOT` 变量或者 `ANDROID_NDK`，那么 [`CMAKE_ANDROID_NDK`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_NDK.html#variable:CMAKE_ANDROID_NDK "CMAKE_ANDROID_NDK") 将会使用它们的值，此时则会使用 NDK。
- 否则，如果设置了环境变量`ANDROID_STANDALONE_TOOLCHAIN`，则它将用作`CMAKE_ANDROID_STANDALONE_TOOLCHAIN`的值，并且将使用独立工具链。
- 否则，将发出错误诊断，提示找不到NDK或独立工具链。

#### 10. 在 Android 上使用 NDK 进行交叉编译
工具链文件中可以配置 [Makefile Generators](https://cmake.org/cmake/help/v3.17/manual/cmake-generators.7.html#makefile-generators) 或者 [`Ninja`](https://cmake.org/cmake/help/v3.17/generator/Ninja.html#generator:Ninja "Ninja") 生成器来进行 Android 上的交叉编译。

使用以下变量来配置 Android NDK 进行交叉编译
- [`CMAKE_SYSTEM_NAME`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_SYSTEM_NAME.html#variable:CMAKE_SYSTEM_NAME "CMAKE_SYSTEM_NAME")：设置为 Android，在 Android 上使用交叉编译必须要指定这一项

- [`CMAKE_SYSTEM_VERSION`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_SYSTEM_VERSION.html#variable:CMAKE_SYSTEM_VERSION "CMAKE_SYSTEM_VERSION")：设置为 Android API 的级别，默认会按照下面的步骤进行赋值
    - 如果配置了 [`CMAKE_ANDROID_API`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_API.html#variable:CMAKE_ANDROID_API "CMAKE_ANDROID_API") 变量，该变量的值将为作为 Android API 的级别
    - 如果配置了 [`CMAKE_SYSROOT`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_SYSROOT.html#variable:CMAKE_SYSROOT "CMAKE_SYSROOT") 变量，CMake 从包含 sysroot 的 NDK 目录结构中检测到 API 级别。
    - 否则，会使用 NDK 的最新级别。

- [`CMAKE_ANDROID_ARCH_ABI`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_ARCH_ABI.html#variable:CMAKE_ANDROID_ARCH_ABI "CMAKE_ANDROID_ARCH_ABI")：配置 Android 的 CPU 架构，默认为 armabi。CMake 会从这个变量中计算出 [`CMAKE_ANDROID_ARCH`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_ARCH.html#variable:CMAKE_ANDROID_ARCH "CMAKE_ANDROID_ARCH")的值。

- [`CMAKE_ANDROID_NDK`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_NDK.html#variable:CMAKE_ANDROID_NDK "CMAKE_ANDROID_NDK")：配置 Android NDK 根目录的绝对路径。必须存在一个路径为 `${CMAKE_ANDROID_NDK}/platforms` 的目录。默认按照选择环境的步骤来设置该值。

- [`CMAKE_ANDROID_NDK_DEPRECATED_HEADERS`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_NDK_DEPRECATED_HEADERS.html#variable:CMAKE_ANDROID_NDK_DEPRECATED_HEADERS "CMAKE_ANDROID_NDK_DEPRECATED_HEADERS")：如果设置为 true，将会使用被废弃的 API 级别的标题。在 NDK 提供了统一标题的情况下，默认为 false，使用的是统一的标题。

> 官方解释：Set to a true value to use the deprecated per-api-level headers instead of the unified headers. If not specified, the default will be false unless using a NDK that does not provide unified headers.

- [`CMAKE_ANDROID_NDK_TOOLCHAIN_VERSION`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_NDK_TOOLCHAIN_VERSION.html#variable:CMAKE_ANDROID_NDK_TOOLCHAIN_VERSION "CMAKE_ANDROID_NDK_TOOLCHAIN_VERSION")：在NDK r19或更高版本上，该变量必须设置为 `clang` 或者直接不配置，在 NDK r18 或更低的版本中，设置该变量为要选择作为编译器的工具链版本。如果未指定，默认为最新可用的 GCC 工具链

- [`CMAKE_ANDROID_STL_TYPE`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_STL_TYPE.html#variable:CMAKE_ANDROID_STL_TYPE "CMAKE_ANDROID_STL_TYPE")：指定使用哪个一个 C++ 标准库，如果未指定，将按照变量文档中的说明选择默认值。

- 下面的变量是 CMake 自动计算出来的
    - [`CMAKE_<LANG>_ANDROID_TOOLCHAIN_PREFIX`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_LANG_ANDROID_TOOLCHAIN_PREFIX.html#variable:CMAKE_%3CLANG%3E_ANDROID_TOOLCHAIN_PREFIX "CMAKE_<LANG>_ANDROID_TOOLCHAIN_PREFIX")：NDK 工具链中 binutils 的绝对路径前缀。
    - [`CMAKE_<LANG>_ANDROID_TOOLCHAIN_SUFFIX`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_LANG_ANDROID_TOOLCHAIN_SUFFIX.html#variable:CMAKE_%3CLANG%3E_ANDROID_TOOLCHAIN_SUFFIX "CMAKE_<LANG>_ANDROID_TOOLCHAIN_SUFFIX")：NDK 工具链中 binutils 的主机平台后缀。

##### （1）demo
工具链文件可能会包含以下内容
```
set(CMAKE_SYSTEM_NAME Android)
set(CMAKE_SYSTEM_VERSION 21) # API level
set(CMAKE_ANDROID_ARCH_ABI arm64-v8a)
set(CMAKE_ANDROID_NDK /path/to/android-ndk)
set(CMAKE_ANDROID_STL_TYPE gnustl_static)
```

也可以不指定工具链文件而是直接用 cmake 命令指定值：
```
$ cmake ../src \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_SYSTEM_VERSION=21 \
  -DCMAKE_ANDROID_ARCH_ABI=arm64-v8a \
  -DCMAKE_ANDROID_NDK=/path/to/android-ndk \
  -DCMAKE_ANDROID_STL_TYPE=gnustl_static
```

> 所以工具链文件中需要包含的最基本的内容是：
- 目标平台
- 目标平台的版本信息
- 目标平台的 CPU 架构
- CMake 编译环境（NDK或者独立工具链）
- C++ 标准库


#### 11. 使用独立工具链对Android进行交叉编译
使用独立工具链在 Android 上进行交叉编译时，工具链文件可以配置 Makefile 生成器或者 Ninja 生成器

通过以下的变量来使用 Android 中的独立工具链
- [`CMAKE_SYSTEM_NAME`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_SYSTEM_NAME.html#variable:CMAKE_SYSTEM_NAME "CMAKE_SYSTEM_NAME")：必须设置为 Android，在 Android 上使用交叉编译必须要指定这一项

- [`CMAKE_ANDROID_STANDALONE_TOOLCHAIN`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_STANDALONE_TOOLCHAIN.html#variable:CMAKE_ANDROID_STANDALONE_TOOLCHAIN "CMAKE_ANDROID_STANDALONE_TOOLCHAIN")：设置为独立工具链根目录的绝对路径。 ${CMAKE_ANDROID_STANDALONE_TOOLCHAIN}/sysroot目录必须存在。

- [`CMAKE_ANDROID_ARM_MODE`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_ARM_MODE.html#variable:CMAKE_ANDROID_ARM_MODE "CMAKE_ANDROID_ARM_MODE")：当独立工具链针对ARM时，可以选择将此选项设置为 ON 以针对 32 位 ARM 而不是 16 位 Thumb 。

- [`CMAKE_ANDROID_ARM_NEON`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_ARM_NEON.html#variable:CMAKE_ANDROID_ARM_NEON "CMAKE_ANDROID_ARM_NEON")：当独立工具链针对ARM v7时，可以选择将此选项设置为ON以针对ARM NEON设备。

- [`CMAKE_SYSTEM_VERSION`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_SYSTEM_VERSION.html#variable:CMAKE_SYSTEM_VERSION "CMAKE_SYSTEM_VERSION")：从独立工具链中检测到Android API级别。

- [`CMAKE_ANDROID_ARCH_ABI`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_ANDROID_ARCH_ABI.html#variable:CMAKE_ANDROID_ARCH_ABI "CMAKE_ANDROID_ARCH_ABI")：从独立工具链中检测到Android ABI。

- [`CMAKE_<LANG>_ANDROID_TOOLCHAIN_PREFIX`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_LANG_ANDROID_TOOLCHAIN_PREFIX.html#variable:CMAKE_%3CLANG%3E_ANDROID_TOOLCHAIN_PREFIX "CMAKE_<LANG>_ANDROID_TOOLCHAIN_PREFIX")：独立工具链中binutils的绝对路径前缀。

- [`CMAKE_<LANG>_ANDROID_TOOLCHAIN_SUFFIX`](https://cmake.org/cmake/help/v3.17/variable/CMAKE_LANG_ANDROID_TOOLCHAIN_SUFFIX.html#variable:CMAKE_%3CLANG%3E_ANDROID_TOOLCHAIN_SUFFIX "CMAKE_<LANG>_ANDROID_TOOLCHAIN_SUFFIX")：独立工具链中binutils的主机平台后缀。

##### demo
一个工具链文件可以包含下面的内容
```
set(CMAKE_SYSTEM_NAME Android)
set(CMAKE_ANDROID_STANDALONE_TOOLCHAIN /path/to/android-toolchain)
```

如果没有工具链文件，也可以通过 cmake 命令指定
```
$ cmake ../src \
  -DCMAKE_SYSTEM_NAME=Android \
  -DCMAKE_ANDROID_STANDALONE_TOOLCHAIN=/path/to/android-toolchain
```

#### 12. 使用 NVIDIA Nsight Tegra Visual Studio Edition 进行 Android 交叉编译
可以通过工具链文件配置 [Visual Studio](https://cmake.org/cmake/help/v3.17/manual/cmake-generators.7.html#visual-studio-generators) 生成器来使用 NVIDIA Nsight Tegra 进行 Android 的交叉编译，使用如下命令：
```
set(CMAKE_SYSTEM_NAME Android)
```

可以设置 CMAKE_GENERATOR_TOOLSET 变量来选择 Nsight Tegra 的“工具链版本”

可配置的属性如下
- [`ANDROID_ANT_ADDITIONAL_OPTIONS`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_ANT_ADDITIONAL_OPTIONS.html#prop_tgt:ANDROID_ANT_ADDITIONAL_OPTIONS "ANDROID_ANT_ADDITIONAL_OPTIONS")

- [`ANDROID_API_MIN`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_API_MIN.html#prop_tgt:ANDROID_API_MIN "ANDROID_API_MIN")

- [`ANDROID_API`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_API.html#prop_tgt:ANDROID_API "ANDROID_API")

- [`ANDROID_ARCH`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_ARCH.html#prop_tgt:ANDROID_ARCH "ANDROID_ARCH")

- [`ANDROID_ASSETS_DIRECTORIES`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_ASSETS_DIRECTORIES.html#prop_tgt:ANDROID_ASSETS_DIRECTORIES "ANDROID_ASSETS_DIRECTORIES")

- [`ANDROID_GUI`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_GUI.html#prop_tgt:ANDROID_GUI "ANDROID_GUI")

- [`ANDROID_JAR_DEPENDENCIES`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_JAR_DEPENDENCIES.html#prop_tgt:ANDROID_JAR_DEPENDENCIES "ANDROID_JAR_DEPENDENCIES")

- [`ANDROID_JAR_DIRECTORIES`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_JAR_DIRECTORIES.html#prop_tgt:ANDROID_JAR_DIRECTORIES "ANDROID_JAR_DIRECTORIES")

- [`ANDROID_JAVA_SOURCE_DIR`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_JAVA_SOURCE_DIR.html#prop_tgt:ANDROID_JAVA_SOURCE_DIR "ANDROID_JAVA_SOURCE_DIR")

- [`ANDROID_NATIVE_LIB_DEPENDENCIES`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_NATIVE_LIB_DEPENDENCIES.html#prop_tgt:ANDROID_NATIVE_LIB_DEPENDENCIES "ANDROID_NATIVE_LIB_DEPENDENCIES")

- [`ANDROID_NATIVE_LIB_DIRECTORIES`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_NATIVE_LIB_DIRECTORIES.html#prop_tgt:ANDROID_NATIVE_LIB_DIRECTORIES "ANDROID_NATIVE_LIB_DIRECTORIES")

- [`ANDROID_PROCESS_MAX`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_PROCESS_MAX.html#prop_tgt:ANDROID_PROCESS_MAX "ANDROID_PROCESS_MAX")

- [`ANDROID_PROGUARD_CONFIG_PATH`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_PROGUARD_CONFIG_PATH.html#prop_tgt:ANDROID_PROGUARD_CONFIG_PATH "ANDROID_PROGUARD_CONFIG_PATH")

- [`ANDROID_PROGUARD`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_PROGUARD.html#prop_tgt:ANDROID_PROGUARD "ANDROID_PROGUARD")

- [`ANDROID_SECURE_PROPS_PATH`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_SECURE_PROPS_PATH.html#prop_tgt:ANDROID_SECURE_PROPS_PATH "ANDROID_SECURE_PROPS_PATH")

- [`ANDROID_SKIP_ANT_STEP`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_SKIP_ANT_STEP.html#prop_tgt:ANDROID_SKIP_ANT_STEP "ANDROID_SKIP_ANT_STEP")

- [`ANDROID_STL_TYPE`](https://cmake.org/cmake/help/v3.17/prop_tgt/ANDROID_STL_TYPE.html#prop_tgt:ANDROID_STL_TYPE "ANDROID_STL_TYPE")

#### 13. 适用于iOS，tvOS或watchOS的交叉编译

推荐使用 xcode 生成器来进行目标平台为 iOS，tvOS 或 watchOS 的交叉编译。也可以使用 [`Unix Makefiles`](https://cmake.org/cmake/help/v3.17/generator/Unix%20Makefiles.html#generator:Unix%20Makefiles "Unix Makefiles") 或者 [`Ninja`](https://cmake.org/cmake/help/v3.17/generator/Ninja.html#generator:Ninja "Ninja") 生成器，不过在项目中使用它们需要配置更多地方，比如目标 CPU 选择和代码签名。

将 CMAKE_SYSTEM_NAME 设置为下表中的值，可以选择使用三个系统中的任何一个所谓目标平台。默认会选择最新的设备 sdk。对于所有Apple平台，可以通过设置CMAKE_OSX_SYSROOT变量来选择不同的SDK（例如模拟器），也可以[在设备和模拟器之间切换](https://cmake.org/cmake/help/v3.17/manual/cmake-toolchains.7.html#switching-between-device-and-simulator)，运行 `xcodebuild -showsdks` 获得可用SDK的列表。

|OS|CMAKE_SYSTEM_NAME|Device SDK (default)|Simulator SDK|
|--|--|--|--|
|iOS|iOS|iphoneos|iphonesimulator|
|tvOS|tvOS|appletvos|appletvsimulator|
|watchOS|watchOS|watchos|watchsimulator|

使用以下命令可以创建 iOS 上的 CMake 配置
```
cmake .. -GXcode -DCMAKE_SYSTEM_NAME=iOS
```

##### （1）配置 CPU 架构
设置 `CMAKE_OSX_ARCHITECTURES` 变量可以指定真机和模拟器的 CPU 架构。`CMAKE_OSX_DEPLOYMENT_TARGET` 可以设置部署目标（iOS/tvOS/watchOS）

下面的配置会安装 5 种体系架构下的 iOS 库，同时会在编译器中增加 `-miphoneos-version-min=9.3/-mios-simulator-version-min=9.3` 标志
```
$ cmake -S. -B_builds -GXcode \
    -DCMAKE_SYSTEM_NAME=iOS \
    "-DCMAKE_OSX_ARCHITECTURES=armv7;armv7s;arm64;i386;x86_64" \
    -DCMAKE_OSX_DEPLOYMENT_TARGET=9.3 \
    -DCMAKE_INSTALL_PREFIX=`pwd`/_install \
    -DCMAKE_XCODE_ATTRIBUTE_ONLY_ACTIVE_ARCH=NO \
    -DCMAKE_IOS_INSTALL_COMBINED=YES
```

> DCMAKE_OSX_DEPLOYMENT_TARGET 是用于指定目标平台的版本信息的吗？每个平台都对应真机和模拟器，CMake 是会同时将项目构建到这两个目标中？。

示例：
```
# CMakeLists.txt
cmake_minimum_required(VERSION 3.14)
project(foo)
add_library(foo foo.cpp)
install(TARGETS foo DESTINATION lib)
```

安装：
```
$ cmake --build _builds --config Release --target install
```

检查库
```
$ lipo -info _install/lib/libfoo.a
Architectures in the fat file: _install/lib/libfoo.a are: i386 armv7 armv7s x86_64 arm64
```
```
$ otool -l _install/lib/libfoo.a | grep -A2 LC_VERSION_MIN_IPHONEOS
      cmd LC_VERSION_MIN_IPHONEOS
  cmdsize 16
  version 9.3
```

##### （2）代码签名
嵌入式Apple平台的某些构建工件需要强制性代码签名。 如果正在使用Xcode生成器，并且需要或希望进行代码签名，则可以通过 `CMAKE_XCODE_ATTRIBUTE_DEVELOPMENT_TEAM `CMake变量指定开发团队ID。 然后，该团队ID将包含在生成的Xcode项目中。 默认情况下，CMake避免在内部配置阶段（即编译器ID和功能检测）进行代码签名。


##### （3）在真机与模拟器之间进行切换
在嵌入式平台中进行构建时，可以选择真机或者模拟器作为平台 SDK，不过 CMake 在配置阶段只支持指定一个单独的 SDK。在使用 XCode 生成器可以临时选择真机或者模拟器进行构建，即使在工具链文件中配置了真机或模拟器中的一个作为 SDK。使用命令行工具进行构建时，可以通过`-sdk`选项来选择需要的 sdk，例如：
```
$ cmake --build ... -- -sdk iphonesimulator
```

要注意的是，对于配置对于命令的检查针对的是在配置时指定的 SDK 进行的，临时切换到其他 SDK 可能会导致某些命令出现问题，比如 `find_package()`，`find_library()` 等之类的命令仅存储和使用已配置的 SDK 或平台的详细信息。通过以下规则来使设备+模拟器配置生效：
- 使用明确的 -l 链接器标志，例如 target_link_libraries(foo PUBLIC "-lz")
- 使用明确的 `-framework` 链接标志，例如：`target_link_libraries(foo PUBLIC "-framework CoreFoundation")`
- 仅对使用 `CMAKE_IOS_INSTALL_COMBINED` 功能安装的库使用 `find_package()`2020-04-16 17:32:47 星期四