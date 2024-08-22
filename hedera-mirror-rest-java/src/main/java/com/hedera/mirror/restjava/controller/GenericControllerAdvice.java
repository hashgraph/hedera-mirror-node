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
import static org.springframework.web.context.request.RequestAttributes.SCOPE_REQUEST;

import com.hedera.mirror.common.exception.InvalidEntityException;
import com.hedera.mirror.rest.model.Error;
import com.hedera.mirror.rest.model.ErrorStatus;
import com.hedera.mirror.rest.model.ErrorStatusMessagesInner;
import com.hedera.mirror.restjava.RestJavaProperties;
import jakarta.persistence.EntityNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.List;
import java.util.Locale;
import lombok.CustomLog;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.lang.Nullable;
import org.springframework.validation.Errors;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.util.WebUtils;

@ControllerAdvice
@CustomLog
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
class GenericControllerAdvice extends ResponseEntityExceptionHandler {

    private final MessageSource messageSource = new ErrorMessageSource();
    private final RestJavaProperties properties;

    @ModelAttribute
    private void responseHeaders(HttpServletRequest request, HttpServletResponse response) {
        var requestMapping = String.valueOf(request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE));
        var responseHeaders = properties.getResponse().getHeaders().getHeadersForPath(requestMapping);
        responseHeaders.forEach(response::setHeader);
    }

    @ExceptionHandler({
        HttpMessageConversionException.class,
        IllegalArgumentException.class,
        InvalidEntityException.class
    })
    private ResponseEntity<Object> badRequest(final Exception e, final WebRequest request) {
        return handleExceptionInternal(e, null, null, BAD_REQUEST, request);
    }

    @ExceptionHandler
    private ResponseEntity<Object> defaultExceptionHandler(final Exception e, final WebRequest request) {
        log.error("Generic error: ", e);
        var headers = e instanceof ErrorResponse er ? er.getHeaders() : null;
        return handleExceptionInternal(e, null, headers, INTERNAL_SERVER_ERROR, request);
    }

    @ExceptionHandler
    private ResponseEntity<Object> notFound(final EntityNotFoundException e, final WebRequest request) {
        return handleExceptionInternal(e, null, null, NOT_FOUND, request);
    }

    @ExceptionHandler
    private ResponseEntity<Object> queryTimeout(final QueryTimeoutException e, final WebRequest request) {
        log.error("Query timed out: {}", e.getMessage());
        return handleExceptionInternal(e, null, null, SERVICE_UNAVAILABLE, request);
    }

    @Override
    protected ResponseEntity<Object> handleMissingPathVariable(
            MissingPathVariableException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (ex.isMissingAfterConversion()) {
            var detail = "Invalid value for path variable '" + ex.getVariableName() + "'";
            var problem = ProblemDetail.forStatusAndDetail(status, detail);
            return handleExceptionInternal(ex, problem, ex.getHeaders(), BAD_REQUEST, request);
        }
        return handleExceptionInternal(ex, ex.getBody(), ex.getHeaders(), BAD_REQUEST, request);
    }

    @Nullable
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {
        Error errorResponse = null;

        if (ex instanceof Errors errors) {
            errorResponse = errorResponse(errors.getAllErrors());
        } else if (ex instanceof MethodValidationResult errors) {
            errorResponse = errorResponse(errors.getAllErrors());
        } else {
            var message = statusCode instanceof HttpStatus hs ? hs.getReasonPhrase() : statusCode.toString();
            var detail = body instanceof ProblemDetail pb ? pb.getDetail() : ex.getMessage();
            var nonSensitiveDetail = !statusCode.is5xxServerError() ? detail : StringUtils.EMPTY;
            errorResponse = errorResponse(message, nonSensitiveDetail);
        }

        request.setAttribute(WebUtils.ERROR_EXCEPTION_ATTRIBUTE, ex, SCOPE_REQUEST);
        return new ResponseEntity<>(errorResponse, headers, statusCode);
    }

    private Error errorResponse(List<? extends MessageSourceResolvable> errors) {
        var messages = errors.stream().map(this::formatErrorMessage).toList();
        var errorStatus = new ErrorStatus().messages(messages);
        return new Error().status(errorStatus);
    }

    private Error errorResponse(final String message, final String detail) {
        var errorMessage = new ErrorStatusMessagesInner();
        errorMessage.setDetail(detail);
        errorMessage.setMessage(message);
        var errorStatus = new ErrorStatus().addMessagesItem(errorMessage);
        var error = new Error().status(errorStatus);
        return error;
    }

    private ErrorStatusMessagesInner formatErrorMessage(MessageSourceResolvable error) {
        var detail = error.getDefaultMessage();

        if (error instanceof FieldError fieldError) {
            detail = fieldError.getField() + " field " + fieldError.getDefaultMessage();
        } else if (error instanceof DefaultMessageSourceResolvable resolvable && !(error instanceof ObjectError)) {
            detail = messageSource.getMessage(resolvable, Locale.getDefault());
        }

        return new ErrorStatusMessagesInner()
                .message(BAD_REQUEST.getReasonPhrase())
                .detail(detail);
    }

    private static class ErrorMessageSource extends StaticMessageSource {

        ErrorMessageSource() {
            setUseCodeAsDefaultMessage(true);
        }

        @Override
        @Nullable
        protected String getDefaultMessage(MessageSourceResolvable resolvable, Locale locale) {
            var message = super.getDefaultMessage(resolvable, locale);
            var field = getField(resolvable);
            if (StringUtils.isNotBlank(field)) {
                return field + " " + message;
            }
            return message;
        }

        private String getField(MessageSourceResolvable resolvable) {
            if (resolvable instanceof FieldError error) {
                return error.getField();
            }

            var arguments = resolvable.getArguments();
            if (arguments != null && arguments.length > 0 && arguments[0] instanceof MessageSourceResolvable msr) {
                return msr.getDefaultMessage();
            }

            return StringUtils.EMPTY;
        }
    }
}
