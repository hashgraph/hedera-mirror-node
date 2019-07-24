package com.hedera.recordFileLogger;

import java.time.Instant;

import com.hedera.configLoader.ConfigLoader;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;

public interface LoggerInterface {
	public static boolean initFile(String fileName) {
		return true;
	}
	public static boolean completeFile() {
		return true;
	}
	public static boolean storeRecord(long counter, Instant consensusTimeStamp, Transaction transaction, TransactionRecord txRecord, ConfigLoader configLoader) {
		return true;
	}
	public static boolean storeSignature(String signature) {
		return true;
	}
	public static boolean start() {
		return true;
	}
	public static boolean finish() {
		return true;
	}
}
