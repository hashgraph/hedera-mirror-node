@contractbase @fullsuite @historical @web3 @acceptance
Feature: Historical Feature

  Scenario Outline: Validate Historical Data
    Given I successfully create estimateGas contract
    Then I successfully update the contract storage and get the initial value via historical data
    Then I successfully update the balance of an account and get the initial balance via historical data
#    Then I verify that historical data for "safe" block is treated as latest
#    Then I verify that historical data for "pending" block is treated as latest
#    Then I verify that historical data for "finalised" block is treated as latest
    Then I verify the response from non existing account