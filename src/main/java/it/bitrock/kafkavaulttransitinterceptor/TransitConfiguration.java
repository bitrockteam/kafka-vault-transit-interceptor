package it.bitrock.kafkavaulttransitinterceptor;

import java.util.AbstractList;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

  final Map<String, ?> configs;

  TransitConfiguration(Map<String, ?> configs) {
    this.configs = configs;
  }

  String getStringList(String configKey) {
    final String value;
    final Object valueObject = configs.get(configKey);
    if (valueObject instanceof AbstractList) {
      AbstractList valueList = (AbstractList) valueObject;
      value = String.join(",", valueList);
    } else {
      LOGGER.warn("{} of type ArrayList is not found in properties", configKey);
      value = null;
    }
    return value;
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

  String getString(String configKey) {
    return getStringOrDefault(configKey, null);
  }
}
