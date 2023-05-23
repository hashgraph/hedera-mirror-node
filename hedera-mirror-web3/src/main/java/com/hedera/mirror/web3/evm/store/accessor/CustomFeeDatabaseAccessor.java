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

import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.EMPTY_EVM_ADDRESS;
import static com.hedera.mirror.web3.evm.utils.EvmTokenUtils.toAddress;
import static java.util.Objects.requireNonNullElse;

import com.hedera.mirror.web3.repository.CustomFeeRepository;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.CustomFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FixedFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.FractionalFee;
import com.hedera.node.app.service.evm.store.contracts.precompile.codec.RoyaltyFee;
import jakarta.inject.Named;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class CustomFeeDatabaseAccessor extends DatabaseAccessor<Long, List<CustomFee>> {
    private final CustomFeeRepository customFeeRepository;

    private final EntityDatabaseAccessor entityDatabaseAccessor;

    @Override
    public @NonNull Optional<List<CustomFee>> get(@NonNull Long tokenId) {
        final var customFeesList = customFeeRepository.findByTokenId(tokenId).stream()
                .filter(customFee -> customFee.getCollectorAccountId() != null)
                .map(this::mapCustomFee)
                .toList();

        return Optional.of(customFeesList);
    }

    private CustomFee mapCustomFee(com.hedera.mirror.common.domain.transaction.CustomFee customFee) {
        final var collector = entityDatabaseAccessor.evmAddressFromId(customFee.getCollectorAccountId());
        final long amount = requireNonNullElse(customFee.getAmount(), 0L);
        final var denominatingTokenId = customFee.getDenominatingTokenId();
        final var denominatingTokenAddress =
                denominatingTokenId == null ? EMPTY_EVM_ADDRESS : toAddress(denominatingTokenId);
        final long amountDenominator = requireNonNullElse(customFee.getAmountDenominator(), 0L);
        final var maximumAmount = requireNonNullElse(customFee.getMaximumAmount(), 0L);
        final var minimumAmount = customFee.getMinimumAmount();
        final var netOfTransfers = requireNonNullElse(customFee.getNetOfTransfers(), false);
        final long royaltyDenominator = requireNonNullElse(customFee.getRoyaltyDenominator(), 0L);
        final long royaltyNumerator = requireNonNullElse(customFee.getRoyaltyNumerator(), 0L);

        CustomFee customFeeConstructed = new CustomFee();

        if (royaltyNumerator > 0 && royaltyDenominator > 0) {
            final var royaltyFee = new RoyaltyFee(
                    royaltyNumerator,
                    royaltyDenominator,
                    amount,
                    denominatingTokenAddress,
                    denominatingTokenId == null,
                    collector);
            customFeeConstructed.setRoyaltyFee(royaltyFee);

        } else if (amountDenominator > 0) {
            final var fractionFee = new FractionalFee(
                    amount, amountDenominator, minimumAmount, maximumAmount, netOfTransfers, collector);
            customFeeConstructed.setFractionalFee(fractionFee);

        } else {
            final var fixedFee =
                    new FixedFee(amount, denominatingTokenAddress, denominatingTokenId == null, false, collector);

            customFeeConstructed.setFixedFee(fixedFee);
        }

        return customFeeConstructed;
    }
}
