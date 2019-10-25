package org.royole

import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.Checkstyle
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.plugins.quality.Pmd
import org.gradle.api.tasks.Exec

import java.util.function.Consumer

class QualityPlugin implements Plugin<Project> {

    Checkstyle checkStyleTask

    FindBugs findBugsTask

    Pmd pmdTask

    File reportsDir

    File configDir

    final static String QUALITY_GROUP = "quality"

    @Override
    void apply(Project targetProject) {

//        targetProject.beforeEvaluate {
        targetProject.pluginManager.apply('checkstyle')
        targetProject.pluginManager.apply('findbugs')
        targetProject.pluginManager.apply('pmd')
//        }


        def ext = targetProject.extensions.create('quality', QualityExtension, targetProject)

        configDir = new File(targetProject.rootProject.buildDir, "quality/config")
        reportsDir = new File(targetProject.buildDir, "reports")

        targetProject.beforeEvaluate {
            //lint
            File lintConfig = new File("$configDir/lint/lint.xml")
            processResource("lint.xml", lintConfig)
            if (targetProject.plugins.hasPlugin('com.android.application')) {
                targetProject.android.lintOptions {
                    abortOnError true
                    xmlReport false
                    htmlReport true
                    lintConfig lintConfig
                    htmlOutput targetProject.file("$reportsDir/lint/lint-result.html")
                    xmlOutput targetProject.file("$reportsDir/lint/lint-result.xml")
                }
            }
        }

        targetProject.afterEvaluate {

            //checkstyle
            checkStyleTask = (Checkstyle) targetProject.task('type': Checkstyle, "checkstyle")
            checkStyleTask.configFile = new File(configDir, "checkstyle/alicheckstyle.xml")
            processResource('alicheckstyle.xml', checkStyleTask.configFile)
            File defaultSuppression = new File(configDir, "/checkstyle/suppressions.xml")
            /**
             * 检查开发者自定义的Suppression文件
             * 如果未指定或指定为默认输出的Suppression文件，则使用默认Suppression文件
             * 如果指定了Suppression文件，则在默认文件的基础上增量修改
             */
            processResource("suppressions.xml", defaultSuppression)
            if (!defaultSuppression.absolutePath.equals(ext.getCheckstyleSuppression().absolutePath)) {
                SuppressionHelper suppressionHelper = new SuppressionHelper(defaultSuppression)
                suppressionHelper.appendSuppression(ext.getCheckstyleSuppression())
            }
            checkStyleTask.configProperties.checkstyleSuppressionsPath = ext.getCheckstyleSuppression().absolutePath
            checkStyleTask.source('src')
                    .include('**/*.java')
                    .exclude('**/gen/**')
            checkStyleTask.setClasspath(targetProject.files())
            checkStyleTask.setGroup(QUALITY_GROUP)


            //findbugs
            findBugsTask = (FindBugs) targetProject.task('type': FindBugs, "findbugs")
            findBugsTask.dependsOn("assembleDebug")
            findBugsTask.setIgnoreFailures(false)
            findBugsTask.setEffort("max")
            findBugsTask.setReportLevel("high")
            File excludeFilter = new File(configDir, "findbugs/findbugs-filter.xml");
            findBugsTask.setExcludeFilter(excludeFilter)
            processResource("findbugs-filter.xml", excludeFilter)
            findBugsTask.setClasses(targetProject.files("${targetProject.buildDir.path}/intermediates/javac"))
            findBugsTask.classpath = targetProject.files()
            findBugsTask.reports {
                xml.enabled = false
                html.enabled = true
                xml {
                    destination targetProject.file("$reportsDir/findbugs/findbugs.xml")
                }
                html {
                    destination targetProject.file("$reportsDir/findbugs/findbugs.html")
                }
            }
            findBugsTask.source('src')
                    .include('**/*.java')
                    .exclude('**/gen/**')
            findBugsTask.setGroup(QUALITY_GROUP)


            //pmd
            pmdTask = (Pmd) targetProject.task('type': Pmd, "pmd")
            pmdTask.ignoreFailures = false
            File pmdRuleSet = new File(configDir, "/pmd/pmd-ruleset.xml")
            processResource("pmd-ruleset.xml", pmdRuleSet)
            pmdTask.ruleSetFiles = targetProject.files(pmdRuleSet)
            pmdTask.ruleSets = []
            pmdTask.reports {
                xml.enabled = false
                html.enabled = true
                xml {
                    destination targetProject.file("$reportsDir/pmd/pmd.xml")
                }
                html {
                    destination targetProject.file("$reportsDir/pmd/pmd.html")
                }
            }
            pmdTask.source('src')
                    .include('**/*.java')
                    .exclude('**/gen/**')
            pmdTask.setGroup(QUALITY_GROUP)

            //check task depend on all quality tasks
            Set<Task> checkTasks = targetProject.getTasksByName('check', false)
            Set<Task> lintTasks = targetProject.getTasksByName('lint', false)
            checkTasks.each {
                it.dependsOn(checkStyleTask, findBugsTask, pmdTask)
                lintTasks.forEach(new Consumer<Task>() {
                    @Override
                    void accept(Task task) {
                        it.dependsOn(task)
                    }
                })
            }


            //detectInfer
            Exec detectInfer = (Exec) targetProject.task('type': Exec, 'detectInfer')
            detectInfer.commandLine('command')
            detectInfer.args(['-v', 'infer'])
            detectInfer.standardOutput = new ByteArrayOutputStream()
            detectInfer.ignoreExitValue = true
            detectInfer.doLast {
                if (detectInfer.execResult.getExitValue() != 0) {
                    throw new GradleException("Infer seems not installed. Please refer to http://fbinfer.com/docs/getting-started.html")
                }
            }
            detectInfer.setGroup(QUALITY_GROUP)


            //infer
            Exec infer = (Exec) targetProject.task('type': Exec, 'infer')
            infer.workingDir(targetProject.rootDir)
            infer.commandLine('infer')
            infer.args(['--', './gradlew', 'build'])
            Set<Task> cleanTasks = targetProject.getTasksByName('clean', false)
            cleanTasks.each {
                infer.dependsOn(it)
            }
            infer.dependsOn(detectInfer)
            infer.setGroup(QUALITY_GROUP)


        }
    }


    static final int READ_BUFFER_SIZE = 128

    /**
     * 获取本插件工程资源文件的输入流，写入到目标工程根目录下
     * @param resourcePath
     * @param output
     */
    static void processResource(String resourcePath, File output) {
        if (null == output) {
            return
        }
        if (output.exists()) {
            output.delete()
        }

        output.parentFile.mkdirs()
        output.createNewFile()
        InputStream inputStream = QualityPlugin.class.getClassLoader().getResourceAsStream(resourcePath)
        byte[] buffer = new byte[READ_BUFFER_SIZE]
        int readLength
        FileOutputStream fos = new FileOutputStream(output)
        while ((readLength = inputStream.read(buffer, 0, READ_BUFFER_SIZE)) != -1) {
            fos.write(buffer, 0, readLength)
        }
        fos.flush()
        fos.close()
    }
}
