package io.github.ddebree.pact.proxy.service;

import au.com.dius.pact.model.PactWriter;
import au.com.dius.pact.model.RequestResponsePact;
import io.github.ddebree.pact.proxy.filters.PactRecorderFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import static org.springframework.util.ReflectionUtils.rethrowRuntimeException;

@Service
public class PactResultWriter {

    private static final Logger LOGGER = LoggerFactory.getLogger(PactRecorderFilter.class);

    private final String outputPath;

    public PactResultWriter(@Value("${outputPath}") String outputPath) {
        this.outputPath = outputPath;

        File outputFolder = new File(outputPath);
        if ( ! outputFolder.exists()) {
            outputFolder.mkdir();
        }
        if ( ! outputFolder.isDirectory()) {
            throw new RuntimeException("Expected output folder " + outputFolder + " to be a directory");
        }
    }

    public void writePact(String url, long requestId, RequestResponsePact pact) {
        String filename = outputPath + "/" + url.replaceAll("[^\\p{Alnum}]", "_") + requestId;
        try {
            String pactDefinition;
            try (StringWriter strOut = new StringWriter()) {
                PactWriter.writePact(pact, new PrintWriter(strOut));
                pactDefinition = strOut.toString();
            }
            try (PrintWriter out = new PrintWriter(filename)) {
                out.println( pactDefinition );
            }
            LOGGER.debug("Pact file: {}", pactDefinition);
        } catch (IOException e) {
            rethrowRuntimeException(e);
        }

    }

}
