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

package com.hedera.mirror.web3.validation;

import java.util.regex.Pattern;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class HexValidator implements ConstraintValidator<Hex, String> {

    public static final String MESSAGE = "invalid hexadecimal string";
    private static final Pattern HEX_PATTERN = Pattern.compile("^(0x)?[0-9a-fA-F]+$");
    private static final String HEX_PREFIX = "0x";

    private long maxLength;
    private long minLength;
    private boolean allowEmpty;

    @Override
    public void initialize(Hex hex) {
        maxLength = hex.maxLength();
        minLength = hex.minLength();
        allowEmpty = hex.allowEmpty();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || ((minLength == 0 || allowEmpty) && value.length() == 0)) {
            return true;
        }

        if (!HEX_PATTERN.matcher(value).matches()) {
            return false;
        }

        int prefixLength = value.startsWith(HEX_PREFIX) ? HEX_PREFIX.length() : 0;
        int length = value.length() - prefixLength;
        return length >= minLength && length <= maxLength;
    }
}
