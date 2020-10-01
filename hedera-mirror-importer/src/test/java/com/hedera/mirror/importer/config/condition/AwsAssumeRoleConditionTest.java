package com.hedera.mirror.importer.config.condition;

import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class AwsAssumeRoleConditionTest extends AbstractConditionTest{

    private AwsAssumeRoleCondition awsAssumeRoleCondition= new AwsAssumeRoleCondition();

    @ParameterizedTest(name = "testAwsAssumeRoleCondition({0},{1})")
    @CsvSource({
            "arn:aws:iam::123123123123:role/testrole, S3, true",
            "arn:aws:iam::123123123123:role/testrole,,true",
            "arn:aws:iam::123123123123:role/testrole, GCP,false",
            ",S3,false",
            ",GCP,false"
    })
    void testRoleArnPresentAndS3(String roleArn, String cloudProvider, boolean expectedResult) {
        //when
        setProperty("hedera.mirror.importer.downloader.s3.roleArn", roleArn);
        setProperty("hedera.mirror.importer.downloader.cloudProvider",
                cloudProvider);

        //then
        assertEquals(expectedResult, awsAssumeRoleCondition.matches(context, metadata));
    }
}
