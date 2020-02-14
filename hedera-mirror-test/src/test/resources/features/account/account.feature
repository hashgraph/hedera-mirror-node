@Accounts @FullSuite
Feature: Account Coverage Feature

    @BalanceCheck @Sanity @Acceptance
    Scenario Outline: Validate account balance check scenario
        When I request balance info for this account
        Then the result should be greater than or equal to <threshold>
        Examples:
            | threshold |
            | 1000000   |
