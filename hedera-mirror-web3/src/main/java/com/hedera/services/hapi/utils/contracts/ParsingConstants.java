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

package com.hedera.services.hapi.utils.contracts;

import static com.hedera.node.app.service.evm.store.contracts.utils.EvmParsingConstants.INT32;

import com.esaulpaugh.headlong.abi.TupleType;

public final class ParsingConstants {
    public static final String INT = "(int)";
    public static final TupleType burnReturnType = TupleType.parse("(int32,uint64)");
    public static final TupleType mintReturnType = TupleType.parse("(int32,uint64,int64[])");
    public static final TupleType hapiAllowanceOfType = TupleType.parse("(int32,uint256)");
    public static final TupleType intAddressTuple = TupleType.parse("(int32,address)");
    public static final TupleType intTuple = TupleType.parse(INT32);

    private ParsingConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    public enum FunctionType {
        HAPI_CREATE,
        HAPI_MINT,
        HAPI_BURN,
        HAPI_APPROVE,
        ERC_TRANSFER,
        ERC_APPROVE,
        HAPI_GET_APPROVED,
        HAPI_ALLOWANCE,
        HAPI_IS_APPROVED_FOR_ALL,
        ERC_IS_APPROVED_FOR_ALL,
        HAPI_TRANSFER_FROM,
        HAPI_APPROVE_NFT,
        HAPI_TRANSFER_FROM_NFT
    }
}
