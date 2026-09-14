/*
 * web: org.nrg.xnat.turbine.modules.actions.PipelineActions
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.apache.turbine.app.xnat.modules.actions;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.turbine.pipeline.PipelineData;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.pipeline.PipelineLaunchParameters;
import org.nrg.xnatx.pipeline.PipelineManager;
import org.nrg.pipeline.XnatPipelineLauncher;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.model.ArcPipelineparameterdataI;
import org.nrg.xdat.om.WrkWorkflowdata;
import org.nrg.xdat.turbine.modules.actions.SecureAction;
import org.nrg.xdat.turbine.utils.AdminUtils;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.ItemI;
import org.nrg.xft.search.CriteriaCollection;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.turbine.utils.ArcSpecManager;

public class PipelineActions extends SecureAction{
    static org.apache.log4j.Logger logger = Logger.getLogger(PipelineActions.class);

    public void doPerform(PipelineData pipelineData, Context context){
        final RunData data = pipelineData.getRunData();
        data.setScreenTemplate("PipelineScreen.vm");
    }

    public void doSkip(PipelineData pipelineData, Context context) throws Exception {
        final RunData data = pipelineData.getRunData();
        data.setScreenTemplate("PipelineScreen.vm");
    }

    public void doLaunch(PipelineData pipelineData, Context context)  throws Exception {
        final RunData data = pipelineData.getRunData();
        String project = ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("project",data));
        String step = ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("pipelineStep",data));
        boolean isDescendant = ((Boolean)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedBoolean("isdescendant",data));
        ItemI data_item = TurbineUtils.GetItemBySearch(data);
        PipelineLaunchParameters pipelineLaunchParameters = getGenericCommonParameters(data,context, project, step, data_item);
        LinkedHashMap<ArcPipelineparameterdataI,ArrayList> paramHash = null;
        // String launcherPrefix = ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("launcherPrefix",data));
        org.nrg.xft.search.CriteriaCollection cc = new CriteriaCollection("AND");
        cc.addClause("wrk:workflowData.ID",data_item.getProperty("ID"));
        cc.addClause("wrk:workflowData.data_type",data_item.getXSIType());

        if (isDescendant) {
            String pipelineXml = PipelineManager.getPathToPipelineForProject(project, step, data_item.getXSIType());
            String pipelineName = PipelineManager.getPipelineNameForProject(project, step, data_item.getXSIType());
            pipelineLaunchParameters.setPipelineName(pipelineXml);
            cc.addClause("wrk:workflowData.pipeline_name",pipelineName);
            paramHash = PipelineManager.getResolvedParametersForDescendantPipeline(step,project, data_item);
        }else {
            String pipelineXml = PipelineManager.getPathToPipelineForProject(project, step);
            String pipelineName = PipelineManager.getPipelineNameForProject(project, step);
            pipelineLaunchParameters.setPipelineName(pipelineXml);
            cc.addClause("wrk:workflowData.pipeline_name",pipelineName);
            paramHash = PipelineManager.getResolvedParametersForPipeline(step,project, data_item);
        }
        final String paramStr = "param:";
        if (paramHash != null) {
            Iterator paramIter = paramHash.keySet().iterator();
            while (paramIter.hasNext()) {
                ArcPipelineparameterdataI aParameter = (ArcPipelineparameterdataI)paramIter.next();
                String parameterTrueName = ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter(paramStr + aParameter.getName() +":truename",data));
                int valueCnt = paramHash.get(aParameter).size();
                if (valueCnt > 1) {
                    for (int i=0; i <valueCnt;i++) {
                        String dataParam = paramStr + aParameter.getName() + ":"+i;
                        if (TurbineUtils.HasPassedParameter(dataParam, data)){
                            pipelineLaunchParameters.setParameter(parameterTrueName, ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter(dataParam,data)));
                        }
                    }
                }else {
                    String dataParam = paramStr + aParameter.getName();
                    if (TurbineUtils.HasPassedParameter(dataParam, data)){
                        pipelineLaunchParameters.setParameter(parameterTrueName, ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter(dataParam,data)));
                    }
                }
            }
        }
        //Remove all preexisting workflow entries for the given item and the pipeline
        ArrayList<WrkWorkflowdata> workflows = WrkWorkflowdata.getWrkWorkflowdatasByField(cc, TurbineUtils.getUser(data), false);
        if (workflows != null && workflows.size() > 0) {
            WrkWorkflowdata workFlow = workflows.get(0);
            if (workFlow.getStatus().equals(org.nrg.xdat.om.base.BaseWrkWorkflowdata.AWAITING_ACTION)) {
                pipelineLaunchParameters.setStartAt(workFlow.getNextStepId());
            }
        }
        XnatPipelineLauncher xnatPipelineLauncher = new XnatPipelineLauncher(pipelineLaunchParameters);
        xnatPipelineLauncher.launch();
        data.setMessage("<p><b>The build process was successfully launched.  Status email will be sent upon its completion.</b></p>");
        data.setScreenTemplate("ClosePage.vm");
    }

    private PipelineLaunchParameters getGenericCommonParameters(RunData data, Context context, String projectId,  String step, ItemI item) throws Exception {
        PipelineLaunchParameters pipelineLaunchParameters = new PipelineLaunchParameters(XDAT.getUserDetails());

        pipelineLaunchParameters.setAdmin_email(XDAT.getSiteConfigPreferences().getAdminEmail());
        pipelineLaunchParameters.setAlwaysEmailAdmin(ArcSpecManager.GetInstance().getEmailspecifications_pipeline());
        UserI user = TurbineUtils.getUser(data);
        pipelineLaunchParameters.setNeedsBuildDir(true);
        pipelineLaunchParameters.setSupressNotification(true);
        pipelineLaunchParameters.setId((String)item.getProperty("ID"));
        pipelineLaunchParameters.setDataType(item.getXSIType());
        pipelineLaunchParameters.setExternalId(projectId);
        pipelineLaunchParameters.setParameter("useremail", user.getEmail());
        pipelineLaunchParameters.setParameter("userfullname", pipelineLaunchParameters.getUserName(user));
        pipelineLaunchParameters.setParameter("adminemail", ArcSpecManager.GetInstance().getSiteAdminEmail());
        pipelineLaunchParameters.setParameter("xnatserver", ArcSpecManager.GetInstance().getSiteId());
        pipelineLaunchParameters.setParameter("mailhost", ArcSpecManager.GetInstance().getSmtpHost());

        String emailsStr =  ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("emailField",data));
        if (emailsStr != null) {
            String[] emails = emailsStr.trim().split(",");
            for (int i = 0 ; i < emails.length; i++)
                if (emails[i] != null && !emails[i].equals("")) pipelineLaunchParameters.notificationEmailId(emails[i]);
        }
        return pipelineLaunchParameters;
    }

    public void doBuild(PipelineData pipelineData, Context context) throws Exception{
        final RunData data = pipelineData.getRunData();
        String projectId = ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("projectId",data));
        int totalSessionsToBuild = ((Integer)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedInteger("param:control:total",data));
        String step = ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("step",data));
        data.getParameters().remove("step");
        try {
            int selectedCount = 0;
            for (int i = 1; i <= totalSessionsToBuild; i++) {
                String sessionParamCode = ":session" + i;
                if (TurbineUtils.HasPassedParameter("param" + sessionParamCode + ":sessionId", data)){
                    selectedCount++;
                }
            }

            String pipelineXml = PipelineManager.getPathToPipelineForProject(projectId, step);
            int selectedCountLast = 0;
            for (int i = 1; i <= totalSessionsToBuild; i++) {
                PipelineLaunchParameters pipelineLaunchParameters = getCommonParameters(data,context, projectId, pipelineXml,step);
                String sessionParamCode = ":session" + i;
                if (TurbineUtils.HasPassedParameter("param" + sessionParamCode + ":sessionId", data)){
                    Hashtable<String,String> sessionParams = getParametersForKey(data,context,"param" + sessionParamCode + ":",sessionParamCode);
                    String sessionId = sessionParams.get("param:sessionid");
                    String xnat_sessionId = sessionParams.get("param:xnat_sessionid");
                    pipelineLaunchParameters.setId(sessionId);
                    if (sessionParams.keySet().size() > 0) {
                        pipelineLaunchParameters.setParameter("xnat_sessionId",xnat_sessionId);
                        setCommandLineArguments(data,sessionParams,projectId,step, pipelineLaunchParameters);
                    }
                    selectedCountLast++;
                    if (selectedCountLast==selectedCount) pipelineLaunchParameters.setParameter("isLast","1");
                    pipelineLaunchParameters.setParameter("projectId",projectId);
                    XnatPipelineLauncher xnatPipelineLauncher = new XnatPipelineLauncher(pipelineLaunchParameters);
                    xnatPipelineLauncher.launch();
                }
            }
            String destinationPage = ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("destinationpage",data));
            logger.debug("BuildPipelineActions::doBuild Destination page is " + destinationPage);
            logger.debug(((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("search_value",data)));
            logger.debug(((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("search_element",data)));
            logger.debug(((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("search_field",data)));
            logger.debug("BuildPipelineActions::doBuild END");

            if (destinationPage != null) {
                data.setRedirectURI(TurbineUtils.GetRelativeServerPath(data)+ "/app/template/" + destinationPage + "/search_field/" + ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("search_field",data)) +  "/search_value/" +  ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("search_value",data))  + "/search_element/" +  ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("search_element",data)));
            }else {
                String msg = "<p><b>The build process was successfully launched.  Status email will be sent upon its completion.</b></p>";
                context.put("msg",msg);
                data.setScreenTemplate("GenericMessage.vm");
            }
        } catch (Exception e){
            logger.error("",e);
            data.setMessage("<p><img src=\"/fcon/images/error.gif\">The build process failed to launch. Please contact the <a href=\"mailto:" + XDAT.getNotificationsPreferences().getHelpContactInfo() + "?subject=Failed to launch build \">NRG techdesk</a>");
            data.setScreenTemplate("Error.vm");
        }
    }


    private PipelineLaunchParameters getCommonParameters(RunData data, Context context, String projectId, String pipelineName, String step) throws Exception {
        UserI user = XDAT.getUserDetails();

        PipelineLaunchParameters pipelineLaunchParameters = new PipelineLaunchParameters(user);

        pipelineLaunchParameters.setAdmin_email(XDAT.getSiteConfigPreferences().getAdminEmail());
        pipelineLaunchParameters.setAlwaysEmailAdmin(ArcSpecManager.GetInstance().getEmailspecifications_pipeline());
        pipelineLaunchParameters.setPipelineName(pipelineName);
        pipelineLaunchParameters.setNeedsBuildDir(true);
        pipelineLaunchParameters.setExternalId(projectId);
        pipelineLaunchParameters.setSupressNotification(true);
        pipelineLaunchParameters.setDataType("xnat:mrSessionData");
        pipelineLaunchParameters.setParameter("useremail", user.getEmail());
        pipelineLaunchParameters.setParameter("userfullname", PipelineLaunchParameters.getUserName(user));
        pipelineLaunchParameters.setParameter("adminemail", XDAT.getSiteConfigPreferences().getAdminEmail());
        pipelineLaunchParameters.setParameter("xnatserver", TurbineUtils.GetSystemName());
        pipelineLaunchParameters.setParameter("mailhost", XDAT.getNotificationsPreferences().getSmtpServer().getHostname());

        String emailsStr =  ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("emailField",data));
        if (emailsStr != null) {
            String[] emails = emailsStr.trim().split(",");
            for (int i = 0 ; i < emails.length; i++)
                if (emails[i] != null && !emails[i].equals("")) pipelineLaunchParameters.notificationEmailId(emails[i]);
        }
        return pipelineLaunchParameters;
    }

    private Hashtable<String,String> setCommandLineArguments(RunData data, Hashtable<String,String> paramNameValue, String projectId, String step, final PipelineLaunchParameters pipelineLaunchParameters) {
        Hashtable<String,String> trueNameValues = new Hashtable<String,String>();
        try {
            List parameters = PipelineManager.getParametersForPipeline(projectId, step);
            if (parameters != null) {
                LinkedHashMap<String,String> parametersHash = new LinkedHashMap<String,String>();
                //TrueName is required as the data parameter names are case insensitive
                Hashtable<String, String> trueName = new Hashtable<String, String>();
                ArrayList<String> paramKeys = new ArrayList<String>();
                Iterator iter = paramNameValue.keySet().iterator();
                while (iter.hasNext()) {
                    String dataKey = (String)iter.next();
                    if (dataKey.startsWith("param"))
                        paramKeys.add(dataKey);
                }
                Collections.sort(paramKeys, new Comparator<Object>() {
                            public int compare(Object o1, Object o2) {
                                int rtn = 0;
                                String[] o1_parts = ((String)o1).split(":");
                                String[] o2_parts = ((String)o2).split(":");
                                if (o1_parts != null && o1_parts.length > 1 && o2_parts != null && o2_parts.length > 1 ) {
                                    if (o1_parts.length != o2_parts.length) {
                                        return ((String)o1).compareTo((String)o2);
                                    }
                                    if (!o1_parts[1].equals(o2_parts[1])) {
                                        return((String)o1).compareTo((String)o2);
                                    }
                                    if (o1_parts[1].equals(o2_parts[1]) && (o1_parts.length == 3 && o2_parts.length == 3)) {
                                        Integer i1 =  Integer.parseInt(o1_parts[2]);
                                        Integer i2 =  Integer.parseInt(o2_parts[2]);
                                        return i1.compareTo(i2);
                                    }
                                    rtn = ((String)o1).compareTo((String)o2);
                                }
                                return rtn;
                            }
                        }
                );
                for (int i =0; i < paramKeys.size(); i++) {
                    String[] parts = ((String)paramKeys.get(i)).split(":");
                    if (parts != null && parts.length > 1 && parts[0].equals("param")) {
                        String paramName = parts[1];
                        if (!trueName.containsKey(paramName)) {
                            ArcPipelineparameterdataI aParameter = PipelineManager.getParameterByName(projectId, step, paramName,true);
                            if (aParameter != null)
                                trueName.put(paramName,aParameter.getName());
                        }
                        if (parametersHash.containsKey(paramName)) {
                            parametersHash.put(paramName,((String)parametersHash.get(paramName))+"," + paramNameValue.get((String)paramKeys.get(i)) );
                        }else {
                            parametersHash.put(paramName, paramNameValue.get((String)paramKeys.get(i)));
                        }
                    }
                }
                iter = parametersHash.keySet().iterator();
                while (iter.hasNext()) {
                    String paramName = (String)iter.next();
                    String trueParamName = (String)trueName.get(paramName);
                    String paramValues = (String)parametersHash.get(paramName);
                    if (paramValues.endsWith(",")) paramValues = paramValues.substring(0,paramValues.length()-1);
                    pipelineLaunchParameters.setParameter(trueParamName, paramValues);
                    trueNameValues.put(trueParamName,paramValues);
                }
            }
        }catch(Exception e) {
            logger.debug("Unable to construct the build parameters for step " + step + " " + e.getMessage() + " " + e.getCause(),e);
        }
        return trueNameValues;
    }

    private Hashtable<String,String> getParametersForKey(RunData data,Context context,String pattern, String replace) {
        Hashtable rtn = new Hashtable();
        Iterator keys = data.getParameters().keySet().iterator();
        while (keys.hasNext()) {
            String key = (String)keys.next();
            if (key.startsWith(pattern)) {
                if (replace != null)
                    rtn.put(StringUtils.replace(key, replace, ""), ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter(key, data)));
                else
                    rtn.put(key,((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter(key,data)));
            }
        }
        return rtn;
    }


}