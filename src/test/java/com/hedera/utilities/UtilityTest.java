package com.hedera.utilities;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 Hedera Hashgraph, LLC
 * ​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ‍
 */

import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.ThresholdKey;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionID;

import org.apache.commons.lang3.tuple.Triple;
import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.*;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Random;

public class UtilityTest {

	@TempDir
	Path tempDir;

	@Test
	void accountIDToString() {
		AccountID accountId = AccountID.newBuilder().setAccountNum(100).build();
		String parsed = Utility.accountIDToString(accountId);
		assertThat(parsed).isEqualTo("0.0.100");
	}

	@Test
	void stringToAccountID() {
		String accountIdString = "0.0.100";
		AccountID parsed = Utility.stringToAccountID(accountIdString);
		assertThat(parsed).isEqualTo(AccountID.newBuilder().setAccountNum(100).build());
	}

	@Test
	void parseS3SummaryKeyWhenRecordSignature() {
		String name = "record0.0.101/2019-06-05T20:29:32.856974Z.rcd_sig";
		Triple<String, String, String> triple = Utility.parseS3SummaryKey(name);
		assertThat(triple).isNotNull();
		assertThat(triple.getMiddle()).isEqualTo("2019-06-05T20:29:32.856974Z");
		assertThat(triple.getLeft()).isEqualTo("0.0.101");
		assertThat(triple.getRight()).isEqualTo("rcd_sig");
	}

	@Test
	void parseS3SummaryKeyWhenRecord() {
		String name = "directory/record10.10.101/2019-06-05T20:29:32.856974Z.rcd";
		Triple<String, String, String> triple = Utility.parseS3SummaryKey(name);
		assertThat(triple).isNotNull();
		assertThat(triple.getMiddle()).isEqualTo("2019-06-05T20:29:32.856974Z");
		assertThat(triple.getLeft()).isEqualTo("10.10.101");
		assertThat(triple.getRight()).isEqualTo("rcd");
	}

	@Test
	void parseS3SummaryKeyWhenEventSignature() {
		String name = "eventstreams/events_0.0.3/2019-07-25T19_57_21.217420Z.evts_sig";
		Triple<String, String, String> triple = Utility.parseS3SummaryKey(name);
		assertThat(triple).isNotNull();
		assertThat(triple.getMiddle()).isEqualTo("2019-07-25T19_57_21.217420Z");
		assertThat(triple.getLeft()).isEqualTo("0.0.3");
		assertThat(triple.getRight()).isEqualTo("evts_sig");
	}

	@Test
	void getFileNameFromS3SummaryKey() {
		String name = "record0.0.101/2019-06-05T20:29:32.856974Z.rcd_sig";
		String filename = Utility.getFileNameFromS3SummaryKey(name);
		assertThat(filename).isNotBlank().isEqualTo("2019-06-05T20:29:32.856974Z.rcd_sig");
	}

	@Test
	void getInstantFromFileNameWhenRecord() {
		String name = "2019-06-05T20:29:32.856974Z.rcd_sig";
		Instant instant = Utility.getInstantFromFileName(name);
		assertThat(instant).isEqualTo(Instant.parse("2019-06-05T20:29:32.856974Z"));
	}

	@Test
	void getInstantFromFileNameWhenBalance() {
		String name = "2019-08-30T18_15_00.016002001Z_Balances.csv";
		Instant instant = Utility.getInstantFromFileName(name);
		assertThat(instant).isEqualTo(Instant.parse("2019-08-30T18:15:00.016002001Z"));
	}

	@Test
	void getInstantFromEventStreamFileNameWhenEvent() {
		String name = "2019-07-25T19_57_21.217420Z.evts_sig";
		Instant instant = Utility.getInstantFromFileName(name);
		assertThat(instant).isEqualTo(Instant.parse("2019-07-25T19:57:21.217420Z"));
	}

