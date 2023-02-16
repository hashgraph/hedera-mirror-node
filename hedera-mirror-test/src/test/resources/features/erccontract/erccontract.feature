@contractbase @fullsuite
Feature: ERC Contract Base Coverage Feature

    @critical @release @acceptance
    Scenario Outline: Validate ERC Contract
        Given I successfully create a contract from contract bytes with <initialBalance> balance
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deployed contract entity
        And I call the contract via the mirror node REST API
        When I successfully delete the contract
        Then the mirror node REST API should return status <httpStatusCode> for the contract transaction
        And the mirror node REST API should verify the deleted contract entity
        Examples:
            | httpStatusCode | initialBalance |
            | 200            | 10000000       |
