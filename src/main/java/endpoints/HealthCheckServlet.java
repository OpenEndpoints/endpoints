package endpoints;

import com.databasesandlife.util.Timer;
import org.apache.log4j.Logger;

import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

import static javax.servlet.http.HttpServletResponse.SC_OK;

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
            Logger.getLogger(getClass()).warn("Health check not OK", e);
            throw e;
        }
    }
}
