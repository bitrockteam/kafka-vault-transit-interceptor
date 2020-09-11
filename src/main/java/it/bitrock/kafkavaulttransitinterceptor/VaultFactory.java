package it.bitrock.kafkavaulttransitinterceptor;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTransitOperations;

import java.net.URI;
import java.net.URISyntaxException;

import static it.bitrock.kafkavaulttransitinterceptor.TransitConfiguration.TRANSIT_MOUNT_CONFIG;
import static it.bitrock.kafkavaulttransitinterceptor.TransitConfiguration.TRANSIT_MOUNT_DEFAULT;

class VaultFactory {
  static final Logger LOGGER = LoggerFactory.getLogger(VaultFactory.class);

  final TransitConfiguration configuration;
  VaultTemplate template;
  VaultTransitOperations transit;

  VaultFactory(TransitConfiguration configuration) throws URISyntaxException {
    this.configuration = configuration;
    this.template = new VaultTemplate(VaultEndpoint.from(new URI(System.getenv("VAULT_ADDR"))), new TokenAuthentication(System.getenv("VAULT_TOKEN")));
    this.transit = template.opsForTransit(configuration.getStringOrDefault(TRANSIT_MOUNT_CONFIG, TRANSIT_MOUNT_DEFAULT));
  }
}
