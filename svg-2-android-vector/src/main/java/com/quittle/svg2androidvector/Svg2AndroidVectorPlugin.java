package com.quittle.svg2androidvector;

import com.android.build.gradle.BaseExtension;
import com.android.build.gradle.api.AndroidSourceDirectorySet;
import com.android.build.gradle.api.AndroidSourceSet;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.tasks.TaskContainer;

import java.io.File;
import java.nio.file.Paths;

/**
 * Entry point for the project. This plugin will replace {@code res/raw/*.svg} files with {@code res/drawable/*.xml}
 * files in the Android Vector format. This allows you check-in the SVG sources for Android vector drawables.
 */
@SuppressWarnings("PMD.DataflowAnomalyAnalysis")
public class Svg2AndroidVectorPlugin implements Plugin<Project> {
    /**
     * The string used for the extension visible in consumers' {@code build.gradle}.
     */
    public static final String EXTENSION_NAME = "svg2androidVector";

    /**
     * The name of the root parent task for all the conversion tasks to be dependencies of.
     */
    public static final String CONVERSION_PARENT_TASK_NAME = "ConvertSvgToXml";

    private static final String ANDROID_RESOURCES_DIR_NAME_RAW = "raw";
    private static final String ANDROID_RESOURCES_DIR_NAME_DRAWABLE = "drawable";
    private static final String SVG_FILE_EXTENSION = ".svg";
    private static final String XML_FILE_EXTENSION = ".xml";
    private static final String BUILD_DIR_RESOURCE_NAME = "android-vector-resources";
    private static final String EARLY_ANDROID_TASK_NAME = "preBuild";
    // TODO: See if the drawable folder may be used instead of raw.
    private static final String SVG_FILTER_PATTERN =
            String.format("%s/**/*%s", ANDROID_RESOURCES_DIR_NAME_RAW, SVG_FILE_EXTENSION);
    private static final String CONVERSION_TASK_NAME_FORMAT = "ConvertSvgToXml-%s";

    @Override
    public void apply(final Project project) {
        final Svg2AndroidVectorExtension extension = new Svg2AndroidVectorExtension();
        project.getExtensions().add(EXTENSION_NAME, extension);

        // Starting in version 3.2.0 of the Android Gradle plugin, a double afterEvaluate is required to ensure the
        // BuildableArtifactImpl is resolvable.
        project.afterEvaluate(p1 -> {
            p1.afterEvaluate(p2 -> {
                onAfterEvaluate(p2, extension);
            });
        });
    }

    private static void onAfterEvaluate(final Project project, final Svg2AndroidVectorExtension extension) {
        final TaskContainer taskContainer = project.getTasks();
        final Task parentTask = taskContainer.create(CONVERSION_PARENT_TASK_NAME);
        // An early Android task all the conversion tasks should be a dependency of
        taskContainer.findByName(EARLY_ANDROID_TASK_NAME).dependsOn(parentTask);
        // The folder to put the converted files
        final File generatedResourceDir = new File(project.getBuildDir(), BUILD_DIR_RESOURCE_NAME);
        // The base Android extension, where all resources can be found.
        final BaseExtension androidExtension = project.getExtensions().getByType(BaseExtension.class);
        for (final AndroidSourceSet sourceSet : androidExtension.getSourceSets()) {
            final String sourceSetName = sourceSet.getName(); // e.g. main, androidTest, debug, etc.
            final AndroidSourceDirectorySet sourceDirectorySet = sourceSet.getRes();
            sourceDirectorySet
                    .getSourceFiles()
                    .matching(filter -> filter.include(SVG_FILTER_PATTERN))
                    .forEach(svgFile -> {
                        // Create a task to build the Android vector equivalent file in an Android resource
                        // directory specifically for this source set
                        final File newResouceDir = new File(generatedResourceDir, sourceSetName);
                        taskContainer.create(buildTaskName(svgFile), Svg2AndroidVectorTask.class, task -> {
                            task.svg = svgFile;
                            task.xml = Paths.get(
                                    newResouceDir.getAbsolutePath(),
                                    ANDROID_RESOURCES_DIR_NAME_DRAWABLE,
                                    svgFile.getName().replace(SVG_FILE_EXTENSION, XML_FILE_EXTENSION)).toFile();
                            task.failOnWarning = extension.getFailOnWarning();
                            parentTask.dependsOn(task);
                        });

                        // Add the new, generated resource directory for the source set
                        sourceDirectorySet.srcDir(newResouceDir);
                        // Remove the original resource from the resources. Two resources with the same name but
                        // different types may not share the same name.
                        sourceDirectorySet.getFilter().exclude(svgFile.getAbsolutePath());
                    });
        }
    }

    private static String buildTaskName(final File svgFile) {
        return String.format(CONVERSION_TASK_NAME_FORMAT, svgFile.getName());
    }
}
