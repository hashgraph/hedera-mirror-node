@contractbase @fullsuite
Feature: EthCall Base Coverage tests

  @modification @web3
  Scenario Outline: Validate EthCall Modification Functions
    Given I successfully create contract from contract bytes with initial 1000000 balance
    Then I call eth call with update function and I expect return of the updated value
    Then I call eth call with update function that makes N times state update
    Then I call eth call with nested deploy using create function
#    #Then I call eth call with nested deploy using create2 function TBD
#    #Then I call eth call with function that executes nested deploy, destroy and redeploy TBD
    Then I call eth call with transfer function that returns the balance
