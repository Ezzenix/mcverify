package com.ezzenix.mcverify.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class Util {
	private static final Map<String, String> XML_CACHE = new ConcurrentHashMap<>();

	public static JsonElement fetchJson(URL url) throws Exception {
		String raw = new String(url.openStream().readAllBytes(), StandardCharsets.UTF_8);
		return JsonParser.parseString(raw);
	}

	public static JsonElement fetchJson(String url) throws Exception {
		return fetchJson(new URL(url));
	}

	public static void download(URL url, Path target) throws IOException {
		Files.createDirectories(target.getParent());
		System.out.println("Downloading " + url.toString());
		Path tempFile = Path.of(target.toAbsolutePath() + ".tmp");
		try (InputStream in = url.openStream()) {
			Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
		}
		Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE);
	}

	public static void downloadIfAbsent(URL url, Path target) throws IOException {
		if (Files.exists(target)) return;
		download(url, target);
	}

	public static String fetchXml(URL url) throws Exception {
		String urlString = url.toString();
		if (XML_CACHE.containsKey(urlString)) {
			return XML_CACHE.get(urlString);
		}
		StringBuilder content = new StringBuilder();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(url.openStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				content.append(line);
			}
		}
		XML_CACHE.put(urlString, content.toString());
		return content.toString();
	}

	/**
	 * Standard dot-separated string version comparator.
	 * Returns > 0 if v1 > v2, < 0 if v1 < v2, and 0 if equal.
	 */
	public static int compareVersions(String v1, String v2) {
		String[] arr1 = v1.split("\\.");
		String[] arr2 = v2.split("\\.");
		int maxLength = Math.max(arr1.length, arr2.length);

		for (int i = 0; i < maxLength; i++) {
			int num1 = i < arr1.length ? Integer.parseInt(arr1[i].replaceAll("\\D", "")) : 0;
			int num2 = i < arr2.length ? Integer.parseInt(arr2[i].replaceAll("\\D", "")) : 0;

			if (num1 != num2) {
				return num1 - num2;
			}
		}
		return 0;
	}

	public static int getJavaVersion(Version version) {
		if (version.gte(new Version("26.1"))) {
			return 25;
		} else if (version.gte(new Version("1.20.5"))) {
			return 21;
		} else if (version.gte(new Version("1.18"))) {
			return 17;
		} else if (version.gte(new Version("1.17"))) {
			return 16;
		} else {
			return 8;
		}
	}
}
