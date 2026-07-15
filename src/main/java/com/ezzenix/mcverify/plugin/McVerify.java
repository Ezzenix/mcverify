package com.ezzenix.mcverify.plugin;

import com.ezzenix.mcverify.Main;
import com.ezzenix.mcverify.minecraft.Instance;
import com.ezzenix.mcverify.util.API;
import com.ezzenix.mcverify.util.Loader;
import com.ezzenix.mcverify.util.Version;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static com.ezzenix.mcverify.plugin.ConfigExtension.findOutputTask;
import static com.ezzenix.mcverify.util.Util.getJavaVersion;

public class McVerify implements Plugin<Project> {
	private static final Logger LOGGER = LoggerFactory.getLogger(McVerify.class);

	@Override
	public void apply(Project project) {
		Main.init();

		Provider<ProcessService> service =
				project.getGradle()
						.getSharedServices()
						.registerIfAbsent(
								"mcverifyProcesses",
								ProcessService.class,
								spec -> {}
						);

		ConfigExtension config = project.getExtensions().create("mcverify", ConfigExtension.class);

		Project root = project.getRootProject();

		if (!root.getTasks().getNames().contains("testAllVersions")) {
			TaskProvider<Task> testAllVersions = root.getTasks().register("testAllVersions", task -> {
				task.setGroup("mcverify");
				task.usesService(service);
				task.doLast(t -> {
					service.get();
					runTestAll(root);
				});
			});

			root.getGradle().projectsEvaluated(g -> {
				root.getAllprojects().forEach(p -> {
					Provider<AbstractArchiveTask> jarTask = findOutputTask(p);
					if (jarTask != null) {
						testAllVersions.configure(task -> task.dependsOn(jarTask));
					}
				});
			});
		}

		project.afterEvaluate(p -> {
			Loader loader = getLoaderFromConfig(config);
			List<Version> versions = getVersionsFromConfig(config);

			versions.forEach(version -> {
				project.getTasks().register("Run " + version.toString() + " " + loader, task -> {
					task.setGroup("mcverify");
					task.usesService(service);
					Provider<AbstractArchiveTask> jarTask = findOutputTask(project);
					if (jarTask != null) task.dependsOn(jarTask);
					task.doLast(t -> {
						service.get();
						run(project, loader, version);
					});
				});

				/*
				project.getTasks().register("Test " + version.toString() + " " + loader, task -> {
					task.setGroup("mcverify");
					task.usesService(service);
					Provider<AbstractArchiveTask> jarTask = findOutputTask(project);
					if (jarTask != null) task.dependsOn(jarTask);
					task.doLast(t -> {
						service.get();
						runTest(project, loader, version);
					});
				});
				 */
			});
		});
	}

	private void run(Project project, Loader loader, Version version) {
		ConfigExtension config = project.getExtensions().findByType(ConfigExtension.class);
		if (config == null) return;

		try {
			Instance instance = Main.createInstance(loader, version);
			applyInstanceSettings(instance, config, project);
			Main.run(instance);
		} catch (Exception e) {
			throw new GradleException("Failed to run version " + version + " " + loader, e);
		}
	}

	/*
	private void runTest(Project project, Loader loader, Version version) {
		ConfigExtension config = project.getExtensions().findByType(ConfigExtension.class);
		if (config == null) return;

		try {
			Instance instance = Main.createInstance(loader, version);
			applyInstanceSettings(instance, config, project);

			Main.runTests(List.of(instance), 1);
		} catch (Exception e) {}
	}
	 */

	private void runTestAll(Project rootProject) {
		List<Instance> instances = new ArrayList<>();

		int workers = 1;

		for (Project subproject : rootProject.getAllprojects()) {
			ConfigExtension config = subproject.getExtensions().findByType(ConfigExtension.class);
			if (config == null) continue;

			Loader loader = getLoaderFromConfig(config);
			List<Version> versions = getVersionsFromConfig(config);

			if (config.getWorkers().isPresent()) {
				workers = Math.max(workers, config.getWorkers().get());
			}

			versions.forEach(version -> {
				try {
					Instance instance = Main.createInstance(loader, version);
					applyInstanceSettings(instance, config, subproject);
					instances.add(instance);
				} catch (Exception e) {
					LOGGER.warn("Failed to setup instance for {} {}", version, loader, e);
				}
			});
		}

		try {
			Main.runTests(instances, workers);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void applyInstanceSettings(Instance instance, ConfigExtension config, Project project) {
		if (config.getUsername().isPresent()) {
			instance.setUsername(config.getUsername().get());
		}
		if (config.getServerAddress().isPresent()) {
			instance.setQuickPlayAddress(config.getServerAddress().get());
		}
		if (config.getFile().isPresent()) {
			instance.copyMod(config.getFile().getAsFile().get().toPath());
		}
		if (config.getCloseDelay().isPresent()) {
			instance.setCloseDelay(config.getCloseDelay().get());
		}
		instance.setJavaPath(getJavaPath(project, instance.getVersion()));
		config.getMods().forEach(instance::downloadMod);
	}

	private List<Version> getVersionsFromConfig(ConfigExtension config) {
		if (config.getVersion().isPresent()
				&& (config.getVersionRange().getStart().isPresent()
				|| config.getVersionRange().getEnd().isPresent())) {
			throw new GradleException("Cannot use version and versionRange together");
		}

		String versionStart;
		String versionEnd;

		if (config.getVersion().isPresent()) {
			versionStart = config.getVersion().get();
			versionEnd = config.getVersion().get();
		} else {
			if (!config.getVersionRange().getStart().isPresent() || !config.getVersionRange().getEnd().isPresent()) {
				throw new GradleException("Version range requires both start and end");
			}

			versionStart = config.getVersionRange().getStart().get();
			versionEnd = config.getVersionRange().getEnd().get();
		}

		Version minVer = new Version(versionStart);
		Version maxVer = new Version(versionEnd);

		return API.getVersions().stream()
				.filter(version -> version.gte(minVer) && version.lte(maxVer))
				.toList();
	}

	private Loader getLoaderFromConfig(ConfigExtension config) {
		if (!config.getLoader().isPresent()) {
			throw new IllegalStateException("Loader is missing");
		}

		String loaderStr = config.getLoader().get().toLowerCase(Locale.ROOT);
		return switch (loaderStr) {
			case "fabric" -> Loader.Fabric;
			case "neoforge" -> Loader.NeoForge;
			case "forge" -> Loader.Forge;
			default -> throw new IllegalStateException("Unexpected loader: " + loaderStr);
		};
	}

	private String getJavaPath(Project project, Version version) {
		JavaToolchainService toolchains = project.getExtensions().getByType(JavaToolchainService.class);

		Provider<JavaLauncher> launcherProvider = toolchains.launcherFor(spec -> {
			spec.getLanguageVersion().set(JavaLanguageVersion.of(getJavaVersion(version)));
		});

		return launcherProvider.get().getExecutablePath().getAsFile().getAbsolutePath();
	}

}
