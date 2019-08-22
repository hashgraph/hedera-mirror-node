package com.hedera.balanceFileLogger;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BalanceFileLoggerPerformanceTest {

	private File temporaryCsvFile;

	public static void main(String[] args) throws IOException {
		var cut = new BalanceFileLoggerPerformanceTest();
		var count = 100_000; // default
		if (args.length > 0) {
			count = Integer.parseInt(args[0]);
		}
		cut.setup(count);
		var start = System.nanoTime();
		cut.largeAccountBalanceTest();
		var end = System.nanoTime();
		System.out.println("Database processing " + count + " records took " + ((end - start) / 1000000.0) + " ms");
	}

	public void setup(int count) throws IOException {
		var rand = new Random();
		var year = (rand.nextInt(100) + 2000);
		var month = (rand.nextInt(12) + 1);
		var day = (rand.nextInt(28) + 1);
		var hour = rand.nextInt(24);
		var minute = rand.nextInt(60);
		var second = rand.nextInt(60);
		var nanos = rand.nextInt(1_000_000_000);
		var fileName = String.format("%04d-%02d-%02dT%02d_%02d_%02d.%09dZ_Balances.csv",
				year, month, day, hour, minute, second, nanos);
		Path tempDir = Files.createTempDirectory("hedera_mirror_node__performance_test");
		var timestamp = String.format("%04d-%02d-%02dT%02d:%02d:%02d.%09dZ",
			year, month, day, hour, minute, second, nanos);
		temporaryCsvFile = new File(tempDir.toString(), fileName);
		temporaryCsvFile.deleteOnExit();

		List<String[]> rows = new ArrayList<>();
		rows.add(new String[]{"Timestamp: " + timestamp});
		rows.add(new String[]{Integer.toString(year), Integer.toString(month),
		Integer.toString(day), Integer.toString(hour), Integer.toString(minute)});

		rows.add(new String[]{"shardNum", "realmNum", "accountNum", "balance"});
		for (var i = 0; i < count; ++i) {
			rows.add(new String[]{"0", "0", Integer.toString(i), Integer.toString(i * 10)});
		}
		try (PrintWriter pw = new PrintWriter(temporaryCsvFile)) {
			rows.stream().map((columns) -> {
					return Stream.of(columns)
							.collect(Collectors.joining(","));
			}).forEach(pw::println);
		}
	}

	public void largeAccountBalanceTest() throws IOException {
		BalanceFileLogger.processFileForHistory(temporaryCsvFile);
	}
}
