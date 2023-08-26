package endpoints;

import com.databasesandlife.util.Timer;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_OK;

@Slf4j
public class HealthCheckServlet extends HttpServlet {

    @Override protected void doGet(@Nonnull HttpServletRequest req, @Nonnull HttpServletResponse resp)
    throws IOException {
        try (var ignored = new Timer("Health Check")) {
            try (var tx = DeploymentParameters.get().newDbTransaction()) {
                // Check database connection OK
                tx.commit();
            }

            resp.sendError(SC_OK);
        }
        catch (Exception e) {
            log.warn("Health check not OK", e);
            throw e;
        }
    }
}
