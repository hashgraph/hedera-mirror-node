package com.hedera.mirror.importer.config.condition;

import com.hedera.mirror.importer.downloader.CommonDownloaderProperties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class StaticCredentialsConditionTest extends AbstractConditionTest{

    private StaticCredentialsCondition staticCredentialsCondition = new StaticCredentialsCondition();

    @ParameterizedTest(name = "testStaticCredentialsProviderCondition({0},{1})")
    @CsvSource({
            "ACCESS_KEY, SECRET_KEY, , true",
            "ACCESS_KEY, , , false",
            ", SECRET_KEY, , false",
            ", , , false",
            "ACCESS_KEY, SECRET_KEY, arn:aws:iam::123123123123:role/testrole, false",
    })
    void testRoleArnPresentAndS3(String accessKey, String secretKey, String roleArn, boolean expectedResult) {
        //when
        setProperty("hedera.mirror.importer.downloader.accessKey", accessKey);
        setProperty("hedera.mirror.importer.downloader.secretKey", secretKey);
        setProperty("hedera.mirror.importer.downloader.s3.roleArn", roleArn);

        //then
        assertEquals(expectedResult, staticCredentialsCondition.matches(context, metadata));
    }

}
