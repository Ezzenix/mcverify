package com.ezzenix.mcverify.minecraft.helper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import static com.ezzenix.mcverify.util.Util.compareVersions;

public class RuleProcessor {
	private static final String OS_NAME = System.getProperty("os.name").toLowerCase();
	private static final String OS_VERSION = System.getProperty("os.version"); // e.g., "10.0.22631"

	public static boolean shouldAllow(JsonArray rules) {
		if (rules == null || rules.isEmpty()) return true;

		for (JsonElement ruleEl : rules) {
			JsonObject rule = ruleEl.getAsJsonObject();
			String action = rule.get("action").getAsString(); // "allow" or "disallow"

			if (rule.has("os") && rule.getAsJsonObject("os").has("name")) {
				JsonObject osRule = rule.getAsJsonObject("os");
				String osName = osRule.get("name").getAsString();

				if (getCurrentOS().equals(osName)) {
					if (osRule.has("versionRange")) {
						boolean isInRange = RuleProcessor.matchesVersionRange(osRule.getAsJsonObject("versionRange"));
						if (!isInRange) continue;
					}

					return action.equals("allow");
				}
			}
		}
		return false;
	}

	/**
	 * Evaluates if a versionRange condition matches the current running OS.
	 */
	public static boolean matchesVersionRange(JsonObject versionRange) {
		if (versionRange == null) return true;

		if (versionRange.has("min")) {
			String min = versionRange.get("min").getAsString();
			if (compareVersions(OS_VERSION, min) < 0) {
				return false; // Current OS is too old
			}
		}

		if (versionRange.has("max")) {
			String max = versionRange.get("max").getAsString();
			if (compareVersions(OS_VERSION, max) >= 0) {
				return false; // Current OS is too new (max is typically exclusive)
			}
		}

		return true;
	}

	private static String getCurrentOS() {
		if (OS_NAME.contains("win")) {
			return "windows";
		} else if (OS_NAME.contains("mac")) {
			return "osx";
		} else if (OS_NAME.contains("nix") || OS_NAME.contains("nux") || OS_NAME.contains("aix")) {
			return "linux";
		}
		return "unknown";
	}
}
