package sda.datastreaming.processor;

import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.behavior.SupportsBatching;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.annotation.lifecycle.OnScheduled;
import org.apache.nifi.processor.ProcessorInitializationContext;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.processor.io.StreamCallback;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;
import org.json.JSONArray;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;



@SupportsBatching
@Tags({"travel", "json", "processor", "distance", "price"})
@CapabilityDescription("Processes travel data in JSON format, calculating distance and travel price.")
public class TravelProcessor extends AbstractProcessor {

    public static final Relationship SUCCESS = new Relationship.Builder()
            .name("success")
            .description("All successful FlowFiles are routed to this relationship")
            .build();

    @Override
    public Set<Relationship> getRelationships() {
        final Set<Relationship> relationships = new HashSet<>();
        relationships.add(SUCCESS);
        return Collections.unmodifiableSet(relationships);
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) {
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        flowFile = session.write(flowFile, new StreamCallback() {
            @Override
            public void process(InputStream in, OutputStream out) {
                try {
                    // Read all bytes from the InputStream
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] data = new byte[1024];
                    int nRead;
                    while ((nRead = in.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    buffer.flush();
                    String inputJson = new String(buffer.toByteArray(), StandardCharsets.UTF_8);

                    // Process the JSON
                    String outputJson = processJson(inputJson);
                    out.write(outputJson.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    getLogger().error("Failed to process JSON", e);
                }
            }
        });

        session.transfer(flowFile, SUCCESS);
    }

    public static String processJson(String jsonString) {
        JSONObject jsonObject = new JSONObject(jsonString);
        JSONArray dataArray = jsonObject.getJSONArray("data");
        JSONObject firstEntry = dataArray.getJSONObject(0);
        JSONObject clientProperties = firstEntry.getJSONObject("properties-client");
        JSONObject driverProperties = firstEntry.getJSONObject("properties-driver");

        // Extraction des coordonnées
        double clientLongitude = clientProperties.getDouble("logitude");
        double clientLatitude = clientProperties.getDouble("latitude");
        double driverLongitude = driverProperties.getDouble("logitude");
        double driverLatitude = driverProperties.getDouble("latitude");

        // Calcul de la distance
        double distance = calculateDistance(clientLatitude, clientLongitude, driverLatitude, driverLongitude);

        // Arrondissement de la distance à 3 chiffres après la virgule
        BigDecimal roundedDistance = new BigDecimal(distance).setScale(3, RoundingMode.HALF_UP);

        // Extraction du prix par km
        double prixBasePerKm = firstEntry.getDouble("prix_base_per_km");

        // Calcul du prix total du voyage
        BigDecimal prixTravel = new BigDecimal(prixBasePerKm).multiply(new BigDecimal(roundedDistance.toString()));

        // Modification du JSON pour inclure "location", "distance", et "prix_travel"
        clientProperties.remove("logitude");
        clientProperties.remove("latitude");
        driverProperties.remove("logitude");
        driverProperties.remove("latitude");

        String clientLocation = clientLongitude + ", " + clientLatitude;
        String driverLocation = driverLongitude + ", " + driverLatitude;

        clientProperties.put("location", clientLocation);
        driverProperties.put("location", driverLocation);

        firstEntry.put("distance", roundedDistance);
        firstEntry.put("prix_travel", prixTravel.setScale(2, RoundingMode.HALF_UP));

        return jsonObject.toString(4); // Retourne le JSON modifié en format lisible
    }

    // Méthode pour calculer la distance en utilisant la formule de Haversine
    public static double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        final double EARTH_RADIUS = 6371.0; // Rayon de la Terre en kilomètres
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);

        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c;
    }
}