package rcnit;

import io.cucumber.java.Status;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rcnit.util.ExecShellCmd;

public class ValidateEnvironment {

  private static final Logger log = LoggerFactory.getLogger(ValidateEnvironment.class);

  @Given("minikube runs {string}")
  public void minikube_runs(String version) {
    ExecShellCmd checkMinikubeVersion = new ExecShellCmd();

    log.info("Starting minikube (this might take a while)...");

    /*
    There is no way to set the API server port in "~/.minikube/config/config.json".
    See https://github.com/kubernetes/minikube/issues/7762
     */
    checkMinikubeVersion.execute("minikube start --apiserver-port=8443");
    String checkMinikubeVersionOutput = checkMinikubeVersion.returnAsString();

    if (checkMinikubeVersionOutput.contains(version)) {
      log.info("minikube runs v1.9.0:\n" + checkMinikubeVersionOutput);
    } else {
      log.error(checkMinikubeVersionOutput);
      throw new io.cucumber.java.PendingException();
    }
  }

  @And("minikube uses the VirtualBox VM driver")
  public void minikube_uses_the_virtualbox_vm_driver() {
    ExecShellCmd minikubeConfig = new ExecShellCmd();
    minikubeConfig.execute("minikube config set driver virtualbox");
    String minikubeConfigOutput = minikubeConfig.returnAsString();

    if (minikubeConfigOutput.isEmpty()) {
      log.error(minikubeConfigOutput);
      throw new io.cucumber.java.PendingException();
    } else {
      log.info("minikube uses the VirtualBox VM driver");
    }
  }

  @And("minikube runs the correct Kubernetes version")
  public void minikube_runs_the_correct_kubernetes_version() {
    ExecShellCmd minikubeConfig = new ExecShellCmd();
    ExecShellCmd kubectlVersion = new ExecShellCmd();

    // minikubeConfig.returnAsString() returns empty even if the command does its job
    minikubeConfig.execute("minikube config set kubernetes-version v1.18.0");
    kubectlVersion.execute("kubectl version --short=true");
    String kubectlVersionOutput = kubectlVersion.returnAsString();

    if (kubectlVersionOutput.contains("Server Version: v1.18.0")
        && kubectlVersionOutput.contains("Client Version: v1.18.0")) {
      log.info("minikube runs k8s v1.18.0");
    } else {
      log.error(kubectlVersionOutput);
      throw new io.cucumber.java.PendingException();
    }
  }

  @And("minikube uses the correct core count")
  public void minikube_uses_the_correct_core_count() {
    ExecShellCmd minikubeConfig = new ExecShellCmd();
    minikubeConfig.execute("minikube config set cpus 4");
    String minikubeConfigOutput = minikubeConfig.returnAsString();

    if (minikubeConfigOutput.isEmpty()) {
      log.error(minikubeConfigOutput);
      throw new io.cucumber.java.PendingException();
    } else {
      log.info("minikube uses 4 CPU cores");
    }
  }

  @And("minikube uses the correct amount of memory")
  public void minikube_uses_the_correct_amount_of_memory() {
    ExecShellCmd minikubeConfig = new ExecShellCmd();
    minikubeConfig.execute("minikube config set memory 8192");
    String minikubeConfigOutput = minikubeConfig.returnAsString();

    if (minikubeConfigOutput.isEmpty()) {
      log.error(minikubeConfigOutput);
      throw new io.cucumber.java.PendingException();
    } else {
      log.info("minikube uses 8 GBs of memory");
    }
  }

  @And("the metrics-server is enabled")
  public void the_metrics_server_is_enabled() {
    ExecShellCmd minikubeEnableMetricsAddon = new ExecShellCmd();
    minikubeEnableMetricsAddon.execute("minikube addons enable metrics-server");
    String minikubeEnableMetricsAddonOutput = minikubeEnableMetricsAddon.returnAsString();

    if (minikubeEnableMetricsAddonOutput.contains("The 'metrics-server' addon is enabled")) {
      log.info("The 'metrics-server' addon is enabled");
    } else {
      log.error(minikubeEnableMetricsAddonOutput);
      throw new io.cucumber.java.PendingException();
    }
  }

  @Then("minikube is configured correctly")
  public void minikube_is_configured_correctly() {
    /*
    The only way to check if you configured minikube correctly is by looking at
    "~/.minikube/config/config.json".
     */
    Path minikubeConfigPath = Paths.get("C:/Users/olden/.minikube/config/config.json");
    String minikubeConfigContent = null;

    try {
      minikubeConfigContent = Files.readString(minikubeConfigPath, Charset.defaultCharset());
    } catch (IOException e) {
      e.printStackTrace();
    }

    assert minikubeConfigContent != null;

    if (minikubeConfigContent.contains(
        "{\n"
            + "    \"cpus\": 4,\n"
            + "    \"driver\": \"virtualbox\",\n"
            + "    \"kubernetes-version\": \"v1.18.0\",\n"
            + "    \"memory\": 8192\n"
            + "}")) {
      log.info(Status.PASSED + "! Minikube is configured correctly!");
    } else {
      log.error(
          Status.FAILED + "! Minikube is not configured correctly:\n" + minikubeConfigContent);
      throw new io.cucumber.java.PendingException();
    }
  }
}
