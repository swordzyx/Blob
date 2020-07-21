[官方文档](https://cmake.org/cmake/help/latest/command/set_source_files_properties.html)

设置源文件，这些文件中包含了可以影响系统构建方式的属性

```
set_source_files_properties(<files> ...
                            [DIRECTORY <dirs> ...]
                            [TARGET_DIRECTORY <targets> ...]
                            PROPERTIES <prop1> <value1>
                            [<prop2> <value2>] ...)
```

使用键值对列表的形式设置与源文件关联的属性

默认情况下，源文件属性仅对添加到同一目录（CMakeLists.txt）中的目标可见。可以使用以下一个或两个选项在其他目录范围内设置可见性：

- **`DIRECTORY <dirs>...`**

源文件属性将在每个 `<dirs>` 目录的作用域中设置。 CMake 必须已经调用 `add_subdirectory()` 添加了这些源目录，或者它们是顶级源目录，已经知道了每个源目录。 相对路径被视为相对于当前源目录的路径。

- **`TARGET_DIRECTORY <targets>...`**

The source file properties will be set in each of the directory scopes where any of the specified   `<targets>` were created (the `<targets>` must therefore already exist).

对每一个创建了指定`<target>`目标的目录启用源文件属性（目录中必须已存在指定的 target 中的任意一个）。

通过 `get_source_file_property()` 获取属性值
