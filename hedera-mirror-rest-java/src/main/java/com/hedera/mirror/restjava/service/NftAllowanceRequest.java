/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.restjava.service;

import com.hedera.mirror.restjava.common.RangeOperator;
import lombok.Builder;
import lombok.Data;
import org.springframework.data.domain.Sort;

@Data
@Builder
public class NftAllowanceRequest {

    @Builder.Default
    private RangeOperator accountIdOperator = RangeOperator.gt;

    private long ownerId;

    @Builder.Default
    private int limit = 25;

    @Builder.Default
    private Sort.Direction order = Sort.Direction.ASC;

    @Builder.Default
    private boolean isOwner = true;

    private long spenderId;

    private long tokenId;

    @Builder.Default
    private RangeOperator tokenIdOperator = RangeOperator.gt;
}
