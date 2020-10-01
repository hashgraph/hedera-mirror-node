package com.hedera.mirror.importer.config.condition;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class AbstractConditionTest {

    @BeforeEach
    void beforeEach() {
        when(context.getEnvironment()).thenReturn(environment);
    }

    @Mock
    protected ConditionContext context;
    @Mock
    protected AnnotatedTypeMetadata metadata;
    @Mock
    protected Environment environment;

    protected void setProperty(String property, String value) {
        when(environment.getProperty(property))
                .thenReturn(value);
    }
}
