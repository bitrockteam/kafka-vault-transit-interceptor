package it.bitrock.kafkavaulttransitinterceptor;

import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class VaultFactory {
    static final Logger LOGGER = LoggerFactory.getLogger(VaultFactory.class);

    final Vault vault;
    final TransitConfiguration configuration;


    VaultFactory(TransitConfiguration configuration) {
        this.configuration = configuration;
        try {
            VaultConfig config = new VaultConfig().sslConfig(new SslConfig().build()).build();
            this.vault = new Vault(config, 1);
        } catch (VaultException e) {
            LOGGER.error("Failed to initialize Vault", e);
            throw new RuntimeException("Failed to initialize Vault");
        }
    }
}
