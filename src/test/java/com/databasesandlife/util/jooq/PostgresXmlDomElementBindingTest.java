package com.databasesandlife.util.jooq;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.RequestId;
import endpoints.config.ApplicationName;
import endpoints.config.NodeName;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
import junit.framework.TestCase;
import lombok.SneakyThrows;
import org.apache.commons.lang.RandomStringUtils;

import java.time.Instant;

import static endpoints.generated.jooq.Tables.REQUEST_LOG;

public class PostgresXmlDomElementBindingTest extends TestCase {
    
    @SneakyThrows(ConfigurationException.class)
    public void test() {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var userAgent = RandomStringUtils.randomAlphanumeric(50);
            
            var toInsert = new RequestLogRecord();
            toInsert.setApplication(new ApplicationName("foo"));
            toInsert.setEndpoint(new NodeName("foo"));
            toInsert.setDatetime(Instant.now());
            toInsert.setStatusCode(200);
            toInsert.setUserAgent(userAgent);
            toInsert.setEnvironment(PublishEnvironment.live);
            toInsert.setParameterTransformationInput(DomParser.from("<input/>"));
            toInsert.setParameterTransformationOutput(null);
            toInsert.setRequestId(RequestId.newRandom());
            tx.insert(toInsert);

            var found = tx.jooq().selectFrom(REQUEST_LOG).where(REQUEST_LOG.USER_AGENT.eq(userAgent)).fetchOne();
            assertEquals("input", found.getParameterTransformationInput().getNodeName());
            assertNull(found.getParameterTransformationOutput());
        }
    }
}