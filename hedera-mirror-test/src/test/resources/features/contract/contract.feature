@contractbase @fullsuite
Feature: Contract Base Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate Contract Flow - ContractCreate, ContractUpdate, ContractCall, ContractDelete
        Given I successfully create a contract from contract bytes with <initialBalance> balance
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        When I successfully update the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the updated contract entity
        When I successfully call the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the called contract function
        When I successfully delete the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deleted contract entity
        Examples:
            | httpStatusCode | initialBalance |
            | 200            | 1000           |
