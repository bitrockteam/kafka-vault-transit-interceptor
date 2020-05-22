package it.bitrock.kafkavaulttransitinterceptor.util;

import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import com.bettercloud.vault.json.Json;
import com.bettercloud.vault.json.JsonObject;
import com.github.dockerjava.api.model.Capability;
import java.io.IOException;
import java.net.HttpURLConnection;
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

  public static final String DEFAULT_IMAGE_AND_TAG = "vault:1.4.2";

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
    JsonObject initJson = Json.parse(stdout).asObject();
    this.unsealKey = initJson.get("unseal_keys_b64").asArray().get(0).asString();
    rootToken = initJson.get("root_token").asString();

    System.out.println("Root token: " + rootToken);

    // Unseal the Vault server
    runCommand("vault", "operator", "unseal", unsealKey);
  }


  public void setupBackendTransit() throws IOException, InterruptedException {
    runCommand("vault", "login", rootToken);

    runCommand("vault", "secrets", "enable", "transit");
  }


  public Vault getVault(final VaultConfig config, final Integer maxRetries, final Integer retryMillis) {
    Vault vault = new Vault(config);
    if (maxRetries != null && retryMillis != null) {
      vault = vault.withRetries(maxRetries, retryMillis);
    } else if (maxRetries != null) {
      vault = vault.withRetries(maxRetries, RETRY_MILLIS);
    } else if (retryMillis != null) {
      vault = vault.withRetries(MAX_RETRIES, retryMillis);
    }
    return vault;
  }

  public Vault getVault() throws VaultException {
    final VaultConfig config =
      new VaultConfig()
        .address(getAddress())
        .openTimeout(5)
        .readTimeout(30)
        .sslConfig(new SslConfig().build())
        .build();
    return getVault(config, MAX_RETRIES, RETRY_MILLIS);
  }

  public VaultConfig getVaultConfig() throws VaultException {
    return new VaultConfig()
      .address(getAddress())
      .openTimeout(5)
      .readTimeout(30)
      .sslConfig(new SslConfig().build())
      .build();
  }

  public Vault getVault(final String token) throws VaultException {
    final VaultConfig config =
      new VaultConfig()
        .address(getAddress())
        .token(token)
        .openTimeout(5)
        .readTimeout(30)
        .sslConfig(new SslConfig().build())
        .build();
    return new Vault(config).withRetries(MAX_RETRIES, RETRY_MILLIS);
  }

  public Vault getRootVaultWithCustomVaultConfig(VaultConfig vaultConfig) throws VaultException {
    final VaultConfig config =
      vaultConfig
        .address(getAddress())
        .token(rootToken)
        .openTimeout(5)
        .readTimeout(30)
        .sslConfig(new SslConfig().build())
        .build();
    return new Vault(config).withRetries(MAX_RETRIES, RETRY_MILLIS);
  }

  public Vault getRootVault() throws VaultException {
    return getVault(rootToken).withRetries(MAX_RETRIES, RETRY_MILLIS);
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
