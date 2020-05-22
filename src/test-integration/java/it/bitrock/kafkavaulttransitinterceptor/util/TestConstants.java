package it.bitrock.kafkavaulttransitinterceptor.util;

import org.testcontainers.containers.Network;
import org.testcontainers.utility.TestEnvironment;

public interface TestConstants {

  int MAX_RETRIES = 5;
  int RETRY_MILLIS = 1000;

  String CONTAINER_CONFIG_FILE = "/vault/config/config.json";

  Network CONTAINER_NETWORK = Network.newNetwork();
  boolean DOCKER_AVAILABLE = TestEnvironment.dockerApiAtLeast("1.10");
}
