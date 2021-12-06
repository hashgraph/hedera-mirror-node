Feature: Crypto related transactions

  Scenario: Validate CryptoCreate transaction
    When I send a CryptoCreate transaction to network
    Then the DATA API should show the CryptoCreate transaction

  Scenario: Validate CryptoTransfer transaction
    When I transfer some hbar to the treasury account
    Then the DATA API should show the CryptoTransfer transaction
