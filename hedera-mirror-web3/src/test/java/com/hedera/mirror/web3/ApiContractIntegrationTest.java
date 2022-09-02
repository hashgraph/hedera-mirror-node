package com.hedera.mirror.web3;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.jdbc.Sql;

@Sql(executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD, scripts = {"classpath:cleanup.sql", "classpath:insert_contract.sql"})
@Sql(executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD, scripts = "classpath:cleanup.sql")
@SpringBootTest
public abstract class ApiContractIntegrationTest {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    @BeforeEach
    void logTest(TestInfo testInfo) {
        log.info("Executing: {}", testInfo.getDisplayName());
    }
}
