@crypto
Feature: Crypto related transactions

  Scenario: Validate CryptoCreate transaction
    When I create a crypto account
    Then the DATA API should show the CryptoCreate transaction

  Scenario: Validate auto account creation with CryptoTransfer transaction
    When I transfer some hbar to a new alias
    Then the DATA API should show the CryptoTransfer transaction and new account id

  Scenario: Validate CryptoTransfer transaction
    When I transfer some hbar to the treasury account
    Then the DATA API should show the CryptoTransfer transaction
