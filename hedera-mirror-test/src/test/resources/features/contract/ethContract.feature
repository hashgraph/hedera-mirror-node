@contractbase @fullsuite
Feature: Contract Base Coverage Feature
    
    @critical @release @acceptance
    Scenario Outline: Validate Contract Flows
        Given I successfully create a contract from the parent contract bytes with 10000000 balance
        When I successfully eth call the  contract