package com.hedera.mirror.importer.config;

import com.hedera.mirror.importer.config.condition.AwsAssumeRoleCondition;
import com.hedera.mirror.importer.config.condition.StaticCredentialsCondition;
import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;
import com.hedera.mirror.importer.exception.MissingCredentialsException;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.auth.StsAssumeRoleCredentialsProvider;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

@Configuration
@EnableAsync
@Log4j2
@RequiredArgsConstructor
public class CredentiialProviderConfiguration {

    private final CommonDownloaderProperties downloaderProperties;

    @Bean
    @Conditional(StaticCredentialsCondition.class)
    public AwsCredentialsProvider staticCredentialsProvider() {
        log.info("Setting up S3 async client using provided access/secret key");
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(downloaderProperties.getAccessKey(),
                downloaderProperties.getSecretKey()));
    }

    @Bean
    @Conditional(AwsAssumeRoleCondition.class)
    public AwsCredentialsProvider stsAssumeRoleCredentialsProvider() {
        log.info("Setting up S3 async client using temporary credentials (AWS AssumeRole)");
        if (StringUtils.isBlank(downloaderProperties.getAccessKey())
                || StringUtils.isBlank(downloaderProperties.getSecretKey())) {
            throw new MissingCredentialsException("Cannot connect to S3 using AssumeRole without user keys");
        }

        StsClient stsClient = StsClient.builder()
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        downloaderProperties.getAccessKey(), downloaderProperties.getSecretKey())))
                .region(Region.of(downloaderProperties.getRegion()))
                .build();

        AssumeRoleRequest.Builder assumeRoleRequestBuilder = AssumeRoleRequest.builder()
                .roleArn(downloaderProperties.getS3().getRoleArn())
                .roleSessionName(downloaderProperties.getS3().getRoleSessionName());

        if(StringUtils.isNotBlank(downloaderProperties.getS3().getExternalId())) {
            assumeRoleRequestBuilder.externalId(downloaderProperties.getS3().getExternalId());
        }

        return StsAssumeRoleCredentialsProvider.builder().stsClient(stsClient)
                .refreshRequest(assumeRoleRequestBuilder.build())
                .build();
    }

    //This should only be created when none of the conditions to create an AwsCredentialsProvider are met.
    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider anonymousCredentialsProvider() {
        log.info("Setting up S3 async client using anonymous credentials");
        return AnonymousCredentialsProvider.create();
    }
}
