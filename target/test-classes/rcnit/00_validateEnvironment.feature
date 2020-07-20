Feature: Validate environment

  Scenario: Configure minikube
    Given minikube runs "v1.9.0"
    And minikube uses the VirtualBox VM driver
    And minikube runs the correct Kubernetes version
    And minikube uses the correct core count
    And minikube uses the correct amount of memory
    And the metrics-server is enabled
    Then minikube is configured correctly