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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@Named
@RequiredArgsConstructor
public class CustomFeeDatabaseAccessor extends DatabaseAccessor<Object, List<CustomFee>> {
    private final CustomFeeRepository customFeeRepository;

    private final EntityDatabaseAccessor entityDatabaseAccessor;

    @Override
    public @NonNull Optional<List<CustomFee>> get(@NonNull Object tokenId) {
        final var customFeeOptional = customFeeRepository.findById((Long) tokenId);
        return customFeeOptional.isEmpty() ? Optional.empty() : Optional.of(mapCustomFee(customFeeOptional.get()));
    }

    private List<CustomFee> mapCustomFee(com.hedera.mirror.common.domain.token.CustomFee customFee) {
        var customFeesConstructed = new ArrayList<CustomFee>();
        customFeesConstructed.addAll(mapFixedFees(customFee));
        customFeesConstructed.addAll(mapFractionalFees(customFee));
        customFeesConstructed.addAll(mapRoyaltyFees(customFee));
        return customFeesConstructed;
    }

    private List<CustomFee> mapFixedFees(com.hedera.mirror.common.domain.token.CustomFee customFee) {
        if (customFee.getFixedFees() == null) {
            return Collections.emptyList();
        }

        var fixedFees = new ArrayList<CustomFee>();
        customFee.getFixedFees().forEach(f -> {
            if (f.getCollectorAccountId() != null) {
                final var collector = entityDatabaseAccessor.evmAddressFromId(f.getCollectorAccountId());
                final var denominatingTokenId = f.getDenominatingTokenId();
                final var denominatingTokenAddress =
                        denominatingTokenId == null ? EMPTY_EVM_ADDRESS : toAddress(denominatingTokenId);
                final var fixedFee = new FixedFee(
                        requireNonNullElse(f.getAmount(), 0L),
                        denominatingTokenAddress,
                        denominatingTokenId == null,
                        false,
                        collector);
                var constructed = new CustomFee();
                constructed.setFixedFee(fixedFee);
                fixedFees.add(constructed);
            }
        });

        return fixedFees;
    }

    private List<CustomFee> mapFractionalFees(com.hedera.mirror.common.domain.token.CustomFee customFee) {
        if (customFee.getFractionalFees() == null) {
            return Collections.emptyList();
        }

        var fractionalFees = new ArrayList<CustomFee>();
        customFee.getFractionalFees().forEach(f -> {
            if (f.getCollectorAccountId() != null) {
                final var collector = entityDatabaseAccessor.evmAddressFromId(f.getCollectorAccountId());
                final var fractionFee = new FractionalFee(
                        requireNonNullElse(f.getNumerator(), 0L),
                        requireNonNullElse(f.getDenominator(), 0L),
                        requireNonNullElse(f.getMinimumAmount(), 0L),
                        requireNonNullElse(f.getMaximumAmount(), 0L),
                        requireNonNullElse(f.isNetOfTransfers(), false),
                        collector);
                var constructed = new CustomFee();
                constructed.setFractionalFee(fractionFee);
                fractionalFees.add(constructed);
            }
        });

        return fractionalFees;
    }

    private List<CustomFee> mapRoyaltyFees(com.hedera.mirror.common.domain.token.CustomFee customFee) {
        if (customFee.getRoyaltyFees() == null) {
            return Collections.emptyList();
        }

        var royaltyFees = new ArrayList<CustomFee>();
        if (customFee.getRoyaltyFees() != null) {
            customFee.getRoyaltyFees().forEach(f -> {
                if (f.getCollectorAccountId() != null) {
                    final var fallbackFee = f.getFallbackFee();
                    if (fallbackFee != null) {
                        final var denominatingTokenId = fallbackFee.getDenominatingTokenId();
                        final var denominatingTokenAddress =
                                denominatingTokenId == null ? EMPTY_EVM_ADDRESS : toAddress(denominatingTokenId);
                        final var collector = entityDatabaseAccessor.evmAddressFromId(f.getCollectorAccountId());
                        final var royaltyFee = new RoyaltyFee(
                                requireNonNullElse(f.getNumerator(), 0L),
                                requireNonNullElse(f.getDenominator(), 0L),
                                requireNonNullElse(fallbackFee.getAmount(), 0L),
                                denominatingTokenAddress,
                                denominatingTokenId == null,
                                collector);
                        var constructed = new CustomFee();
                        constructed.setRoyaltyFee(royaltyFee);
                        royaltyFees.add(constructed);
                    }
                }
            });
        }

        return royaltyFees;
    }
}
