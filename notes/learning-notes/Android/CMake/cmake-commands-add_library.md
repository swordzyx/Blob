[官方文档](https://cmake.org/cmake/help/latest/command/add_library.html)

使用指定的源文件将库添加到项目中。

## 一、Normal Libraries（常规库）
```
add_library(<name> [STATIC | SHARED | MODULE]
            [EXCLUDE_FROM_ALL]
            [source1] [source2 ...])
```
添加一个名为 `<name>` 的库，该库由该命令所列出的源文件构建产生（如果之后要使用 target_sources() 命令添加源文件，则此处可以省略）。`<name>` 在整个项目中必须时全局唯一的，而实际的文件名则是根据平台规则命名。

通过 STATIC, SHARED, MODULE 参数来确定要创建的库的类型，STATIC（静态） 库是目标源文件的归档，供链接其他目标时使用。SHARED（动态） 库是动态链接的，并在运行时加载。MODULE 库是未链接到其他目标的插件，但可以在运行时使用类似 dlopen 的功能动态加载。

如果没有明确指定类型，则根据变量 BUILD_SHARED_LIBS 的当前值是否为ON，将其指定为 STATIC 或 SHARED 。对于 SHARED 和 MODULE 库，POSITION_INDEPENDENT_CODE 目标属性自动设置为 ON 。可以使用 FRAMEWORK 目标属性标记 SHARED 或 STATIC 库以创建 macOS 框架。

如果库未导出任何符号，则不得将其声明为 SHARED 库。
> 原文：If a library does not export any symbols, it must not be declared as a SHARED library. For example, a Windows resource DLL or a managed C++/CLI DLL that exports no unmanaged symbols would need to be a MODULE library. This is because CMake expects a SHARED library to always have an associated import library on Windows.

如果指定了 EXCLUDE_FROM_ALL 参数，则将在创建的目标上设置相应的属性。