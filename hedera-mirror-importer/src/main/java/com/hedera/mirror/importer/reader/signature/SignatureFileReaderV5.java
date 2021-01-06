package com.hedera.mirror.importer.reader.signature;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;

@Log4j2
@Named
public class SignatureFileReaderV5 implements SignatureFileReader {

    public static final int SIGNATURE_FILE_FORMAT_VERSION = 5;
    public static final int OBJECT_STREAM_SIGNATURE_VERSION = 1;

    private static final long HASH_CLASS_ID = 0xf422da83a251741eL;
    private static final int HASH_CLASS_VERSION = 1;
    private static final int HASH_DIGEST_TYPE = 0x58ff811b;
    private static final int HASH_LENGTH = 48; // the length of the hash

    private static final long SIGNATURE_CLASS_ID = 0x13dc4b399b245c69L;
    private static final int SIGNATURE_CLASS_VERSION = 1;
    private static final int SIGNATURE_TYPE = 1;

    @Override
    public FileStreamSignature read(InputStream inputStream) {
        FileStreamSignature fileStreamSignature = new FileStreamSignature();

        try (DataInputStream dis = new DataInputStream(inputStream)) {

            int fileVersion = dis.readInt();
            validateLongValue(fileVersion, SIGNATURE_FILE_FORMAT_VERSION, "Invalid signature file version for " +
                    "SignatureFileReaderV5: ");

            int signatureVersion = dis.readInt();
            validateLongValue(OBJECT_STREAM_SIGNATURE_VERSION, signatureVersion, "Invalid signature object stream " +
                    "version for " +
                    "SignatureFileReaderV5: " + signatureVersion);

            fileStreamSignature.setHash(readHashObject(dis));

            fileStreamSignature.setSignature(readSignatureObject(dis));

//            if (dis.available() != 0) {
//                throw new SignatureFileParsingException("Extra data discovered in signature file");
//            }

            return fileStreamSignature;
        } catch (IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }

    private byte[] readSignatureObject(DataInputStream dis) throws IOException {
        long signatureclassId = dis.readLong();
        validateLongValue(SIGNATURE_CLASS_ID, signatureclassId, "");
        int signatureClassVersion = dis.readInt();
        validateIntValue(SIGNATURE_CLASS_VERSION, signatureClassVersion, "");
        int signatureType = dis.readInt();
        validateIntValue(SIGNATURE_TYPE, signatureType, "");

        int sigLength = dis.readInt();
        byte[] sigBytes = new byte[sigLength];
        int actualSigLength = dis.read(sigBytes);
        validateIntValue(sigLength, actualSigLength, "");
        return sigBytes;
    }

    private byte[] readHashObject(DataInputStream dis) throws IOException {
        byte[] fileHash = new byte[HASH_LENGTH];

        long hashClassId = dis.readLong();
        validateLongValue(HASH_CLASS_ID, hashClassId,
                "Invalid hash class id for SignatureFileReaderV5: " + hashClassId);

        int hashClassVersion = dis.readInt();
        validateIntValue(HASH_CLASS_VERSION, hashClassVersion,
                "Invalid hash class id for SignatureFileReaderV5: " + hashClassId);
        int hashType = dis.readInt();
        validateIntValue(HASH_DIGEST_TYPE, hashType,
                "Invalid hash class id for SignatureFileReaderV5: " + hashClassId);
        int hashLength = dis.readInt();
        validateIntValue(fileHash.length, hashLength, "Unable to read signature file hash for " +
                "SignatureFileReaderV5: hash length" + hashLength);

        byte[] hash = new byte[hashLength];
        int actualHashLength = dis.read(hash);
        validateIntValue(fileHash.length, actualHashLength, "Unable to read signature file hash for " +
                "SignatureFileReaderV5: hash length" + actualHashLength);
        return hash;
    }

    private void validateLongValue(long expected, long actual, String exceptionMessage) {
        if (expected != actual) {
            Long.compare(1, 2);
            throw new SignatureFileParsingException(exceptionMessage + actual);
        }
    }

    private void validateIntValue(int expected, int actual, String exceptionMessage) {
        if (expected != actual) {
            throw new SignatureFileParsingException(exceptionMessage + actual);
        }
    }
}
