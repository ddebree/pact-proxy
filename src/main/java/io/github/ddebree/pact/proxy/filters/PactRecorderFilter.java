package io.github.ddebree.pact.proxy.filters;

import javax.servlet.http.HttpServletRequest;

import au.com.dius.pact.consumer.ConsumerPactBuilder;
import au.com.dius.pact.model.PactWriter;
import au.com.dius.pact.model.RequestResponsePact;
import com.netflix.zuul.context.RequestContext;
import com.netflix.zuul.ZuulFilter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Random;

public class PactRecorderFilter extends ZuulFilter {

    private static Logger log = LoggerFactory.getLogger(PactRecorderFilter.class);

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
        RequestContext ctx = RequestContext.getCurrentContext();
        HttpServletRequest request = ctx.getRequest();

        Random random = new Random();

        log.info("{} request to {}", request.getMethod(), request.getRequestURL().toString());

        /*
        See https://groups.google.com/forum/m/#!topic/pact-support/48AirZ-a5-s
         */
        RequestResponsePact pact = ConsumerPactBuilder
                .consumer("client")
                .hasPactWith("server")
                .uponReceiving("a request_" + Math.abs(random.nextLong()))
                .path(request.getRequestURL().toString())
                .method(request.getMethod())
                //.body(req.getEntity(String.class))
                .willRespondWith()
                //.status(res.getStatus())
                //.body(res.getResponse().getEntity().toString())
                .toFragment().toPact();

        StringWriter strOut = new StringWriter();
        PactWriter.writePact(pact, new PrintWriter(strOut));

        log.info("Pact file: {}", strOut);

        return null;
    }

}
