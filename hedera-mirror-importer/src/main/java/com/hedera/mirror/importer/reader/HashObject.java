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

    public static HashObject read(ValidatedDataInputStream dis, String sectionName, DigestAlgorithm digestAlgorithm) {
        try {
            long classId = dis.readLong();
            int classVersion = dis.readInt();

            dis.readInt(digestAlgorithm.getType(), sectionName, "hash digest type");
            int hashLength = digestAlgorithm.getSize();
            byte[] hash = dis.readLengthAndBytes(hashLength, hashLength, false, sectionName, "hash");

            return new HashObject(classId, classVersion, digestAlgorithm.getType(), hash);
        } catch (IOException e) {
            throw new InvalidStreamFileException(e);
        }
    }

    public static HashObject read(ValidatedDataInputStream dis, DigestAlgorithm digestAlgorithm) {
        return read(dis, null, digestAlgorithm);
    }
}
