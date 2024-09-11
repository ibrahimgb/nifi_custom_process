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
import sda.datastreaming.Travel;

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
                    String outputJson = Travel.processJson(inputJson);
                    out.write(outputJson.getBytes(StandardCharsets.UTF_8));
                } catch (Exception e) {
                    getLogger().error("Failed to process JSON", e);
                }
            }
        });

        session.transfer(flowFile, SUCCESS);
    }

}