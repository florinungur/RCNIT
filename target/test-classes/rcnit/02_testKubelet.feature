Feature: Test the Kubernetes kubelet

  Scenario: Test the liveness probe
    Given A pod with a liveness probe of 5 periodSeconds and a "sleep 10" container command
    Then There are no anomalous pod events for the first ten seconds
    But There are anomalous pod events after 10 seconds