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

package com.hedera.services.txns.validation;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

/**
 * Copied Logic type from hedera-services. Unnecessary methods are deleted.
 */
public class ContextOptionValidator implements OptionValidator {

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    public ContextOptionValidator(final MirrorNodeEvmProperties mirrorNodeEvmProperties) {
        this.mirrorNodeEvmProperties = mirrorNodeEvmProperties;
    }

    public static ResponseCodeEnum batchSizeCheck(final int length, final int limit) {
        return lengthCheck(length, limit, ResponseCodeEnum.BATCH_SIZE_LIMIT_EXCEEDED);
    }

    private static ResponseCodeEnum lengthCheck(final long length, final long limit, final ResponseCodeEnum onFailure) {
        if (length > limit) {
            return onFailure;
        }
        return OK;
    }

    @Override
    public ResponseCodeEnum maxBatchSizeMintCheck(final int length) {
        return batchSizeCheck(length, mirrorNodeEvmProperties.getMaxBatchSizeMint());
    }

    @Override
    public ResponseCodeEnum nftMetadataCheck(final byte[] metadata) {
        return lengthCheck(
                metadata.length, mirrorNodeEvmProperties.getMaxNftMetadataBytes(), ResponseCodeEnum.METADATA_TOO_LONG);
    }
}
