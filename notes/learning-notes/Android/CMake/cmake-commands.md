CMake 中的命令有以下几类

## 一. Scripting Commands（脚本命令）
可用的脚本命令如下：
#### 1. `break`


#### 2. `cmake_host_system_information`


#### 3. `cmake_minimum_required`

用于请求 Cmake 的最小版本。该命令格式如下
```
cmake_minimum_required(VERSION <min>[...<max>] [FATAL_ERROR])
```

`<min>` 和 `<max>` 指定 CMake 的最小和最大的版本，其中 `<max>` 是可选的，这两个属性的值得格式为 `major.minor[.patch[.tweak]]`（major 是主版本号，minor 是次版本号，patch 是补丁的版本号），`...` 是文本。

如果运行的 CMake 版本小于 `<min>` 所指定的版本，则会终止运行，并报告一个错误。如果指定了 `<max>`，必须要大于或等于 `<min>` 所指定的版本，它会根据以下描述来影响策略的设置：

如果 CMake 的版本小于 3.12，则多余的 `...` 将会被视为版本组件分隔符，此时 `<max>` 部分将会被忽略，同时保留 3.12 之前的，在 `<min>` 上的行为。

`<FATAL_ERROR>` 在 CMake 2.6 及以上的版本中会被忽略，不过在 CMake 2.4 及以下的版本中使用这个参数来报告失败错误，而非发出警告

> 官网对于 `<FATAL_ERROR>` 的解释：It should be specified so CMake versions 2.4 and lower fail with an error instead of just a warning.

> 注意：在项目根目录下的 CMakeLists.txt 文件中的开头必须调用该命令，并且必须在 `project()` 命令之前调用。在可能受到该命令影响的其他命令之前必须要使用这个命令来创建版本以及策略的构建。

**策略设定**：`cmake_minimum_required` 会隐式调用 `cmake_policy(VERSION)` 命令，该命令指定了当前项目的代码将被给定版本的 CMake 写入构建系统。

当前运行的版本， `<min>` （或者 `<max>`） 指定的版本以及更早的版本中所有已知的策略都将被设置为使用 `NEW` 行为，而更新的版本中的策略则不会被设置。这可以有效的请求指定版本的首选行为，并且在新版本的 CMake 中会给出一个关于它们的新策略的警告。

当 `<min>` 指定的版本高于 2.4 时会隐式调用下面的命令

```
cmake_policy(VERSION <min>[...<max>])
```

该命令会设置上面所指定的一系列版本中的策略。如果 `<min>` 所指定的版本小于或等于 2.4，会隐式调用下面的命令

```
cmake_policy(VERSION 2.4[...<max>])
```

上面的命令兼容了 CMake 2.4 及以下的版本。

#### 4. `cmake_parse_arguments`
#### 5. `cmake_policy`
#### 6. `configure_file`
#### 7. `continue`
#### 8. `else`
#### 9. `elseif`
#### 10. `endforeach`
#### 11. `endfunction`
#### 12. `endif`
#### 13. `endmacro`
#### 14. `endwhile`
#### 15. `execute_process`

#### 16. `file` 
> https://cmake.org/cmake/help/latest/command/file.html










#### 3. `find_file`
#### 3. `find_library`
#### 3. `find_package`
#### 3. `find_path`
#### 3. `find_program`
#### 3. `foreach`
#### 3. `function`
- `get_cmake_property`
- `get_directory_property`
- `get_filename_component`
- `get_property`
- `if`
- `include`
- `include_guard`
- `list`
- `macro`
- `mark_as_advanced`
- `math`
- `message`
- `option`
- `return`
- `separate_arguments`

