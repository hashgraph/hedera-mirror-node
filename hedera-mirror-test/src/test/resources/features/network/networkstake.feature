@network @fullsuite
Feature: Account Coverage Feature

    @critical @release @networkstake
    Scenario Outline: Get network stake
        When I query the network stake
        Then the mirror node REST API returns the network stake
