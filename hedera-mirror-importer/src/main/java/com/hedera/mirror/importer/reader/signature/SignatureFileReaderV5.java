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

import java.io.IOException;
import javax.inject.Named;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.FileStreamSignature.SignatureType;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;
import com.hedera.mirror.importer.reader.AbstractStreamObject;
import com.hedera.mirror.importer.reader.HashObject;
import com.hedera.mirror.importer.reader.ValidatedDataInputStream;

@Named
public class SignatureFileReaderV5 implements SignatureFileReader {

    protected static final byte SIGNATURE_FILE_FORMAT_VERSION = 5;

    @Override
    public FileStreamSignature read(StreamFileData signatureFileData) {
        String filename = signatureFileData.getFilename();

        try (ValidatedDataInputStream vdis = new ValidatedDataInputStream(
                signatureFileData.getInputStream(), filename)) {
            vdis.readByte(SIGNATURE_FILE_FORMAT_VERSION, "fileVersion");

            // Read the objectStreamSignatureVersion, which is not used
            vdis.readInt();

            HashObject fileHashObject = new HashObject(vdis, "entireFile", SHA384);
            SignatureObject fileHashSignatureObject = new SignatureObject(vdis, "entireFile");

            HashObject metadataHashObject = new HashObject(vdis, "metadata", SHA384);
            SignatureObject metadataHashSignatureObject = new SignatureObject(vdis, "metadata");

            if (vdis.available() != 0) {
                throw new SignatureFileParsingException("Extra data discovered in signature file " + filename);
            }

            FileStreamSignature fileStreamSignature = new FileStreamSignature();
            fileStreamSignature.setBytes(signatureFileData.getBytes());
            fileStreamSignature.setFileHash(fileHashObject.getHash());
            fileStreamSignature.setFileHashSignature(fileHashSignatureObject.getSignature());
            fileStreamSignature.setFilename(filename);
            fileStreamSignature.setMetadataHash(metadataHashObject.getHash());
            fileStreamSignature.setMetadataHashSignature(metadataHashSignatureObject.getSignature());
            fileStreamSignature.setSignatureType(fileHashSignatureObject.getSignatureType());

            return fileStreamSignature;
        } catch (InvalidStreamFileException | IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }

    @EqualsAndHashCode(callSuper = true)
    @Getter
    private static class SignatureObject extends AbstractStreamObject {

        private final byte[] signature;
        private final SignatureType signatureType;

        SignatureObject(ValidatedDataInputStream vdis, String sectionName) {
            super(vdis);

            try {
                signatureType = SignatureType.SHA_384_WITH_RSA;
                vdis.readInt(signatureType.getFileMarker(), sectionName, "signature type");
                signature = vdis.readLengthAndBytes(1, signatureType.getMaxLength(), true, sectionName, "signature");
            } catch (IOException e) {
                throw new InvalidStreamFileException(e);
            }
        }
    }
}
