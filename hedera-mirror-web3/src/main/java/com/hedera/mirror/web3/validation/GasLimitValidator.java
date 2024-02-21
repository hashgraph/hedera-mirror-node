/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.validation;

import com.hedera.mirror.web3.evm.properties.MirrorNodeEvmProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class GasLimitValidator implements ConstraintValidator<MaxGas, Long> {

    private static final String MAX_GAS_MESSAGE = "must be less than or equal to ";

    private final MirrorNodeEvmProperties mirrorNodeEvmProperties;

    @Override
    public boolean isValid(Long gasLimit, ConstraintValidatorContext context) {
        if (gasLimit == null || gasLimit <= mirrorNodeEvmProperties.getMaxGasLimit()) {
            return true;
        } else {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(MAX_GAS_MESSAGE + mirrorNodeEvmProperties.getMaxGasLimit())
                    .addConstraintViolation();
            return false;
        }
    }
}
