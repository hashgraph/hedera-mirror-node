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

package com.hedera.mirror.common.domain.transaction;

import com.hedera.hapi.block.stream.output.protoc.StateChanges;
import com.hedera.hapi.block.stream.output.protoc.TransactionOutput;
import com.hedera.hapi.block.stream.output.protoc.TransactionResult;
import com.hedera.mirror.common.domain.StreamItem;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.Transaction;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Builder(buildMethodName = "buildInternal")
@Data
public class BlockItem implements StreamItem {

    public final Transaction transaction;
    public final TransactionResult transactionResult;
    public final List<TransactionOutput> transactionOutput;
    public final List<StateChanges> stateChanges;

    private BlockItem parent;
    private BlockItem previous;
    private boolean successful;

    public static class BlockItemBuilder {
        public BlockItem build() {
            this.parent = parseParent();
            successful = parseSuccess(this.parent);
            return buildInternal();
        }

        private BlockItem parseParent() {
            // set parent, parent-child items are assured to exist in sequential order of [Parent, Child1,..., ChildN]
            if (transactionResult.hasParentConsensusTimestamp() && previous != null) {
                var parentTimestamp = transactionResult.getParentConsensusTimestamp();
                if (parentTimestamp.equals(previous.transactionResult.getConsensusTimestamp())) {
                    return previous;
                } else if (previous.parent != null
                        && parentTimestamp.equals(previous.parent.transactionResult.getConsensusTimestamp())) {
                    // check older siblings parent, if child count is > 1 this prevents having to search to parent
                    return previous.parent;
                }
            }
            return null;
        }

        private boolean parseSuccess(BlockItem parent) {
            if (parent != null && !parent.isSuccessful()) {
                return false;
            }

            var status = transactionResult.getStatus();
            return status == ResponseCodeEnum.FEE_SCHEDULE_FILE_PART_UPLOADED
                    || status == ResponseCodeEnum.SUCCESS
                    || status == ResponseCodeEnum.SUCCESS_BUT_MISSING_EXPECTED_OPERATION;
        }
    }
}
