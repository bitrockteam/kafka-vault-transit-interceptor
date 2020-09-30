package it.bitrock.kafkavaulttransitinterceptor.util;

import com.github.dockerjava.api.model.Capability;
import java.io.IOException;
import java.net.HttpURLConnection;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.BindMode;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.lifecycle.TestDescription;
import org.testcontainers.lifecycle.TestLifecycleAware;

import static org.junit.Assume.assumeTrue;

public class VaultContainer extends GenericContainer<VaultContainer> implements TestConstants, TestLifecycleAware {

  private static final Logger LOGGER = LoggerFactory.getLogger(VaultContainer.class);

  public static final String DEFAULT_IMAGE_AND_TAG = "vault:1.5.3";

  public static String rootToken;
  private String unsealKey;

  /**
   * Establishes a running Docker container, hosting a Vault server instance.
   */
  public VaultContainer(String image) {
    super(image);
    this.withNetwork(CONTAINER_NETWORK)
      .withNetworkAliases("vault")
      .withClasspathResourceMapping("/config.json", CONTAINER_CONFIG_FILE, BindMode.READ_ONLY)
      .withCreateContainerCmdModifier(command -> command.withCapAdd(Capability.IPC_LOCK))
      .withEnv("VAULT_ADDR", "http://localhost:8200")
      .withEnv("VAULT_CONFIG_DIR", CONTAINER_CONFIG_FILE)
      .withCommand("server")
      .withLogConsumer(new Slf4jLogConsumer(LOGGER))
      .waitingFor(
        new HttpWaitStrategy()
          .forPort(8200)
          .forPath("/v1/sys/seal-status")
          .forStatusCode(HttpURLConnection.HTTP_OK)
      );
  }

  public VaultContainer() {
    this(DEFAULT_IMAGE_AND_TAG);
  }

  public void initAndUnsealVault() throws IOException, InterruptedException {
    // Initialize the Vault server
    final Container.ExecResult initResult = runCommand("vault", "operator", "init", "-key-shares=1", "-key-threshold=1", "-format=json");
    final String stdout = initResult.getStdout().replaceAll("\\r?\\n", "");
    JSONObject initJson = new JSONObject(stdout);
    this.unsealKey = initJson.getJSONArray("unseal_keys_b64").get(0).toString();
    rootToken = initJson.get("root_token").toString();

    System.out.println("Root token: " + rootToken);

    // Unseal the Vault server
    runCommand("vault", "operator", "unseal", unsealKey);
  }


  public void setupBackendTransit() throws IOException, InterruptedException {
    runCommand("vault", "login", rootToken);

    runCommand("vault", "secrets", "enable", "transit");
  }


  public void rotateKey(String key) throws IOException, InterruptedException  {
    runCommand("vault", "login", rootToken);

    runCommand("vault", "write", "-f", "transit/keys/"+key+"/rotate");
  }

  public void deleteKey(String key) throws IOException, InterruptedException  {
    runCommand("vault", "login", rootToken);

    runCommand("vault", "write", "transit/keys/"+key+"/config", "deletion_allowed=true");
    runCommand("vault", "delete", "transit/keys/"+key);
  }

  public String getAddress() {
    return String.format("http://%s:%d", getContainerIpAddress(), getMappedPort(8200));
  }

  public String getUnsealKey() {
    return unsealKey;
  }

  private Container.ExecResult runCommand(final String... command) throws IOException, InterruptedException {
    LOGGER.info("Command: {}", String.join(" ", command));
    final Container.ExecResult result = execInContainer(command);
    final String out = result.getStdout();
    final String err = result.getStderr();
    if (out != null && !out.isEmpty()) {
      LOGGER.info("Command stdout: {}", result.getStdout());
    }
    if (err != null && !err.isEmpty()) {
      LOGGER.info("Command stderr: {}", result.getStderr());
    }
    return result;
  }

  @Override
  public void beforeTest(TestDescription description) {
    assumeTrue(DOCKER_AVAILABLE);
  }
}
