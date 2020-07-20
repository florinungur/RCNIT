Feature: Use Chaos Mesh

  Background:
    Given Chaos Mesh is running

  Scenario Outline: Inject chaos actions
    When I inject the <chaos-test-name> <chaos-action> with 10 test pods
    Then The <chaos-action> should be successful

    Examples:
      | chaos-test-name | chaos-action      |
      | podchaos        | pod-failure       |
      | podchaos        | pod-kill          |
      | networkchaos    | network-loss      |
      | networkchaos    | network-delay     |
      | networkchaos    | network-duplicate |
      | networkchaos    | network-corrupt   |
      | timechaos       | time-chaos        |