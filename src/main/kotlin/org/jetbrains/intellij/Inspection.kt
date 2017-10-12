package org.jetbrains.intellij

import groovy.lang.Closure
import groovy.lang.DelegatesTo
import org.gradle.api.Action
import org.gradle.api.GradleException
import org.gradle.api.file.FileCollection
import org.gradle.api.file.FileTree
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.api.plugins.quality.CheckstyleReports
import org.gradle.api.reporting.Reporting
import org.gradle.api.resources.TextResource
import org.gradle.api.tasks.*
import org.gradle.api.tasks.Optional
import org.jdom.input.SAXBuilder
import org.jetbrains.idea.inspections.InspectionRunner
import java.io.File
import java.util.*
import org.gradle.api.Project as GradleProject

@CacheableTask
open class Inspection : SourceTask(), VerificationTask, Reporting<CheckstyleReports> {

    /**
     * The class path containing the compiled classes for the source files to be analyzed.
     */
    @get:Classpath
    lateinit var classpath: FileCollection

    /**
     * The configuration to use. Replaces the `configFile` property.
     *
     * @since 2.2
     */
    var config: TextResource
        get() = extension.config
        set(value) {
            extension.config = value
        }

    /**
     * The properties available for use in the configuration file. These are substituted into the configuration file.
     */
    @get:Input
    @get:Optional
    var configProperties: Map<String, Any> = LinkedHashMap()

    private val reports = IdeaCheckstyleReports(this)
    private var ignoreFailures: Boolean = false

    private val extension get() = project.extensions.findByType(InspectionPluginExtension::class.java)!!
    /**
     * The maximum number of errors that are tolerated before breaking the build
     * or setting the failure property.
     *
     * @since 3.4
     * @return the maximum number of errors allowed
     */
    var maxErrors: Int
        @Input get() = extension.maxErrors
        set(value) {
            extension.maxErrors = value
        }

    /**
     * The maximum number of warnings that are tolerated before breaking the build
     * or setting the failure property.
     *
     * @since 3.4
     * @return the maximum number of warnings allowed
     */
    var maxWarnings: Int
        @Input get() = extension.maxWarnings
        set(value) {
            extension.maxWarnings = value
        }

    /**
     * Whether rule violations are to be displayed on the console.
     *
     * @return true if violations should be displayed on console
     */

    @get:Console
    var showViolations: Boolean
        @Input get() = extension.isShowViolations
        set(value) {
            extension.isShowViolations = value
        }


    /**
     * The configuration file to use.
     */
    var configFile: File?
        @Internal
        get() = config.asFile()
        set(configFile) {
            config = project.resources.text.fromFile(configFile)
        }

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * checkstyleTask {
     * reports {
     * html {
     * destination "build/codenarc.html"
     * }
     * }
     * }
    </pre> *
     *
     * @param closure The configuration
     * @return The reports container
     */
    override fun reports(
            @DelegatesTo(value = CheckstyleReports::class, strategy = Closure.DELEGATE_FIRST) closure: Closure<*>
    ): CheckstyleReports = reports(ClosureBackedAction(closure))

    /**
     * Configures the reports to be generated by this task.
     *
     * The contained reports can be configured by name and closures. Example:
     *
     * <pre>
     * checkstyleTask {
     * reports {
     * html {
     * destination "build/codenarc.html"
     * }
     * }
     * }
    </pre> *
     *
     * @since 3.0
     * @param configureAction The configuration
     * @return The reports container
     */
    override fun reports(configureAction: Action<in CheckstyleReports>): CheckstyleReports {
        configureAction.execute(reports)
        return reports
    }

    private fun readInspectionClassesFromConfigFile(): InspectionClassesSuite {
        val builder = SAXBuilder()
        val document = builder.build(configFile)
        val root = document.rootElement

        val errorClasses = root.getChild("errors").children.map { it.getAttributeValue("class") }
        val warningClasses = root.getChild("warnings").children.map { it.getAttributeValue("class") }
        val infoClasses = root.getChild("infos").children.map { it.getAttributeValue("class") }

        return InspectionClassesSuite(errorClasses, warningClasses, infoClasses)
    }

    @TaskAction
    fun run() {
        try {
            val inspectionClasses = readInspectionClassesFromConfigFile()

            val runner = InspectionRunner(
                    project.projectDir.absolutePath, maxErrors, maxWarnings, showViolations, inspectionClasses, reports)
            runner.analyzeTreeAndLogResults(getSource(), logger)
        }
        catch (e: Throwable) {
            logger.error("EXCEPTION caught in inspections plugin: " + e.message)
            if (e is GradleException) throw e
            throw GradleException("Exception occurred in analyze task", e)
        }
    }

    /**
     * {@inheritDoc}
     *
     *
     * The sources for this task are relatively relocatable even though it produces output that
     * includes absolute paths. This is a compromise made to ensure that results can be reused
     * between different builds. The downside is that up-to-date results, or results loaded
     * from cache can show different absolute paths than would be produced if the task was
     * executed.
     */
    @PathSensitive(PathSensitivity.RELATIVE)
    override fun getSource(): FileTree = super.getSource()

     /**
     * The reports to be generated by this task.
     */
    @Nested
    override fun getReports(): CheckstyleReports = reports

    /**
     * Whether or not this task will ignore failures and continue running the build.
     *
     * @return true if failures should be ignored
     */
    @Input
    override fun getIgnoreFailures(): Boolean = ignoreFailures

    /**
     * Whether this task will ignore failures and continue running the build.
     *
     * @return true if failures should be ignored
     */
    @Suppress("unused")
    open fun isIgnoreFailures(): Boolean = ignoreFailures

    /**
     * Whether this task will ignore failures and continue running the build.
     */
    override fun setIgnoreFailures(ignoreFailures: Boolean) {
        this.ignoreFailures = ignoreFailures
    }

    fun setSourceSet(source: FileTree) {
        setSource(source as Any)
    }
}