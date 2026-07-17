package com.ezzenix.mcverify.plugin;

import com.ezzenix.mcverify.Main;
import com.ezzenix.mcverify.minecraft.Instance;
import com.ezzenix.mcverify.util.API;
import com.ezzenix.mcverify.util.Loader;
import com.ezzenix.mcverify.util.Version;
import org.gradle.api.*;
import org.gradle.api.file.RegularFile;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;
import org.gradle.jvm.toolchain.JavaLanguageVersion;
import org.gradle.jvm.toolchain.JavaLauncher;
import org.gradle.jvm.toolchain.JavaToolchainService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static com.ezzenix.mcverify.plugin.ConfigExtension.findOutputTask;
import static com.ezzenix.mcverify.util.Util.getJavaVersion;

public class McVerify implements Plugin<Project> {
	private static final Logger LOGGER = LoggerFactory.getLogger(McVerify.class);

	private static final InstanceFilter FILTER_VERSION_ONLY_ONCE = (instances) -> {
		Set<Version> foundVersions = new HashSet<>();
		return instances.stream().filter(instance -> {
			boolean ok = !foundVersions.contains(instance.version);
			foundVersions.add(instance.version);
			return ok;
		}).toList();
	};

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
		registerRootTestTask(root, "testAllVersions", service, FILTER_VERSION_ONLY_ONCE);
		registerRootTestTask(root, "testAllVersionsAndLoaders", service, null);

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
			});
		});
	}

	private void registerRootTestTask(Project root, String taskName, Provider<ProcessService> service, InstanceFilter filter) {
		try {
			root.getTasks().named(taskName);
		} catch (UnknownTaskException e) {
			TaskProvider<Task> newTask = root.getTasks().register(taskName, task -> {
				task.setGroup("mcverify");
				task.usesService(service);
				task.doLast(t -> {
					service.get();
					runTestAll(root, filter);
				});
			});

			root.getGradle().projectsEvaluated(g -> {
				root.getAllprojects().forEach(p -> {
					Provider<AbstractArchiveTask> jarTask = findOutputTask(p);
					if (jarTask != null) {
						newTask.configure(task -> task.dependsOn(jarTask));
					}
				});
			});
		}
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

	private void runTestAll(Project rootProject, InstanceFilter filter) {
		List<InstanceInfo> instanceInfos = new ArrayList<>();
		int workers = 1;

		for (Project subproject : rootProject.getAllprojects()) {
			ConfigExtension config = subproject.getExtensions().findByType(ConfigExtension.class);
			if (config == null) continue;

			Loader loader = getLoaderFromConfig(config);
			List<Version> versions = getVersionsFromConfig(config);

			if (config.getWorkers().isPresent()) {
				workers = Math.max(workers, config.getWorkers().get());
			}

			for (Version version : versions) {
				InstanceInfo info = new InstanceInfo(loader, version, config, subproject);
				if (!instanceInfos.contains(info)) {
					instanceInfos.add(info);
				} else {
					LOGGER.warn("Found instance {} {} defined more than once", info.version, info.loader);
				}
			}
		}

		if (filter != null) {
			instanceInfos = filter.apply(instanceInfos);
		}

		List<Instance> instances = new ArrayList<>();

		for (InstanceInfo info : instanceInfos) {
			try {
				Instance instance = Main.createInstance(info.loader, info.version);
				applyInstanceSettings(instance, info.config, info.project);
				instances.add(instance);
			} catch (Exception e) {
				LOGGER.warn("Failed to setup instance for {} {}", info.version, info.loader, e);
			}
		}

		if (instances.isEmpty()) {
			LOGGER.error("Found no instances to run.");
			return;
		}

		try {
			Main.runTests(instances, workers);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private void applyInstanceSettings(Instance instance, ConfigExtension config, Project project) {
		String username = config.getUsername().getOrNull();
		if (username != null) instance.setUsername(username);

		String address = config.getServerAddress().getOrNull();
		if (address != null) instance.setQuickPlayAddress(address);

		RegularFile file = config.getFile().getOrNull();
		if (file != null) instance.copyMod(file.getAsFile().toPath());

		Integer closeDelay = config.getCloseDelay().getOrNull();
		if (closeDelay != null) instance.setCloseDelay(closeDelay);

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

	private record InstanceInfo(Loader loader, Version version, ConfigExtension config, Project project) {
		@Override
		public boolean equals(Object o) {
			if (o == null || getClass() != o.getClass()) return false;
			InstanceInfo that = (InstanceInfo) o;
			return loader == that.loader && Objects.equals(version, that.version);
		}

		@Override
		public int hashCode() {
			return Objects.hash(loader, version);
		}
	}

	@FunctionalInterface
	private interface InstanceFilter {
		List<InstanceInfo> apply(List<InstanceInfo> instanceInfos);
	}

}
