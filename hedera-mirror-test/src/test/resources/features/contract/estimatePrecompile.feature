@contractbase @fullsuite
Feature: EstimateGas Contract Base Coverage Feature

  @estimatepre @web3
  Scenario Outline: Validate EstimateGas with precompile
    Given I create contract with 0 balance
    Given I successfully create and verify a fungible token for estimateGas precompile tests
    Given I successfully create and verify a non fungible token for estimateGas precompile tests
    Given I mint and verify a new nft
    And I set lower deviation at 5% and upper deviation at 20%
    Then I call estimateGas with associate token function
    #Then I call estimateGas with dissociate token function without association
    Then I call estimateGas with nested associate function that executes it twice
    Then I call estimateGas with dissociate token function
    Then I call estimateGas with nested dissociate function that executes it twice
    Then I call estimateGas with dissociate and associate nested function
    Then I call estimateGas with approve function
    Then I call estimateGas with transferFromNFT function
    Then I call estimateGas with transferFrom function without approval
    Then I call estimateGas with transferFrom function with more than the approved allowance
    Then I call estimateGas with transferFrom function
