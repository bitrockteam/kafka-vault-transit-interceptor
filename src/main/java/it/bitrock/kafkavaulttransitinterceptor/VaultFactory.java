package it.bitrock.kafkavaulttransitinterceptor;


import org.springframework.vault.authentication.TokenAuthentication;
import org.springframework.vault.client.VaultEndpoint;
import org.springframework.vault.core.VaultTemplate;
import org.springframework.vault.core.VaultTransitOperations;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import static it.bitrock.kafkavaulttransitinterceptor.TransitConfiguration.TRANSIT_MOUNT_CONFIG;
import static it.bitrock.kafkavaulttransitinterceptor.TransitConfiguration.TRANSIT_MOUNT_DEFAULT;

class VaultFactory {

  VaultTemplate template;
  VaultTransitOperations transit;
  final TransitConfiguration configuration;

  VaultFactory(Map<String, ?> configs) throws URISyntaxException {
    this.configuration = new TransitConfiguration(configs);
    this.template = new VaultTemplate(VaultEndpoint.from(new URI(System.getenv("VAULT_ADDR"))), new TokenAuthentication(System.getenv("VAULT_TOKEN")));
    this.transit = template.opsForTransit(configuration.getStringOrDefault(TRANSIT_MOUNT_CONFIG, TRANSIT_MOUNT_DEFAULT));
  }
}
