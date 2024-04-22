package org.nrg.xnatx.pipeline.services.impl.legacy;

import lombok.extern.slf4j.Slf4j;
import org.nrg.pipeline.PipelineLaunchParameters;
import org.nrg.xnatx.pipeline.component.DefaultXnatPipelineLauncher;
import org.nrg.xapi.exceptions.NotFoundException;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.preferences.SiteConfigPreferences;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.preferences.PipelinePreferences;
import org.nrg.xnat.restlet.util.XNATRestConstants;
import org.nrg.xnat.services.archive.PipelineService;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
@Slf4j
public class DefaultPipelineServiceImpl implements PipelineService {
    @Autowired
    public DefaultPipelineServiceImpl(final PipelinePreferences preferences, final SiteConfigPreferences siteConfigPreferences) {
        _preferences = preferences;
        _siteConfigPreferences = siteConfigPreferences;
    }

    @Override
    public boolean launchAutoRun(final XnatExperimentdata experiment, final boolean suppressEmail, final UserI user) {
        return launchAutoRun(experiment, suppressEmail, user, false);
    }

    @Override
    public boolean launchAutoRun(final XnatExperimentdata experiment, final boolean suppressEmail, final UserI user, final boolean waitFor) {
        try {
            if (!_preferences.isAutoRunEnabled(experiment.getProject())) {
                log.info("AutoRun pipeline was not enabled for this site or for project {}, returning true because this is fine but not launching AutoRun pipeline", experiment.getProject());
                return true;
            }
        } catch (NotFoundException e) {
            throw new RuntimeException("Couldn't find the project " + experiment.getProject() + " as specified on experiment with ID " + experiment.getId());
        }

        PipelineLaunchParameters pipelineLaunchParameters = PipelineLaunchParameters.builder().user(user).build();

        pipelineLaunchParameters.setAdmin_email(_siteConfigPreferences.getAdminEmail());
        pipelineLaunchParameters.setAlwaysEmailAdmin(ArcSpecManager.GetInstance().getEmailspecifications_pipeline());
        pipelineLaunchParameters.setPipelineName(XNAT_TOOLS_AUTO_RUN_XML);
        pipelineLaunchParameters.setNeedsBuildDir(false);
        pipelineLaunchParameters.setSupressNotification(true);
        pipelineLaunchParameters.setId(experiment.getId());
        pipelineLaunchParameters.setLabel(experiment.getLabel());
        pipelineLaunchParameters.setDataType(experiment.getXSIType());
        pipelineLaunchParameters.setExternalId(experiment.getProject());
        pipelineLaunchParameters.setWaitFor(waitFor);
        pipelineLaunchParameters.setParameter(XNATRestConstants.SUPRESS_EMAIL, (Boolean.valueOf(suppressEmail)).toString());
        pipelineLaunchParameters.setParameter("session", experiment.getId());
        pipelineLaunchParameters.setParameter("sessionLabel", experiment.getLabel());
        pipelineLaunchParameters.setParameter("useremail", user.getEmail());
        pipelineLaunchParameters.setParameter("userfullname", DefaultXnatPipelineLauncher.getUserName(user));
        pipelineLaunchParameters.setParameter("adminemail", XDAT.getSiteConfigPreferences().getAdminEmail());
        pipelineLaunchParameters.setParameter("xnatserver", TurbineUtils.GetSystemName());
        pipelineLaunchParameters.setParameter("mailhost", XDAT.getNotificationsPreferences().getSmtpServer().getHostname());
        pipelineLaunchParameters.setParameter("sessionType", experiment.getXSIType());
        pipelineLaunchParameters.setParameter("xnat_project", experiment.getProject());
        return DefaultXnatPipelineLauncher.GetLauncher(pipelineLaunchParameters).launch(null);
    }

    private static final String XNAT_TOOLS_AUTO_RUN_XML = "xnat_tools/AutoRun.xml";

    private final PipelinePreferences      _preferences;
    private final SiteConfigPreferences    _siteConfigPreferences;
}