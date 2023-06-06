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

package com.hedera.mirror.web3.exception;

import com.hedera.node.app.service.evm.exceptions.InvalidTransactionException;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

import java.io.Serial;

/**
 * Exception thrown when a resource limit is exceeded (e.g. max nfts mint exceeded).
 * **/
public class ResourceLimitException extends InvalidTransactionException {

    @Serial
    private static final long serialVersionUID = -5647430220816450850L;

    public ResourceLimitException(ResponseCodeEnum responseCode) {
        super(responseCode);
    }
}
