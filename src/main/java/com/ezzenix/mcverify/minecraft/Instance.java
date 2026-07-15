package com.ezzenix.mcverify.minecraft;

import com.ezzenix.mcverify.minecraft.helper.LibraryContainer;
import com.ezzenix.mcverify.minecraft.helper.RuleProcessor;
import com.ezzenix.mcverify.util.API;
import com.ezzenix.mcverify.util.Loader;
import com.ezzenix.mcverify.util.Version;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

import static com.ezzenix.mcverify.Main.DIRECTORY;
import static com.ezzenix.mcverify.Main.GSON;
import static com.ezzenix.mcverify.util.Util.downloadIfAbsent;
import static com.ezzenix.mcverify.util.Util.fetchJson;

public abstract class Instance {
	private static final Logger LOGGER = LoggerFactory.getLogger(Instance.class);

	private final Path directory;
	private final Version version;
	private String mainClass;
	private String javaPath = "java";
	private final List<String> jvmArgs = new ArrayList<>();
	private final List<String> gameArgs = new ArrayList<>();
	private final LibraryContainer libraryContainer = new LibraryContainer();
	private final Map<String, String> placeholders = new HashMap<>();
	private final List<String> classpath = new ArrayList<>();
	private final List<String> modFileNames = new ArrayList<>();

	private String assetIndex;
	private String assetUrl;

	private String username;
	private String quickPlayAddress;
	private int closeDelay = 0;

	public Instance(Version version) throws Exception {
		this.directory = DIRECTORY.resolve("instances/" + version + "-" + getLoader());
		this.version = version;

		Files.createDirectories(this.directory);

		/* load vanilla profile */
		this.loadFromProfileJson(fetchJson(API.getVersionManifestUrl(version)).getAsJsonObject());

		/* run loader installer */
		this.install();
	}

	public abstract Loader getLoader();

	public abstract void install() throws Exception;

	public ProcessBuilder start() throws Exception {
		/* add libraries to classpath and download them */
		libraryContainer.getLibraries().forEach(lib -> {
			try {
				if (lib.downloadUrl != null && !lib.downloadUrl.isEmpty()) {
					downloadIfAbsent(new URL(lib.downloadUrl), lib.path);
				}
				classpath.add(lib.path.toAbsolutePath().toString());
			} catch (Exception e) {
				throw new RuntimeException("Failed to download library: " + lib.path, e);
			}
		});

		/* delete old mods */
		for (Path mod : Files.list(directory.resolve("mods")).toList()) {
			if (!this.modFileNames.contains(mod.getFileName().toString())) {
				Files.delete(mod);
			}
		}

		/* set placeholders */
		setPlaceholder("auth_player_name", this.username != null ? this.username : "Player"+Math.round(Math.random()*999));
		setPlaceholder("auth_uuid", "00000000-0000-0000-0000-000000000000");
		setPlaceholder("auth_access_token", "null");
		setPlaceholder("user_type", "legacy");
		setPlaceholder("game_directory", directory.toAbsolutePath().toString());
		setPlaceholder("assets_root", DIRECTORY.resolve("assets").toAbsolutePath().toString());
		setPlaceholder("version_type", "release");
		setPlaceholder("classpath_separator", File.pathSeparator);
		setPlaceholder("natives_directory", DIRECTORY.resolve("natives").toAbsolutePath().toString());
		setPlaceholder("library_directory", DIRECTORY.resolve("libraries").toAbsolutePath().toString());
		setPlaceholder("classpath", String.join(File.pathSeparator, classpath));
		setPlaceholder("assets_index_name", assetIndex);

		try {
			copyDefaults(directory);
		} catch (Exception e) {
			LOGGER.debug("Could not copy default configs to instance directory", e);
		}

		if (this.quickPlayAddress != null) {
			this.gameArgs.add("--quickPlayMultiplayer");
			this.gameArgs.add(this.quickPlayAddress);
		}

		List<String> cmd = new ArrayList<>();
		cmd.add(this.javaPath);
		cmd.addAll(this.jvmArgs.stream().map(this::replacePlaceholders).toList());
		cmd.add(this.mainClass);
		cmd.addAll(this.gameArgs.stream().map(this::replacePlaceholders).toList());

		ProcessBuilder pb = new ProcessBuilder(cmd);
		pb.directory(directory.toFile());
		pb.redirectErrorStream(true);

		return pb;
	}

