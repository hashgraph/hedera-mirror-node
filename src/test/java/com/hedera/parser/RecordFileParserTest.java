package com.hedera.parser;

import com.google.protobuf.TextFormat;
import com.hedera.databaseUtilities.ApplicationStatus;
import com.hedera.recordFileLogger.RecordFileLogger;
import com.hedera.utilities.Utility;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.binary.Hex;
import org.junit.Test;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;

import static com.hedera.parser.RecordFileParser.*;


@Log4j2
public class RecordFileParserTest {

    @Test
    public  void testRecordFileParser() {
//        try {
//            int RECORD_FORMAT_VERSION = 2;
//            long counter = 0;
//            byte[] readFileHash = new byte[48];
//            String newFileHash = "";
//            ApplicationStatus applicationStatus = new ApplicationStatus();
//            String previousFileHash = applicationStatus.getLastProcessedRcdHash();
//
//            counter++;
//
//            String fileName = "src/test/resources/testfile.rcd";
//            File file = new File(fileName);
//            System.out.println(file.getAbsolutePath());
//            DataInputStream inputStream = new DataInputStream(new FileInputStream(file));
//            MessageDigest md = MessageDigest.getInstance("SHA-384");
//            MessageDigest mdForContent = MessageDigest.getInstance("SHA-384");
//
//            int record_format_version = inputStream.readInt();
//            int version = inputStream.readInt();
//
//            md.update(Utility.integerToBytes(record_format_version));
//            md.update(Utility.integerToBytes(version));
//            System.out.println("dis.available(): " + inputStream.available());
//
//            while (inputStream.available() >= 0) {
//                byte typeDelimiter = inputStream.readByte();
//                switch (typeDelimiter) {
//                    case TYPE_PREV_HASH:
//                        md.update(typeDelimiter);
//                        inputStream.read(readFileHash);
//                        md.update(readFileHash);
//
//                        if (Utility.hashIsEmpty(previousFileHash)) {
//                            log.error("Previous file hash not available");
//                            previousFileHash = Hex.encodeHexString(readFileHash);
//                        } else {
//                            log.trace("Previous file Hash = {}", previousFileHash);
//                        }
//                        newFileHash = Hex.encodeHexString(readFileHash);
//                        log.trace("New file Hash = {}", newFileHash);
//
//                        if (!newFileHash.contentEquals(previousFileHash)) {
//
//                            if (applicationStatus.getBypassRecordHashMismatchUntilAfter().compareTo(Utility.getFileName(fileName)) < 0) {
//                                // last file for which mismatch is allowed is in the past
//                                log.error("Hash mismatch for file {}. Previous = {}, Current = {}", fileName, previousFileHash, newFileHash);
//                            }
//                        }
//                        break;
//                    case TYPE_RECORD:
//                        counter++;
//
//                        int byteLength = inputStream.readInt();
//                        byte[] rawBytes = new byte[byteLength];
//                        inputStream.readFully(rawBytes);
//                        if (record_format_version >= RECORD_FORMAT_VERSION) {
//                            mdForContent.update(typeDelimiter);
//                            mdForContent.update(Utility.integerToBytes(byteLength));
//                            mdForContent.update(rawBytes);
//
//                        } else {
//                            md.update(typeDelimiter);
//                            md.update(Utility.integerToBytes(byteLength));
//                            md.update(rawBytes);
//                        }
//                        Transaction transaction = Transaction.parseFrom(rawBytes);
//
//                        byteLength = inputStream.readInt();
//                        rawBytes = new byte[byteLength];
//                        inputStream.readFully(rawBytes);
//
//                        if (record_format_version >= RECORD_FORMAT_VERSION) {
//                            mdForContent.update(Utility.integerToBytes(byteLength));
//                            mdForContent.update(rawBytes);
//
//                        } else {
//                            md.update(Utility.integerToBytes(byteLength));
//                            md.update(rawBytes);
//                        }
//
//                        TransactionRecord txRecord = TransactionRecord.parseFrom(rawBytes);
//                        RecordFileLogger.INIT_RESULT initFileResult = RecordFileLogger.initFile(fileName);
//
//                        if (initFileResult != RecordFileLogger.INIT_RESULT.SKIP) {
//                            boolean bStored = RecordFileLogger.storeRecord(counter, Utility.convertToInstant(txRecord.getConsensusTimestamp()), transaction, txRecord);
//                            if (bStored) {
//                                if (log.isTraceEnabled()) {
//                                    log.trace("Transaction = {}, Record = {}", Utility.printTransaction(transaction), TextFormat.shortDebugString(txRecord));
//                                } else {
//                                    log.debug("Stored transaction with consensus timestamp {}", txRecord.getConsensusTimestamp());
//                                }
//                            } else {
//                                RecordFileLogger.rollback();
//                            }
//                        }
//                        break;
//                    case TYPE_SIGNATURE:
//                        int sigLength = inputStream.readInt();
//                        byte[] sigBytes = new byte[sigLength];
//                        inputStream.readFully(sigBytes);
//                        log.trace("File {} has signature {}", fileName, Hex.encodeHexString(sigBytes));
//                        if (RecordFileLogger.storeSignature(Hex.encodeHexString(sigBytes))) {
//                            break;
//                        }
//
//                    default:
//                        log.error("Unknown record file delimiter {} for file {}", typeDelimiter, file);
//                }
//            }
//        } catch (Exception e) {
//            e.printStackTrace();
//        }
    }
}
