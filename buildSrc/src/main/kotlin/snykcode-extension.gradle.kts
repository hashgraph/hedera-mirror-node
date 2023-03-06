plugins {
    id("io.snyk.gradle.plugin.snykplugin")
}
abstract class SnykCodeTask : io.snyk.gradle.plugin.SnykTask() {
    @TaskAction
    fun doSnykTest() {
        log.debug("Snyk Code Test Task")
        authentication()
        val output: io.snyk.gradle.plugin.Runner.Result = runSnykCommand("code test")
        log.lifecycle(output.output)
        if (output.exitcode > 0) {
            throw GradleException("Snyk Code Test failed")
        }
    }
}


tasks.register<SnykCodeTask>("snyk-code"){
    dependsOn("snyk-check-binary")
    snyk {
        setSeverity("high")
        setArguments("--all-sub-projects --json-file-output=reports/snyk-test.json")
    }
}

tasks.`snyk-monitor`{
    doFirst{
        snyk{
            setArguments("--all-sub-projects")
        }
    }
}

tasks.`snyk-test`{
    snyk {
        setSeverity("high")
        setArguments("--all-sub-projects --json-file-output=reports/snyk-test.json")
    }
}
