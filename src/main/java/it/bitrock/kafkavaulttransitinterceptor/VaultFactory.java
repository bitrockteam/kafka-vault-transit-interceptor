package it.bitrock.kafkavaulttransitinterceptor;

import com.bettercloud.vault.SslConfig;
import com.bettercloud.vault.Vault;
import com.bettercloud.vault.VaultConfig;
import com.bettercloud.vault.VaultException;

class VaultFactory {
    private static Vault vault = null;

    static Vault getInstance(){
        if (vault == null) {
            initialize();
        }
        return vault;
    }

    private static void initialize() {
        try {
            VaultConfig config = new VaultConfig().sslConfig(new SslConfig().build()).build();
            vault = new Vault(config);
        } catch (VaultException e) {
            throw new RuntimeException("Failed to initialize Vault");
        }
    }
}
