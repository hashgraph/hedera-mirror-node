package com.hedera.databaseutilities;

import static org.junit.Assert.assertEquals;

import com.hedera.config.Config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.testcontainers.containers.PostgreSQLContainer;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { Config.class })
public class DatabaseUtilitiesTest {

  @Autowired
  Config config;

  @Rule
  public PostgreSQLContainer pgContainer;

  @Test
  public void testPostgreSQLConnection() {
    pgContainer = new PostgreSQLContainer().withDatabaseName(config.getDbname()).withUsername(config.getUsername())
        .withPassword(config.getPassword());

    assertEquals(config.getDbname(), pgContainer.getDatabaseName());
    assertEquals(config.getUsername(), pgContainer.getUsername());
    assertEquals(config.getPassword(), pgContainer.getPassword());
  }

}