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

import static com.hedera.mirror.importer.domain.DigestAlgorithm.SHA384;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.inject.Named;
import lombok.Value;

import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.FileStreamSignature.SignatureType;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;
import com.hedera.mirror.importer.reader.Utility;

@Named
public class SignatureFileReaderV5 implements SignatureFileReader {

    protected static final byte SIGNATURE_FILE_FORMAT_VERSION = 5;

    @Override
    public FileStreamSignature read(InputStream inputStream) {
        FileStreamSignature fileStreamSignature = new FileStreamSignature();

        try (DataInputStream dis = new DataInputStream(inputStream)) {

            byte fileVersion = dis.readByte();
            Utility.validate(SIGNATURE_FILE_FORMAT_VERSION, fileVersion, "fileVersion");

            //Read the objectStreamSignatureVersion, which is not used
            dis.readInt();

            fileStreamSignature.setFileHash(readHashObject(dis, "entireFile"));
            Signature fileHashSignature = readSignatureObject(dis, "entireFile");
            fileStreamSignature.setFileHashSignature(fileHashSignature.getSignatureBytes());
            fileStreamSignature.setSignatureType(fileHashSignature.getSignatureType());

            fileStreamSignature.setMetadataHash(readHashObject(dis, "metadata"));
            Signature metadataSignature = readSignatureObject(dis, "metadata");
            fileStreamSignature.setMetadataHashSignature(metadataSignature.getSignatureBytes());

            if (dis.available() != 0) {
                throw new SignatureFileParsingException("Extra data discovered in signature file");
            }

            return fileStreamSignature;
        } catch (InvalidStreamFileException | IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }

    private byte[] readHashObject(DataInputStream dis, String sectionName) throws IOException {
        readClassIdAndVersion(dis);

        int hashType = dis.readInt();
        Utility.validate(SHA384.getType(), hashType, "hash digest type", sectionName);
        return Utility.readLengthAndBytes(dis, SHA384.getSize(), SHA384.getSize(), false, sectionName, "hash");
    }

    private Signature readSignatureObject(DataInputStream dis, String sectionName) throws IOException {
        readClassIdAndVersion(dis);

        int signatureTypeIndicator = dis.readInt();
        Utility.validate(SignatureType.SHA_384_WITH_RSA.getFileMarker(), signatureTypeIndicator, "signature type",
                sectionName);

        SignatureType signatureType = SignatureType.of(signatureTypeIndicator);
        byte[] signature = Utility.readLengthAndBytes(dis, 1, signatureType.getMaxLength(), true, sectionName, "signature");

        return new Signature(signature, signatureType);
    }

    private void readClassIdAndVersion(DataInputStream dis) throws IOException {
        //Read the ClassId and ClassVersion, which are not used
        dis.readLong();
        dis.readInt();
    }

    @Value
    private static class Signature {
        byte[] signatureBytes;
        FileStreamSignature.SignatureType signatureType;
    }
}
