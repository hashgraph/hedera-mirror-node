package com.hedera.mirror.test.e2e.acceptance.config;

import javax.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "hedera.mirror.test.acceptance.web3")
@Data
@Validated
public class Web3RestProperties {

    @NotBlank
    private String baseUrl;
}
