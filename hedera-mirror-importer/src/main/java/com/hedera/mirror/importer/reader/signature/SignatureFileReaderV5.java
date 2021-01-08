package com.hedera.mirror.importer.reader.signature;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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
    protected static final int HASH_SIZE = 48; //48 bytes for SHA-384

    protected static final long SIGNATURE_CLASS_ID = 0x13dc4b399b245c69L;
    protected static final int SIGNATURE_CLASS_VERSION = 1;
    protected static final int SIGNATURE_TYPE = 1; //denotes SHA384withRSA

    @Override
    public FileStreamSignature read(InputStream inputStream) {
        FileStreamSignature fileStreamSignature = new FileStreamSignature();

        try (DataInputStream dis = new DataInputStream(inputStream)) {

            byte fileVersion = dis.readByte();
            validate(SIGNATURE_FILE_FORMAT_VERSION, fileVersion, "fileVersion");

            int objectStreamSignatureVersion = dis.readInt();
            validate(OBJECT_STREAM_SIGNATURE_VERSION, objectStreamSignatureVersion, "objectStreamSignatureVersion");

            fileStreamSignature.setHash(readHashObject(dis));

            fileStreamSignature.setSignature(readSignatureObject(dis));

            return fileStreamSignature;
        } catch (IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }

    private byte[] readHashObject(DataInputStream dis) throws IOException {
        //Read the hashClassId and hashClassVersion, which are not used
        dis.readLong();
        dis.readInt();

        int hashType = dis.readInt();
        validate(HASH_DIGEST_TYPE, hashType,
                "hashDigestType");
        int hashLength = dis.readInt();
        validate(HASH_SIZE, hashLength, "hashLength");

        byte[] hash = new byte[hashLength];
        int actualHashLength = dis.read(hash);
        validate(hashLength, actualHashLength,
                "actualHashLength");
        return hash;
    }

    private byte[] readSignatureObject(DataInputStream dis) throws IOException {
        //Read the signatureClassId and signatureClassVersion, which are not used
        dis.readLong();
        dis.readInt();

        int signatureType = dis.readInt();
        validate(SIGNATURE_TYPE, signatureType, "signatureType");

        int signatureLength = dis.readInt();
        byte[] signature = new byte[signatureLength];
        int actualSignatureLength = dis.read(signature);
        validate(signatureLength, actualSignatureLength,
                "actualSignatureLength");
        return signature;
    }
}
