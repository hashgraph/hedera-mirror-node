package com.hedera.mirror.importer.reader;

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
import lombok.Data;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;

@Data
public class HashObject {

    private final long classId;
    private final int classVersion;
    private final int digestType;
    private final byte[] hash;

    public static HashObject read(DataInputStream dis, String filename, String sectionName,
            DigestAlgorithm digestAlgorithm) {
        try {
            long classId = dis.readLong();
            int classVersion = dis.readInt();

            int digestType = dis.readInt();
            ReaderUtility.validate(digestAlgorithm.getType(), digestType, filename, sectionName, "hash digest type");

            int hashLength = digestAlgorithm.getSize();
            byte[] hash = ReaderUtility.readLengthAndBytes(dis, hashLength, hashLength, false, filename,
                    sectionName, "hash");

            return new HashObject(classId, classVersion, digestType, hash);
        } catch (IOException e) {
            throw new InvalidStreamFileException(e);
        }
    }

    public static HashObject read(DataInputStream dis, String filename, DigestAlgorithm digestAlgorithm) {
        return read(dis, filename, null, digestAlgorithm);
    }
}
