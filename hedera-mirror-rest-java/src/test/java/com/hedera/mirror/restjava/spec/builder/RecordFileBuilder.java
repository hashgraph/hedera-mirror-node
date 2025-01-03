/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.spec.builder;

import static com.hedera.mirror.common.util.DomainUtils.EMPTY_BYTE_ARRAY;

import com.hedera.mirror.common.domain.DigestAlgorithm;
import com.hedera.mirror.common.domain.transaction.RecordFile;
import com.hedera.mirror.restjava.spec.model.SpecSetup;
import jakarta.inject.Named;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Named
class RecordFileBuilder extends AbstractEntityBuilder<RecordFile, RecordFile.RecordFileBuilder> {

    private static final Map<String, String> ATTRIBUTE_NAME_MAP = Map.of("prev_hash", "previous_hash");

    private static final byte[] DEFAULT_BYTES = new byte[] {1, 1, 2, 2, 3, 3};

    RecordFileBuilder() {
        super(Map.of(), ATTRIBUTE_NAME_MAP);
    }

    @Override
    protected Supplier<List<Map<String, Object>>> getSpecEntitiesSupplier(SpecSetup specSetup) {
        return specSetup::recordFiles;
    }

    @Override
    protected RecordFile.RecordFileBuilder getEntityBuilder() {
        return RecordFile.builder()
                .bytes(DEFAULT_BYTES)
                .consensusEnd(1628751573995691000L)
                .consensusStart(1628751572000852000L)
                .count(1200L)
                .digestAlgorithm(DigestAlgorithm.SHA_384)
                .fileHash(
                        "dee34bdd8bbe32fdb53ce7e3cf764a0495fa5e93b15ca567208cfb384231301bedf821de07b0d8dc3fb55c5b3c90ac61")
                .gasUsed(0L)
                .hapiVersionMajor(0)
                .hapiVersionMinor(11)
                .hapiVersionPatch(0)
                .hash(
                        "ed55d98d53fd55c9caf5f61affe88cd2978d37128ec54af5dace29b6fd271cbd079ebe487bda5f227087e2638b1100cf")
                .index(123456789L)
                .loadEnd(1629298236L)
                .loadStart(1629298233L)
                .logsBloom(EMPTY_BYTE_ARRAY)
                .name("2021-08-12T06_59_32.000852000Z.rcd")
                .nodeId(0L)
                .previousHash(
                        "000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000")
                .size(6)
                .version(5);
    }

    @Override
    protected RecordFile getFinalEntity(RecordFile.RecordFileBuilder builder, Map<String, Object> account) {
        var entity = builder.build();
        builder.size(entity.getBytes() != null ? entity.getBytes().length : entity.getSize());
        return builder.build();
    }
}
