package org.nrg.xdat.turbine.utils;
import org.apache.axis.AxisEngine;
import org.apache.axis.MessageContext;
import org.apache.axis.session.Session;
import org.apache.axis.transport.http.HTTPConstants;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.helpers.MessageFormatter;
import org.nrg.xdat.turbine.utils.AccessLogger;

import javax.servlet.http.HttpServletRequest;

public class AxisAccessLogger {

    public static void LogServiceAccess(final String user, final MessageContext context, final String service, final String message) {
        AccessLogger.LogServiceAccess(user, getAxisRequest(context), service, message);
    }


    private static HttpServletRequest getAxisRequest(final MessageContext context) {
        return (HttpServletRequest) context.getProperty(HTTPConstants.MC_HTTP_SERVLETREQUEST);
    }
}
