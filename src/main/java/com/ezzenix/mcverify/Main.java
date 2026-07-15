package com.ezzenix.mcverify;

import com.ezzenix.mcverify.minecraft.FabricInstance;
import com.ezzenix.mcverify.minecraft.ForgeInstance;
import com.ezzenix.mcverify.minecraft.Instance;
import com.ezzenix.mcverify.minecraft.NeoForgeInstance;
import com.ezzenix.mcverify.util.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

public class Main {
	private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);
	public static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	public static final Path DIRECTORY = Path.of(System.getProperty("user.home"), ".mcverify");
	private static final List<String> LOG_START_MATCHES = List.of(
		"Sound engine started"
	);

	public static Map<Loader, InstanceFactory> instanceFactories = new HashMap<>();
	private static boolean hasInitialized = false;

	public static void init() {
		if (hasInitialized) return;
		hasInitialized = true;

		if (!Files.exists(DIRECTORY)) {
			try {
				Files.createDirectories(DIRECTORY);
			} catch (Exception ignored) {
				LOGGER.error("Could not create .mcverify directory");
			}
		}

		API.init();

		instanceFactories.put(Loader.Fabric, FabricInstance::new);
		instanceFactories.put(Loader.NeoForge, NeoForgeInstance::new);
		instanceFactories.put(Loader.Forge, ForgeInstance::new);

		Runtime.getRuntime().addShutdownHook(new Thread(ProcessUtils::killAllProcesses));
	}

	public static Instance createInstance(Loader loader, Version version) throws Exception {
		return instanceFactories.get(loader).create(version);
	}

	public static void run(Instance instance) throws Exception {
		instance.downloadAssets();

		LOGGER.info("Running {} {}", instance.getVersion(), instance.getLoader());
		var result = ProcessUtils.run(instance.start(), System.out::println);
		LOGGER.info("Minecraft {} {} exited with code: {}", instance.getLoader(), instance.getVersion(), result.exitCode());
	}

	private static TestResult runTest(Instance instance) {
		try {
			ProcessUtils.runUntil(instance.start(), line -> {
				return (LOG_START_MATCHES.stream().anyMatch(line::contains));
			}, instance.getCloseDelay(), 3);
			return new TestResult(instance, true, null);
		} catch (Exception e) {
			return new TestResult(instance, false, e);
		}
	}

	public static void runTests(List<Instance> instances, int workers) throws ExecutionException {
		System.out.println(Colors.GREEN + "\nFound " + instances.size() + " instance(s) to run.\n" + Colors.RESET);

		ExecutorService executor = Executors.newFixedThreadPool(workers);
		CompletionService<TestResult> completion = new ExecutorCompletionService<>(executor);

		try {
			int submittedTasks = 0;

			for (Instance instance : instances) {
				submittedTasks += 1;
				completion.submit(() -> runTest(instance));
			}

			for (int i = 0; i < submittedTasks; i++) {
				TestResult result = completion.take().get();

				String targetInfo = result.instance().getVersion() + " " + result.instance().getLoader();

				String prefix = result.success() ? Colors.bold("[ PASS ] ", Colors.GREEN) : Colors.bold("[ FAIL ] ", Colors.RED);
				System.out.println(prefix + Colors.RESET + targetInfo + "               ");
				if (!result.success()) {
					Path logPath = result.instance.getDirectory().resolve("logs").resolve("latest.log");
					if (Files.exists(logPath)) {
						System.out.println(Colors.RED + "         Log: file:///" + logPath.toAbsolutePath().toString().replace("\\", "/") + Colors.RESET + "               ");
					}
					if (result.exception != null) {
						LOGGER.error("Test failed with exception", result.exception);
					}
				}
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} finally {
			executor.shutdownNow();
		}
	}

	@FunctionalInterface
	public interface InstanceFactory {
		Instance create(Version version) throws Exception;
	}

	public record TestResult(
		Instance instance,
		boolean success,
		Exception exception
	) {}
}
