package it.bitrock.kafkavaulttransitinterceptor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Tracing Configuration wraps properties provided by a Kafka Client and enable access to
 * configuration values.
 */
public class TransitConfiguration {
  static final Logger LOGGER = LoggerFactory.getLogger(TransitConfiguration.class);

  public static final String TRANSIT_MOUNT_CONFIG = "vault.transit.mount";
  public static final String TRANSIT_MOUNT_DEFAULT = "transit";
  public static final String TRANSIT_KEY_CONFIG = "vault.transit.key";
  public static final String TRANSIT_KEY_DEFAULT = "default";
  public static final String TRANSIT_KEY_TTL_CONFIG = "vault.transit.key.ttl";
  public static final Long TRANSIT_KEY_TTL_DEFAULT = 5L * 60000;


  final Map<String, ?> configs;

  TransitConfiguration(Map<String, ?> configs) {
    this.configs = configs;
  }

  /**
   * @return Value as String. If not found, then null is returned.
   */
  String getStringOrDefault(String configKey, String defaultValue) {
    final String value;
    final Object valueObject = configs.get(configKey);
    if (valueObject instanceof String) {
      value = (String) valueObject;
    } else {
      LOGGER.warn("{} of type String is not found in properties", configKey);
      value = defaultValue;
    }
    return value;
  }

  /**
   * @return Value as Long. If not found, then null is returned.
   */
  Long getLongOrDefault(String configKey, Long defaultValue) {
    final long value;
    final Object valueObject = configs.get(configKey);
    if (valueObject instanceof Long) {
      value = (Long) valueObject;
    } else {
      LOGGER.warn("{} of type String is not found in properties", configKey);
      value = defaultValue;
    }
    return value;
  }

  String getString(String configKey) {
    return getStringOrDefault(configKey, null);
  }
}
