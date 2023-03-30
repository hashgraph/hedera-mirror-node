/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.services.store.contracts.precompile.impl;

import com.google.protobuf.ByteString;

import com.hedera.node.app.service.evm.store.contracts.precompile.codec.EvmEncodingFacade;

import com.hedera.services.store.contracts.MirrorState;
import com.hedera.services.store.contracts.precompile.Precompile;

import com.hedera.services.store.contracts.precompile.codec.EncodingFacade;

import com.hedera.services.store.contracts.precompile.utils.PrecompilePricingUtils;

import com.hederahashgraph.api.proto.java.ContractCallTransactionBody;
import com.hederahashgraph.api.proto.java.ContractID;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenID;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.MessageFrame;
import java.util.function.UnaryOperator;

import static com.hedera.services.utils.EntityIdUtils.contractIdFromEvmAddress;

public abstract class AbstractReadOnlyPrecompile implements Precompile {
    protected TokenID tokenId;
    protected final MirrorState ledgers;
    protected final EncodingFacade encoder;
    protected final EvmEncodingFacade evmEncoder;
    protected final PrecompilePricingUtils pricingUtils;

    public static final String HTS_PRECOMPILED_CONTRACT_ADDRESS = "0x167";
    public static final ContractID HTS_PRECOMPILE_MIRROR_ID = contractIdFromEvmAddress(
            Address.fromHexString(HTS_PRECOMPILED_CONTRACT_ADDRESS).toArrayUnsafe());

    protected AbstractReadOnlyPrecompile(
            final TokenID tokenId,
            final MirrorState ledgers,
            final EncodingFacade encoder,
            final EvmEncodingFacade evmEncoder,
            final PrecompilePricingUtils pricingUtils) {
        this.tokenId = tokenId;
        this.ledgers = ledgers;
        this.encoder = encoder;
        this.evmEncoder = evmEncoder;
        this.pricingUtils = pricingUtils;
    }

    @Override
    public void body(final Bytes input, final UnaryOperator<byte[]> aliasResolver) {
        final var builder = ContractCallTransactionBody.newBuilder();

        builder.setContractID(HTS_PRECOMPILE_MIRROR_ID);
        builder.setGas(1L);
        builder.setFunctionParameters(ByteString.copyFrom(input.toArray()));
    }

    @Override
    public void run(final MessageFrame frame) {
        // No changes to state to apply
    }

    @Override
    public long getMinimumFeeInTinybars(final Timestamp consensusTime) {
        return 100;
    }

    @Override
    public long getGasRequirement(long blockTimestamp) {
        final var now = Timestamp.newBuilder().setSeconds(blockTimestamp).build();
        return pricingUtils.computeViewFunctionGas(now, getMinimumFeeInTinybars(now));
    }
}
