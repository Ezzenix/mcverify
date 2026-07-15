package com.ezzenix.mcverify.minecraft;

import com.ezzenix.mcverify.util.Loader;
import com.ezzenix.mcverify.util.Version;
import com.google.gson.JsonObject;

import java.net.URL;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.ezzenix.mcverify.Main.DIRECTORY;
import static com.ezzenix.mcverify.util.Util.fetchJson;
import static com.ezzenix.mcverify.util.Util.fetchXml;

public class FabricInstance extends Instance {
	private static final String MAVEN_METADATA_URL = "https://maven.fabricmc.net/net/fabricmc/fabric-loader/maven-metadata.xml";

	public FabricInstance(Version version) throws Exception {
		super(version);
	}

	@Override
	public Loader getLoader() {
		return Loader.Fabric;
	}

	@Override
	public void install() throws Exception {
		String loaderVersion = getLatestLoaderVersion();
		String fabricProfileUrl = "https://meta.fabricmc.net/v2/versions/loader/" + this.getVersion() + "/" + loaderVersion + "/profile/json";
		JsonObject fabricProfile = fetchJson(fabricProfileUrl).getAsJsonObject();

		this.loadFromProfileJson(fabricProfile);
		this.addClasspath(DIRECTORY.resolve("versions").resolve(this.getVersion().toString()).resolve(getVersion()+".jar"));

		this.downloadMod("modmenu");
	}

	private String getLatestLoaderVersion() throws Exception {
		String content = fetchXml(new URL(MAVEN_METADATA_URL));
		Pattern pattern = Pattern.compile("<latest>([^<]+)</latest>");
		Matcher matcher = pattern.matcher(content);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return null;
	}
}
