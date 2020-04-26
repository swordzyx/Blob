CMake 中的命令有以下几类

#### 1. Scripting Commands（脚本命令）
可用的脚本命令如下：
- **`break`**


- **`cmake_host_system_information`**


- **`cmake_minimum_required`**

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

- `cmake_parse_arguments`
- `cmake_policy`
- `configure_file`
- `continue`
- `else`
- `elseif`
- `endforeach`
- `endfunction`
- `endif`
- `endmacro`
- `endwhile`
- `execute_process`
- `file`
- `find_file`
- `find_library`
- `find_package`
- `find_path`
- `find_program`
- `foreach`
- `function`
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
- `set_property`
- `site_name`
- `string`
- `unset`
- `variable_watch`
- `while`

#### 2. Project Commands（项目命令）
下面的命令仅仅在基于 CMake 的项目中使用
- `add_compile_definitions`
- `add_compile_options`
- `add_custom_command`
- `add_custom_target`
- `add_definitions`
- `add_dependencies`
- `add_executable`
- `add_library`
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
- `include_external_msproject`
- `include_regular_expression`
- `install`
- `link_directories`
- `link_libraries`
- `load_cache`
- `project`
- `remove_definitions`
- `set_source_files_properties`
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