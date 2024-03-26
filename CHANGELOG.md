Changelog
=========

0.4.1
-----

_2024-03-25_

- **Fix**: Fix decoding of classloader resources.
- Update to Kotlin `1.9.23`.
- Update to KSP `1.9.2301.0.19`.
- Update to classgraph `4.8.168`.
- Update to Okio `3.9.0`.

Special thanks to [@jbarr21](https://github.com/jbarr21) for contributing to this release!

0.4.0
-----

_2023-10-31_

- **Enhancement**: Create parent directories of `SourceFile` in compilations.
- Update to Kotlin `1.9.20`.
- Update to KSP `1.9.20-1.0.13`.
- Update to ClassGraph `4.8.162`.
- Update to Okio `3.6.0`.

Special thanks to [@BraisGabin](https://github.com/BraisGabin) for contributing to this release!

0.3.2
-----

_2023-08-01_

- **Fix**: Include KSP-generated Java files in java compilation. This is particularly useful for KSP processors that generate Java code.
- **Enhancement**: Print full diagnostic messages when javac compilation fails, not just the cause. The cause message alone was often not very helpful.

0.3.1
-----

_2023-07-22_

- **Fix**: Set required `languageVersionSettings` property in `KspOptions`.
- Update to KSP `1.9.0-1.0.12`.
- Update to Okio `3.4.0`.

0.3.0
-----

_2023-07-06_

- **New**: Refactor results into common `CompilationResult` hierarchy.
- **Fix**: Missing UTF-8 encoding of logs resulting in unknown chars.
- **Fix**: Set resources path when compilerPluginRegistrars not empty.
- `useIR` is now enabled by default.
- Update to Kotlin `1.9.0`.
- Update to KSP `1.9.0-1.0.11`.

Special thanks to [@SimonMarquis](https://github.com/SimonMarquis) and [@bennyhuo](https://github.com/bennyhuo) for contributing to this release!

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
