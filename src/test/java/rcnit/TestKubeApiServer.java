package rcnit;

import io.cucumber.java.Status;
import io.cucumber.java.en.And;
import io.cucumber.java.en.But;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.fabric8.kubernetes.api.model.LimitRange;
import io.fabric8.kubernetes.api.model.LimitRangeBuilder;
import io.fabric8.kubernetes.api.model.NodeSelectorRequirementBuilder;
import io.fabric8.kubernetes.api.model.ObjectMeta;
import io.fabric8.kubernetes.api.model.PersistentVolume;
import io.fabric8.kubernetes.api.model.PersistentVolumeBuilder;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaim;
import io.fabric8.kubernetes.api.model.PersistentVolumeClaimBuilder;
import io.fabric8.kubernetes.api.model.Quantity;
import io.fabric8.kubernetes.api.model.ResourceQuota;
import io.fabric8.kubernetes.api.model.ResourceQuotaBuilder;
import io.fabric8.kubernetes.api.model.storage.StorageClass;
import io.fabric8.kubernetes.api.model.storage.StorageClassBuilder;
import io.fabric8.kubernetes.client.DefaultKubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rcnit.util.ExecKubernetesCmd;
import rcnit.util.ExecShellCmd;

public class TestKubeApiServer {

  private static final String NAMESPACE_NAME = "rcnit-kube-apiserver-testing";
  private static final Logger log = LoggerFactory.getLogger(TestKubeApiServer.class);
  private static final String SECOND_STORAGE_CLASS_NAME = "rcnit-storage-class";
  private static PersistentVolume persistentVolume;
  private static LimitRange limitRange;
  private static ResourceQuota resourceQuota;
  private String defaultStorageClassName = null;

  @Given("The default admission plugins are enabled")
  public void the_default_admission_plugins_are_enabled() {
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      if (kubernetesClient
          .pods()
          .inNamespace("kube-system")
          .withName("kube-apiserver-minikube")
          .get()
          .getSpec()
          .getContainers()
          .get(0)
          .getCommand()
          .toString()
          .contains(
              "--enable-admission-plugins="
                  + "NamespaceLifecycle,"
                  + "LimitRanger,"
                  + "ServiceAccount,"
                  + "DefaultStorageClass,"
                  + "DefaultTolerationSeconds,"
                  + "NodeRestriction,"
                  + "MutatingAdmissionWebhook,"
                  + "ValidatingAdmissionWebhook,"
                  + "ResourceQuota")) {
        log.info("The default admission controllers for Kubernetes v1.18 are enabled");
      } else {
        log.error("The default admission controllers for Kubernetes v1.18 are not enabled");
        log.error(
            "Please check the `--enable-admission-plugins` argument in the following command:\n"
                + kubernetesClient
                    .pods()
                    .inNamespace("kube-system")
                    .withName("kube-apiserver-minikube")
                    .get()
                    .getSpec()
                    .getContainers()
                    .get(0)
                    .getCommand()
                    .toString());
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  @And("A default storage class")
  public void a_default_storage_class() {
    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient
          .storage()
          .storageClasses()
          .inNamespace(NAMESPACE_NAME)
          .list()
          .getItems()
          .forEach(
              storageClass -> {
                try {
                  if (storageClass
                      .getMetadata()
                      .getAnnotations()
                      .toString()
                      .contains("storageclass.kubernetes.io/is-default-class=true")) {
                    log.info(
                        "The storage class '"
                            + storageClass.getMetadata().getName()
                            + "' is the default storage class");
                    defaultStorageClassName = storageClass.getMetadata().getName();
                  }
                } catch (KubernetesClientException e) {
                  e.printStackTrace();
                  throw new io.cucumber.java.PendingException();
                }
              });

      if (defaultStorageClassName == null) {
        log.error("There is no default storage class");
      }
    }
  }

  @And("A {int} GB PersistentVolume")
  public void a_GB_PersistentVolume(Integer persistentVolumeSize) {
    ExecKubernetesCmd.createNamespace(NAMESPACE_NAME);

    persistentVolume =
        new PersistentVolumeBuilder()
            .withNewMetadata()
            .withName("persistent-volume-test")
            .addToLabels("rcnit-testing", "yup")
            .endMetadata()
            .withNewSpec()
            .addToCapacity(
                Collections.singletonMap("storage", new Quantity(persistentVolumeSize + "Gi")))
            .withAccessModes("ReadWriteOnce")
            .withPersistentVolumeReclaimPolicy("Retain")
            .withStorageClassName(defaultStorageClassName)
            .withNewLocal()
            .withPath("/mnt/disks/vol1")
            .endLocal()
            .withNewNodeAffinity()
            .withNewRequired()
            .addNewNodeSelectorTerm()
            .withMatchExpressions(
                Collections.singletonList(
                    new NodeSelectorRequirementBuilder()
                        .withKey("kubernetes.io/hostname")
                        .withOperator("In")
                        .withValues("minikube")
                        .build()))
            .endNodeSelectorTerm()
            .endRequired()
            .endNodeAffinity()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.persistentVolumes().createOrReplace(persistentVolume);
      log.info("Created '" + persistentVolume.getMetadata().getName() + "' PersistentVolume");
    }
  }

