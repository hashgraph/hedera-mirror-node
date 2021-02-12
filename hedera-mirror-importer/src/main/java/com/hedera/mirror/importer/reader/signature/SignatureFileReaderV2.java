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

import java.io.IOException;
import javax.inject.Named;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.domain.FileStreamSignature.SignatureType;
import com.hedera.mirror.importer.domain.StreamFileData;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;
import com.hedera.mirror.importer.reader.ValidatedDataInputStream;

@Named
public class SignatureFileReaderV2 implements SignatureFileReader {

    protected static final byte SIGNATURE_TYPE_SIGNATURE = 3; // the file content signature, should not be hashed
    protected static final byte SIGNATURE_TYPE_FILE_HASH = 4; // next 48 bytes are SHA-384 of content of record file

    @Override
    public FileStreamSignature read(StreamFileData signatureFileData) {
        String filename = signatureFileData.getFilename();

        try (ValidatedDataInputStream vdis = new ValidatedDataInputStream(
                signatureFileData.getInputStream(), filename)) {
            vdis.readByte(SIGNATURE_TYPE_FILE_HASH, "hash delimiter");
            byte[] fileHash = vdis.readNBytes(DigestAlgorithm.SHA384.getSize(), "hash");

            vdis.readByte(SIGNATURE_TYPE_SIGNATURE, "signature delimiter");
            byte[] signature = vdis.readLengthAndBytes(1, SignatureType.SHA_384_WITH_RSA.getMaxLength(),
                    false, "signature");

            if (vdis.available() != 0) {
                throw new SignatureFileParsingException("Extra data discovered in signature file " + filename);
            }

            FileStreamSignature fileStreamSignature = new FileStreamSignature();
            fileStreamSignature.setFileHash(fileHash);
            fileStreamSignature.setFileHashSignature(signature);
            fileStreamSignature.setFilename(filename);
            fileStreamSignature.setSignatureType(SignatureType.SHA_384_WITH_RSA);

            return fileStreamSignature;
        } catch (InvalidStreamFileException | IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }
}
