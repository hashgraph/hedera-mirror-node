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
import java.util.List;
import java.util.Optional;
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ServerWebInputException;

@ControllerAdvice
@CustomLog
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ControllerExceptionHandler {


    /**
     * Temporary handler, intended for dealing with forthcoming features that are not yet available, such as the absence
     * of a precompile for gas estimation.
     **/
    @ExceptionHandler
    @ResponseStatus(NOT_IMPLEMENTED)
    private ResponseEntity<?> unsupportedOpResponse(final UnsupportedOperationException e) {
        return new ResponseEntity<>(errorResponse(e.getMessage()), NOT_IMPLEMENTED);
    }

    @ExceptionHandler
    @ResponseStatus(TOO_MANY_REQUESTS)
    private ResponseEntity<?> rateLimitError(final RateLimitException e) {
        return new ResponseEntity<>(errorResponse(e.getMessage()), TOO_MANY_REQUESTS);
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private ResponseEntity<?> validationError(final BindException e) {
        final var errors = extractValidationError(e);
        log.warn("Validation error: {}", errors);
        return new ResponseEntity<>(errorResponse(errors), BAD_REQUEST);
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private ResponseEntity<?> inputValidationError(final InvalidInputException e) {
        log.warn("Input validation error: {}", e.getMessage());
        return new ResponseEntity<>(errorResponse(e.getMessage()), BAD_REQUEST);
    }

    @ExceptionHandler({
            MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class
    })
    @ResponseStatus(BAD_REQUEST)
    private ResponseEntity<?> invalidArgumentError(final Exception e) {
        if (e instanceof MethodArgumentTypeMismatchException mismatchException) {
            final var message = Optional.ofNullable(mismatchException.getRootCause())
                    .orElse(mismatchException)
                    .getMessage();
            log.warn("Invalid argument error: {}", message);
            return new ResponseEntity<>(errorResponse(message), BAD_REQUEST);
        }
        log.warn("Invalid argument error: {}", e.getMessage());
        return new ResponseEntity<>(errorResponse(e.getMessage()), BAD_REQUEST);
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private ResponseEntity<?> mirrorEvmTransactionException(final MirrorEvmTransactionException e) {
        log.warn("Mirror EVM transaction error: {}", e.getMessage());
        return new ResponseEntity<>(errorResponse(e.getMessage(), e.getDetail(), e.getData()), BAD_REQUEST);
    }

    @ExceptionHandler({
            ServerWebInputException.class,
            HttpMessageConversionException.class
    })
    @ResponseStatus(BAD_REQUEST)
    private ResponseEntity<?> invalidJson(final Exception e) {
        log.warn("Transaction body parsing error: {}", e.getMessage());
        if (e instanceof ServerWebInputException webInputException) {
            return new ResponseEntity<>(
                    errorResponse(webInputException.getReason(), webInputException.getMessage(), StringUtils.EMPTY),
                    BAD_REQUEST
            );
        }
        return new ResponseEntity<>(
                errorResponse("Unable to parse JSON", e.getMessage(), StringUtils.EMPTY),
                BAD_REQUEST
        );
    }

    @ExceptionHandler
    @ResponseStatus(NOT_FOUND)
    private ResponseEntity<?> notFound(final EntityNotFoundException e) {
        log.warn("Not found: {}", e.getMessage());
        return new ResponseEntity<>(errorResponse(e.getMessage()), NOT_FOUND);
    }

    @ExceptionHandler
    @ResponseStatus(UNSUPPORTED_MEDIA_TYPE)
    private ResponseEntity<?> unsupportedMediaTypeError(final HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported media type error: {}", e.getMessage());
        return new ResponseEntity<>(
                errorResponse(UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(), e.getMessage(), StringUtils.EMPTY),
                UNSUPPORTED_MEDIA_TYPE
        );
    }

    @ExceptionHandler
    @ResponseStatus(INTERNAL_SERVER_ERROR)
    private ResponseEntity<?> genericError(final Exception e) {
        log.error("Generic error: ", e);
        return new ResponseEntity<>(
                errorResponse(INTERNAL_SERVER_ERROR.getReasonPhrase()),
                INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler
    @ResponseStatus(SERVICE_UNAVAILABLE)
    private ResponseEntity<?> queryTimeout(final QueryTimeoutException e) {
        log.error("Query timed out: {}", e.getMessage());
        return new ResponseEntity<>(
                errorResponse(SERVICE_UNAVAILABLE.getReasonPhrase()),
                SERVICE_UNAVAILABLE
        );
    }

    private GenericErrorResponse errorResponse(final List<String> errors) {
        return new GenericErrorResponse(errors);
    }

    private GenericErrorResponse errorResponse(final String errorMessage) {
        return new GenericErrorResponse(errorMessage);
    }

    private GenericErrorResponse errorResponse(
            final String errorMessage, final String detailedErrorMessage, final String hexErrorMessage) {
        return new GenericErrorResponse(errorMessage, detailedErrorMessage, hexErrorMessage);
    }
}
