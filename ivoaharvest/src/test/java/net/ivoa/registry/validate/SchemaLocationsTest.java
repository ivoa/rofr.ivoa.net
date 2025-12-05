package net.ivoa.registry.validate;

import org.junit.Assert;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Path;
import java.util.Properties;

public class SchemaLocationsTest {
    @Test
    public void loadSchemaLocationsExist() throws Exception {
        final Properties properties = new Properties();
        final Path registryManifestPath = Path.of("/net/ivoa/registry/validate/registrySchemaLocation.txt");
        try (final BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                     SchemaLocationsTest.class.getResourceAsStream(registryManifestPath.toString())))) {
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                final String entry = line.trim();

                if (entry.isEmpty() || entry.startsWith("#")) {
                    continue;
                }

                final String[] parts = line.split(" ");

                if (parts.length != 2) {
                    throw new IllegalArgumentException("Invalid schema location entry: " + line);
                }

                final String namespace = parts[0].trim();
                final String location = parts[1].trim();
                properties.put(namespace, location);
            }
        }

        Assert.assertFalse("Schema locations should not be empty", properties.isEmpty());
        properties.forEach((schemaURI, location) -> {
            final Path schemaPath = Path.of(registryManifestPath.getParent().toString(), location.toString());
            final URL schemaLocation = SchemaLocationsTest.class.getResource(schemaPath.toString());
            Assert.assertNotNull("Schema file should exist for " + schemaURI + " at " + location, schemaLocation);
        });
    }
}
