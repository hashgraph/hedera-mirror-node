/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.mirror.web3.controller;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.support.WebExchangeBindException;

@UtilityClass
public class ValidationErrorParser {

    public static String parseValidationError(WebExchangeBindException e) {
        return e.getAllErrors().stream().map(ValidationErrorParser::formatError).collect(Collectors.joining(", "));
    }

    public static List<String> extractValidationError(WebExchangeBindException e) {
        return e.getAllErrors().stream().map(ValidationErrorParser::formatError).toList();
    }

    private static String formatError(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField() + " field " + fieldError.getDefaultMessage();
        }
        return error.getDefaultMessage();
    }
}
