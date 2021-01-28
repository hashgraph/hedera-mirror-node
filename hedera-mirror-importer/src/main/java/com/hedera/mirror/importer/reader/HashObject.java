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
import lombok.EqualsAndHashCode;
import lombok.Value;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;

@EqualsAndHashCode(callSuper=true)
@Value
public class HashObject extends AbstractStreamObject {

    int digestType;
    byte[] hash;

    public HashObject(ValidatedDataInputStream vdis, String sectionName, DigestAlgorithm digestAlgorithm) {
        super(vdis);

        try {
            digestType = vdis.readInt(digestAlgorithm.getType(), sectionName, "hash digest type");
            int hashLength = digestAlgorithm.getSize();
            hash = vdis.readLengthAndBytes(hashLength, hashLength, false, sectionName, "hash");
        } catch (IOException e) {
            throw new InvalidStreamFileException(e);
        }
    }

    public HashObject(ValidatedDataInputStream dis, DigestAlgorithm digestAlgorithm) {
        this(dis, null, digestAlgorithm);
    }

    protected HashObject(long classId, int classVersion, int digestType, byte[] hash) {
        super(classId, classVersion);
        this.digestType = digestType;
        this.hash = hash;
    }
}
