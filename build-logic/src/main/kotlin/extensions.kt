import org.gradle.api.Project
import org.gradle.api.provider.Provider

// set by GitHub Actions
val Project.ci: Provider<Boolean>
    get() = providers.environmentVariable("CI")
            .forUseAtConfigurationTime()
            .map { it.toBoolean() }
            .orElse(false)

val Project.compileExamples: Boolean
    get() = providers.gradleProperty("compile-examples")
            .forUseAtConfigurationTime()
            .isPresent
