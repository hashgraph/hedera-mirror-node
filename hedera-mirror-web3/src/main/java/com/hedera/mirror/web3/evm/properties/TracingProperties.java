package com.hedera.mirror.web3.evm.properties;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.HashSet;
import java.util.Set;

@Setter
@Validated
@ConfigurationProperties(prefix = "hedera.mirror.web3.evm.tracing")
public class TracingProperties {

    @Getter
    boolean enabled = false;

    @Getter
    Set<Long> contract = new HashSet<>();

    @Getter
    Set<String> status = new HashSet<>();
}
