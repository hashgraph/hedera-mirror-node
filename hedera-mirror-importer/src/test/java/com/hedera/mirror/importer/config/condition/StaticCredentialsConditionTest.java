package com.hedera.mirror.importer.config.condition;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StaticCredentialsConditionTest extends AbstractConditionTest {

    private final StaticCredentialsCondition staticCredentialsCondition = new StaticCredentialsCondition();

    @ParameterizedTest(name = "testStaticCredentialsProviderCondition({0},{1})")
    @CsvSource({
            "ACCESS_KEY, SECRET_KEY, true",
            "ACCESS_KEY, , false",
            ", SECRET_KEY, false",
            ", , false"
    })
    void testRoleArnPresentAndS3(String accessKey, String secretKey, boolean expectedResult) {
        //when
        setProperty("hedera.mirror.importer.downloader.accessKey", accessKey);
        setProperty("hedera.mirror.importer.downloader.secretKey", secretKey);

        //then
        assertEquals(expectedResult, staticCredentialsCondition.matches(context, metadata));
    }
}
