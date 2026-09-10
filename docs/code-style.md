# Code style

Spotless runs Spring Java Format 0.0.48 on Java sources in every module, including tests. It uses tabs, 120-column wrapping and multiline method bodies. `.editorconfig` provides matching indentation and line-ending settings for editors.

Imports are grouped as `java`, `javax`, third-party packages, `dev.onyxium`, then static imports. Spotless shortens fully qualified type references, removes unused imports and rejects wildcard imports. Gradle scripts, properties and the version catalog are checked for trailing whitespace and final newlines.

```sh
./gradlew lint
./gradlew spotlessApply
./gradlew build
```

`lint` checks all modules without changing files. `spotlessApply` fixes formatting and imports; wildcard imports require explicit replacements. `build` includes the same checks and the test suite. For one module, run `./gradlew :onyxium-proxy:spotlessCheck` or `./gradlew :onyxium-proxy:spotlessApply`.

The configuration lives in the root `build.gradle.kts`; the Spotless version is pinned in `gradle/libs.versions.toml`. The Spring formatter and its Eclipse runtime are build dependencies. When changing the custom formatter's behavior, increment `bumpThisNumberIfACustomStepChanges` so existing files are checked again.

The formatter does not change local declarations to `var`, replace ordinary comments with documentation, or enforce every Spring Checkstyle rule. Continue to use `var`, explicit imports and documentation comments when writing code.

References: [Spring Java Format](https://github.com/spring-io/spring-javaformat), [Spotless for Gradle](https://github.com/diffplug/spotless/tree/main/plugin-gradle).
