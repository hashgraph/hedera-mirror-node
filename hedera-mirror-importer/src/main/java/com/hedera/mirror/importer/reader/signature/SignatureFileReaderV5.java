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

import java.io.BufferedInputStream;
import java.io.IOException;
import javax.inject.Named;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import org.apache.commons.io.FilenameUtils;

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
        FileStreamSignature fileStreamSignature = new FileStreamSignature();
        String filename = FilenameUtils.getName(signatureFileData.getFilename());

        try (ValidatedDataInputStream dis = new ValidatedDataInputStream(
                new BufferedInputStream(signatureFileData.getInputStream()),
                filename
        )) {
            dis.readByte(SIGNATURE_FILE_FORMAT_VERSION, "fileVersion");

            // Read the objectStreamSignatureVersion, which is not used
            dis.readInt();

            HashObject fileHashObject = new HashObject(dis, "entireFile", SHA384);
            fileStreamSignature.setFileHash(fileHashObject.getHash());
            SignatureObject signatureObject = new SignatureObject(dis, "entireFile");
            fileStreamSignature.setFileHashSignature(signatureObject.getSignature());
            fileStreamSignature.setSignatureType(signatureObject.getSignatureType());

            HashObject metadataHashObject = new HashObject(dis, "metadata", SHA384);
            fileStreamSignature.setMetadataHash(metadataHashObject.getHash());
            signatureObject = new SignatureObject(dis, "metadata");
            fileStreamSignature.setMetadataHashSignature(signatureObject.getSignature());

            if (dis.available() != 0) {
                throw new SignatureFileParsingException("Extra data discovered in signature file " + filename);
            }

            return fileStreamSignature;
        } catch (InvalidStreamFileException | IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }

    @EqualsAndHashCode(callSuper=true)
    @Getter
    private static class SignatureObject extends AbstractStreamObject {

        private final byte[] signature;
        private final SignatureType signatureType;

        SignatureObject(ValidatedDataInputStream dis, String sectionName) {
            super(dis);

            try {
                signatureType = SignatureType.SHA_384_WITH_RSA;
                dis.readInt(signatureType.getFileMarker(), sectionName, "signature type");
                signature = dis.readLengthAndBytes(1, signatureType.getMaxLength(), true, sectionName, "signature");
            } catch (IOException e) {
                throw new InvalidStreamFileException(e);
            }
        }
    }
}
