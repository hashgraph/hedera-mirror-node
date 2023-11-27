/*
 * Copyright (C) 2019-2023 Hedera Hashgraph, LLC
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

package com.hedera.mirror.importer.parser.record;

import com.hedera.mirror.common.domain.transaction.RecordItem;
import com.hedera.mirror.importer.parser.StreamItemListener;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public interface RecordItemListener extends StreamItemListener<RecordItem> {

    default boolean receiptStatusContainsInvalidId(ResponseCodeEnum receiptStatus) {
        return switch (receiptStatus) {
            case INVALID_ACCOUNT_ID,
                    INVALID_CONTRACT_ID,
                    INVALID_FILE_ID,
                    INVALID_SCHEDULE_ID,
                    INVALID_TOKEN_ID,
                    INVALID_TOPIC_ID -> true;
            default -> false;
        };
    }
}
