package com.databasesandlife.util.jooq;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.RequestId;
import endpoints.config.ApplicationName;
import endpoints.config.NodeName;
import endpoints.generated.jooq.tables.records.RequestLogIdsRecord;
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
            var id = RequestId.newRandom();
            var userAgent = RandomStringUtils.randomAlphanumeric(50);

            var requestLogIds = new RequestLogIdsRecord();
            requestLogIds.setRequestId(id);
            requestLogIds.setApplication(new ApplicationName("foo"));
            requestLogIds.setEndpoint(new NodeName("foo"));
            requestLogIds.setEnvironment(PublishEnvironment.live);
            tx.insert(requestLogIds);
            
            var toInsert = new RequestLogRecord();
            toInsert.setRequestId(id);
            toInsert.setDatetime(Instant.now());
            toInsert.setStatusCode(200);
            toInsert.setUserAgent(userAgent);
            toInsert.setParameterTransformationInput(DomParser.from("<input/>"));
            toInsert.setParameterTransformationOutput(null);
            tx.insert(toInsert);

            var found = tx.jooq().selectFrom(REQUEST_LOG).where(REQUEST_LOG.USER_AGENT.eq(userAgent)).fetchOne();
            assertEquals("input", found.getParameterTransformationInput().getNodeName());
            assertNull(found.getParameterTransformationOutput());
        }
    }
}