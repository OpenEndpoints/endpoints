package endpoints;

import com.databasesandlife.util.jdbc.DbTransaction;
import org.apache.commons.lang.StringUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;

public abstract class AbstractEndpointsServlet extends HttpServlet {

    @Override
    protected void doOptions(@Nonnull HttpServletRequest req, @Nonnull HttpServletResponse resp) throws IOException {
        var headers = new ArrayList<String>();
        req.getHeaderNames().asIterator().forEachRemaining(h -> headers.add(h));

        resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
        resp.setHeader("Access-Control-Allow-Headers", String.join(", ", headers));
        resp.setHeader("Access-Control-Allow-Methods", req.getMethod());
        resp.setHeader("Access-Control-Allow-Origin", req.getHeader("Origin"));
        resp.flushBuffer();
    }

    protected void setCorsHeaders(@Nonnull HttpServletRequest req, @Nonnull HttpServletResponse resp) {
        if (StringUtils.isNotEmpty(req.getHeader("Origin")))
            resp.setHeader("Access-Control-Allow-Origin", req.getHeader("Origin"));
    }

    @Override
    public void init() throws ServletException {
        super.init();

        var configuration = new FluentConfiguration()
            .dataSource(DeploymentParameters.get().jdbcUrl, null, null)
            .locations("classpath:endpoints/migration");
        var flyway = new Flyway(configuration);
        flyway.migrate();

        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            DeploymentParameters.get().getApplications(tx); // load all previously-published applications at startup
            tx.commit();
        }
        catch (DbTransaction.CannotConnectToDatabaseException e) {
            LoggerFactory.getLogger(getClass()).warn("Cannot load applications at servlet startup, "
                + "will load lazily during requests instead: Database connection problem", e);
        }
    }
}
