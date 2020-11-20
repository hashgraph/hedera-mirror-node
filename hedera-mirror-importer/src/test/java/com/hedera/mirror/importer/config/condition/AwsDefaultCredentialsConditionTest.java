package com.hedera.mirror.importer.config.condition;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AwsDefaultCredentialsConditionTest extends AbstractConditionTest {

    private final AwsDefaultCredentialsCondition awsDefaultCredentialsCondition = new AwsDefaultCredentialsCondition();

    @ParameterizedTest(name = "testAwsDefaultCredentialsProviderCondition({0},{1},{2})")
    @CsvSource({
            "ACCESS_KEY, SECRET_KEY, S3, false",
            ", SECRET_KEY, S3, true",
            "ACCESS_KEY,, S3, true",
            ",, S3, true",
            ",,, true",
            "ACCESS_KEY, SECRET_KEY, GCP, false",
            ", SECRET_KEY, GCO, false",
            "ACCESS_KEY,, GCP, false",
            ",, GCP, false"
    })
    void testAwsDefaultCredentialsProviderCondition(String accessKey, String secretKey, String cloudProvider,
                                                    boolean expectedResult) {
        //when
        setProperty("hedera.mirror.importer.downloader.accessKey", accessKey);
        setProperty("hedera.mirror.importer.downloader.secretKey", secretKey);
        setProperty("hedera.mirror.importer.downloader.cloudProvider",
                cloudProvider);

        //then
        assertEquals(expectedResult, awsDefaultCredentialsCondition.matches(context, metadata));
    }
}
