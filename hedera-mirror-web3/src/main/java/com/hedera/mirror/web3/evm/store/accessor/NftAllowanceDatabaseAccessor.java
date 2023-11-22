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

import com.hedera.mirror.common.domain.entity.AbstractNftAllowance.Id;
import com.hedera.mirror.common.domain.entity.NftAllowance;
import com.hedera.mirror.web3.common.ContractCallContext;
import com.hedera.mirror.web3.repository.NftAllowanceRepository;
import jakarta.inject.Named;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class NftAllowanceDatabaseAccessor extends DatabaseAccessor<Object, NftAllowance> {

    private final NftAllowanceRepository nftAllowanceRepository;

    @Override
    public @NonNull Optional<NftAllowance> get(@NonNull Object key) {
        final var historicalRecordFile = ContractCallContext.get().getRecordFile();
        final var timestamp = (historicalRecordFile != null) ? historicalRecordFile.getConsensusEnd() : -1;
        return (timestamp != -1)
                ? nftAllowanceRepository.findByIdAndTimestamp(((Id) key).getTokenId(), timestamp)
                : nftAllowanceRepository.findById((Id) key);
    }
}
