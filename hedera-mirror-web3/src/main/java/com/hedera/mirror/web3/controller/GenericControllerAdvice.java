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

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import com.hedera.mirror.web3.exception.EntityNotFoundException;
import com.hedera.mirror.web3.exception.InvalidInputException;
import com.hedera.mirror.web3.exception.MirrorEvmTransactionException;
import com.hedera.mirror.web3.exception.RateLimitException;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse;
import com.hedera.mirror.web3.viewmodel.GenericErrorResponse.ErrorMessage;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.TypeMismatchException;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.lang.Nullable;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.WebUtils;

@ControllerAdvice
@CustomLog
@Order(Ordered.HIGHEST_PRECEDENCE)
class GenericControllerAdvice extends ResponseEntityExceptionHandler {

    @Bean
    @SuppressWarnings("java:S5122") // Make sure that enabling CORS is safe here.
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(@NotNull CorsRegistry registry) {
                registry.addMapping("/api/v1/contracts/**").allowedOrigins("*");
            }
        };
    }

    @ExceptionHandler
    private ResponseEntity<?> defaultExceptionHandler(final Exception e, WebRequest request) {
        log.error("Generic error: ", e);
        var headers = e instanceof ErrorResponse er ? er.getHeaders() : null;
        return handleExceptionInternal(e, null, headers, INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler({HttpMessageConversionException.class, IllegalArgumentException.class, InvalidInputException.class
    })
    private ResponseEntity<?> badRequest(final Exception e, final WebRequest request) {
        return handleExceptionInternal(e, null, null, BAD_REQUEST, request);
    }

    @ExceptionHandler
    private ResponseEntity<?> mirrorEvmTransactionError(final MirrorEvmTransactionException e, WebRequest request) {
        log.warn("Mirror EVM transaction error: {}, detail: {}, data: {}", e.getMessage(), e.getDetail(), e.getData());
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, e, WebRequest.SCOPE_REQUEST);
        return new ResponseEntity<>(new GenericErrorResponse(e.getMessage(), e.getDetail(), e.getData()), BAD_REQUEST);
    }

    @ExceptionHandler
    private ResponseEntity<?> notFoundException(final EntityNotFoundException e, final WebRequest request) {
        return handleExceptionInternal(e, null, null, NOT_FOUND, request);
    }

    @ExceptionHandler
    private ResponseEntity<?> queryTimeoutException(final QueryTimeoutException e, WebRequest request) {
        return handleExceptionInternal(e, null, null, SERVICE_UNAVAILABLE, request);
    }

    @ExceptionHandler
    private ResponseEntity<?> rateLimitException(final RateLimitException e, final WebRequest request) {
        return handleExceptionInternal(e, null, null, TOO_MANY_REQUESTS, request);
    }

    @Nullable
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var messages = ex.getAllErrors().stream().map(this::formatErrorMessage).toList();
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex, WebRequest.SCOPE_REQUEST);
        return new ResponseEntity<>(new GenericErrorResponse(messages), headers, status);
    }

    @Nullable
    protected ResponseEntity<Object> handleTypeMismatch(
            TypeMismatchException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        final var cause = ex.getRootCause() instanceof Exception rc ? rc : ex;
        return handleExceptionInternal(cause, null, headers, status, request);
    }

    @Nullable
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        var message = statusCode instanceof HttpStatus hs ? hs.getReasonPhrase() : statusCode.toString();
        var detail = !statusCode.is5xxServerError() ? ex.getMessage() : StringUtils.EMPTY; // Don't leak server errors
        var genericErrorResponse = new GenericErrorResponse(message, detail, StringUtils.EMPTY);
        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex, WebRequest.SCOPE_REQUEST);
        return new ResponseEntity<>(genericErrorResponse, headers, statusCode);
    }

    private ErrorMessage formatErrorMessage(ObjectError error) {
        var detail = error.getDefaultMessage();

        if (error instanceof FieldError fieldError) {
            detail = fieldError.getField() + " field " + fieldError.getDefaultMessage();
        }

        return new ErrorMessage(BAD_REQUEST.getReasonPhrase(), detail, StringUtils.EMPTY);
    }
}
