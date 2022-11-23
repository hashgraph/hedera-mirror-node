package com.hedera.mirror.web3.controller;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.mirror.web3.controller.ValidationErrorParser.parseValidationError;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;

import javax.validation.Valid;
import lombok.CustomLog;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import com.hedera.mirror.web3.viewmodel.ContractCallRequest;
import com.hedera.mirror.web3.viewmodel.ContractCallResponse;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;

@CustomLog
@RequestMapping("/api/v1/contracts")
@RestController
class ContractController {

    static final String NOT_IMPLEMENTED_ERROR = "Operation not supported yet!";

    @PostMapping(value = "/call")
    Mono<ContractCallResponse> call(@RequestBody @Valid ContractCallRequest request) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED_ERROR);
    }

    //This is temporary method till eth_call and gas_estimate business logic got impl.
    @ExceptionHandler
    @ResponseStatus(NOT_IMPLEMENTED)
    private Mono<GenericErrorResponse> unsupportedOpResponse(UnsupportedOperationException e) {
        return errorResponse(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private Mono<GenericErrorResponse> validationError(WebExchangeBindException e) {
        return errorResponse(parseValidationError(e));
    }

    private Mono<GenericErrorResponse> errorResponse(String errorMessage) {
        return Mono.just(new GenericErrorResponse(errorMessage));
    }
}
