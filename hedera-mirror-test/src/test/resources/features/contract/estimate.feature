@contractbase @fullsuite @estimate @web3
Feature: EstimateGas Contract Base Coverage Feature
  
  Scenario Outline: Validate EstimateGas
    Given I successfully create contract from contract bytes with 10000000 balance
    And lower deviation is 5% and upper deviation is 20%
    Then I call estimateGas without arguments that multiplies two numbers
    Then I call estimateGas with function msgSender
    Then I call estimateGas with function tx origin
    Then I call estimateGas with function messageValue
    Then I call estimateGas with function messageSigner
    Then I call estimateGas with function balance of address
    Then I call estimateGas with function that changes contract slot information by updating global contract field with the passed argument
    Then I call estimateGas with function that successfully deploys a new smart contract via CREATE op code
    Then I call estimateGas with function that successfully deploys a new smart contract via CREATE2 op code
    Then I call estimateGas with function that makes a static call to a method from a different contract
    Then I call estimateGas with function that makes a delegate call to a method from a different contract
    Then I call estimateGas with function that makes a call code to a method from a different contract
#    Then I call estimateGas with function that performs LOG0, LOG1, LOG2, LOG3, LOG4 operations - TODO: Fix failing test
    Then I call estimateGas with function that performs self destruct
    Then I call estimateGas with request body that contains wrong method signature
    Then I call estimateGas with wrong encoded parameter
    Then I call estimateGas with non-existing from address in the request body
    Then I call estimateGas with function that makes a call to invalid smart contract
    Then I call estimateGas with function that makes a delegate call to invalid smart contract
    Then I call estimateGas with function that makes a static call to invalid smart contract
    Then I call estimateGas with function that makes a call code to invalid smart contract
    Then I call estimateGas with function that makes call to an external contract function
    Then I call estimateGas with function that makes delegate call to an external contract function
    Then I call estimateGas with function that makes call to an external contract view function
    Then I call estimateGas with function that makes a state update to a contract
    Then I call estimateGas with function that makes a state update to a contract several times and estimateGas is higher
    Then I call estimateGas with function that executes gasLeft
    # Then I call estimateGas with function that executes reentrancy attack with transfer - TODO: Waiting HIP-584 implementation
    Then I call estimateGas with function that executes reentrancy attack with call
    Then I call estimateGas with function that executes positive nested calls
    Then I call estimateGas with function that executes limited nested calls
