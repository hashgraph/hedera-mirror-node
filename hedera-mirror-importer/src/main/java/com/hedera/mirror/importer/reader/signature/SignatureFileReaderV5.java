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
import com.hedera.mirror.importer.domain.FileStreamSignature.SignatureType;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;

@Log4j2
@Named
public class SignatureFileReaderV5 extends AbstractSignatureFileReader {

    protected static final byte SIGNATURE_FILE_FORMAT_VERSION = 5;
    protected static final int OBJECT_STREAM_SIGNATURE_VERSION = 1; //defines the format for the remainder of the file

    protected static final int HASH_SIZE = 48; //48 bytes for SHA-384

    @Override
    public FileStreamSignature read(InputStream inputStream) {
        FileStreamSignature fileStreamSignature = new FileStreamSignature();

        try (DataInputStream dis = new DataInputStream(inputStream)) {

            byte fileVersion = dis.readByte();
            validate(SIGNATURE_FILE_FORMAT_VERSION, fileVersion, "fileVersion");

            //Read the objectStreamSignatureVersion, which is not used
            dis.readInt();

            fileStreamSignature.setFileHash(readHashObject(dis, "file"));
            Signature fileHashSignature = readSignatureObject(dis, "file");
            fileStreamSignature.setFileHashSignature(fileHashSignature.getSignatureBytes());
            fileStreamSignature.setSignatureType(fileHashSignature.getSignatureType());

            fileStreamSignature.setMetadataHash(readHashObject(dis, "metadata"));
            Signature metadataSignature = readSignatureObject(dis, "metadata");
            fileStreamSignature.setMetadataHashSignature(metadataSignature.getSignatureBytes());

            if (dis.available() != 0) {
                throw new SignatureFileParsingException("Extra data discovered in signature file");
            }

            return fileStreamSignature;
        } catch (IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }

    private byte[] readHashObject(DataInputStream dis, String sectionName) throws IOException {
        readClassIdAndVersion(dis);

        int hashType = dis.readInt();
        validate(HASH_DIGEST_TYPE, hashType, "hashDigestType", sectionName);
        int hashLength = dis.readInt();
        validate(HASH_SIZE, hashLength, "hashLength", sectionName);

        byte[] hash = new byte[hashLength];
        int actualHashLength = dis.read(hash);
        validate(hashLength, actualHashLength, "actualHashLength", sectionName);
        return hash;
    }

    private Signature readSignatureObject(DataInputStream dis, String sectionName) throws IOException {
        readClassIdAndVersion(dis);

        int signatureTypeIndicator = dis.readInt();
        validate(SignatureType.SHA_384_WITH_RSA
                .getFileMarker(), signatureTypeIndicator, "signatureType", sectionName);

        SignatureType signatureType = SignatureType.fromSignatureTypeIndicator(signatureTypeIndicator);

        int signatureLength = dis.readInt();
        validateBetween(1, signatureType.getMaxLength(), signatureLength, "signatureLength", sectionName);
        //Checksum is calculated as 101 - length of signature bytes
        int checkSum = dis.readInt();
        validate(101 - signatureLength, checkSum, "checkSum", sectionName);
        byte[] signature = new byte[signatureLength];
        int actualSignatureLength = dis.read(signature);
        validate(signatureLength, actualSignatureLength, "actualSignatureLength", sectionName);

        return new Signature(signature, signatureType);
    }

    private void readClassIdAndVersion(DataInputStream dis) throws IOException {
        //Read the ClassId and ClassVersion, which are not used
        dis.readLong();
        dis.readInt();
    }
}
