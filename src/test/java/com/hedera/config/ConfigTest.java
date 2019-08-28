package com.hedera.config;

import static org.junit.Assert.assertEquals;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = { Config.class })
public class ConfigTest {

  @Autowired
  private Config config;

  @Test
  public void shouldHaveDefaultConfig() {

    assertEquals("jdbc:postgresql://localhost:5433/hederamirror", config.getUrl());
    assertEquals("hederamirror", config.getDbname());
    assertEquals("hederamirror", config.getUsername());
    assertEquals("mysecretpassword", config.getPassword());

  }

}