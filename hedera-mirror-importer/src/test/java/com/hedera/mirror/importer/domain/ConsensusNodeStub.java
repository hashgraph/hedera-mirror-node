package com.hedera.mirror.importer.domain;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import java.security.PublicKey;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

import com.hedera.mirror.common.domain.entity.EntityId;
import com.hedera.mirror.importer.addressbook.ConsensusNode;

@Builder
@Data
@EqualsAndHashCode(of = "nodeId")
@ToString(exclude = "publicKey")
public class ConsensusNodeStub implements ConsensusNode {
    private EntityId nodeAccountId;
    private long nodeId;
    private PublicKey publicKey;
    private long stake;
    private long totalStake;
}
