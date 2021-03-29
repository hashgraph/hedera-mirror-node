package com.hedera.mirror.importer.parser.record;

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

import static com.hedera.mirror.importer.domain.TransactionTypeEnum.SCHEDULECREATE;
import static com.hedera.mirror.importer.domain.TransactionTypeEnum.SCHEDULESIGN;

import com.google.common.collect.Sets;
import java.util.Collection;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import com.hedera.mirror.importer.MirrorProperties;
import com.hedera.mirror.importer.domain.StreamType;
import com.hedera.mirror.importer.domain.TransactionTypeEnum;
import com.hedera.mirror.importer.parser.AbstractParserProperties;

@Component("recordParserProperties")
@Data
@RequiredArgsConstructor
@Validated
@ConfigurationProperties("hedera.mirror.importer.parser.record")
public class RecordParserProperties extends AbstractParserProperties {

    private final MirrorProperties mirrorProperties;

    private Collection<TransactionTypeEnum> transactionSignatureTypes = Sets.newHashSet(SCHEDULECREATE, SCHEDULESIGN);

    @Override
    public StreamType getStreamType() {
        return StreamType.RECORD;
    }
}
