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

- 4. `cmake_parse_arguments`
- 5. `cmake_policy`
- 6. `configure_file`
- 7. `continue`
- 8. `else`
- 9. `elseif`
- 10. `endforeach`
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
#### 39. `set`
设置指定的正常变量，缓存变量或者环境变量为给定的值，

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

#### 30. `project`

用于设置项目的名称。

##### （1）语法摘要：

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

##### （2）操作选项

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


##### （3）代码注入
如果设置了`CMAKE_PROJECT_INCLUDE_BEFORE` 或者 `CMAKE_PROJECT_<PROJECT-NAME>_INCLUDE_BEFORE` 变量，那么它们所指向的文件将在 `project()` 的第一步包含进来。如果同时设置了两个，`CMAKE_PROJECT_INCLUDE_BEFORE` 会比 `CMAKE_PROJECT_<PROJECT-NAME>_INCLUDE_BEFORE` 先包含进来

如果设置了 `CMAKE_PROJECT_INCLUDE` 或者 `CMAKE_PROJECT_<PROJECT-NAME>_INCLUDE` ,它们所指向的文件会在 `project()` 命令的最后一步被包含进来，如果同时设置了两者，那么 `CMAKE_PROJECT_INCLUDE` 会比 `CMAKE_PROJECT_<PROJECT-NAME>_INCLUDE` 先被包含进来


##### （4）用法
CMakeList.txt 文件的顶层必须包含对于 `project()` 的直接调用，仅仅通过 `include()` 命令加载一个是不够的，如果不存在这样的调用，CMake 会发出一个警告，并且会自动认为已经在顶部调用了 `project(project)` ，以启用默认语言（C 或者 CXX）

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