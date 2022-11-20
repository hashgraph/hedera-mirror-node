package com.hedera.mirror.web3.config.validation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

@Constraint(validatedBy = AddressValidator.class)
@Target({ElementType.FIELD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Address {
    String message() default "must be 20 bytes hex format";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
