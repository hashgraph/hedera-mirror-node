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

import static com.hedera.mirror.importer.domain.DigestAlgorithm.SHA384;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.hedera.mirror.importer.TestUtils;
import com.hedera.mirror.importer.exception.InvalidStreamFileException;

public class HashObjectTest {

    @ParameterizedTest
    @CsvSource({
            "1226, 1, , 0, false",
            "1228, 1, , 0, false",
            "1226, 2, , 0, false",
            "1226, 1, 2, 0, true",
            "1226, 1, , 1, true",
            "1226, 1, , 49, true",
            "1226, 1, , 53, true",
            "1226, 1, , 57, true",
            "1226, 1, , 61, true",
    })
    void read(long classId, int classVersion, Integer digestType, int bytesToTruncate, boolean expectThrown) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(); DataOutputStream dos = new DataOutputStream(bos)) {
            // given
            dos.writeLong(classId);
            dos.writeInt(classVersion);
            dos.writeInt(Objects.requireNonNullElseGet(digestType, SHA384::getType));
            dos.writeInt(SHA384.getSize());
            byte[] expected = TestUtils.generateRandomByteArray(SHA384.getSize());
            dos.write(expected);

            byte[] data = bos.toByteArray();
            if (bytesToTruncate > 0) {
                data = Arrays.copyOfRange(data, 0, data.length - bytesToTruncate);
            }

            try (DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data))) {
                // when, then
                if (expectThrown) {
                    assertThrows(InvalidStreamFileException.class, () -> HashObject.read(dis, "testfile", SHA384));
                } else {
                    HashObject hashObject = HashObject.read(dis, "testfile", SHA384);
                    assertThat(hashObject.getClassId()).isEqualTo(classId);
                    assertThat(hashObject.getHash()).isEqualTo(expected);
                }
            }

        }
    }
}
