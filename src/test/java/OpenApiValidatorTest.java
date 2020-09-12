import com.mservicetech.openapi.validation.OpenApiValidator;
import com.networknt.openapi.OpenApiHandler;
import com.networknt.openapi.OpenApiHelper;
import com.networknt.status.Status;
import io.undertow.Undertow;
import io.undertow.server.HttpHandler;
import org.junit.Assert;
import org.junit.Before;

import org.junit.BeforeClass;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

public class OpenApiValidatorTest {

    static OpenApiValidator openApiValidator;

    @BeforeClass
    public static void setUp() {
        openApiValidator = new OpenApiValidator("openapi.yaml");
    }

    @Test
    public void testFileLoad() {

        Assert.assertNotNull(openApiValidator.openApiHelper.getOpenApi3());
        Assert.assertNotNull(openApiValidator.openApiHelper.basePath);
    }

    @Test
    public void testURLPath() {
        Status status1 = openApiValidator.validateRequestPath("/pets", "get", null);
        Assert.assertNull(status1);
        Status status2 = openApiValidator.validateRequestPath("/pets/1111", "get", null);
        Assert.assertNull(status2);
        Status status3 = openApiValidator.validateRequestPath("/pets1/v1/1111", "get", null);
        Assert.assertNotNull(status3);
        Assert.assertEquals( status3.getCode(), "ERR10007");
    }

    @Test
    public void testRequestBody() {

    }
}
