package io.github.ddebree.pact.proxy;

import au.com.dius.pact.model.RequestResponsePact;
import com.netflix.zuul.context.RequestContext;
import io.github.ddebree.pact.proxy.service.PactResultWriter;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Matchers.endsWith;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.verify;

@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        classes = PactProxyApplication.class, properties = {"remote=http://localhost:8090", "outputPath=target/results"})
public class PactProxyApplicationTest {

    private static final String REQUEST_PATH = "/some-service";
    private static final String RESPONSE_BODY = "somebody";

    @Autowired
    private TestRestTemplate rest;
    @MockBean
    private PactResultWriter pactResultWriter;

    static ConfigurableApplicationContext testService;

    @BeforeClass
    public static void startMockRemoteService() {
        testService = SpringApplication.run(TestService.class, "--port=8090");
    }

    @AfterClass
    public static void closeMockRemoteService() {
        testService.close();
    }

    @Before
    public void setup() {
        RequestContext.testSetCurrentContext(new RequestContext());
    }

    @Test
    public void test() {
        String resp = rest.getForObject(REQUEST_PATH, String.class);
        assertThat(resp).isEqualTo(RESPONSE_BODY);

        ArgumentCaptor<RequestResponsePact> argument = ArgumentCaptor.forClass(RequestResponsePact.class);
        verify(pactResultWriter).writePact(endsWith("/some-service"), anyLong(), argument.capture());
    }

    @Configuration
    @EnableAutoConfiguration
    @RestController
    static class TestService {
        @RequestMapping(REQUEST_PATH)
        public String getAvailable() {
            return RESPONSE_BODY;
        }
    }
}
