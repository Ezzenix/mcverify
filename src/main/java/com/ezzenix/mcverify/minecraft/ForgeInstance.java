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

public class ForgeInstance extends Instance {
	private static final String MAVEN_METADATA_URL = "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";

	public ForgeInstance(Version version) throws Exception {
		super(version);
	}

	@Override
	public Loader getLoader() {
		return Loader.Forge;
	}

	public void install() throws Exception {
		String mcVersion = this.getVersion().toString();

		/* fetch forge version */
		String fullForgeVersion = getLatestVersionFor(mcVersion);

		// Forge installers typically name the output JSON using the pattern: <mcVersion>-forge-<forgeVersion>
		String forgeVersionOnly = fullForgeVersion.contains("-")
				? fullForgeVersion.substring(fullForgeVersion.indexOf('-') + 1)
				: fullForgeVersion;
		String forgeId = mcVersion + "-forge-" + forgeVersionOnly;

		Path forgeJsonPath = DIRECTORY.resolve("versions").resolve(forgeId).resolve(forgeId + ".json");

		if (!Files.exists(forgeJsonPath)) {
			// 2. Download the Forge Installer JAR from their official Maven
			String installerName = "forge-" + fullForgeVersion + "-installer.jar";
			URL installerUrl = new URL("https://maven.minecraftforge.net/net/minecraftforge/forge/"
					+ fullForgeVersion + "/" + installerName);

			Path installerPath = this.getDirectory().resolve(installerName);
			System.out.println("Downloading Forge Installer...");
			downloadIfAbsent(installerUrl, installerPath);

			/* create dummy launcher profiles to make installer work */
			Path dummyProfiles = DIRECTORY.resolve("launcher_profiles.json");
			if (!Files.exists(dummyProfiles)) {
				Files.writeString(dummyProfiles, "{\"profiles\": {}}");
			}

			// 3. Run the installer headlessly targeting your custom instance directory
			System.out.println("Running Forge Installer headlessly... This will take a moment.");
			List<String> installCmd = new ArrayList<>();
			installCmd.add("java");
			installCmd.add("-jar");
			installCmd.add(installerPath.toString());
			installCmd.add("--installClient");
			installCmd.add(DIRECTORY.toAbsolutePath().toString());

			ProcessBuilder pb = new ProcessBuilder(installCmd);
			pb.directory(this.getDirectory().toFile());
			var result = ProcessUtils.run(pb, line -> {
				System.out.println("[Forge Installer] " + line);
			});

			if (result.exitCode() != 0) {
				throw new RuntimeException("Forge installation failed with exit code: " + result.exitCode());
			}

			Files.deleteIfExists(installerPath);
		}

		JsonObject forgeJson = JsonParser.parseReader(new FileReader(forgeJsonPath.toFile())).getAsJsonObject();
		this.loadFromProfileJson(forgeJson);
		this.setPlaceholder("version_name", forgeId);
	}

	/**
	 * Fetches all available Forge versions for a specific Minecraft version.
	 * Returns a list ordered from oldest to newest (latest release will be the last item).
	 */
	private List<String> getVersionsFor(String mcVersion) throws Exception {
		List<String> matchingVersions = new ArrayList<>();

		// Forge versions in maven-metadata.xml typically look like "1.20.1-47.3.0"
		String prefix = mcVersion + "-";

		// 2. Fetch the live maven-metadata.xml
		String content = fetchXml(new URL(MAVEN_METADATA_URL));

		// 3. Parse out the <version> tags via regex
		Pattern pattern = Pattern.compile("<version>([^<]+)</version>");
		Matcher matcher = pattern.matcher(content);

		while (matcher.find()) {
			String forgeVersion = matcher.group(1);
			if (forgeVersion.startsWith(prefix)) {
				matchingVersions.add(forgeVersion);
			}
		}

		return matchingVersions;
	}

	/**
	 * Resolves the latest available Forge version for a specific Minecraft version.
	 */
	private String getLatestVersionFor(String mcVersion) throws Exception {
		List<String> versions = getVersionsFor(mcVersion);
		if (versions.isEmpty()) {
			throw new RuntimeException("No Forge versions found for Minecraft " + mcVersion);
		}
		// Maven metadata lists versions chronologically; the last element is the newest
		return versions.getLast();
	}
}
