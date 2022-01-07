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

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.validation.Valid;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import lombok.Value;
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

@CustomLog
@RequestMapping("/web3/v1")
@RequiredArgsConstructor
@RestController
class Web3Controller {

    static final String INVALID_VERSION = "jsonrpc field must be " + JsonRpcResponse.VERSION;
    static final String METRIC = "hedera.mirror.web3.requests";

    final Map<Tags, Timer> timers = new ConcurrentHashMap<>();

    private final MeterRegistry meterRegistry;
    private final Web3ServiceFactory web3ServiceFactory;

    @PostMapping
    public Mono<JsonRpcResponse> web3(@Valid @RequestBody JsonRpcRequest<?> request) {
        try {
            if (!request.getJsonrpc().equals(JsonRpcResponse.VERSION)) {
                return response(request, new JsonRpcErrorResponse(JsonRpcErrorCode.INVALID_REQUEST, INVALID_VERSION));
            }

            Web3Service<Object, Object> web3Service = web3ServiceFactory.lookup(request.getMethod());

            if (web3Service == null) {
                return response(request, new JsonRpcErrorResponse(JsonRpcErrorCode.METHOD_NOT_FOUND));
            }

            Object result = web3Service.get(request.getParams());

            JsonRpcSuccessResponse jsonRpcSuccessResponse = new JsonRpcSuccessResponse();
            jsonRpcSuccessResponse.setId(request.getId());
            jsonRpcSuccessResponse.setResult(result);

            return response(request, jsonRpcSuccessResponse);
        } catch (InvalidParametersException e) {
            return response(request, new JsonRpcErrorResponse(JsonRpcErrorCode.INVALID_PARAMS, e.getMessage()));
        } catch (Exception e) {
            return response(request, new JsonRpcErrorResponse(JsonRpcErrorCode.INTERNAL_ERROR));
        }
    }

    private Mono<JsonRpcResponse> response(JsonRpcRequest<?> request, JsonRpcResponse response) {
        if (request.getId() != null && request.getId() >= 0) {
            response.setId(request.getId());
        }

        // Ensure bad user input doesn't cause a cardinality explosion
        String method = request.getMethod();
        if (response instanceof JsonRpcErrorResponse && !web3ServiceFactory.isValid(method)) {
            method = Tags.UNKNOWN_METHOD;
        }

        Tags tags = new Tags(method, response.getStatus());
        long time = System.nanoTime() - request.getStartTime();
        Timer timer = timers.computeIfAbsent(tags, this::newTimer);
        timer.record(time, TimeUnit.NANOSECONDS);

        return Mono.just(response);
    }

    private Mono<JsonRpcResponse> response(JsonRpcResponse response) {
        Tags tags = new Tags(Tags.UNKNOWN_METHOD, response.getStatus());
        Timer timer = timers.computeIfAbsent(tags, this::newTimer);
        timer.record(0, TimeUnit.NANOSECONDS); // We can't calculate an accurate start time
        return Mono.just(response);
    }

    private Timer newTimer(Tags tags) {
        return Timer.builder(METRIC)
                .description("The time it takes to process a web3 request")
                .tag("method", tags.getMethod())
                .tag("status", tags.getStatus())
                .register(meterRegistry);
    }

    @ExceptionHandler
    Mono<JsonRpcResponse> parseError(DecodingException e) {
        log.warn("Parse error: {}", e.getMessage());
        return response(new JsonRpcErrorResponse(JsonRpcErrorCode.PARSE_ERROR));
    }

    @ExceptionHandler
    Mono<JsonRpcResponse> validationError(WebExchangeBindException e) {
        log.warn("Validation error: {}", e.getMessage());
        String message = e.getAllErrors()
                .stream()
                .map(this::formatError)
                .collect(Collectors.joining(", "));
        var errorResponse = new JsonRpcErrorResponse(JsonRpcErrorCode.INVALID_REQUEST, message);
        var target = e.getTarget();

        if (target instanceof JsonRpcRequest) {
            return response((JsonRpcRequest<?>) target, errorResponse);
        } else {
            return response(errorResponse);
        }
    }

    private String formatError(ObjectError error) {
        if (error instanceof FieldError) {
            FieldError fieldError = (FieldError) error;
            return fieldError.getField() + " field " + fieldError.getDefaultMessage();
        }
        return error.getDefaultMessage();
    }

    @Value
    private class Tags {
        private static final String UNKNOWN_METHOD = "unknown";

        private final String method;
        private final String status;
    }
}
