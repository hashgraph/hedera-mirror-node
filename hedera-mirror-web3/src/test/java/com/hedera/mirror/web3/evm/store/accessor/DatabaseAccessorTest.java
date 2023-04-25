/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
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
 */

package com.hedera.mirror.web3.evm.store.accessor;

import java.nio.file.AccessDeniedException;
import java.util.Optional;
import java.util.stream.LongStream;
import lombok.NonNull;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SoftAssertionsExtension.class)
class DatabaseAccessorTest {

    @InjectSoftAssertions
    private SoftAssertions softly;

    static class DBAccessorTestImpl extends DatabaseAccessor<AccessDeniedException, LongStream> {
        @NonNull
        public Optional<LongStream> get(@NonNull final AccessDeniedException ignored) {
            return Optional.empty();
        }
    }

    @Test
    void genericTypeParametersReflectionTest() {
        final var sut = new DBAccessorTestImpl();

        softly.assertThat(sut.getKeyClass()).isEqualTo(AccessDeniedException.class);
        softly.assertThat(sut.getValueClass()).isEqualTo(LongStream.class);
    }
}
