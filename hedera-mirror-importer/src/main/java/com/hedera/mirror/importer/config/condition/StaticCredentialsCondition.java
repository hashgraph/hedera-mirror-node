package com.hedera.mirror.importer.config.condition;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class StaticCredentialsCondition implements Condition {

    // The access and secret keys must be provided, and there must not be a roleArn to use static credentials
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String accessKey = context.getEnvironment().getProperty("hedera.mirror.importer.downloader.accessKey");
        String secretKey = context.getEnvironment().getProperty("hedera.mirror.importer.downloader.secretKey");
        String roleArn = context.getEnvironment().getProperty("hedera.mirror.importer.downloader.s3.roleArn");
        return StringUtils.isNotBlank(accessKey) && StringUtils.isNotBlank(secretKey) && StringUtils.isBlank(roleArn);
    }
}