	public Version getVersion() {
		return this.version;
	}

	public Path getDirectory() {
		return this.directory;
	}

	public void setPlaceholder(String name, String value) {
		this.placeholders.put(name, value != null ? value : "");
	}

	public void addClasspath(Path path) {
		this.classpath.add(path.toAbsolutePath().toString());
	}

	public void downloadMod(String idOrSlug) {
		this.modFileNames.addAll(API.downloadMod(idOrSlug, version, getLoader(), directory.resolve("mods")));
	}

	public void copyMod(Path path) {
		if (!path.toString().endsWith(".jar") || !path.toFile().exists()) {
			throw new RuntimeException(String.format("Cannot copy mod: %s is not a jar file.", path.toAbsolutePath()));
		}
		try {
			Path targetPath = getDirectory().resolve("mods").resolve(path.getFileName());
			Files.createDirectories(targetPath.getParent());
			Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			LOGGER.error("Failed to copy mod file", e);
		}
		this.modFileNames.add(path.getFileName().toString());
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public void setQuickPlayAddress(String address) {
		this.quickPlayAddress = address;
	}

	public int getCloseDelay() {
		return this.closeDelay;
	}

	public void setCloseDelay(int closeDelay) {
		this.closeDelay = closeDelay;
	}

	public void setJavaPath(String javaPath) {
		this.javaPath = javaPath;
	}

	public void loadFromProfileJson(JsonObject obj) {
		mainClass = obj.get("mainClass").getAsString();

		JsonObject argsObj = obj.getAsJsonObject("arguments");
		if (argsObj.has("default-user-jvm")) {
			readArguments(jvmArgs, argsObj.getAsJsonArray("default-user-jvm"));
		}
		if (argsObj.has("jvm")) {
			readArguments(jvmArgs, argsObj.getAsJsonArray("jvm"));
		}
		if (argsObj.has("game")) {
			readArguments(gameArgs, argsObj.getAsJsonArray("game"));
		}

		if (obj.has("libraries")) {
			readLibraries(obj.getAsJsonArray("libraries"));
		}

		if (obj.has("assetIndex")) {
			this.assetIndex = obj.getAsJsonObject("assetIndex").get("id").getAsString();
			this.assetUrl = obj.getAsJsonObject("assetIndex").get("url").getAsString();
		}

		if (obj.has("downloads") && obj.getAsJsonObject("downloads").has("client")) {
			try {
				URL releaseDownloadUrl = new URL(obj.getAsJsonObject("downloads").getAsJsonObject("client").get("url").getAsString());
				downloadIfAbsent(releaseDownloadUrl, DIRECTORY.resolve("versions").resolve(version.toString()).resolve(version+".jar"));
				Files.write(DIRECTORY.resolve("versions").resolve(version.toString()).resolve(version+".json"), GSON.toJson(obj).getBytes());
			} catch (Exception e) {
				throw new RuntimeException("Failed to download client", e);
			}
		}
	}

	private void readArguments(List<String> target, JsonArray array) {
		for (JsonElement element : array) {
			if (element.isJsonPrimitive()) {
				target.add(element.getAsString());
			} else if (element.isJsonObject()) {
				JsonObject obj = element.getAsJsonObject();
				if (obj.has("rules") && !RuleProcessor.shouldAllow(obj.getAsJsonArray("rules"))) {
					continue;
				}
				JsonElement valuesElement = obj.get("value");
				if (valuesElement.isJsonPrimitive()) {
					target.add(valuesElement.getAsString());
				} else if (valuesElement.isJsonArray()) {
					valuesElement.getAsJsonArray().forEach(el -> target.add(el.getAsString()));
				}
			}
		}
	}

	private void readLibraries(JsonArray array) {
		for (JsonElement element : array) {
			JsonObject obj = element.getAsJsonObject();
			if (obj.has("rules") && !RuleProcessor.shouldAllow(obj.getAsJsonArray("rules"))) {
				continue;
			}
			this.libraryContainer.add(obj);
		}
	}

	public void downloadAssets() {
		try {
			URL assetJsonUrl = new URL(assetUrl);
			Path assetsDir = DIRECTORY.resolve("assets");
			Path indexesDir = assetsDir.resolve("indexes");
			Files.createDirectories(indexesDir);

			Path indexFile = indexesDir.resolve(assetIndex + ".json");
			downloadIfAbsent(assetJsonUrl, indexFile);

			JsonObject assetIndexJson = fetchJson(assetJsonUrl).getAsJsonObject();
			JsonObject objects = assetIndexJson.getAsJsonObject("objects");
			Path objectsDir = assetsDir.resolve("objects");

			System.out.println("Checking / downloading Minecraft assets... This may take a moment on first run.");
			for (String key : objects.keySet()) {
				JsonObject asset = objects.getAsJsonObject(key);
				String hash = asset.get("hash").getAsString();
				String twoChars = hash.substring(0, 2);

				URL downloadUrl = new URL("https://resources.download.minecraft.net/" + twoChars + "/" + hash);
				Path targetPath = objectsDir.resolve(twoChars).resolve(hash);

				Files.createDirectories(targetPath.getParent());
				downloadIfAbsent(downloadUrl, targetPath);
			}
		} catch (Exception e) {
			System.out.printf("Failed to download assets for %s %s: %s%n", getLoader(), getVersion(), e.getMessage());
		}
	}

	private String replacePlaceholders(String arg) {
		for (Map.Entry<String, String> entry : placeholders.entrySet()) {
			arg = arg.replace("${"+entry.getKey()+"}", entry.getValue());
		}
		return arg;
	}

	public void debugPrint() {
		System.out.println("mainClass: " + mainClass);
		System.out.println("jvmArgs:");
		jvmArgs.forEach(line -> System.out.println("\t"+this.replacePlaceholders(line)));
		System.out.println("gameArgs:");
		gameArgs.forEach(line -> System.out.println("\t"+this.replacePlaceholders(line)));
		System.out.println("libraries:");
		libraryContainer.getLibraries().forEach(lib -> System.out.println("\t"+lib.path.toString()));
	}

	/**
	 * Copies all files and folders from a resource directory into a target file system path.
	 *
	 * @param instanceDir The destination Path on the hard drive
	 */
	private static void copyDefaults(Path instanceDir) throws IOException, URISyntaxException {
		var resourceUrl = Instance.class.getClassLoader().getResource("defaults");
		if (resourceUrl == null) {
			return;
		}

		URI uri = resourceUrl.toURI();

		// If resources are packed inside a compiled JAR file
		if ("jar".equals(uri.getScheme())) {
			// Open the JAR as a virtual file system
			try (FileSystem fileSystem = openFileSystem(uri)) {
				Path source = fileSystem.getPath("defaults");
				walkAndCopy(source, instanceDir);
			}
		} else {
			// If running normally inside an IDE
			Path source = Paths.get(uri);
			walkAndCopy(source, instanceDir);
		}
	}

	private static void walkAndCopy(Path source, Path target) throws IOException {
		try (Stream<Path> stream = Files.walk(source)) {
			stream.forEach(sourcePath -> {
				try {
					// Figure out the relative path inside the defaults folder
					String relative = source.relativize(sourcePath).toString();
					Path targetPath = target.resolve(relative);

					if (Files.isDirectory(sourcePath)) {
						Files.createDirectories(targetPath);
					} else {
						// Create parent directories if they don't exist yet
						if (targetPath.getParent() != null) {
							Files.createDirectories(targetPath.getParent());
						}
						// Copy the file
						if (!Files.exists(targetPath)) {
							Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
						}
					}
				} catch (IOException e) {
					throw new RuntimeException("Failed to copy default file: " + sourcePath, e);
				}
			});
		}
	}

	private static FileSystem openFileSystem(URI uri) throws IOException {
		synchronized (FileSystems.class) {
			try {
				return FileSystems.newFileSystem(uri, Collections.emptyMap());
			} catch (FileSystemAlreadyExistsException e) {
				return FileSystems.getFileSystem(uri);
			}
		}
	}
}
