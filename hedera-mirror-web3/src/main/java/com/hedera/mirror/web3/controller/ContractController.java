package com.hedera.mirror.web3.controller;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2023 Hedera Hashgraph, LLC
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

import static com.hedera.mirror.web3.controller.ValidationErrorParser.extractValidationError;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_CALL;
import static com.hedera.mirror.web3.service.model.CallServiceParameters.CallType.ETH_ESTIMATE_GAS;
import static org.apache.tuweni.bytes.Bytes.EMPTY;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;

import io.github.bucket4j.Bucket;
import javax.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.server.UnsupportedMediaTypeStatusException;
import reactor.core.publisher.Mono;

import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.mirror.web3.exception.InvalidTransactionException;
import com.hedera.mirror.web3.exception.RateLimitException;
import com.hedera.mirror.web3.service.ContractCallService;
import com.hedera.mirror.web3.service.model.CallServiceParameters;
import com.hedera.mirror.web3.viewmodel.ContractCallRequest;
import com.hedera.mirror.web3.viewmodel.ContractCallResponse;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;
import com.hedera.node.app.service.evm.store.models.HederaEvmAccount;

@CustomLog
@RequestMapping("/api/v1/contracts")
@RequiredArgsConstructor
@RestController
class ContractController {

    static final String NOT_IMPLEMENTED_ERROR = "Operation not supported yet!";
    private final ContractCallService contractCallService;
    private final Bucket bucket;

    @PostMapping(value = "/call")
    Mono<ContractCallResponse> call(@RequestBody @Valid ContractCallRequest request) {
        if (request.isEstimate()) {
            throw new UnsupportedOperationException(NOT_IMPLEMENTED_ERROR);
        }

        if (!bucket.tryConsume(1)) {
            throw new RateLimitException("Rate limit exceeded.");
        }
        final var params = constructServiceParameters(request);

        final var callResponse =
                new ContractCallResponse(
                        contractCallService.processCall(params));

        return Mono.just(callResponse);
}

    private CallServiceParameters constructServiceParameters(ContractCallRequest request) {
        final var fromAddress =
                request.getFrom() != null
                        ? Address.fromHexString(request.getFrom())
                        : Address.ZERO;
        final var sender = new HederaEvmAccount(fromAddress);

        final var receiver = Address.fromHexString(request.getTo());
        final var data =
                request.getData() != null
                        ? Bytes.fromHexString(request.getData())
                        : EMPTY;
        final var isStaticCall = !request.isEstimate();
        final var callType = request.isEstimate() ? ETH_ESTIMATE_GAS : ETH_CALL;

        return CallServiceParameters.builder()
                .sender(sender)
                .receiver(receiver)
                .callData(data)
                .providedGasLimit(request.getGas())
                .value(request.getValue())
                .isStatic(isStaticCall)
                .callType(callType)
                .build();
    }

    //This is temporary method till estimate_gas business logic got impl.
    @ExceptionHandler
    @ResponseStatus(NOT_IMPLEMENTED)
    private Mono<GenericErrorResponse> unsupportedOpResponse(final UnsupportedOperationException e) {
        return errorResponse(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(TOO_MANY_REQUESTS)
    private Mono<GenericErrorResponse> rateLimitError(final RateLimitException e) {
        return errorResponse(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private Mono<GenericErrorResponse> validationError(final WebExchangeBindException e) {
        final var errors = extractValidationError(e);
        log.warn("Validation error: {}", errors);
        return Mono.just(new GenericErrorResponse(errors));
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private Mono<GenericErrorResponse> addressValidationError(final InvalidParametersException e) {
        log.warn("Address validation error");
        return Mono.just(new GenericErrorResponse(e.getMessage()));
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private Mono<GenericErrorResponse> invalidTxnError(final InvalidTransactionException e) {
        log.warn("Transaction error: {}", e.getMessage());
        return errorResponse(e.getMessage(), e.getDetail(), e.getData());
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private Mono<GenericErrorResponse> invalidTxnBodyError(final ServerWebInputException e) {
        log.warn("Transaction body parsing error: {}", e.getMessage());
        return errorResponse(e.getReason(), e.getMostSpecificCause().getMessage(), StringUtils.EMPTY);
    }

    @ExceptionHandler
    @ResponseStatus(UNSUPPORTED_MEDIA_TYPE)
    private Mono<GenericErrorResponse> unsupportedMediaTypeError(final UnsupportedMediaTypeStatusException e) {
        log.warn("Unsupported media type error: {}", e.getMessage());
        return errorResponse(e.getStatus().getReasonPhrase(), e.getReason(), StringUtils.EMPTY);
    }

    @ExceptionHandler()
    @ResponseStatus(INTERNAL_SERVER_ERROR)
    private Mono<GenericErrorResponse> genericError(final Exception e) {
        log.error("Generic error: ", e);
        return errorResponse(INTERNAL_SERVER_ERROR.getReasonPhrase());
    }

    private Mono<GenericErrorResponse> errorResponse(final String errorMessage) {
        return Mono.just(new GenericErrorResponse(errorMessage));
    }

    private Mono<GenericErrorResponse> errorResponse(final String errorMessage, final String detailedErrorMessage, final String hexErrorMessage) {
        return Mono.just(new GenericErrorResponse(errorMessage, detailedErrorMessage, hexErrorMessage));
    }
}
