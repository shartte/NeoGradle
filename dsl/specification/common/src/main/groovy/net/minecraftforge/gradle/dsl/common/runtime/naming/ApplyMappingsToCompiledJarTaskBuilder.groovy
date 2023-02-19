package net.minecraftforge.gradle.dsl.common.runtime.naming

import groovy.transform.CompileStatic
import net.minecraftforge.gradle.dsl.common.runtime.tasks.Runtime;
import net.minecraftforge.gradle.dsl.common.tasks.WithOutput;
import org.gradle.api.tasks.TaskProvider;
import org.jetbrains.annotations.NotNull;

/**
 * Factory interface which can construct a new task provider for a compiled jar mapping.
 */
@FunctionalInterface
@CompileStatic
interface ApplyMappingsToCompiledJarTaskBuilder {

    /**
     * Invoked to construct a new task provider for a compiled jar mapping from the given context.
     *
     * @param context The context.
     * @return The task provider.
     */
    @NotNull TaskProvider<? extends Runtime> build(TaskBuildingContext context);
}
