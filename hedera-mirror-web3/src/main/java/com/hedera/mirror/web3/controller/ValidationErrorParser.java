package com.hedera.mirror.web3.controller;

import java.util.List;
import java.util.stream.Collectors;
import lombok.experimental.UtilityClass;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.support.WebExchangeBindException;

@UtilityClass
public class ValidationErrorParser {

    public static String parseValidationError(WebExchangeBindException e) {
        return e.getAllErrors()
                .stream()
                .map(ValidationErrorParser::formatError)
                .collect(Collectors.joining(", "));
    }

    public static List<String> extractValidationError(WebExchangeBindException e) {
        return e.getAllErrors()
                .stream()
                .map(ValidationErrorParser::formatError)
                .toList();
    }

    private static String formatError(ObjectError error) {
        if (error instanceof FieldError fieldError) {
            return fieldError.getField() + " field " + fieldError.getDefaultMessage();
        }
        return error.getDefaultMessage();
    }
}
