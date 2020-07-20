package rcnit;

import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rcnit.util.DeployChaos;
import rcnit.util.ExecKubernetesCmd;
import rcnit.util.ExecShellCmd;

public class UseChaosMesh {

  public static final String NAMESPACE_NAME = "rcnit-chaos-mesh-testing";
  public static final String MY_CHAOS_MESH_TESTS_PATH = "src/test/resources/my-chaos-mesh-tests/";
  private static final Logger log = LoggerFactory.getLogger(UseChaosMesh.class);

  @Given("Chaos Mesh is running")
  public void chaos_mesh_is_running() {
    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    ExecShellCmd kubectlApply = new ExecShellCmd();
    kubectlApply.execute(
        "kubectl apply -f "
            + "https://raw.githubusercontent.com/pingcap/chaos-mesh/master/manifests/crd.yaml");
    String kubectlApplyOutput = kubectlApply.returnAsString();

    if (kubectlApplyOutput.isEmpty()) {
      log.error("Failed creating Chaos Mesh CRDs!");
      log.error(kubectlApplyOutput);
      throw new io.cucumber.java.PendingException();
    } else if (kubectlApplyOutput.contains("configured")) {
      log.info("Chaos Mesh CRDs are already configured:\n" + kubectlApplyOutput);
    } else {
      log.info("Created Chaos Mesh CRDs:\n" + kubectlApplyOutput);
    }

    ExecShellCmd helmInstall = new ExecShellCmd();
    log.info("Installing 'chaos-mesh' Helm templates...");

    helmInstall.execute(
        "helm install chaos-mesh src/test/resources/chaos-mesh/helm/chaos-mesh "
            + "--namespace="
            + NAMESPACE_NAME);
    /*
    New alternative (https://github.com/chaos-mesh/chaos-mesh/issues/462):
    helmInstall.execute(
        "helm install chaos-mesh chaos-mesh/chaos-mesh --namespace=" + NAMESPACE_NAME);
    */
    String helmInstallOutput = helmInstall.returnAsString();

    if (helmInstallOutput.isEmpty()) {
      log.error("Failed installing 'chaos-mesh' Helm templates!");
      log.error(helmInstallOutput);
      throw new io.cucumber.java.PendingException();
    } else if (helmInstallOutput.contains(
            "Error: rendered manifests contain a resource that already exists")
        || helmInstallOutput.contains("cannot re-use a name that is still in use")) {
      log.info("Helm templates already installed");
    } else {
      log.info("Installed 'chaos-mesh' Helm templates:\n" + helmInstallOutput);
    }

    /*
    Webhook-cert pods might get into the 'items' list even though they're ephemeral,
    thus causing a hang.
    If sleep < 25 seconds I get a null pointer exception at waitForPodsInNamespaceToBeReady().
     */
    try {
      Thread.sleep(25 * 1000);
    } catch (InterruptedException e) {
      e.printStackTrace();
    }
    // This assumes the only pods in namespaceName are Chaos Mesh pods
    ExecKubernetesCmd.waitForPodsInNamespaceToBeReady(NAMESPACE_NAME);
  }

  @When("I inject the podchaos pod-failure with {int} test pods")
  public void i_inject_the_podchaos_pod_failure_with_test_pods(int amountOfTestPods) {
    DeployChaos.launchAction("podchaos", "pod-failure", amountOfTestPods);
  }

  @Then("The pod-failure should be successful")
  public void the_pod_failure_should_be_successful() {
    DeployChaos.checkIfActionSuccessful("podchaos", "pod-failure");
  }

  @When("I inject the podchaos pod-kill with {int} test pods")
  public void i_inject_the_podchaos_pod_kill_with_test_pods(int amountOfTestPods) {
    DeployChaos.launchAction("podchaos", "pod-kill", amountOfTestPods);
  }

  @Then("The pod-kill should be successful")
  public void the_pod_kill_should_be_successful() {
    DeployChaos.checkIfActionSuccessful("podchaos", "pod-kill");
  }

  @When("I inject the networkchaos network-loss with {int} test pods")
  public void i_inject_the_networkchaos_network_loss_with_test_pods(int amountOfTestPods) {
    DeployChaos.launchAction("networkchaos", "network-loss", amountOfTestPods);
  }

  @Then("The network-loss should be successful")
  public void the_network_loss_should_be_successful() {
    DeployChaos.checkIfActionSuccessful("networkchaos", "network-loss");
  }

  @When("I inject the networkchaos network-delay with {int} test pods")
  public void i_inject_the_networkchaos_network_delay_with_test_pods(int amountOfTestPods) {
    DeployChaos.launchAction("networkchaos", "network-delay", amountOfTestPods);
  }

  @Then("The network-delay should be successful")
  public void the_network_delay_should_be_successful() {
    DeployChaos.checkIfActionSuccessful("networkchaos", "network-delay");
  }

  @When("I inject the networkchaos network-duplicate with {int} test pods")
  public void i_inject_the_networkchaos_network_duplicate_with_test_pods(int amountOfTestPods) {
    DeployChaos.launchAction("networkchaos", "network-duplicate", amountOfTestPods);
  }

  @Then("The network-duplicate should be successful")
  public void the_network_duplicate_should_be_successful() {
    DeployChaos.checkIfActionSuccessful("networkchaos", "network-duplicate");
  }

  @When("I inject the networkchaos network-corrupt with {int} test pods")
  public void i_inject_the_networkchaos_network_corrupt_with_test_pods(int amountOfTestPods) {
    DeployChaos.launchAction("networkchaos", "network-corrupt", amountOfTestPods);
  }

  @Then("The network-corrupt should be successful")
  public void the_network_corrupt_should_be_successful() {
    DeployChaos.checkIfActionSuccessful("networkchaos", "network-corrupt");
  }

  @When("I inject the timechaos time-chaos with {int} test pods")
  public void i_inject_the_timechaos_time_chaos_with_test_pods(int amountOfTestPods) {
    DeployChaos.launchAction("timechaos", "time-chaos", amountOfTestPods);
  }

  @Then("The time-chaos should be successful")
  public void the_time_chaos_should_be_successful() {
    DeployChaos.checkIfActionSuccessful("timechaos", "time-chaos");

    ExecKubernetesCmd.helmUninstall("chaos-mesh", NAMESPACE_NAME);

    ExecShellCmd kubectlDelete = new ExecShellCmd();
    kubectlDelete.execute(
        "kubectl delete -f "
            + "https://raw.githubusercontent.com/pingcap/chaos-mesh/master/manifests/crd.yaml");
    String kubectlApplyOutput = kubectlDelete.returnAsString();

    if (kubectlApplyOutput.isEmpty()) {
      log.error("Failed deleting Chaos Mesh CRDs:");
      log.error(kubectlApplyOutput);
      throw new io.cucumber.java.PendingException();
    } else {
      log.info("Deleted Chaos Mesh CRDs:\n" + kubectlApplyOutput);
    }
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }
}
