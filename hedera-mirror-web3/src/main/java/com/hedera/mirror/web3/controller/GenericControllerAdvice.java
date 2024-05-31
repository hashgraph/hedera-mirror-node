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

package com.hedera.mirror.web3.controller;

import static com.hedera.mirror.web3.controller.ValidationErrorParser.extractValidationError;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NOT_IMPLEMENTED;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNSUPPORTED_MEDIA_TYPE;

import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.exception.InvalidInputException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.exception.RateLimitException;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;
import java.util.Optional;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@ControllerAdvice
@CustomLog
@Order(Ordered.HIGHEST_PRECEDENCE)
class GenericControllerAdvice {

    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NotNull CorsRegistry registry) {
                registry.addMapping("/api/v1/contracts/**").allowedOrigins("*");
            }
        };
    }

    /**
     * Temporary handler, intended for dealing with forthcoming features that are not yet available, such as the absence
     * of a precompile for gas estimation.
     **/
    @ExceptionHandler
    private ResponseEntity<?> unsupportedOpResponse(final UnsupportedOperationException e) {
        return new ResponseEntity<>(new GenericErrorResponse(e.getMessage()), NOT_IMPLEMENTED);
    }

    @ExceptionHandler
    private ResponseEntity<?> rateLimitError(final RateLimitException e) {
        return new ResponseEntity<>(new GenericErrorResponse(e.getMessage()), TOO_MANY_REQUESTS);
    }

    @ExceptionHandler
    private ResponseEntity<?> validationError(final BindException e) {
        final var errors = extractValidationError(e);
        log.warn("Validation error: {}", errors);
        return new ResponseEntity<>(new GenericErrorResponse(errors), BAD_REQUEST);
    }

    @ExceptionHandler
    private ResponseEntity<?> invalidInputError(final InvalidInputException e) {
        log.warn("Input validation error: {}", e.getMessage());
        return new ResponseEntity<>(new GenericErrorResponse(e.getMessage()), BAD_REQUEST);
    }

    @ExceptionHandler
    private ResponseEntity<?> illegalArgumentError(final IllegalArgumentException e) {
        log.warn("Invalid argument error: {}", e.getMessage());
        return new ResponseEntity<>(new GenericErrorResponse(e.getMessage()), BAD_REQUEST);
    }

    @ExceptionHandler
    private ResponseEntity<?> typeMismatchError(final TypeMismatchException e) {
        final var message = Optional.ofNullable(e.getRootCause()).orElse(e).getMessage();
        log.warn("Type mismatch error: {}", message);
        return new ResponseEntity<>(new GenericErrorResponse(message), BAD_REQUEST);
    }

    @ExceptionHandler
    private ResponseEntity<?> mirrorEvmTransactionError(final MirrorEvmTransactionException e) {
        log.warn("Mirror EVM transaction error: {}", e.getMessage());
        return new ResponseEntity<>(new GenericErrorResponse(e.getMessage(), e.getDetail(), e.getData()), BAD_REQUEST);
    }

    @ExceptionHandler
    private ResponseEntity<?> httpMessageConversionError(final HttpMessageConversionException e) {
        log.warn("Transaction body parsing error: {}", e.getMessage());
        return new ResponseEntity<>(
                new GenericErrorResponse("Unable to parse JSON", e.getMessage(), StringUtils.EMPTY),
                BAD_REQUEST
        );
    }

    @ExceptionHandler
    private ResponseEntity<?> serverWebInputError(final ServerWebInputException e) {
        log.warn("Transaction body parsing error: {}", e.getMessage());
        return new ResponseEntity<>(
                new GenericErrorResponse(e.getReason(), e.getMessage(), StringUtils.EMPTY),
                BAD_REQUEST
        );
    }

    @ExceptionHandler
    private ResponseEntity<?> notFoundError(final EntityNotFoundException e) {
        log.warn("Not found: {}", e.getMessage());
        return new ResponseEntity<>(new GenericErrorResponse(e.getMessage()), NOT_FOUND);
    }

    @ExceptionHandler
    private ResponseEntity<?> unsupportedMediaTypeError(final HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported media type error: {}", e.getMessage());
        return new ResponseEntity<>(
                new GenericErrorResponse(UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(), e.getMessage(), StringUtils.EMPTY),
                UNSUPPORTED_MEDIA_TYPE
        );
    }

    @ExceptionHandler
    private ResponseEntity<?> queryTimeoutError(final QueryTimeoutException e) {
        log.error("Query timed out: {}", e.getMessage());
        return new ResponseEntity<>(
                new GenericErrorResponse(SERVICE_UNAVAILABLE.getReasonPhrase()),
                SERVICE_UNAVAILABLE
        );
    }

    @ExceptionHandler
    private ResponseEntity<?> genericError(final Exception e) {
        log.error("Generic error: ", e);
        return new ResponseEntity<>(
                new GenericErrorResponse(INTERNAL_SERVER_ERROR.getReasonPhrase()),
                INTERNAL_SERVER_ERROR
        );
    }
}
