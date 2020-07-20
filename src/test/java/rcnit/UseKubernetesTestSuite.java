package rcnit;

import io.cucumber.java.Status;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rcnit.util.ExecKubernetesCmd;
import rcnit.util.ExecShellCmd;

public class UseKubernetesTestSuite {

  private static final Logger log = LoggerFactory.getLogger(UseKubernetesTestSuite.class);
  private static final String NAMESPACE_NAME = "rcnit-k8s-testsuite-testing";

  @When("I deploy the k8s-testsuite")
  public void i_deploy_the_k8s_testsuite() {
    ExecShellCmd helmInstall = new ExecShellCmd();

    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    log.info("Installing 'load-test' Helm chart...");
    helmInstall.execute(
        "helm install load-test --namespace "
            + NAMESPACE_NAME
            + " src/test/resources/k8s-testsuite/load-test"
            + " --set cpuRequests.webserver=10m --set cpuRequests.loadbot=10m"
            + " --set aggregator.maxReplicas=10"
            + " --set loadbot.rate=10 --set loadbot.workers=2");
    String helmInstallOutput = helmInstall.returnAsString();

    if (helmInstallOutput.isEmpty()) {
      log.error("Failed installing 'load-test' Helm chart:\n" + helmInstallOutput);
      throw new io.cucumber.java.PendingException();
    } else {
      log.info("Installed 'load-test' Helm chart:\n" + helmInstallOutput);
    }
    ExecKubernetesCmd.waitForPodsInNamespaceToBeReady(NAMESPACE_NAME);
  }

  @Then("The k8s-testsuite should be successful")
  public void the_k8s_testsuite_should_be_successful() {
    ExecKubernetesCmd.getPodWatchLogOutput(
        "aggregator", NAMESPACE_NAME, "Scaling webserver to 0 replicas");

    boolean allIsGood =
        ExecKubernetesCmd.checkForFailedSchedulingEventsAndSchedulerHealth(NAMESPACE_NAME);

    if (!allIsGood) {
      throw new io.cucumber.java.PendingException();
    } else {
      log.info(Status.PASSED + "! The load-test finished successfully!");
    }
    ExecKubernetesCmd.helmUninstall("load-test", NAMESPACE_NAME);
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }
}
