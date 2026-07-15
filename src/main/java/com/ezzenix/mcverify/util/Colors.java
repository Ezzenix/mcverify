package com.ezzenix.mcverify.util;

public class Colors {
	public static final String RESET = "\u001B[0m";
	public static final String RED = "\u001B[31m";
	public static final String GREEN = "\u001B[32m";
	public static final String YELLOW = "\u001B[33m";
	public static final String CYAN = "\u001B[36m";
	public static final String GRAY = "\u001B[90m";

	public static final String BOLD = "\u001B[1m";

	public static final String CLEAR_LINE = "\r\u001B[K";

	public static String bold(String text, String color) {
		return BOLD + color + text + RESET;
	}
}
