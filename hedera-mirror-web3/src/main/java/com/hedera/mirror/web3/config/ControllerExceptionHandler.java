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

package com.hedera.mirror.web3.config;

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
import lombok.CustomLog;
import org.apache.commons.lang3.StringUtils;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ServerWebInputException;

@CustomLog
@ControllerAdvice
public class ControllerExceptionHandler {

    /**
     * Temporary handler, intended for dealing with forthcoming features that are not yet available, such as the absence
     * of a precompile for gas estimation.
     **/
    @ExceptionHandler
    @ResponseStatus(NOT_IMPLEMENTED)
    private GenericErrorResponse unsupportedOpResponse(final UnsupportedOperationException e) {
        return errorResponse(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(TOO_MANY_REQUESTS)
    private GenericErrorResponse rateLimitError(final RateLimitException e) {
        return errorResponse(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private GenericErrorResponse validationError(final BindException e) {
        final var errors = extractValidationError(e);
        log.warn("Validation error: {}", errors);
        return new GenericErrorResponse(errors);
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private GenericErrorResponse inputValidationError(final InvalidInputException e) {
        log.warn("Input validation error: {}", e.getMessage());
        return new GenericErrorResponse(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private GenericErrorResponse invalidArgumentError(final IllegalArgumentException e) {
        log.warn("Invalid argument error: {}", e.getMessage());
        return errorResponse(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private GenericErrorResponse mirrorEvmTransactionException(final MirrorEvmTransactionException e) {
        log.warn("Mirror EVM transaction error: {}", e.getMessage());
        return errorResponse(e.getMessage(), e.getDetail(), e.getData());
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private GenericErrorResponse invalidJson(final ServerWebInputException e) {
        log.warn("Transaction body parsing error: {}", e.getMessage());
        return errorResponse(e.getReason(), "Unable to parse JSON", StringUtils.EMPTY);
    }

    @ExceptionHandler
    @ResponseStatus(BAD_REQUEST)
    private GenericErrorResponse invalidJson(final HttpMessageConversionException e) {
        log.warn("Transaction body parsing error: {}", e.getMessage());
        return errorResponse("Unable to parse JSON", e.getMessage(), StringUtils.EMPTY);
    }

    @ExceptionHandler
    @ResponseStatus(NOT_FOUND)
    private GenericErrorResponse notFound(final EntityNotFoundException e) {
        log.warn("Not found: {}", e.getMessage());
        return errorResponse(e.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(UNSUPPORTED_MEDIA_TYPE)
    private GenericErrorResponse unsupportedMediaTypeError(final HttpMediaTypeNotSupportedException e) {
        log.warn("Unsupported media type error: {}", e.getMessage());
        return errorResponse(UNSUPPORTED_MEDIA_TYPE.getReasonPhrase(), e.getMessage(), StringUtils.EMPTY);
    }

    @ExceptionHandler
    @ResponseStatus(INTERNAL_SERVER_ERROR)
    private GenericErrorResponse genericError(final Exception e) {
        log.error("Generic error: ", e);
        return errorResponse(INTERNAL_SERVER_ERROR.getReasonPhrase());
    }

    @ExceptionHandler
    @ResponseStatus(SERVICE_UNAVAILABLE)
    private GenericErrorResponse queryTimeout(final QueryTimeoutException e) {
        log.error("Query timed out: {}", e.getMessage());
        return errorResponse(SERVICE_UNAVAILABLE.getReasonPhrase());
    }

    private GenericErrorResponse errorResponse(final String errorMessage) {
        return new GenericErrorResponse(errorMessage);
    }

    private GenericErrorResponse errorResponse(
            final String errorMessage, final String detailedErrorMessage, final String hexErrorMessage) {
        return new GenericErrorResponse(errorMessage, detailedErrorMessage, hexErrorMessage);
    }
}
