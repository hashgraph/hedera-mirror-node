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

package com.hedera.mirror.restjava.controller;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.rest.model.Error;
import com.hedera.mirror.rest.model.ErrorStatus;
import com.hedera.mirror.rest.model.ErrorStatusMessagesInner;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletResponse;
import lombok.CustomLog;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@ControllerAdvice
@CustomLog
@Order(Ordered.HIGHEST_PRECEDENCE)
class GenericControllerAdvice extends ResponseEntityExceptionHandler {

    @ModelAttribute
    @SuppressWarnings("java:S5122")
    private void corsHeader(HttpServletResponse response) {
        response.setHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    }

    @ExceptionHandler
    private ResponseEntity<Error> notFound(final EntityNotFoundException e) {
        return errorResponse(e.getMessage(), NOT_FOUND);
    }

    @ExceptionHandler
    private ResponseEntity<Error> inputValidationError(final InvalidEntityException e) {
        return errorResponse(e.getMessage(), BAD_REQUEST);
    }

    @ExceptionHandler
    private ResponseEntity<Error> inputValidationError(final IllegalArgumentException e) {
        return errorResponse(e.getMessage(), BAD_REQUEST);
    }

    @ExceptionHandler
    private ResponseEntity<Error> bindError(final BindException e) {
        return errorResponse(e.getMessage(), BAD_REQUEST);
    }

    @ExceptionHandler
    private ResponseEntity<Error> queryTimeout(final QueryTimeoutException e) {
        log.error("Query timed out: {}", e.getMessage());
        return errorResponse(SERVICE_UNAVAILABLE.getReasonPhrase(), SERVICE_UNAVAILABLE);
    }

    @ExceptionHandler
    private ResponseEntity<Error> genericError(final Exception e) {
        log.error("Generic error: ", e);
        return errorResponse(SERVICE_UNAVAILABLE.getReasonPhrase(), INTERNAL_SERVER_ERROR);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected @NotNull ResponseEntity<Object> createResponseEntity(
            Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        var message = statusCode instanceof HttpStatus hs ? hs.getReasonPhrase() : statusCode.toString();
        ResponseEntity<?> responseEntity = errorResponse(message, statusCode);
        return (ResponseEntity<Object>) responseEntity;
    }

    private ResponseEntity<Error> errorResponse(final String e, HttpStatusCode statusCode) {
        var errorMessage = new ErrorStatusMessagesInner();
        errorMessage.setMessage(e);
        var errorStatus = new ErrorStatus().addMessagesItem(errorMessage);
        var error = new Error().status(errorStatus);
        return new ResponseEntity<>(error, statusCode);
    }
}
