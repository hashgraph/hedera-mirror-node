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
import lombok.Getter;

import com.hedera.mirror.importer.domain.DigestAlgorithm;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;

@Getter
@EqualsAndHashCode(callSuper=false)
public class HashObject extends AbstractStreamObject {

    private final int digestType;
    private final byte[] hash;

    public HashObject(Header header, int digestType, byte[] hash) {
        super(header);
        this.digestType = digestType;
        this.hash = hash;
    }

    public HashObject(ValidatedDataInputStream dis, String sectionName, DigestAlgorithm digestAlgorithm) {
        super(dis);

        try {
            digestType = dis.readInt(digestAlgorithm.getType(), sectionName, "hash digest type");
            int hashLength = digestAlgorithm.getSize();
            hash = dis.readLengthAndBytes(hashLength, hashLength, false, sectionName, "hash");
        } catch (IOException e) {
            throw new InvalidStreamFileException(e);
        }
    }

    public HashObject(ValidatedDataInputStream dis, DigestAlgorithm digestAlgorithm) {
        this(dis, null, digestAlgorithm);
    }
}
