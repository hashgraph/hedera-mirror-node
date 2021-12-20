package com.hedera.mirror.web3.controller;

/*-
 * ‌
 * Hedera Mirror Node
 * ​
 * Copyright (C) 2019 - 2021 Hedera Hashgraph, LLC
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

import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.DecodingException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import com.hedera.mirror.web3.exception.InvalidParametersException;
import com.hedera.mirror.web3.service.Web3Service;
import com.hedera.mirror.web3.service.Web3ServiceFactory;

@Slf4j
@RequestMapping("/web3/v1")
@RequiredArgsConstructor
@RestController
class Web3Controller {

    static final String INVALID_VERSION = "jsonrpc field must be " + JsonRpcResponse.VERSION;

    private final Web3ServiceFactory web3ServiceFactory;

    @PostMapping
    public Mono<JsonRpcResponse> web3(@Valid @RequestBody JsonRpcRequest request) {
        try {
            if (!request.getJsonrpc().equals(JsonRpcResponse.VERSION)) {
                return Mono.just(new JsonRpcErrorResponse(request, JsonRpcErrorCode.INVALID_REQUEST, INVALID_VERSION));
            }

            Web3Service<Object, Object> web3Service = web3ServiceFactory.lookup(request.getMethod());

            if (web3Service == null) {
                return Mono.just(new JsonRpcErrorResponse(request, JsonRpcErrorCode.METHOD_NOT_FOUND));
            }

            Object result = web3Service.get(request.getParams());

            JsonRpcSuccessResponse jsonRpcSuccessResponse = new JsonRpcSuccessResponse();
            jsonRpcSuccessResponse.setId(request.getId());
            jsonRpcSuccessResponse.setResult(result);

            return Mono.just(jsonRpcSuccessResponse);
        } catch (InvalidParametersException e) {
            return Mono.just(new JsonRpcErrorResponse(request, JsonRpcErrorCode.INVALID_PARAMS, e.getMessage()));
        } catch (Exception e) {
            return Mono.just(new JsonRpcErrorResponse(request, JsonRpcErrorCode.INTERNAL_ERROR));
        }
    }

    @ExceptionHandler
    Mono<JsonRpcResponse> parseError(DecodingException e) {
        log.warn("Parse error: {}", e.getMessage());
        return Mono.just(new JsonRpcErrorResponse(JsonRpcErrorCode.PARSE_ERROR));
    }

    @ExceptionHandler
    Mono<JsonRpcResponse> validationError(WebExchangeBindException e) {
        log.warn("Validation error: {}", e.getMessage());
        String message = e.getAllErrors()
                .stream()
                .map(this::formatError)
                .collect(Collectors.joining(", "));
        return Mono.just(new JsonRpcErrorResponse((JsonRpcRequest) e.getTarget(), JsonRpcErrorCode.INVALID_REQUEST,
                message));
    }

    private String formatError(ObjectError error) {
        if (error instanceof FieldError) {
            FieldError fieldError = (FieldError) error;
            return fieldError.getField() + " field " + fieldError.getDefaultMessage();
        }
        return error.getDefaultMessage();
    }
}
