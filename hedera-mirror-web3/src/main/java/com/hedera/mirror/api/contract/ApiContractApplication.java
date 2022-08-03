package com.hedera.mirror.api.contract;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.nativex.hint.NativeHint;
import reactor.core.scheduler.Schedulers;

@ConfigurationPropertiesScan
@EntityScan("com.hedera.mirror.common.domain")
@NativeHint(options = "--trace-object-instantiation=io.netty.handler.ssl.BouncyCastleAlpnSslUtils")
@SpringBootApplication
public class ApiContractApplication {

    public static void main(String[] args) {
        Schedulers.enableMetrics();
        SpringApplication.run(ApiContractApplication.class, args);
    }
}
