Feature: Test the Kubernetes scheduler

  Scenario: Test CPU request scheduling
    When I create a pod with a CPU request that is too big for my node
    Then The scheduler should not schedule the pod due to insufficient CPU

  Scenario: Test memory request scheduling
    When I create a pod with a memory request that is too big for my node
    Then The scheduler should not schedule the pod due to insufficient memory

  Scenario: CPU-test a pod
    When I use 1000 milliCPU from a pod with 2000 milliCPU request and limit
    Then The CPU-test pod should be fine
    When I use 10000 milliCPU from a pod with 1000 milliCPU compute allocation the CPU-test pod should be throttled

  Scenario: Memory-test a pod
    When I use 100 MB of memory from a pod with 200 MB memory request and limit
    Then The memory-test pod should be fine
    When I use 300 MB of memory from a pod with 200 MB memory allocation
    Then The memory-test pod should be terminated

  Scenario: Test killing deployment replicas
    Given A deployment with 20 replicas
    When I kill 5 replicas
    Then The deployment should create 5 more replicas to equal 20 again
    And The scheduler should be healthy

  Scenario: Test node affinity
    Given A node label
    Then A pod with a correct requiredDuringSchedulingIgnoredDuringExecution node affinity will get scheduled
    But A pod with a wrong requiredDuringSchedulingIgnoredDuringExecution node affinity will get rejected
    And A pod with a correct preferredDuringSchedulingIgnoredDuringExecution node affinity will get scheduled
    And A pod with a wrong preferredDuringSchedulingIgnoredDuringExecution node affinity will get scheduled