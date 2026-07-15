package com.ezzenix.mcverify.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

public class ProcessUtils {
	private static final Set<Process> PROCESSES = ConcurrentHashMap.newKeySet();

	public static String gradleCommand() {
		return System.getProperty("os.name")
				.toLowerCase()
				.contains("win")
				? "gradlew.bat"
				: "./gradlew";
	}

	public static Result run(ProcessBuilder pb) throws Exception {
		return run(pb, line -> {});
	}

	public static Result run(ProcessBuilder pb, Consumer<String> consumer) throws Exception {
		pb.redirectErrorStream(true);
		Process process = pb.start();

		PROCESSES.add(process);

		try {
			StringBuilder output = new StringBuilder();
			new Thread(() -> {
				try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
					String line;
					while ((line = reader.readLine()) != null) {
						consumer.accept(line);
						output.append(line).append("\n");
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
			}).start();

			int exitCode = process.waitFor();

			return new Result(exitCode, process, output.toString());
		} catch (InterruptedException e) {
			killProcess(process);
			Thread.currentThread().interrupt();
		} finally {
			PROCESSES.remove(process);
		}

		return new Result(0, process, "");
	}

	public static void runUntil(ProcessBuilder pb, Function<String, Boolean> matcher, int closeDelay, int timeoutMinutes) throws Exception {
		pb.redirectErrorStream(true);
		Process process = pb.start();

		PROCESSES.add(process);

		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean conditionMet = new AtomicBoolean(false);

		Thread streamReader = new Thread(() -> {
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				String line;
				while ((line = reader.readLine()) != null) {
					if (matcher.apply(line)) {
						conditionMet.set(true);
						latch.countDown();
					}
				}
			} catch (IOException ignored) {
				// Stream closed when process dies
			} finally {
				latch.countDown();
			}
		});

		streamReader.setDaemon(true);
		streamReader.start();

		try {
			boolean finishedBeforeTimeout = latch.await(timeoutMinutes, TimeUnit.MINUTES);

			if (conditionMet.get()) {
				/* condition met */
				if (closeDelay > 0) Thread.sleep(closeDelay);
				process.destroy();
				process.waitFor();
			} else if (!finishedBeforeTimeout) {
				/* timeout expired */
				process.destroyForcibly();
				throw new RuntimeException("Process timed out waiting.");
			} else {
				/* process exited before condition */
				int exitCode = process.waitFor();
				throw new RuntimeException("Process exited prematurely with code: " + exitCode);
			}
		} catch (InterruptedException e) {
			killProcess(process);
			Thread.currentThread().interrupt();
		} finally {
			PROCESSES.remove(process);
		}
	}

	public record Result(int exitCode, Process process, String output) { }

	public static void killProcess(Process process) {
		PROCESSES.remove(process);
		ProcessHandle handle = process.toHandle();
		handle.descendants().forEach(ProcessHandle::destroyForcibly);
		process.destroy();
		try {
			if (!process.waitFor(2, TimeUnit.SECONDS)) {
				process.destroyForcibly();
			}
		} catch (InterruptedException ignored) {
			process.destroyForcibly();
		}
	}

	public static void killAllProcesses() {
		for (Process process : PROCESSES) {
			if (process.isAlive()) {
				killProcess(process);
			}
		}
	}

}
