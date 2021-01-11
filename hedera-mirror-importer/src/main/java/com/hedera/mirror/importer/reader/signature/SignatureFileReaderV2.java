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

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.inject.Named;
import lombok.extern.log4j.Log4j2;

import com.hedera.mirror.importer.domain.FileStreamSignature;
import com.hedera.mirror.importer.exception.SignatureFileParsingException;

@Log4j2
@Named
public class SignatureFileReaderV2 implements SignatureFileReader {

    private static final byte SIGNATURE_TYPE_SIGNATURE = 3; // the file content signature, should not be hashed
    public static final byte SIGNATURE_TYPE_FILE_HASH = 4; // next 48 bytes are SHA-384 of content of record file
    public static final byte HASH_SIZE = 48; // the size of the hash

    @Override
    public FileStreamSignature read(InputStream inputStream) {
        FileStreamSignature fileStreamSignature = new FileStreamSignature();

        try (DataInputStream dis = new DataInputStream(new BufferedInputStream(inputStream))) {
            byte[] fileHash = new byte[HASH_SIZE];

            byte hashTypeDelimiter = dis.readByte();
            if (hashTypeDelimiter != SIGNATURE_TYPE_FILE_HASH) {
                throw new SignatureFileParsingException("Unable to read signature file hash: type delimiter " + hashTypeDelimiter);
            }
            int length = dis.read(fileHash);
            if (length != fileHash.length) {
                throw new SignatureFileParsingException("Unable to read signature file hash: hash length " + length);
            }
            fileStreamSignature.setHash(fileHash);

            byte signatureTypeDelimiter = dis.readByte();
            if (signatureTypeDelimiter != SIGNATURE_TYPE_SIGNATURE) {
                throw new SignatureFileParsingException("Unable to read signature file signature: type delimiter " + signatureTypeDelimiter);
            }
            int sigLength = dis.readInt();
            byte[] sigBytes = new byte[sigLength];
            dis.readFully(sigBytes);
            fileStreamSignature.setSignature(sigBytes);

            if (dis.available() != 0) {
                throw new SignatureFileParsingException("Extra data discovered in signature file");
            }

            return fileStreamSignature;
        } catch (IOException e) {
            throw new SignatureFileParsingException(e);
        }
    }
}
