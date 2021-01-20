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
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.inject.Named;
import lombok.Value;
import org.apache.commons.io.FilenameUtils;

import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.FileStreamSignature.SignatureType;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;
import com.hedera.mirror.importer.reader.HashObject;
import com.hedera.mirror.importer.reader.ValidatedDataInputStream;

@Named
public class SignatureFileReaderV5 implements SignatureFileReader {

    protected static final byte SIGNATURE_FILE_FORMAT_VERSION = 5;

    @Override
    public FileStreamSignature read(StreamFileData signatureFileData) {
        FileStreamSignature fileStreamSignature = new FileStreamSignature();
        String filename = FilenameUtils.getName(signatureFileData.getFilename());
        InputStream inputStream = signatureFileData.getInputStream();

        try (ValidatedDataInputStream dis = new ValidatedDataInputStream(new BufferedInputStream(inputStream),
                filename)) {
            dis.readByte(SIGNATURE_FILE_FORMAT_VERSION, "fileVersion");

            // Read the objectStreamSignatureVersion, which is not used
            dis.readInt();

            HashObject fileHashObject = HashObject.read(dis, "entireFile", SHA384);
            fileStreamSignature.setFileHash(fileHashObject.getHash());
            Signature fileHashSignature = readSignatureObject(dis, "entireFile");
            fileStreamSignature.setFileHashSignature(fileHashSignature.getSignatureBytes());
            fileStreamSignature.setSignatureType(fileHashSignature.getSignatureType());

            HashObject metadataHashObject = HashObject.read(dis, "metadata", SHA384);
            fileStreamSignature.setMetadataHash(metadataHashObject.getHash());
            Signature metadataSignature = readSignatureObject(dis, "metadata");
            fileStreamSignature.setMetadataHashSignature(metadataSignature.getSignatureBytes());

            if (dis.available() != 0) {
                throw new SignatureFileParsingException("Extra data discovered in signature file " + filename);
            }

            return fileStreamSignature;
        } catch (InvalidStreamFileException | IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }

    private Signature readSignatureObject(ValidatedDataInputStream dis, String sectionName) throws IOException {
        readClassIdAndVersion(dis);

        dis.readInt(SignatureType.SHA_384_WITH_RSA.getFileMarker(), sectionName, "signature type");
        byte[] signature = dis.readLengthAndBytes(1, SignatureType.SHA_384_WITH_RSA.getMaxLength(),
                true, sectionName, "signature");

        return new Signature(signature, SignatureType.SHA_384_WITH_RSA);
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
