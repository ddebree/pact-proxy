package io.github.ddebree.pact.proxy.filters;

import javax.servlet.http.HttpServletRequest;

import au.com.dius.pact.consumer.ConsumerPactBuilder;
import au.com.dius.pact.model.PactWriter;
import au.com.dius.pact.model.RequestResponsePact;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.ZuulFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Random;

import static org.springframework.util.ReflectionUtils.rethrowRuntimeException;

@Component
public class PactRecorderFilter extends ZuulFilter {

    private static final Logger log = LoggerFactory.getLogger(PactRecorderFilter.class);
    private static final Random random = new Random();

    private final String outputPath;

    public PactRecorderFilter(@Value("${outputPath}") String outputPath) {
        this.outputPath = outputPath;

        File outputFolder = new File(outputPath);
        if ( ! outputFolder.exists()) {
            outputFolder.mkdir();
        }
        if ( ! outputFolder.isDirectory()) {
            throw new RuntimeException("Expected output folder " + outputFolder + " to be a directory");
        }
    }


    @Override
    public String filterType() {
        return "post";
    }

    @Override
    public int filterOrder() {
        return 1;
    }

    @Override
    public boolean shouldFilter() {
        RequestContext ctx = RequestContext.getCurrentContext();

        log.info("Headers: {}", ctx.getZuulResponseHeaders());

        return true;
    }

    @Override
    public Object run() {
        try {
            RequestContext context = RequestContext.getCurrentContext();
            HttpServletRequest request = context.getRequest();

            long id = Math.abs(random.nextLong());
            String url = request.getRequestURL().toString();
            String filename = outputPath + "/" + url.replaceAll("[^\\p{Alnum}]", "_") + id;

            log.info("{} request to {}, filename: {}", request.getMethod(), url, filename);

            InputStream in = (InputStream) context.get("requestEntity");
            if (in == null) {
                in = context.getRequest().getInputStream();
            }
            String requestBody = StreamUtils.copyToString(in, Charset.forName("UTF-8"));

            String responseBody = null;
            InputStream stream = context.getResponseDataStream();
            if (stream != null) {
                responseBody = StreamUtils.copyToString(stream, Charset.forName("UTF-8"));
                context.setResponseBody(responseBody);
            }

            RequestResponsePact pact = ConsumerPactBuilder
                    .consumer("client")
                    .hasPactWith("server")
                    .uponReceiving("a request for " + id)
                        .path(url)
                        .method(request.getMethod())
                        .body(requestBody)
                    .willRespondWith()
                        .status(context.getResponseStatusCode())
                        .body(responseBody != null ? responseBody : "")
                    .toFragment().toPact();

            String pactDefinition = null;
            try (StringWriter strOut = new StringWriter()) {
                PactWriter.writePact(pact, new PrintWriter(strOut));
                pactDefinition = strOut.toString();
            }
            try (PrintWriter out = new PrintWriter(filename)) {
                out.println( pactDefinition );
            }
            log.info("Pact file: {}", pactDefinition);

        } catch (IOException e) {
            rethrowRuntimeException(e);
        }

        return null;
    }
}
