package com.hedera.mirror.web3.config.validation;

import java.util.regex.Pattern;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class AddressValidator implements ConstraintValidator<Address, String> {

    @Override
    public void initialize(Address constraintAnnotation) {
        // Nothing needs to be done with Address upon initialize
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if(value == null){
            return true;
        }
        final var pattern = Pattern.compile("^0[xX][0-9a-fA-F]{40}$");
        return pattern.matcher(value).matches();
    }
}
