package com.ezzenix.mcverify.util;

import java.util.ArrayList;
import java.util.List;

public class Version {
	public int major;
	public int minor;
	public int patch;

	public Version(int major, int minor, int patch) {
		this.major = major;
		this.minor = minor;
		this.patch = patch;
	}

	public Version(String string) {
		String[] split = string.split("\\.");
		int major = Integer.parseInt(split.length >= 1 ? split[0] : "0");
		int minor = Integer.parseInt(split.length >= 2 ? split[1] : "0");
		int patch = Integer.parseInt(split.length >= 3 ? split[2] : "0");
		this(major, minor, patch);
	}

	@Override
	public boolean equals(Object object) {
		if (!(object instanceof Version other)) return false;
		return this.major == other.major && this.minor == other.minor && this.patch == other.patch;
	}

	@Override
	public int hashCode() {
		int result = Integer.hashCode(major);
		result = 31 * result + Integer.hashCode(minor);
		result = 31 * result + Integer.hashCode(patch);
		return result;
	}

	@Override
	public String toString() {
		List<String> list = new ArrayList<>();
		if (this.major != 0) list.add(String.valueOf(this.major));
		if (this.minor != 0) list.add(String.valueOf(this.minor));
		if (this.patch != 0) list.add(String.valueOf(this.patch));
		return String.join(".", list);
	}

	private int compareTo(Version other) {
		if (this.major != other.major)
			return Integer.compare(this.major, other.major);

		if (this.minor != other.minor)
			return Integer.compare(this.minor, other.minor);

		return Integer.compare(this.patch, other.patch);
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
}
