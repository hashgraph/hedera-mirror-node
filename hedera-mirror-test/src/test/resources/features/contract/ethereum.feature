@contractbase @fullsuite @acceptance @critical @release @ethereum
Feature: Ethereum transactions Coverage Feature

  Scenario Outline: Validate Ethereum Contract create and call


  Given I successfully created a signer account with an EVM address alias
  Then validate the signer account and its balance

  Given I successfully create contract by Legacy ethereum transaction
  Then the mirror node REST API should return status <httpStatusCode> for the eth contract creation transaction
  And the mirror node REST API should verify the deployed contract entity by eth call

  When I successfully call function using EIP-1559 ethereum transaction
  Then the mirror node REST API should return status <httpStatusCode> for the ethereum transaction
  And the mirror node REST API should verify the ethereum called contract function

  Given I successfully call function using EIP-2930 ethereum transaction
  Then the mirror node REST API should return status <httpStatusCode> for the ethereum transaction
  And the mirror node REST API should verify the ethereum called contract function

  Examples:
    | httpStatusCode |
    | 200            |