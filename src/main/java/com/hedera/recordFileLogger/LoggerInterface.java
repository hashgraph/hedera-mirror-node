package com.hedera.recordFileLogger;

import java.time.Instant;

import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;

public interface LoggerInterface {
	public static void initFile(String fileName) {
	}
	public static void completeFile() {
	}
	public static void storeRecord(long counter, Instant consensusTimeStamp, Transaction transaction, TransactionRecord txRecord) {
	}
	public static void storeSignature(String signature) {
	}
	public static void start() {
	}
	public static void finish() {
	}
	
}
