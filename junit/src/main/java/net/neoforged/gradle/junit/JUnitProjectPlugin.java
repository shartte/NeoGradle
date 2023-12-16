package net.neoforged.gradle.junit;

import net.neoforged.gradle.common.runs.run.RunImpl;
import net.neoforged.gradle.common.runtime.definition.CommonRuntimeDefinition;
import net.neoforged.gradle.common.util.TaskDependencyUtils;
import net.neoforged.gradle.common.util.exceptions.MultipleDefinitionsFoundException;
import net.neoforged.gradle.common.util.exceptions.NoDefinitionsFoundException;
import net.neoforged.gradle.common.util.run.RunsUtil;
import net.neoforged.gradle.dsl.common.extensions.Minecraft;
import net.neoforged.gradle.userdev.runtime.definition.UserDevRuntimeDefinition;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JUnitProjectPlugin implements Plugin<Project> {
    private static final Logger LOG = LoggerFactory.getLogger(JUnitProjectPlugin.class);

    @Override
    public void apply(Project project) {
        project.afterEvaluate(this::applyAfterEvaluate);
    }

    private void applyAfterEvaluate(Project project) {
        Test testTask = (Test) project.getTasks().getByName("test");
        if (!(testTask.getTestFrameworkProperty().get() instanceof JUnitPlatformTestFramework)) {
            LOG.info("Ignoring test task {} because it doesn't use JUnit 5", testTask.getName());
            return;
        }

        // Find the runtime reachable via the testRuntimeClasspath
        CommonRuntimeDefinition<?> runtimeDefinition;
        try {
            runtimeDefinition = TaskDependencyUtils.extractRuntimeDefinition(project, testTask);
        } catch (MultipleDefinitionsFoundException e) {
            throw new RuntimeException(e); // TODO: better error
        } catch (NoDefinitionsFoundException e) {
            throw new RuntimeException(e); // TODO: better error
        }

        // If it is a userdev runtime, we add the additional testing libraries
        Configuration testRuntimeOnly = project.getConfigurations().getByName("testRuntimeOnly");
        DependencyFactory dependencyFactory = project.getDependencyFactory();
        if (runtimeDefinition instanceof UserDevRuntimeDefinition) {
            UserDevRuntimeDefinition userdevRuntime = (UserDevRuntimeDefinition) runtimeDefinition;
            List<String> testDependencies = userdevRuntime.getUserdevConfiguration().getAdditionalTestDependencyArtifactCoordinates().get();
            for (String testDependency : testDependencies) {
                testRuntimeOnly.getDependencies().add(dependencyFactory.create(testDependency));
            }
        }

        RunImpl junitRun = project.getObjects().newInstance(RunImpl.class, "junit");
        junitRun.configure("test");
        junitRun.configure();
        runtimeDefinition.configureRun(junitRun);
        testTask.getSystemProperties().putAll(junitRun.getSystemProperties().get());
        File argsFile = project.getLayout().getBuildDirectory().file("test_args.txt").get().getAsFile();
        try {
            Files.write(argsFile.toPath(), junitRun.getProgramArguments().get(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        testTask.systemProperty("fml.junit.argsfile", argsFile.getAbsolutePath());
        testTask.getEnvironment().putAll(junitRun.getEnvironmentVariables().get());

//        Configuration testRuntimeClasspath = project.getConfigurations().getByName("testRuntimeClasspath");
        List<String> jvmArgs = new ArrayList<>(junitRun.getJvmArguments().get());
//        int modulePathIndex = jvmArgs.indexOf("-p");
//        if (modulePathIndex == -1) {
//            modulePathIndex = jvmArgs.indexOf("--module-path");
//        }
//        if (modulePathIndex != -1) {
//            // Convert junit into a module, such that it's visible to both the Gradle test runner, and mods
//            // running via FMLs transforming class-loader.
//            List<String> modulePath = new ArrayList<>();
//            if (modulePathIndex + 1 < jvmArgs.size()) {
//                Collections.addAll(
//                        modulePath,
//                        jvmArgs.remove(modulePathIndex + 1).split(Pattern.quote(File.pathSeparator))
//                );
//            }
//
//            Set<File> junitApi = testRuntimeClasspath.files(element -> Objects.equals(element.getGroup(), "org.junit.jupiter")
//                    || Objects.equals(element.getGroup(), "org.junit.platform")
//                    || Objects.equals(element.getGroup(), "org.opentest4j"));
//            for (File file : junitApi) {
//                modulePath.add(file.getAbsolutePath());
//            }
//            jvmArgs.add(modulePathIndex + 1, String.join(File.pathSeparator, modulePath));
//        }
        testTask.setJvmArgs(jvmArgs);

        // Extend MOD_CLASSES with test sources
        List<String> modClassesDirs = new ArrayList<>();
        String modId = project.getExtensions().getByType(Minecraft.class).getModIdentifier().get();
        for (File testClassesDir : testTask.getClasspath().getFiles().stream().filter(File::isDirectory).collect(Collectors.toList())) {
            modClassesDirs.add(modId + "%%" + testClassesDir.getAbsolutePath());
        }
        testTask.getEnvironment().put("MOD_CLASSES", String.join(File.pathSeparator, modClassesDirs));

        RunsUtil.addRunSourcesDependenciesToTask(testTask, junitRun);
        for (TaskProvider<? extends Task> taskDependency : junitRun.getTaskDependencies()) {
            testTask.dependsOn(taskDependency);
        }
    }
}
