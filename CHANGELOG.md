Changelog
=========

0.2.1
-----

_2023-01-09_

Happy new year!

- **New**: Expose the API to pass flags to KAPT. This is necessary in order to use KAPT's new JVM IR support.

0.2.0
-----

_2022-12-28_

- Deprecate `KotlinCompilation.singleModule` option as it no longer exists in kotlinc.
- Propagate `@ExperimentalCompilerApi` annotations
- `KotlinJsCompilation.irOnly` and `KotlinJsCompilation.irProduceJs` now default to true and are the only supported options.
- Expose new `KotlinCompilation.compilerPluginRegistrars` property for adding `CompilerPluginRegistrar` instances (the new entrypoint API for compiler plugins)
  ```kotlin
  KotlinCompilation().apply {
    compilerPluginRegistrars = listOf(MyCompilerPluginRegistrar())
  }
  ```
- Deprecate `KotlinCompilation.compilerPlugins` in favor of `KotlinCompilation.componentRegistrars`. The latter is also deprecated, but this is at least a clearer name.
  ```diff
  KotlinCompilation().apply {
  -  compilerPlugins = listOf(MyComponentRegistrar())
  +  componentRegistrars = listOf(MyComponentRegistrar())
  }
  ```
- Don't try to set removed kotlinc args. If they're removed, they're removed forever. This library will just track latest kotlin releases with its own.
- Dependency updates:
  ```
  Kotlin (and its associated artifacts) 1.8.0
  KSP 1.8.0
  Classgraph: 4.8.153
  ```

Special thanks to [@bnorm](https://github.com/bnorm) for contributing to this release.

0.1.0
-----

_2022-12-01_

Initial release. Changes from the original repo are as follows

Base commit: https://github.com/tschuchortdev/kotlin-compile-testing/commit/4f394fe485a0d6e0ed438dd9ce140b172b1bd746

- **New**: Add `supportsK2` option to `KotlinCompilation` to allow testing the new K2 compiler.
- Update to Kotlin `1.7.22`.
- Update to KSP `1.7.22-1.0.8`.
