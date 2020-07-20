package rcnit;

import io.cucumber.java.Status;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.api.model.SecretBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rcnit.util.ExecKubernetesCmd;

public class TestEtcd {

  private static final String NAMESPACE_NAME = "rcnit-etcd-testing";
  private static final String SECRET_DATA = "buriedInNeverlandRanch";
  private static final Logger log = LoggerFactory.getLogger(TestEtcd.class);
  private Secret jimmyHoffaLocation;

  @When("I create a secret")
  public void i_create_a_secret() {
    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    jimmyHoffaLocation =
        new SecretBuilder()
            .withNewMetadata()
            .withName("jimmy-hoffa-location")
            .endMetadata()
            .addToStringData("data", SECRET_DATA)
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.secrets().inNamespace(NAMESPACE_NAME).create(jimmyHoffaLocation);
      log.info("Created '" + jimmyHoffaLocation.getMetadata().getName() + "' secret");
    } catch (KubernetesClientException e) {
      log.error("Failed creating '" + jimmyHoffaLocation.getMetadata().getName() + "' secret");
      e.printStackTrace();
      throw new io.cucumber.java.PendingException();
    }
  }

  @Then("The etcd key should be encrypted")
  public void the_etcd_key_should_be_encrypted() {
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      String secretDataEncodedToString =
          kubernetesClient
              .secrets()
              .inNamespace(NAMESPACE_NAME)
              .withName(jimmyHoffaLocation.getMetadata().getName())
              .get()
              .getData()
              .values()
              .toString()
              .replace("[", "")
              .replace("]", "");
      byte[] decodedSecretBytes = Base64.getDecoder().decode(secretDataEncodedToString);
      String decodedSecretString = new String(decodedSecretBytes);

      if (SECRET_DATA.equals(decodedSecretString)) {
        log.info(
            Status.PASSED
                + "! The '"
                + jimmyHoffaLocation.getMetadata().getName()
                + "' secret was successfully encrypted by etcd!");
        log.info("Encrypted secret stored in etcd: " + secretDataEncodedToString);
        log.info("Decrypted secret stored in etcd: " + decodedSecretString);
      } else {
        log.error(
            Status.FAILED
                + "! The '"
                + jimmyHoffaLocation.getMetadata().getName()
                + "' secret was not properly encrypted by etcd!");
        log.error("Encrypted secret stored in etcd: " + secretDataEncodedToString);
        log.error("Decrypted secret stored in etcd: " + decodedSecretString);
        throw new io.cucumber.java.PendingException();
      }

      try {
        kubernetesClient
            .secrets()
            .inNamespace(NAMESPACE_NAME)
            .withName(jimmyHoffaLocation.getMetadata().getName())
            .delete();
        log.info("Deleted '" + jimmyHoffaLocation.getMetadata().getName() + "' secret");
      } catch (KubernetesClientException e) {
        log.error("Failed deleting '" + jimmyHoffaLocation.getMetadata().getName() + "' secret");
        e.printStackTrace();
      }
    }
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }
}