#### 39. `set`
设置指定的正常变量，缓存变量或者环境变量为给定的值（相当于创建变量并且赋值），查看 [cmake-language(7) variables](https://cmake.org/cmake/help/latest/manual/cmake-language.7.html#cmake-language-variables) 文档可查看正常变量以及缓存变量的范围以及作用

此命令可以通过`<value> ...`占位符的方式指定 0 个或者多个参数。多个参数以分号分隔的列表形式连接，无参数将重置普通变量。

##### （1）设置普通变量
```
set(<variable> <value>... [PARENT_SCOPE])
```

设置 `<variable>` 的作用范围为当前函数或者当前目录，如果设置了 `[PARENT_SCOPE]` 操作项，则会将该变量的作用域设置为当前作用域的上一级作用域。每一个新目录或者函数都会有一个新的范围，该命令会将变量的值设置到父目录或者调用该命令的函数中，变量的状态将保持不变。

##### （2）设置缓存条目
```
set(<variable> <value>... CACHE <type> <docstring> [FORCE])
```
设置缓存变量。由于高速缓存条目旨在提供用户可设置的值，因此默认情况下不会覆盖现有的高速缓存条目。 使用FORCE选项强制覆盖现有条目。

`<type>` 必须为以下值之一
- `BOOL`：可选值为 `ON` 或者 `OFF`。
- `FILEPATH`：文件在磁盘中的路径
- `PATH`：文件夹在磁盘中的路径
- `STRING`：一个文本行，在 `cmake-gui` 工具中会以下拉文本框的形式显示。
- `INTERNAL`：一行文字，这种类型的变量在 `cmake-gui` 工具中不会显示，它们主要用于在运行过程中存储永久的变量，使用 `FORCE` 来启用这个类型。

必须将`<docstring>`指定为一行文本，以提供向cmake-gui（1）用户演示的选项的快速摘要。如果在调用之前不存在高速缓存条目，或者给出了FORCE选项，则将高速缓存条目设置为给定值。并且如果新的缓存值随后会被赋值，则于当前作用域绑定的正常变量将会被移除。

如果用户使用在命令行中通过 `-D<var>=<value>` 操作项创建了缓存变量，但是未指定任何类型，那么这个缓存变量是可能在调用之前就已经存在的，此时 `set` 将会为其添加类型。此外，如果 `<type>` 是 `PATH` 类型或者 `FILEPATH` 类型，并且在命令行已经指定了路径值，则 `set` 会将该路径视为相对于当前的工作路径，同时会将其转换为绝对路径

##### （3）设置环境变量
语法：
```
set(ENV{<variable>} [<value>])
```
将指定的环境变量设为给定的值。随后调用的 `$ENV{<variable>}` 将返回新的值。此处设置的环境变量只会对当前的 CMake 进程产生影响，不影响调用CMake的进程，也不影响整个系统环境，也不影响后续构建或测试过程的环境。

如果后面的 `[<value>]` 为空，则会清除环境变量的值。`<value>` 背后不应该有任何其他的参数，否则会发出警告。


- `set_property`
- `site_name`
- `string`
- `unset`
- `variable_watch`
- `while`

## 二. Project Commands（项目命令）
下面的命令仅仅在基于 CMake 的项目中使用
- `add_compile_definitions`
- `add_compile_options`
- `add_custom_command`
- `add_custom_target`
- `add_definitions`
- `add_dependencies`
- `add_executable`
- [`add_library`](cmake-commands-add_library.md)
- `add_link_options`
- `add_subdirectory`
- `add_test`
- `aux_source_dirctory`
- `build_command`
- `create_test_sourcelist`
- `define_property`
- `enable_language`
- `enable_testing`
- `export`
- `fltk_wrap_ui`
- `get_source_file_property`
- `get_target_property`
- `get_test_property`
- `include_directories`
将指定的目录包含进构建过程中，语法如下
```
include_directories([AFTER|BEFORE] [SYSTEM] dir1 [dir2 ...])
```
构建过程中，编译器将从该命令指定的目录中寻找编译需要的包含文件。如果是相对路径，会被认为是相对于当前路径（`CMAKE_CURRENT_SOURCE_DIR` 指向当前所在的路径 ）。

包含目录将添加到当前 `CMakeLists` 文件的 `INCLUDE_DIRECTORIES` 目录属性中。它们也将被添加到当前 `CMakeLists` 文件中每个目标的 `INCLUDE_DIRECTORIES` 目标属性中。 目标属性值是生成器使用的属性值。

该命令指定的目录默认会被追加到当前的目录列表中，将 `CMAKE_INCLUDE_DIRECTORIES_BEFORE` 设置为 `NO` ，可以更改此行为。`AFTER` 和 `BEFORE` 选项可以配置是前置添加还是后置添加（这里不是很明白）
> 官方文档描述如下：By using AFTER or BEFORE explicitly, you can select between appending and prepending, independent of the default.

`SYSTEM` 用于告诉编译器指定的目录为系统目录，使用这个配置可以实现一些效果，比如编译器跳过警告，或者在依赖项计算中不考虑这些固定安装的系统文件。

在该命令中可以使用 `$<...>` 表达式。可以参考 [cmake-generator-expressions](https://cmake.org/cmake/help/latest/manual/cmake-generator-expressions.7.html#manual:cmake-generator-expressions(7)) 查看更多的表达式。

> 注意：如果是将目录添加到单个目标，可以使用 `target_include_directories()` 命令，还可以选择是否将这些目录导出到依赖项中。


- `include_external_msproject`
- `include_regular_expression`
- `install`
- `link_directories`
- `link_libraries`
- `load_cache`

- [`project`](cmake-command-project.md)

- `remove_definitions`

- [`set_source_files_properties`](cmake-command-set_source_files_properties.md)
- `set_target_properties`
- `set_tests_properties`
- `source_group`
- `target_compile_definitions`
- `target_compile_features`
- `target_compile_options`
- `target_include_directories`
- `target_link_directories`
- `target_link_libraries`
- `target_link_options`
- `target_precompile_headers`
- `target_sources`
- `try_compile`
- `try_run`

#### 3. CTest Commands（测试命令）
下面的命令仅用于 CTest 脚本，用于测试
- `ctest_build`
- `ctest_configure`
- `ctest_coverage`
- `ctest_empty_binary_directory`
- `ctest_mencheck`
- `ctest_read_custom_files`
- `ctest_run_script`
- `ctest_sleep`
- `ctest_start`
- `ctest_submit`
- `ctest_test`
- `ctest_update`
- `ctest_upload`


#### 4. Deprecate  Commands（被弃用的命令）
以下命令几乎用于兼容历史版本，在新版本中已被弃用。
- `build_name`
- `exec_program`
- `export_library_dependencies`
- `install_files`
- `install_programs`
- `install_targets`
- `load_command`
- `make_directory`
- `output_required_files`
- `qt_wrap_cpp`
- `qt_wrap_ui`
- `remove`
- `subdir_depends`
- `subdirs`
- `use_mangled_mesa`
- `utility_source`
- `variable_requires`
- `write_file`