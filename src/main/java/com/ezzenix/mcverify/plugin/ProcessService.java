package com.ezzenix.mcverify.plugin;

import com.ezzenix.mcverify.util.ProcessUtils;
import org.gradle.api.services.BuildService;
import org.gradle.api.services.BuildServiceParameters;

public class ProcessService implements BuildService<BuildServiceParameters.None>, AutoCloseable {
	@Override
	public void close() {
		ProcessUtils.killAllProcesses();
	}

	@Override
	public BuildServiceParameters.None getParameters() {
		return null;
	}
}

