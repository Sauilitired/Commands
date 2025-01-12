import com.diffplug.gradle.spotless.SpotlessExtension
import net.kyori.indra.IndraExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.attributes
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.incendo.cloudbuildlogic.CloudSpotlessExtension

class CloudBaseConventions : Plugin<Project> {
    override fun apply(target: Project) {
        target.plugins.apply("org.incendo.cloud-build-logic")
        target.plugins.apply("org.incendo.cloud-build-logic.spotless")
        target.plugins.apply("org.incendo.cloud-build-logic.errorprone")

        val libs = target.libs

        target.extensions.configure(IndraExtension::class) {
            checkstyle().set(libs.versions.checkstyle)
        }

        target.extensions.configure(CloudSpotlessExtension::class) {
            ktlintVersion.set(libs.versions.ktlint)
        }

        target.extensions.configure(SpotlessExtension::class) {
            java {
                importOrderFile(target.rootProject.file(".spotless/cloud.importorder"))
            }
        }

        /* Disable checkstyle on tests */
        target.gradle.startParameter.excludedTaskNames.add("checkstyleTest")

        target.dependencies {
            "compileOnly"(libs.bundles.immutables)
            "annotationProcessor"(libs.bundles.immutables)

            "testImplementation"(libs.bundles.baseTestingDependencies)

            "errorprone"(libs.errorproneCore)
            // Silences compiler warnings from guava using errorprone
            "compileOnly"(libs.errorproneAnnotations)

            "checkstyle"(libs.stylecheck)
        }

        target.tasks.withType(Jar::class).configureEach {
            manifest {
                attributes("Automatic-Module-Name" to "%s.%s".format(target.group, target.name.replace('-', '.')))
            }
        }
    }
}