  @Given("A max {int} GB LimitRange")
  public void a_max_GB_LimitRange(Integer maxLimitRangeSize) {
    limitRange =
        new LimitRangeBuilder()
            .withNewMetadata()
            .withName("limit-range-test")
            .endMetadata()
            .withNewSpec()
            .addNewLimit()
            .withNewType("PersistentVolumeClaim")
            .withMax(Collections.singletonMap("storage", new Quantity(maxLimitRangeSize + "Gi")))
            .withMin(Collections.singletonMap("storage", new Quantity("2Gi")))
            .endLimit()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.limitRanges().inNamespace(NAMESPACE_NAME).createOrReplace(limitRange);
      log.info("Created '" + limitRange.getMetadata().getName() + "' LimitRange");
    }
  }

  @Then("A tolerated {int} GB PersistentVolumeClaim will get provisioned")
  public void a_tolerated_GB_PersistentVolumeClaim_will_get_provisioned(
      Integer persistentVolumeClaimSize) {
    PersistentVolumeClaim persistentVolumeClaim =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName("tolerated-claim-test-1")
            .withNamespace(NAMESPACE_NAME)
            .endMetadata()
            .withNewSpec()
            .withStorageClassName(defaultStorageClassName)
            .withAccessModes("ReadWriteOnce")
            .withNewResources()
            .addToRequests("storage", new Quantity(persistentVolumeClaimSize + "Gi"))
            .endResources()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      try {
        ExecKubernetesCmd.createPersistentVolumeClaim(persistentVolumeClaim, NAMESPACE_NAME);

        if (kubernetesClient
            .persistentVolumeClaims()
            .inNamespace(NAMESPACE_NAME)
            .withName(persistentVolumeClaim.getMetadata().getName())
            .get()
            .getStatus()
            .getPhase()
            .contentEquals("Bound")) {
          log.info(
              Status.PASSED
                  + "! The '"
                  + persistentVolumeClaim.getMetadata().getName()
                  + "' PersistentVolumeClaim was provisioned and is bound to the '"
                  + persistentVolume.getMetadata().getName()
                  + "' PersistentVolume!");

          ExecKubernetesCmd.getObjectEvents(
              persistentVolumeClaim.getMetadata().getName(), NAMESPACE_NAME);

          ExecKubernetesCmd.deletePersistentVolumeClaim(
              persistentVolumeClaim.getMetadata().getName(), NAMESPACE_NAME);
        }
      } catch (KubernetesClientException e) {
        log.error(
            Status.FAILED
                + "! The '"
                + persistentVolumeClaim.getMetadata().getName()
                + "' PersistentVolumeClaim was rejected!");
        log.error(e.getMessage());
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  @But("A prohibited {int} GB PersistentVolumeClaim will get rejected")
  public void a_prohibited_GB_PersistentVolumeClaim_will_get_rejected(
      Integer persistentVolumeClaimSize) {
    PersistentVolumeClaim persistentVolumeClaim =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName("prohibited-claim-test-1")
            .withNamespace(NAMESPACE_NAME)
            .endMetadata()
            .withNewSpec()
            .withStorageClassName(defaultStorageClassName)
            .withAccessModes("ReadWriteOnce")
            .withNewResources()
            .addToRequests("storage", new Quantity(persistentVolumeClaimSize + "Gi"))
            .endResources()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      try {
        ExecKubernetesCmd.createPersistentVolumeClaim(persistentVolumeClaim, NAMESPACE_NAME);

        if (kubernetesClient
            .persistentVolumeClaims()
            .inNamespace(NAMESPACE_NAME)
            .withName(persistentVolumeClaim.getMetadata().getName())
            .get()
            .getStatus()
            .getPhase()
            .contentEquals("Bound")) {
          log.error(
              Status.FAILED
                  + "! The '"
                  + persistentVolumeClaim.getMetadata().getName()
                  + "' PersistentVolumeClaim was provisioned and is bound to the '"
                  + persistentVolume.getMetadata().getName()
                  + "' PersistentVolume!");

          ExecKubernetesCmd.getObjectEvents(
              persistentVolumeClaim.getMetadata().getName(), NAMESPACE_NAME);

          throw new io.cucumber.java.PendingException();
        }
      } catch (KubernetesClientException e) {
        log.info(
            Status.PASSED
                + "! The '"
                + persistentVolumeClaim.getMetadata().getName()
                + "' PersistentVolumeClaim was rejected!");
        log.info(e.getMessage());
      }
    }
    ExecKubernetesCmd.deleteLimitRange(limitRange.getMetadata().getName(), NAMESPACE_NAME);
    ExecKubernetesCmd.deletePersistentVolume(persistentVolume.getMetadata().getName());
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }

  @Given("A max {int} GB storage ResourceQuota")
  public void a_max_GB_storage_ResourceQuota(Integer resourceQuotaSize) {
    resourceQuota =
        new ResourceQuotaBuilder()
            .withNewMetadata()
            .withName("resource-quota-test")
            .endMetadata()
            .withNewSpec()
            .addToHard(
                Collections.singletonMap(
                    "requests.storage", new Quantity(resourceQuotaSize + "Gi")))
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.resourceQuotas().inNamespace(NAMESPACE_NAME).createOrReplace(resourceQuota);
      log.info("Created '" + resourceQuota.getMetadata().getName() + "' ResourceQuota");
    }
  }

  @Then("A tolerated {int} GB PersistentVolumeClaim will get accepted")
  public void a_tolerated_GB_PersistentVolumeClaim_will_get_accepted(
      Integer persistentVolumeClaimSize) {
    PersistentVolumeClaim persistentVolumeClaim =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName("tolerated-claim-test-2")
            .withNamespace(NAMESPACE_NAME)
            .endMetadata()
            .withNewSpec()
            .withStorageClassName(defaultStorageClassName)
            .withAccessModes("ReadWriteOnce")
            .withNewResources()
            .addToRequests("storage", new Quantity(persistentVolumeClaimSize + "Gi"))
            .endResources()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      try {
        ExecKubernetesCmd.createPersistentVolumeClaim(persistentVolumeClaim, NAMESPACE_NAME);

        if (kubernetesClient
            .persistentVolumeClaims()
            .inNamespace(NAMESPACE_NAME)
            .withName(persistentVolumeClaim.getMetadata().getName())
            .get()
            .getStatus()
            .getPhase()
            .contentEquals("Bound")) {
          log.info(
              Status.PASSED
                  + "! The '"
                  + persistentVolumeClaim.getMetadata().getName()
                  + "' PersistentVolumeClaim was provisioned and is bound to the '"
                  + persistentVolume.getMetadata().getName()
                  + "' PersistentVolume!");

          ExecKubernetesCmd.getObjectEvents(
              persistentVolumeClaim.getMetadata().getName(), NAMESPACE_NAME);

          ExecKubernetesCmd.deletePersistentVolumeClaim(
              persistentVolumeClaim.getMetadata().getName(), NAMESPACE_NAME);
        }
      } catch (KubernetesClientException e) {
        log.error(
            Status.FAILED
                + "! The '"
                + persistentVolumeClaim.getMetadata().getName()
                + "' PersistentVolumeClaim was rejected!");
        log.error(e.getMessage());
        throw new io.cucumber.java.PendingException();
      }
    }
  }

  @Then("A prohibited {int} GB PersistentVolumeClaim will be denied")
  public void a_prohibited_GB_PersistentVolumeClaim_will_be_denied(
      Integer persistentVolumeClaimSize) {
    PersistentVolumeClaim persistentVolumeClaim =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName("prohibited-claim-test-2")
            .withNamespace(NAMESPACE_NAME)
            .endMetadata()
            .withNewSpec()
            .withStorageClassName(defaultStorageClassName)
            .withAccessModes("ReadWriteOnce")
            .withNewResources()
            .addToRequests("storage", new Quantity(persistentVolumeClaimSize + "Gi"))
            .endResources()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      try {
        ExecKubernetesCmd.createPersistentVolumeClaim(persistentVolumeClaim, NAMESPACE_NAME);

        if (kubernetesClient
            .persistentVolumeClaims()
            .inNamespace(NAMESPACE_NAME)
            .withName(persistentVolumeClaim.getMetadata().getName())
            .get()
            .getStatus()
            .getPhase()
            .contentEquals("Bound")) {
          log.error(
              Status.FAILED
                  + "! The '"
                  + persistentVolumeClaim.getMetadata().getName()
                  + "' PersistentVolumeClaim was provisioned and is bound to the '"
                  + persistentVolume.getMetadata().getName()
                  + "' PersistentVolume!");

          ExecKubernetesCmd.getObjectEvents(
              persistentVolumeClaim.getMetadata().getName(), NAMESPACE_NAME);

          throw new io.cucumber.java.PendingException();
        }
      } catch (KubernetesClientException e) {
        log.info(
            Status.PASSED
                + "! The '"
                + persistentVolumeClaim.getMetadata().getName()
                + "' PersistentVolumeClaim was rejected!");
        log.info(e.getMessage());
      }
    }
    ExecKubernetesCmd.deleteResourceQuota(resourceQuota.getMetadata().getName(), NAMESPACE_NAME);
    ExecKubernetesCmd.deletePersistentVolume(persistentVolume.getMetadata().getName());
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }

  @Then("A PersistentVolumeClaim with no storage specification will get the default one")
  public void
      a_PersistentVolumeClaim_with_no_storage_class_specification_will_get_the_default_one() {
    PersistentVolumeClaim persistentVolumeClaim =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName("pvc-with-no-class-specification-test-1")
            .withNamespace(NAMESPACE_NAME)
            .endMetadata()
            .withNewSpec()
            .withAccessModes("ReadWriteOnce")
            .withNewResources()
            .addToRequests("storage", new Quantity("3Gi"))
            .endResources()
            .endSpec()
            .build();

    ExecKubernetesCmd.createPersistentVolumeClaim(persistentVolumeClaim, NAMESPACE_NAME);

    String storageClassName;

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      storageClassName =
          kubernetesClient
              .persistentVolumeClaims()
              .inNamespace(NAMESPACE_NAME)
              .withName(persistentVolumeClaim.getMetadata().getName())
              .get()
              .getSpec()
              .getStorageClassName();
    }
    boolean pvcGotDefaultClass = storageClassName.equals(defaultStorageClassName);

    ExecShellCmd describePersistentVolumeClaim = new ExecShellCmd();
    describePersistentVolumeClaim.execute(
        "kubectl describe pvc "
            + persistentVolumeClaim.getMetadata().getName()
            + " --namespace "
            + NAMESPACE_NAME);
    String describePersistentVolumeClaimOutput = describePersistentVolumeClaim.returnAsString();

    ExecKubernetesCmd.getObjectEvents(
        persistentVolumeClaim.getMetadata().getName(), NAMESPACE_NAME);

    if (pvcGotDefaultClass) {
      log.info(
          Status.PASSED
              + "! The '"
              + persistentVolumeClaim.getMetadata().getName()
              + "' PersistentVolumeClaim was assigned the '"
              + storageClassName
              + "' default storage class!\n"
              + describePersistentVolumeClaimOutput);
    } else {
      log.error(
          Status.FAILED
              + "! The '"
              + persistentVolumeClaim.getMetadata().getName()
              + "' PersistentVolumeClaim was assigned the '"
              + storageClassName
              + "' storage class!\n"
              + describePersistentVolumeClaimOutput);
      throw new io.cucumber.java.PendingException();
    }

    ExecKubernetesCmd.deletePersistentVolumeClaim(
        persistentVolumeClaim.getMetadata().getName(), NAMESPACE_NAME);
  }

  @When("I do not have a default storage class")
  public void i_do_not_have_a_default_storage_class() {
    // See https://kubernetes.io/docs/tasks/administer-cluster/change-default-storage-class/
    log.info(
        "Making sure there is no default storage class in the '"
            + NAMESPACE_NAME
            + "' namespace...");

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      List<StorageClass> storageClassList =
          kubernetesClient.storage().storageClasses().list().getItems();

      for (StorageClass storageClass : storageClassList) {
        kubernetesClient
            .storage()
            .storageClasses()
            .inNamespace(NAMESPACE_NAME)
            .withName(storageClass.getMetadata().getName())
            .edit()
            .editMetadata()
            .addToAnnotations(
                Collections.singletonMap("storageclass.kubernetes.io/is-default-class", "false"))
            .endMetadata()
            .done();
        log.info("Storage class '" + storageClass.getMetadata().getName() + "' is now non-default");
      }

      for (StorageClass storageClass : storageClassList) {
        if (kubernetesClient
            .storage()
            .storageClasses()
            .inNamespace(NAMESPACE_NAME)
            .withName(storageClass.getMetadata().getName())
            .get()
            .getMetadata()
            .getAnnotations()
            .toString()
            .contains("storageclass.kubernetes.io/is-default-class=true")) {
          log.error("The storage class '" + storageClass.getMetadata().getName() + "' is default!");
          throw new io.cucumber.java.PendingException();
        }
      }
    }
  }

  @Then("The DefaultStorageClass admission controller does not do anything")
  public void the_DefaultStorageClass_admission_controller_does_not_do_anything() {
    String pvcStorageClassName;

    PersistentVolumeClaim persistentVolumeClaim =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName("pvc-with-no-class-specification-test-2")
            .withNamespace(NAMESPACE_NAME)
            .endMetadata()
            .withNewSpec()
            .withAccessModes("ReadWriteOnce")
            .withNewResources()
            .addToRequests("storage", new Quantity("3Gi"))
            .endResources()
            .endSpec()
            .build();
    ExecKubernetesCmd.createPersistentVolumeClaim(persistentVolumeClaim, NAMESPACE_NAME);

    ExecShellCmd describePersistentVolumeClaim = new ExecShellCmd();
    describePersistentVolumeClaim.execute(
        "kubectl describe pvc "
            + persistentVolumeClaim.getMetadata().getName()
            + " --namespace "
            + NAMESPACE_NAME);
    String describePersistentVolumeClaimOutput = describePersistentVolumeClaim.returnAsString();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      pvcStorageClassName =
          kubernetesClient
              .persistentVolumeClaims()
              .inNamespace(NAMESPACE_NAME)
              .withName(persistentVolumeClaim.getMetadata().getName())
              .get()
              .getSpec()
              .getStorageClassName();
    }

    if (pvcStorageClassName == null) {
      log.info(
          Status.PASSED
              + "! The '"
              + persistentVolumeClaim.getMetadata().getName()
              + "' PersistentVolumeClaim was provisioned with no storage class!\n"
              + describePersistentVolumeClaimOutput);
    } else {
      log.error(
          Status.FAILED
              + "! The '"
              + persistentVolumeClaim.getMetadata().getName()
              + "' PersistentVolumeClaim was assigned the '"
              + pvcStorageClassName
              + "' storage class!\n"
              + describePersistentVolumeClaimOutput);
      /*
      getNamespacedEvents.returnAsString() is not present in the 'if' statement because it doesn't
      output any events. Look at the describePersistentVolumeClaimOutput from the 'if' statement.
      */
      ExecKubernetesCmd.getObjectEvents(
          persistentVolumeClaim.getMetadata().getName(), NAMESPACE_NAME);
      log.error(describePersistentVolumeClaimOutput);
      throw new io.cucumber.java.PendingException();
    }

    ExecKubernetesCmd.deletePersistentVolumeClaim(
        persistentVolumeClaim.getMetadata().getName(), NAMESPACE_NAME);
  }

  @When("I have multiple default storage classes")
  public void i_have_multiple_default_storage_classes() {
    log.info(
        "Creating '"
            + SECOND_STORAGE_CLASS_NAME
            + "' storage class in the '"
            + NAMESPACE_NAME
            + "' namespace...");
    ObjectMeta metadata = new ObjectMeta();
    metadata.setName(SECOND_STORAGE_CLASS_NAME);

    StorageClass storageClass =
        new StorageClassBuilder()
            .withApiVersion("storage.k8s.io/v1")
            .withKind("StorageClass")
            .withMetadata(metadata)
            .withProvisioner("k8s.io/minikube-hostpath")
            .withReclaimPolicy("Delete")
            .withVolumeBindingMode("Immediate")
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      kubernetesClient.storage().storageClasses().inNamespace(NAMESPACE_NAME).create(storageClass);

      log.info(
          "Making sure that all storage classes in the '"
              + NAMESPACE_NAME
              + "' namespace are default...");
      List<StorageClass> storageClassList =
          kubernetesClient.storage().storageClasses().list().getItems();

      for (StorageClass sc : storageClassList) {
        kubernetesClient
            .storage()
            .storageClasses()
            .inNamespace(NAMESPACE_NAME)
            .withName(sc.getMetadata().getName())
            .edit()
            .editMetadata()
            .addToAnnotations(
                Collections.singletonMap("storageclass.kubernetes.io/is-default-class", "true"))
            .endMetadata()
            .done();
        log.info("Storage class '" + sc.getMetadata().getName() + "' is now default");
      }

      for (StorageClass sc : storageClassList) {
        if (kubernetesClient
            .storage()
            .storageClasses()
            .inNamespace(NAMESPACE_NAME)
            .withName(sc.getMetadata().getName())
            .get()
            .getMetadata()
            .getAnnotations()
            .toString()
            .contains("storageclass.kubernetes.io/is-default-class=false")) {
          log.error("The storage class '" + sc.getMetadata().getName() + "' is non-default!");
          throw new io.cucumber.java.PendingException();
        }
      }
    }
  }

  @Then("The DefaultStorageClass admission controller will reject the PersistentVolumeClaim")
  public void the_DefaultStorageClass_admission_controller_will_reject_the_PersistentVolumeClaim() {
    PersistentVolumeClaim persistentVolumeClaim =
        new PersistentVolumeClaimBuilder()
            .withNewMetadata()
            .withName("pvc-with-no-class-specification-test-3")
            .withNamespace(NAMESPACE_NAME)
            .endMetadata()
            .withNewSpec()
            .withAccessModes("ReadWriteOnce")
            .withNewResources()
            .addToRequests("storage", new Quantity("3Gi"))
            .endResources()
            .endSpec()
            .build();

    try (final KubernetesClient kubernetesClient = new DefaultKubernetesClient()) {
      try {
        ExecKubernetesCmd.createPersistentVolumeClaim(persistentVolumeClaim, NAMESPACE_NAME);

        if (kubernetesClient
            .persistentVolumeClaims()
            .inNamespace(NAMESPACE_NAME)
            .withName(persistentVolumeClaim.getMetadata().getName())
            .get()
            .getStatus()
            .getPhase()
            .contentEquals("Bound")) {
          log.error(
              Status.FAILED
                  + "! The '"
                  + persistentVolumeClaim.getMetadata().getName()
                  + "' PersistentVolumeClaim was provisioned and is bound to the '"
                  + persistentVolume.getMetadata().getName()
                  + "' PersistentVolume!");

          ExecKubernetesCmd.getObjectEvents(
              persistentVolumeClaim.getMetadata().getName(), NAMESPACE_NAME);

          throw new io.cucumber.java.PendingException();
        }
      } catch (KubernetesClientException e) {
        log.info(
            Status.PASSED
                + "! The '"
                + persistentVolumeClaim.getMetadata().getName()
                + "' PersistentVolumeClaim was rejected!");
        log.info(e.getMessage());
      }
    }
    ExecKubernetesCmd.deleteStorageClass(SECOND_STORAGE_CLASS_NAME, NAMESPACE_NAME);
    ExecKubernetesCmd.deletePersistentVolume(persistentVolume.getMetadata().getName());
    ExecKubernetesCmd.deleteNamespace(NAMESPACE_NAME);
  }
}
