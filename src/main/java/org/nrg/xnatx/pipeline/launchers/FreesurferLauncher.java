/*
 * web: org.nrg.pipeline.launchers.FreesurferLauncher
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnatx.pipeline.launchers;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.pipeline.PipelineLaunchParameters;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xft.security.UserI;
import org.nrg.xnatx.pipeline.component.DefaultXnatPipelineLauncher;
import org.nrg.xnatx.pipeline.utils.PipelineFileUtils;
import org.nrg.pipeline.xmlbeans.ParameterData;
import org.nrg.pipeline.xmlbeans.ParameterData.Values;
import org.nrg.pipeline.xmlbeans.ParametersDocument.Parameters;
import org.nrg.xdat.om.XnatMrsessiondata;
import org.nrg.xdat.turbine.utils.TurbineUtils;

public class FreesurferLauncher extends PipelineLauncher {
    ArrayList<String> mprageScans = null;
    static org.apache.log4j.Logger logger = Logger.getLogger(FreesurferLauncher.class);

    public FreesurferLauncher(ArrayList<String> mprs) {
        mprageScans = mprs;
    }

    public FreesurferLauncher(RunData data, XnatMrsessiondata mr) {
        mprageScans = getCheckBoxSelections(data,mr,"MPRAGE");
    }

    public boolean launch(RunData data, Context context) {
        return false;
    }


    public boolean launch(RunData data, Context context, XnatMrsessiondata mr) {
        try {

            PipelineLaunchParameters pipelineLaunchParameters = PipelineLaunchParameters.builder().user(XDAT.getUserDetails()).build();
            String pipelineName = ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("freesurfer_pipelinename",data));
            String cmdPrefix = ((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("cmdprefix",data));

            pipelineLaunchParameters.setPipelineName(pipelineName);
            pipelineLaunchParameters.setSupressNotification(true);

            String buildDir = PipelineFileUtils.getBuildDir(mr.getProject(), true);
            buildDir +=  "fsrfer"  ;

            pipelineLaunchParameters.setBuildDir(buildDir);
            pipelineLaunchParameters.setNeedsBuildDir(false);

            Parameters parameters = Parameters.Factory.newInstance();

            if (TurbineUtils.HasPassedParameter("custom_command", data)) {
                ParameterData param = parameters.addNewParameter();
                param.setName("custom_command");
                param.addNewValues().setUnique(((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("custom_command",data)));
            }else {

                ParameterData param = parameters.addNewParameter();
                param.setName("sessionId");
                param.addNewValues().setUnique(mr.getLabel());

                param = parameters.addNewParameter();
                param.setName("isDicom");
                param.addNewValues().setUnique(((String)org.nrg.xdat.turbine.utils.TurbineUtils.GetPassedParameter("isDicom",data)));

                // Add MPRAGE list
                param = parameters.addNewParameter();
                param.setName("mprs");
                Values values = param.addNewValues();
                if (mprageScans.size() == 1) {
                    values.setUnique(mprageScans.get(0));
                }else {
                    for (int i = 0; i < mprageScans.size(); i++) {
                        values.addList(mprageScans.get(i));
                    }
                }

                param = parameters.addNewParameter();
                param.setName("useall_t1s");
                if (TurbineUtils.HasPassedParameter("useall_t1s", data)) {
                    param.addNewValues().setUnique("1");
                }else {
                    param.addNewValues().setUnique("0");
                }
            }

            String emailsStr = TurbineUtils.getUser(data).getEmail() + "," + data.getParameters().get("emailField");
            String[] emails = emailsStr.trim().split(",");
            for (int i = 0 ; i < emails.length; i++) {
                if (emails[i]!=null && !emails[i].equals(""))  pipelineLaunchParameters.notificationEmailId(emails[i]);
            }

            String paramFileName = getName(pipelineName);
            Date date = new Date();
            SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMdd");
            String s = formatter.format(date);

            paramFileName += "_params_" + s + ".xml";

            String paramFilePath = saveParameters(buildDir + File.separator + mr.getLabel(),paramFileName,parameters);

            pipelineLaunchParameters.setParameterFile(paramFilePath);
            DefaultXnatPipelineLauncher xnatPipelineLauncher = DefaultXnatPipelineLauncher.GetLauncher(data, context, mr);
            return xnatPipelineLauncher.launch(cmdPrefix);
        }catch(Exception e) {
            logger.error(e.getCause() + " " + e.getLocalizedMessage());
            return false;
        }
    }

}
