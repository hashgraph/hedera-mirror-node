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
public class SignatureFileReaderV5 extends AbstractSignatureFileReader {

    protected static final byte SIGNATURE_FILE_FORMAT_VERSION = 5;
    protected static final int OBJECT_STREAM_SIGNATURE_VERSION = 1; //defines the format for the remainder of the file

    protected static final long HASH_CLASS_ID = 0xf422da83a251741eL;
    protected static final int HASH_CLASS_VERSION = 1;
    protected static final int HASH_DIGEST_TYPE = 0x58ff811b; //denotes SHA-384
    protected static final int HASH_LENGTH = 48; //48 bytes for SHA-384

    protected static final long SIGNATURE_CLASS_ID = 0x13dc4b399b245c69L;
    protected static final int SIGNATURE_CLASS_VERSION = 1;
    protected static final int SIGNATURE_TYPE = 1; //denotes SHA384withRSA

    @Override
    public FileStreamSignature read(InputStream inputStream) {
        FileStreamSignature fileStreamSignature = new FileStreamSignature();

        try (DataInputStream dis = new DataInputStream(inputStream)) {

            byte fileVersion = dis.readByte();
            validateLongValue(SIGNATURE_FILE_FORMAT_VERSION, fileVersion, "Unable to read signature file v5: file " +
                    "version ");

            int objectStreamSignatureVersion = dis.readInt();
            validateIntValue(OBJECT_STREAM_SIGNATURE_VERSION, objectStreamSignatureVersion, "Unable to read signature" +
                    " file v5: object stream signature version ");

            fileStreamSignature.setHash(readHashObject(dis));

            fileStreamSignature.setSignature(readSignatureObject(dis));

            return fileStreamSignature;
        } catch (IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }

    private byte[] readSignatureObject(DataInputStream dis) throws IOException {
        long signatureclassId = dis.readLong();
        validateLongValue(SIGNATURE_CLASS_ID, signatureclassId, "Unable to read signature file v5 signature: invalid " +
                "signature class id ");
        int signatureClassVersion = dis.readInt();
        validateIntValue(SIGNATURE_CLASS_VERSION, signatureClassVersion, "Unable to read signature file v5 signature:" +
                " invalid signature class version ");
        int signatureType = dis.readInt();
        validateIntValue(SIGNATURE_TYPE, signatureType, "Unable to read signature file v5 signature: invalid " +
                "signature type ");

        int sigLength = dis.readInt();
        byte[] sigBytes = new byte[sigLength];
        int actualSigLength = dis.read(sigBytes);
        validateIntValue(sigLength, actualSigLength,
                "Unable to read signature file v5 signature: listed signature length " + sigLength + " != actual " +
                        "signature length ");
        return sigBytes;
    }

    private byte[] readHashObject(DataInputStream dis) throws IOException {
        byte[] fileHash = new byte[HASH_LENGTH];

        long hashClassId = dis.readLong();
        validateLongValue(HASH_CLASS_ID, hashClassId,
                "Unable to read signature file v5 hash: invalid class id ");

        int hashClassVersion = dis.readInt();
        validateIntValue(HASH_CLASS_VERSION, hashClassVersion,
                "Unable to read signature file v5 hash: invalid class version ");
        int hashType = dis.readInt();
        validateIntValue(HASH_DIGEST_TYPE, hashType,
                "Unable to read signature file v5 hash: invalid digest type: " + hashClassId);
        int hashLength = dis.readInt();
        validateIntValue(fileHash.length, hashLength, "Unable to read signature file v5 hash: invalid length ");

        byte[] hash = new byte[hashLength];
        int actualHashLength = dis.read(hash);
        validateIntValue(fileHash.length, actualHashLength,
                "Unable to read signature file v5 hash: listed length " + hashLength + " does not equal actual hash " +
                        "length ");
        return hash;
    }
}
