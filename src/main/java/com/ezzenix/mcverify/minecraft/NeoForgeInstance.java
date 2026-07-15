package com.ezzenix.mcverify.minecraft;

import com.ezzenix.mcverify.util.Loader;
import com.ezzenix.mcverify.util.ProcessUtils;
import com.ezzenix.mcverify.util.Version;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.FileReader;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ezzenix.mcverify.Main.DIRECTORY;
import static com.ezzenix.mcverify.util.Util.downloadIfAbsent;
import static com.ezzenix.mcverify.util.Util.fetchXml;

public class NeoForgeInstance extends Instance {
	private static final String MAVEN_METADATA_URL = "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";

	public NeoForgeInstance(Version version) throws Exception {
		super(version);
	}

	@Override
	public Loader getLoader() {
		return Loader.NeoForge;
	}

	@Override
	public void install() throws Exception {
		/* fetch neoforge version */
		String neoForgeVersion = getLatestVersionFor(this.getVersion().toString());

		String neoforgeId = "neoforge-" + neoForgeVersion;
		Path neoforgeJsonPath = DIRECTORY.resolve("versions").resolve(neoforgeId).resolve(neoforgeId + ".json");

		if (!Files.exists(neoforgeJsonPath)) {
			// 2. Download the NeoForge Installer JAR from their official Maven
			String installerName = "neoforge-" + neoForgeVersion + "-installer.jar";
			URL installerUrl = new URL("https://maven.neoforged.net/releases/net/neoforged/neoforge/"
					+ neoForgeVersion + "/" + installerName);

			Path installerPath = this.getDirectory().resolve(installerName);
			System.out.println("Downloading NeoForge Installer...");
			downloadIfAbsent(installerUrl, installerPath);

			/* create dummy launcher profiles to make installer work */
			Path dummyProfiles = DIRECTORY.resolve("launcher_profiles.json");
			if (!Files.exists(dummyProfiles)) {
				Files.writeString(dummyProfiles, "{\"profiles\": {}}");
			}

			// 3. Run the installer headlessly targeting your custom instance directory
			System.out.println("Running NeoForge Installer headlessly... This will take a moment.");
			List<String> installCmd = new ArrayList<>();
			installCmd.add("java");
			installCmd.add("-jar");
			installCmd.add(installerPath.toString());
			installCmd.add("--installClient");
			installCmd.add(DIRECTORY.toAbsolutePath().toString());

			ProcessBuilder pb = new ProcessBuilder(installCmd);
			pb.directory(this.getDirectory().toFile());
			var result = ProcessUtils.run(pb, line -> {
				System.out.println("[NeoForge Installer] " + line);
			});

			if (result.exitCode() != 0) {
				throw new RuntimeException("NeoForge installation failed with exit code: " + result.exitCode());
			}

			Files.deleteIfExists(installerPath);
		}

		JsonObject neoForgeJson = JsonParser.parseReader(new FileReader(neoforgeJsonPath.toFile())).getAsJsonObject();
		this.loadFromProfileJson(neoForgeJson);
		this.setPlaceholder("version_name", neoforgeId);
	}

	/**
	 * Fetches all available NeoForge versions for a specific Minecraft version.
	 * Returns a list ordered from oldest to newest (latest release will be the last item).
	 */
	private List<String> getVersionsFor(String mcVersion) throws Exception {
		List<String> matchingVersions = new ArrayList<>();

		// 1. Convert Minecraft version to the expected NeoForge prefix
		String prefix = getNeoForgePrefix(mcVersion);

		// 2. Fetch the live maven-metadata.xml
		String content = fetchXml(new URL(MAVEN_METADATA_URL));

		// 3. Parse out the <version> tags via regex
		Pattern pattern = Pattern.compile("<version>([^<]+)</version>");
		Matcher matcher = pattern.matcher(content);

		while (matcher.find()) {
			String neoVersion = matcher.group(1);
			if (neoVersion.startsWith(prefix)) {
				matchingVersions.add(neoVersion);
			}
		}

		return matchingVersions;
	}

	/**
	 * Resolves the latest available NeoForge version for a specific Minecraft version.
	 */
	private String getLatestVersionFor(String mcVersion) throws Exception {
		List<String> versions = getVersionsFor(mcVersion);
		if (versions.isEmpty()) {
			throw new RuntimeException("No NeoForge versions found for Minecraft " + mcVersion);
		}
		// Maven metadata lists versions chronologically; the last element is the newest
		return versions.getLast();
	}

	private String getNeoForgePrefix(String mcVersion) {
		if (mcVersion.startsWith("1.")) {
			String stripped = mcVersion.substring(2); // "1.20.4" -> "20.4"
			if (!stripped.contains(".")) {
				return stripped + ".0."; // Handles flat versions like "1.21" -> "21.0."
			}
			return stripped + "."; // Handles "1.20.4" -> "20.4."
		}
		// For modern CalVer versions (e.g., "26.1" -> "26.1.")
		return mcVersion + ".";
	}
}
