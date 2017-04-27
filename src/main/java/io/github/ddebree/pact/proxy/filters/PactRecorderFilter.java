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
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicLong;

import static org.springframework.util.ReflectionUtils.rethrowRuntimeException;

@Component
public class PactRecorderFilter extends ZuulFilter {

    private static final AtomicLong COUNTER = new AtomicLong(0);
    private static final Logger LOGGER = LoggerFactory.getLogger(PactRecorderFilter.class);

    private final PactResultWriter pactResultWriter;

    @Autowired
    public PactRecorderFilter(PactResultWriter pactResultWriter) {
        this.pactResultWriter = pactResultWriter;
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
        return true;
    }

    @Override
    public Object run() {
        long requestId = COUNTER.incrementAndGet();
        try {
            RequestContext context = RequestContext.getCurrentContext();
            HttpServletRequest request = context.getRequest();

            String url = request.getRequestURL().toString();

            LOGGER.info("{} request to {}, filename: {}", request.getMethod(), url);

            String requestBody = null;
            InputStream in = (InputStream) context.get("requestEntity");
            if (in == null) {
                in = context.getRequest().getInputStream();
            }
            if (in != null) {
                requestBody = StreamUtils.copyToString(in, Charset.forName("UTF-8"));
            }

            String responseBody = null;
            InputStream stream = context.getResponseDataStream();
            if (stream != null) {
                responseBody = StreamUtils.copyToString(stream, Charset.forName("UTF-8"));
                context.setResponseBody(responseBody);
            }

            PactDslRequestWithPath pactRequest = ConsumerPactBuilder
                    .consumer("client")
                    .hasPactWith("server")
                    .uponReceiving("Request id " + requestId)
                        .path(url)
                        .method(request.getMethod());
            if (requestBody != null) {
                pactRequest
                        .body(requestBody);
            }
            PactDslResponse pactResponse = pactRequest
                    .willRespondWith()
                        .status(context.getResponseStatusCode());
            if (responseBody != null) {
                pactResponse
                        .body(responseBody);
            }

            RequestResponsePact pact = pactResponse.toFragment().toPact();

            pactResultWriter.writePact(url, requestId, pact);
        } catch (IOException e) {
            rethrowRuntimeException(e);
        }

        return null;
    }
}
