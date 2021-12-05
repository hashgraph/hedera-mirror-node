@crypto
Feature: Crypto related transactions

  Scenario: Validate CryptoCreate transaction
    When I create a crypto account
    Then the DATA API should show the CryptoCreate transaction

  Scenario: Validate CryptoTransfer transaction
    When I transfer some hbar to the treasury account
    Then the DATA API should show the CryptoTransfer transaction
