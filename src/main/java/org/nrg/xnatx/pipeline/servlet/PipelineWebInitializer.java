package org.nrg.xnatx.pipeline.servlet;

import org.apache.axis.transport.http.AdminServlet;
import org.apache.axis.transport.http.AxisHTTPSessionListener;
import org.apache.axis.transport.http.AxisServlet;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.WebApplicationInitializer;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRegistration;

public class PipelineWebInitializer implements WebApplicationInitializer {

    public void onStartup(ServletContext context) throws ServletException {
        context.addListener(AxisHTTPSessionListener.class);
        addServlet(AdminServlet.class, 10, "/servlet/AdminServlet");
        addServlet(AxisServlet.class, 11, "/servlet/AxisServlet", "*.jws", "/services/*");
    }

    private void addServlet(final ServletContext context, final Class<? extends Servlet> clazz, final int loadOnStartup, final String... mappings) {
        final String                      name         = StringUtils.uncapitalize(clazz.getSimpleName());
        final ServletRegistration.Dynamic registration = context.addServlet(name, clazz);
        registration.setLoadOnStartup(loadOnStartup);
        registration.addMapping(mappings);
    }
}
