package io.github.ddebree.pact.proxy.filters;

import javax.servlet.http.HttpServletRequest;

import au.com.dius.pact.consumer.ConsumerPactBuilder;
import au.com.dius.pact.consumer.dsl.PactDslRequestWithPath;
import au.com.dius.pact.consumer.dsl.PactDslResponse;
import au.com.dius.pact.model.RequestResponsePact;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.ZuulFilter;

import io.github.ddebree.pact.proxy.service.PactResultWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.GZIPInputStream;

import static org.springframework.cloud.netflix.zuul.filters.support.FilterConstants.POST_TYPE;
import static org.springframework.util.ReflectionUtils.rethrowRuntimeException;

@Component
public class PactRecorderFilter extends ZuulFilter {

    private static final AtomicLong COUNTER = new AtomicLong(0);
    private static final Logger LOGGER = LoggerFactory.getLogger(PactRecorderFilter.class);

    private final PactResultWriter pactResultWriter;
    private final String clientName;
    private final String providerName;

    @Autowired
    public PactRecorderFilter(PactResultWriter pactResultWriter,
                              @Value("${clientName}") String clientName,
                              @Value("${providerName}") String providerName) {
        this.pactResultWriter = pactResultWriter;
        this.clientName = clientName;
        this.providerName = providerName;
    }

    @Override
    public String filterType() {
        return POST_TYPE;
    }

    @Override
    public int filterOrder() {
        return 1;
    }

    @Override
    public boolean shouldFilter() {
        return true;
    }

    @Override
    public Object run() {
        long requestId = COUNTER.incrementAndGet();
        try {
            RequestContext context = RequestContext.getCurrentContext();
            HttpServletRequest request = context.getRequest();

            String url = request.getRequestURL().toString();

            LOGGER.info("Request context: {}", context);
            LOGGER.info("Request {}: {} request to {}", requestId, request.getMethod(), url);

            PactDslRequestWithPath pactRequest = ConsumerPactBuilder
                    .consumer(clientName)
                    .hasPactWith(providerName)
                    .uponReceiving("Request id " + requestId)
                        .path(url)
                        .method(request.getMethod());
            buildRequestBody(pactRequest);

            PactDslResponse pactResponse = pactRequest
                    .willRespondWith()
                        .status(context.getResponseStatusCode());
            buildResponseBody(pactResponse);

            RequestResponsePact pact = pactResponse.toFragment().toPact();

            pactResultWriter.writePact(url, requestId, pact);
        } catch (IOException e) {
            rethrowRuntimeException(e);
        }

        return null;
    }

    private void buildRequestBody(PactDslRequestWithPath pactRequest) throws IOException {
        final RequestContext context = RequestContext.getCurrentContext();
        String requestBody = null;
        InputStream in = (InputStream) context.get("requestEntity");
        if (in == null) {
            in = context.getRequest().getInputStream();

        }
        if (in != null) {
            String encoding = context.getRequest().getCharacterEncoding();
            requestBody = StreamUtils.copyToString(in,
                    Charset.forName(encoding != null ? encoding : "UTF-8"));
        }
        if (requestBody != null && requestBody.length() > 0) {
            pactRequest.body(requestBody);
        }
    }

    private void buildResponseBody(PactDslResponse pactResponse) throws IOException {
        RequestContext context = RequestContext.getCurrentContext();
        if (context.getResponseBody() != null) {
            String body = context.getResponseBody();
            pactResponse.body(body);
        } else if (context.getResponseDataStream() != null) {
            String encoding = context.getRequest().getCharacterEncoding();
            InputStream stream = context.getResponseDataStream();
            byte[] responseBytes = StreamUtils.copyToByteArray(stream);
            context.setResponseDataStream(new ByteArrayInputStream(responseBytes));

            if (context.getResponseGZipped()) {
                LOGGER.warn("GZipped content found");
                final Long len = context.getOriginContentLength();
                if (len == null || len > 0) {
                    try {
                        String responseBody = StreamUtils.copyToString(new GZIPInputStream(new ByteArrayInputStream(responseBytes)),
                                Charset.forName(encoding != null ? encoding : "UTF-8"));
                        pactResponse.body(responseBody);
                    } catch (java.util.zip.ZipException ex) {
                        LOGGER.debug(
                                "gzip expected but not "
                                        + "received assuming unencoded response "
                                        + RequestContext.getCurrentContext()
                                        .getRequest().getRequestURL()
                                        .toString());
                    }
                }
            } else {
                String responseBody = StreamUtils.copyToString(new ByteArrayInputStream(responseBytes),
                        Charset.forName(encoding != null ? encoding : "UTF-8"));
                pactResponse.body(responseBody);
            }
        }
    }

}