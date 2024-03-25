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
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import com.hedera.mirror.rest.model.Error;
import com.hedera.mirror.rest.model.ErrorStatus;
import com.hedera.mirror.rest.model.ErrorStatusMessagesInner;
import com.hedera.mirror.restjava.exception.InvalidParametersException;
import jakarta.persistence.EntityNotFoundException;
import java.net.BindException;
import lombok.CustomLog;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@CustomLog
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExceptionControllerAdvice extends ResponseEntityExceptionHandler {

    @ExceptionHandler()
    @ResponseStatus(NOT_FOUND)
    private Error notFound(final EntityNotFoundException e) {
        log.warn("Not found: {}", e.getMessage());
        return errorResponse(e.getMessage());
    }

    @ExceptionHandler(value = InvalidParametersException.class)
    @ResponseStatus(BAD_REQUEST)
    private Error inputValidationError(final InvalidParametersException e) {
        return errorResponse(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private Error inputValidationError(final IllegalArgumentException e) {
        return errorResponse(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private Error bindError(final BindException e) {
        log.warn("Validation error: {}", e.getMessage());
        return errorResponse(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(SERVICE_UNAVAILABLE)
    private Error queryTimeout(final QueryTimeoutException e) {
        log.error("Query timed out: {}", e.getMessage());
        return errorResponse(SERVICE_UNAVAILABLE.getReasonPhrase());
    }

    private Error errorResponse(final String e) {
        var errorMessage = new ErrorStatusMessagesInner();
        errorMessage.setMessage(e);
        var errorStatus = new ErrorStatus().addMessagesItem(errorMessage);
        var error = new Error();
        return error.status(errorStatus);
    }
}
