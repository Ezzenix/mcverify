package com.ezzenix.mcverify.minecraft.helper;

import com.google.gson.JsonObject;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.ezzenix.mcverify.Main.DIRECTORY;
import static com.ezzenix.mcverify.util.Util.compareVersions;

public class LibraryContainer {
	private static final Path LIBRARIES_DIR = DIRECTORY.resolve("libraries");

	private final Map<String, Library> libraries = new HashMap<>();

	public List<Library> getLibraries() {
		return this.libraries.values().stream().toList();
	}

	public void add(JsonObject obj) {
		String name = obj.get("name").getAsString();
		String[] parts = name.split(":");
		String groupId = parts[0];
		String artifactId = parts[1];
		String currentVersion = parts[2];
		String classifier = parts.length > 3 ? ":" + parts[3] : "";
		String mavenKey = groupId + ":" + artifactId + classifier;

		String path;
		String downloadUrl;

		if (obj.has("downloads")) {
			path = obj.getAsJsonObject("downloads").getAsJsonObject("artifact").get("path").getAsString();
			downloadUrl = obj.getAsJsonObject("downloads").getAsJsonObject("artifact").get("url").getAsString();
		} else {
			path = convertNameToPath(name);
			downloadUrl = obj.get("url").getAsString() + path;
		}

		Path targetFile = LIBRARIES_DIR.resolve(path);
		if (libraries.containsKey(mavenKey)) {
			String existingVersion = libraries.get(mavenKey).version;
			// overwrite if this version is newer
			if (compareVersions(currentVersion, existingVersion) > 0) {
				libraries.put(mavenKey, new Library(targetFile, currentVersion, downloadUrl));
			}
		} else {
			libraries.put(mavenKey, new Library(targetFile, currentVersion, downloadUrl));
		}
	}

	private String convertNameToPath(String name) {
		String[] parts = name.split(":");
		String groupId = parts[0];
		String artifactId = parts[1];
		String ver = parts[2];
		String classifier = parts.length > 3 ? "-" + parts[3] : "";
		return groupId.replace('.', '/') + "/"
				+ artifactId + "/"
				+ ver + "/"
				+ artifactId + "-" + ver + classifier + ".jar";
	}

	public static class Library {
		public final Path path;
		public final String version;
		public final String downloadUrl;

		Library(Path path, String version, String downloadUrl) {
			this.path = path;
			this.version = version;
			this.downloadUrl = downloadUrl;
		}
	}
}
