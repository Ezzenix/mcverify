package com.ezzenix.mcverify.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.nio.file.Path;
import java.util.*;

import static com.ezzenix.mcverify.util.Util.downloadIfAbsent;
import static com.ezzenix.mcverify.util.Util.fetchJson;

public class API {
	private static final Logger LOGGER = LoggerFactory.getLogger(API.class);

	private static final Map<Version, URL> versionsMap = new HashMap<>();

	public static void init() {
		fetchReleases();
	}

	private static void fetchReleases() {
		try {
			JsonObject obj = fetchJson("https://launchermeta.mojang.com/mc/game/version_manifest.json").getAsJsonObject();
			JsonArray versions = obj.getAsJsonArray("versions");

			for (JsonElement el : versions) {
				JsonObject v = el.getAsJsonObject();

				if (v.get("type").getAsString().equals("release")) {
					Version version = new Version(v.get("id").getAsString());
					URL url = new URL(v.get("url").getAsString());
					versionsMap.put(version, url);
				}
			}
		} catch (Exception e) {
			throw new RuntimeException("Failed to fetch Minecraft releases", e);
		}
	}

	public static List<Version> getVersions() {
		return versionsMap.keySet().stream().toList();
	}

	public static URL getVersionManifestUrl(Version version) {
		return versionsMap.get(version);
	}

	public static List<String> downloadMod(String idOrSlug, Version version, Loader loader, Path directory) {
		return downloadMod(idOrSlug, version, loader, directory, new ArrayList<>());
	}

	private static List<String> downloadMod(String idOrSlug, Version version, Loader loader, Path directory, List<String> filenameList) {
		try {
			String versionId = fetchModVersionId(idOrSlug, version, loader);
			JsonObject obj = fetchJson("https://api.modrinth.com/v2/version/" + versionId).getAsJsonObject();
			for (JsonElement el : obj.get("files").getAsJsonArray()) {
				JsonObject file = el.getAsJsonObject();
				String filename = file.get("filename").getAsString();
				boolean isPrimary = file.get("primary").getAsBoolean();
				if (!isPrimary) continue;
				filenameList.add(filename);
				URL url = new URL(file.get("url").getAsString());
				downloadIfAbsent(url, directory.resolve(filename));
			}
			for (JsonElement el : obj.get("dependencies").getAsJsonArray()) {
				JsonObject dependency = el.getAsJsonObject();
				String projectId = dependency.get("project_id").getAsString();
				String type = dependency.get("dependency_type").getAsString();
				if (type.equals("required")) {
					downloadMod(projectId, version, loader, directory, filenameList);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to download mod {} for version {} with loader {}", idOrSlug, version, loader, e);
		}
		return filenameList;
	}

	private static String fetchModVersionId(String idOrSlug, Version version, Loader loader) {
		try {
			JsonArray arr = fetchJson("https://api.modrinth.com/v2/project/" + idOrSlug + "/version").getAsJsonArray();
			for (JsonElement el : arr) {
				JsonObject v = el.getAsJsonObject();
				boolean matchingVersion = v.getAsJsonArray("game_versions").asList().stream().anyMatch(x -> x.getAsString().equals(version.toString()));
				boolean matchingLoader = v.getAsJsonArray("loaders").asList().stream().anyMatch(x -> x.getAsString().equals(loader.name().toLowerCase(Locale.ROOT)));
				if (matchingVersion && matchingLoader) {
					return v.get("id").getAsString();
				}
			}
			return null;
		} catch (Exception e) {
			throw new RuntimeException("Could not to fetch mod version", e);
		}
	}

}
