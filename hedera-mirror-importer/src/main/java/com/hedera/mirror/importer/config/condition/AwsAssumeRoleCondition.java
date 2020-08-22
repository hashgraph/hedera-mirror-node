package com.hedera.mirror.importer.config.condition;

import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;

import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

public class AwsAssumeRoleCondition implements Condition {

    // The roleArn must be provided, and the cloudProvider must be S3 to use AssumeRole
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String roleArn = context.getEnvironment().getProperty("hedera.mirror.importer.downloader.s3.roleArn");
        String cloudProvider = context.getEnvironment().getProperty("hedera.mirror.importer.downloader.cloudProvider");
        if(cloudProvider == null) {
            cloudProvider = CommonDownloaderProperties.CloudProvider.S3.name();
        }
        return StringUtils.isNotBlank(roleArn) && StringUtils.equals(CommonDownloaderProperties.CloudProvider.S3.name(),
                cloudProvider);
    }
}
