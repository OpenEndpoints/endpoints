package endpoints.shortlinktoendpoint;

import endpoints.DailyJob;
import endpoints.DeploymentParameters;

import java.time.Instant;

import static endpoints.generated.jooq.Tables.SHORT_LINK_TO_ENDPOINT;
import static endpoints.generated.jooq.Tables.SHORT_LINK_TO_ENDPOINT_PARAMETER;
import static org.jooq.impl.DSL.select;

public class ShortLinkToEndpointExpiryJob extends DailyJob {

    @Override protected void performJob() {
        try (var tx = DeploymentParameters.get().newDbTransaction();) {
            var now = Instant.now();
            tx.jooq()
                .deleteFrom(SHORT_LINK_TO_ENDPOINT_PARAMETER)
                .where(SHORT_LINK_TO_ENDPOINT_PARAMETER.SHORT_LINK_TO_ENDPOINT_CODE.in(
                    select(SHORT_LINK_TO_ENDPOINT.SHORT_LINK_TO_ENDPOINT_CODE)
                    .from(SHORT_LINK_TO_ENDPOINT)
                    .where(SHORT_LINK_TO_ENDPOINT.EXPIRES_ON.lt(now))
                ))
                .execute();
            tx.jooq()
                .deleteFrom(SHORT_LINK_TO_ENDPOINT)
                .where(SHORT_LINK_TO_ENDPOINT.EXPIRES_ON.lt(now))
                .execute();
            tx.commit();
        }
    }
}
