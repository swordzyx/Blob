## 一、介绍

> 官方对于生成器的解释如下：A CMake Generator is responsible for writing the input files for a native build system. Exactly one of the CMake Generators must be selected for a build tree to determine what native build system is to be used. Optionally one of the Extra Generators may be selected as a variant of some of the Command-Line Build Tool Generators to produce project files for an auxiliary IDE.

可以认为生成器就是读取指定的输入文件（比如源代码文件），然后构建成目标。因此必须要为构建树选定一个生成器。每个生成器都有其特定的运行环境，因此每个平台都对应特定的生成器，例如有些生成器可以在一些集成开发环境（IDE）中运行。



## 二、CMake 生成器分类

#### 1. 命令行构建工具生成器
这些生成器可以在命令行构建工具中运行。使用这个工具需要从命令提示符启动 CMake，并且为配置好编译器与构建工具所需要的环境
##### （1）Makefile Generators
- [Borland Makefiles](https://cmake.org/cmake/help/v3.17/generator/Borland%20Makefiles.html)

- [MSYS Makefiles](https://cmake.org/cmake/help/v3.17/generator/MSYS%20Makefiles.html)

- [MinGW Makefiles](https://cmake.org/cmake/help/v3.17/generator/MinGW%20Makefiles.html)

- [NMake Makefiles](https://cmake.org/cmake/help/v3.17/generator/NMake%20Makefiles.html)

- [NMake Makefiles JOM](https://cmake.org/cmake/help/v3.17/generator/NMake%20Makefiles%20JOM.html)

- [Unix Makefiles](https://cmake.org/cmake/help/v3.17/generator/Unix%20Makefiles.html)

- [Watcom WMake](https://cmake.org/cmake/help/v3.17/generator/Watcom%20WMake.html)


##### （2）Ninja Generators
- [Ninja](https://cmake.org/cmake/help/v3.17/generator/Ninja.html)

- [Ninja Multi-Config](https://cmake.org/cmake/help/v3.17/generator/Ninja%20Multi-Config.html)

#### 2. IDE Build Tool Generators
这些生成器可以在 IDE（集成开发工具环境）中运行，这些 IDE 自身就带有 CMake，因此可以在 IDE 内启动 CMake 来进行构建。

##### （1）Visual Studio Generators
- [Visual Studio 6](https://cmake.org/cmake/help/v3.17/generator/Visual%20Studio%206.html)

- [Visual Studio 7](https://cmake.org/cmake/help/v3.17/generator/Visual%20Studio%207.html)

- [Visual Studio 7 .NET 2003](https://cmake.org/cmake/help/v3.17/generator/Visual%20Studio%207%20.NET%202003.html)

- [Visual Studio 8 2005](https://cmake.org/cmake/help/v3.17/generator/Visual%20Studio%208%202005.html)

*   [Visual Studio 9 2008](https://cmake.org/cmake/help/v3.17/generator/Visual%20Studio%209%202008.html)

*   [Visual Studio 10 2010](https://cmake.org/cmake/help/v3.17/generator/Visual%20Studio%2010%202010.html)

*   [Visual Studio 11 2012](https://cmake.org/cmake/help/v3.17/generator/Visual%20Studio%2011%202012.html)

*   [Visual Studio 12 2013](https://cmake.org/cmake/help/v3.17/generator/Visual%20Studio%2012%202013.html)

*   [Visual Studio 14 2015](https://cmake.org/cmake/help/v3.17/generator/Visual%20Studio%2014%202015.html)

*   [Visual Studio 15 2017](https://cmake.org/cmake/help/v3.17/generator/Visual%20Studio%2015%202017.html)

*   [Visual Studio 16 2019](https://cmake.org/cmake/help/v3.17/generator/Visual%20Studio%2016%202019.html)

##### （2）Other Generators
*   [Green Hills MULTI](https://cmake.org/cmake/help/v3.17/generator/Green%20Hills%20MULTI.html)

*   [Xcode](https://cmake.org/cmake/help/v3.17/generator/Xcode.html)


#### 3. Extra Generators

在 cmake 命令中输入 --help 参数会输出一些 CMake 生成器变体，这些生成器可用于一些辅助的 IDE 工具上，这些生成器以 `<extra-generator> - <main-generator>` 的形式命名，也被称为附加生成器（extra generator），下面是 CMake 中已知的附加生成器
- [CodeBlocks](https://cmake.org/cmake/help/v3.17/generator/CodeBlocks.html)

- [CodeLite](https://cmake.org/cmake/help/v3.17/generator/CodeLite.html)

- [Eclipse CDT4](https://cmake.org/cmake/help/v3.17/generator/Eclipse%20CDT4.html)

- [Kate](https://cmake.org/cmake/help/v3.17/generator/Kate.html)

- [Sublime Text 2](https://cmake.org/cmake/help/v3.17/generator/Sublime%20Text%202.html)





