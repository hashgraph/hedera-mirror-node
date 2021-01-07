package com.hedera.mirror.importer.reader.record;

/*-
 *
 * Hedera Mirror Node
 *  ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
 *  ​
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
 *
 */

import java.io.DataInputStream;
import java.io.IOException;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

import com.hedera.mirror.importer.exception.InvalidStreamFileException;

@UtilityClass
public class Utility {

    public <T> void checkField(@NonNull T actual, @NonNull T expected, String name, String filename) {
        if (!actual.equals(expected)) {
            throw new InvalidStreamFileException(String.format("Expect %s (%s) got %s for record file %s",
                    name, expected, actual, filename));
        }
    }

    public byte[] readLengthAndBytes(@NonNull DataInputStream dis) throws IOException {
        int len = dis.readInt();
        byte[] bytes = new byte[len];
        dis.readFully(bytes);
        return bytes;
    }
}
