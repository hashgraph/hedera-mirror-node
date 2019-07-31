package com.hedera.downloader;

import com.hedera.configLoader.ConfigLoader;
import com.hedera.parser.EventStreamFileParser;

public class EventStreamDownloader extends Downloader {
	public EventStreamDownloader(ConfigLoader configLoader) {
		super(configLoader);
	}

	public static void main(String[] args) {
		configLoader = new ConfigLoader("./config/config.json");
		EventStreamDownloader downloader = new EventStreamDownloader(configLoader);
		setupCloudConnection();
		downloader.listObjects();

	}
}
