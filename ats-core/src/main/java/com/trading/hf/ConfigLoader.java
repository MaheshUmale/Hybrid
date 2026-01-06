package com.trading.hf;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {

    private static final Properties properties = new Properties();
    private static final String CONFIG_FILE = "config.properties";

    static {
        InputStream input = null;
        try {
            // First, try loading from the classpath
            input = ConfigLoader.class.getClassLoader().getResourceAsStream(CONFIG_FILE);

            if (input == null) {
                // If not found, try loading from the filesystem (current directory)
                System.out.println("Could not find " + CONFIG_FILE + " on classpath, trying filesystem...");
                try {
                    input = new FileInputStream(CONFIG_FILE);
                } catch (Exception e) {
                    System.out.println("Could not find " + CONFIG_FILE + " on filesystem either.");
                    // Still continue, might be using defaults or env vars
                }
            }

            if (input != null) {
                properties.load(input);
            } else {
                 System.out.println("WARNING: config.properties not found. Using default values.");
            }

        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static String getProperty(String key) {
        String sysProp = System.getProperty(key);
        if (sysProp != null) return sysProp;
        return properties.getProperty(key);
    }

    public static String getProperty(String key, String defaultValue) {
        String sysProp = System.getProperty(key);
        if (sysProp != null) return sysProp;
        return properties.getProperty(key, defaultValue);
    }

    public static boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value);
    }
}
