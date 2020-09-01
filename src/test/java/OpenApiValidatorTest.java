import com.mservicetech.openapi.validation.OpenApiValidator;
import com.networknt.openapi.OpenApiHandler;
import com.networknt.openapi.OpenApiHelper;
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
//        InputStream in =  this.getClass().getClassLoader().getResourceAsStream("openapi.yaml");
//        String spec = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().collect(Collectors.joining("\n"));
//        System.out.println(spec);

        Assert.assertNotNull(openApiValidator.openApiHelper.getOpenApi3());
        Assert.assertNotNull(openApiValidator.openApiHelper.basePath);
    }


}
