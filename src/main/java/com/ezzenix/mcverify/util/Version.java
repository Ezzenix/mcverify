package com.ezzenix.mcverify.util;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Version {
	public int major;
	public int minor;
	public int patch;
	public String buildType = ""; // "", "rc", "pre", "snapshot", etc.
	public int buildNumber = 0;

	private static final Pattern VERSION_PATTERN = Pattern.compile(
			"^(\\d+)(?:\\.(\\d+))?(?:\\.(\\d+))?(?:-([a-zA-Z]+)-?(\\d+))?$"
	);

	public Version(int major, int minor, int patch) {
		this(major, minor, patch, "", 0);
	}

	public Version(int major, int minor, int patch, String buildType, int buildNumber) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
		this.buildType = buildType != null ? buildType.toLowerCase() : "";
		this.buildNumber = buildNumber;
	}

	public Version(String string) {
		Matcher matcher = VERSION_PATTERN.matcher(string.trim());
		if (matcher.matches()) {
			this.major = matcher.group(1) != null ? Integer.parseInt(matcher.group(1)) : 0;
			this.minor = matcher.group(2) != null ? Integer.parseInt(matcher.group(2)) : 0;
			this.patch = matcher.group(3) != null ? Integer.parseInt(matcher.group(3)) : 0;
			this.buildType = matcher.group(4) != null ? matcher.group(4).toLowerCase() : "";
			this.buildNumber = matcher.group(5) != null ? Integer.parseInt(matcher.group(5)) : 0;
		} else {
			// Fallback default if string format is totally unrecognized
			this.major = 0;
			this.minor = 0;
			this.patch = 0;
		}
	}

	private int getBuildTypePriority(String type) {
		if (type.isEmpty()) return 4;
		return switch (type) {
			case "rc" -> 3;
			case "pre" -> 2;
			case "snapshot" -> 1;
			default -> 0;
		};
	}

	private int compareTo(Version other) {
		if (this.major != other.major) return Integer.compare(this.major, other.major);
		if (this.minor != other.minor) return Integer.compare(this.minor, other.minor);
		if (this.patch != other.patch) return Integer.compare(this.patch, other.patch);

		int thisPriority = getBuildTypePriority(this.buildType);
		int otherPriority = getBuildTypePriority(other.buildType);

		if (thisPriority != otherPriority) {
			return Integer.compare(thisPriority, otherPriority);
		}

		return Integer.compare(this.buildNumber, other.buildNumber);
	}

	public boolean gt(Version other) {
		return compareTo(other) > 0;
	}

	public boolean lt(Version other) {
		return compareTo(other) < 0;
	}

	public boolean gte(Version other) {
		return compareTo(other) >= 0;
	}

	public boolean lte(Version other) {
		return compareTo(other) <= 0;
	}

	@Override
	public boolean equals(Object object) {
		if (this == object) return true;
		if (!(object instanceof Version other)) return false;
		return this.major == other.major &&
				this.minor == other.minor &&
				this.patch == other.patch &&
				this.buildNumber == other.buildNumber &&
				Objects.equals(this.buildType, other.buildType);
	}

	@Override
	public int hashCode() {
		return Objects.hash(major, minor, patch, buildType, buildNumber);
	}

	@Override
	public String toString() {
		String base;
		if (patch != 0) {
			base = major + "." + minor + "." + patch;
		} else if (minor != 0) {
			base = major + "." + minor;
		} else {
			base = String.valueOf(major);
		}

		if (!buildType.isEmpty()) {
			return base + "-" + buildType + "-" + buildNumber;
		}
		return base;
	}
}