	@Test
	void ensureDirectory() throws Exception {
		File directory = tempDir.resolve("created").toFile();
		File file = tempDir.resolve("file").toFile();

		Utility.ensureDirectory(directory.getAbsolutePath()); // Creates successfully
		Utility.ensureDirectory(directory.getAbsolutePath()); // Already exists

		file.createNewFile();
		assertThatThrownBy(() -> Utility.ensureDirectory(file.getAbsolutePath())).isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> Utility.ensureDirectory((String)null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> Utility.ensureDirectory(" ")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("Loads resource from classpath")
	void getResource() {
		assertThat(Utility.getResource("log4j2.xml")).exists().canRead();
		assertThat(Utility.getResource("log4j2-test.xml")).exists().canRead();
	}

	private Utility getCut() throws SQLException { return new Utility(); }

	@Test
	@DisplayName("protobufKeyToHexIfEd25519OrNull null key")
	public void protobufKeyToHexIfEd25519OrNull_Null() throws InvalidProtocolBufferException, SQLException {
		final var result = getCut().protobufKeyToHexIfEd25519OrNull(null);

		assertThat(result).isNull();
	}

	@Test
	@DisplayName("protobufKeyToHexIfEd25519OrNull valid ED25519 key")
	public void protobufKeyToHexIfEd25519OrNull_Valid() throws InvalidProtocolBufferException, SQLException {
		final var instr = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
		final var input = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decode(instr))).build();

		final var result = getCut().protobufKeyToHexIfEd25519OrNull(input.toByteArray());

		assertThat(result).isEqualTo(instr);
	}

	@Test
	@DisplayName("protobufKeyToHexIfEd25519OrNull threshold key")
	public void protobufKeyToHexIfEd25519OrNull_ThresholdKey() throws InvalidProtocolBufferException, SQLException {
		final var ks = "0011223344556677889900aabbccddeeff0011223344556677889900aabbccddeeff";
		final var key = Key.newBuilder().setEd25519(ByteString.copyFrom(Hex.decode(ks))).build();
		final var keyList = KeyList.newBuilder().addKeys(key).build();
		final var tk = ThresholdKey.newBuilder().setThreshold(1).setKeys(keyList).build();
		final var input = Key.newBuilder().setThresholdKey(tk).build();

		final var result = getCut().protobufKeyToHexIfEd25519OrNull(input.toByteArray());

		assertThat(result).isNull();
	}

	@Test
	@DisplayName("get TransactionId")
	public void getTransactionID()  {
		final AccountID payerAccountId = AccountID.newBuilder().setShardNum(0).setRealmNum(0).setAccountNum(2).build();
		final TransactionID transactionId = Utility.getTransactionId(payerAccountId);
        assertThat(transactionId)
        	.isNotEqualTo(TransactionID.getDefaultInstance());

        final AccountID testAccountId = transactionId.getAccountID();
    	assertAll(
    			// row counts
                () -> assertEquals(payerAccountId.getShardNum(), testAccountId.getShardNum())
                ,() -> assertEquals(payerAccountId.getRealmNum(), testAccountId.getRealmNum())
                ,() -> assertEquals(payerAccountId.getAccountNum(), testAccountId.getAccountNum())
        );
	}

	@Test
	public void instantToTimestamp()  {
		Random rand = new Random();
		int nanos = rand.nextInt();
		Instant instant = Instant.now().plusNanos(nanos);
		Timestamp test = Utility.instantToTimestamp(instant);
		assertAll(
                () -> assertEquals(instant.getEpochSecond(), test.getSeconds())
                ,() -> assertEquals(instant.getNano(), test.getNanos())
        );
	}

	@Test
	public void timeStampInNanosSecondNano()  {
		Random rand = new Random();
		int nanos = rand.nextInt();
		Instant now = Instant.now().plusNanos(nanos);

		Long timeStamp = Utility.timeStampInNanos(now.getEpochSecond(), now.getNano());
		Instant fromTimeStamp = Utility.convertNanosToInstant(timeStamp);

		assertAll(
                () -> assertEquals(now.getEpochSecond(), fromTimeStamp.getEpochSecond())
                ,() -> assertEquals(now.getNano(), fromTimeStamp.getNano())
        );
	}

	@Test
	public void timeStampInNanosTimeStamp()  {
		Random rand = new Random();
		int nanos = rand.nextInt(999999);
		long seconds = Instant.now().getEpochSecond();
		
		Timestamp timestamp = Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();

		long timeStampInNanos = Utility.timeStampInNanos(timestamp);
		Instant fromTimeStamp = Utility.convertNanosToInstant(timeStampInNanos);

		assertAll(
                () -> assertEquals(timestamp.getSeconds(), fromTimeStamp.getEpochSecond())
                ,() -> assertEquals(timestamp.getNanos(), fromTimeStamp.getNano())
        );
	}
}
