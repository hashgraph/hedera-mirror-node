/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.services.store.contracts.precompile;

import com.hederahashgraph.api.proto.java.TransferList;
import java.util.List;

public record TransferWrapper(List<HbarTransfer> hbarTransfers) {
    public TransferList.Builder asGrpcBuilder() {
        final var builder = TransferList.newBuilder();

        for (final var transfer : hbarTransfers) {
            if (transfer.sender() != null) {
                builder.addAccountAmounts(transfer.senderAdjustment());
            }
            if (transfer.receiver() != null) {
                builder.addAccountAmounts(transfer.receiverAdjustment());
            }
        }
        return builder;
    }
}
