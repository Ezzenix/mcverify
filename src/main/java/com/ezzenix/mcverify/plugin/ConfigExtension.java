package com.ezzenix.mcverify.plugin;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.RegularFile;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.bundling.AbstractArchiveTask;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public class ConfigExtension {

	private final RegularFileProperty file;
	private final Property<String> loader;
	private final Property<String> version;

	private final Property<String> username;
	private final Property<String> serverAddress;
	private final Property<Integer> closeDelay;
	private final Property<Integer> workers;

	private final List<String> mods = new ArrayList<>();

	private final VersionRange versionRange;

	@Inject
	public ConfigExtension(ObjectFactory objects, Project project) {
		file = objects.fileProperty();
		loader = objects.property(String.class);
		version = objects.property(String.class);

		username = objects.property(String.class);
		serverAddress = objects.property(String.class);
		closeDelay = objects.property(Integer.class);
		workers = objects.property(Integer.class);

		versionRange = objects.newInstance(VersionRange.class);

		Provider<RegularFile> jarFile = findOutputJar(project);
		if (jarFile != null) {
			file.convention(jarFile);
		}
	}

	public RegularFileProperty getFile() {
		return file;
	}

	public Property<String> getLoader() {
		return loader;
	}

	public Property<String> getVersion() {
		return version;
	}

	public Property<String> getUsername() {
		return username;
	}

	public Property<String> getServerAddress() {
		return serverAddress;
	}

	public Property<Integer> getCloseDelay() {
		return closeDelay;
	}

	public Property<Integer> getWorkers() {
		return workers;
	}

	public VersionRange getVersionRange() {
		return versionRange;
	}

	public void versionRange(Action<? super VersionRange> action) {
		action.execute(versionRange);
	}

	public void mod(String projectId) {
		this.mods.add(projectId);
	}

	public List<String> getMods() {
		return mods;
	}

	private static Provider<RegularFile> findOutputJar(Project project) {
		TaskProvider<AbstractArchiveTask> task = findOutputTask(project);
		return task != null ? task.flatMap(AbstractArchiveTask::getArchiveFile) : null;
	}

	public static TaskProvider<AbstractArchiveTask> findOutputTask(Project project) {
		if (project.getTasks().getNames().contains("remapJar")) {
			return project.getTasks()
					.named("remapJar", AbstractArchiveTask.class);
		}

		if (project.getTasks().getNames().contains("jar")) {
			return project.getTasks()
					.named("jar", AbstractArchiveTask.class);
		}

		return null;
	}

	public abstract static class VersionRange {

		private final Property<String> start;
		private final Property<String> end;


		@Inject
		public VersionRange(ObjectFactory objects) {
			start = objects.property(String.class);
			end = objects.property(String.class);
		}


		public Property<String> getStart() {
			return start;
		}


		public Property<String> getEnd() {
			return end;
		}
	}
}