/*
 * web: org.nrg.pipeline.XnatPipelineLauncher
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.xnatx.pipeline.component;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.pipeline.PipelineLaunchParameters;
import org.nrg.pipeline.client.XNATPipelineLauncher;
import org.nrg.pipeline.services.PipelineLauncherService;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.entities.AliasToken;
import org.nrg.xdat.om.WrkWorkflowdata;
import org.nrg.xdat.om.XnatExperimentdata;
import org.nrg.xdat.om.XnatImagesessiondata;
import org.nrg.xdat.services.AliasTokenService;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.event.EventUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.turbine.utils.ArcSpecManager;
import org.nrg.xnat.utils.WorkflowUtils;
import org.nrg.xnatx.pipeline.ProcessLauncher;
import org.nrg.xnatx.pipeline.utils.PipelineFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
@Primary
public class DefaultXnatPipelineLauncher implements PipelineLauncherService {

    private static final Logger launchLogger = LoggerFactory.getLogger("org.nrg.pipeline.launch");

    public static final String SCHEDULE = "schedule";
    public static final Pattern DEPASSWORDIFIZER = Pattern.compile("(.*)(-pwd )\\S+(.*)");
    public static final String NOT_A_PASSWORD = "********";


    public void setPipelineLaunchParameters(final PipelineLaunchParameters pipelineLaunchParameters) {
        this.pipelineLaunchParameters = pipelineLaunchParameters;
    }

    public void setPipelineName(String pipelineName) {
        pipelineLaunchParameters.setPipelineName(pipelineName);
    }

    public void notify(String... emails) {
        pipelineLaunchParameters.notificationEmailId(emails);
    }

    public void setParameterFile(String parameterFile) {
        pipelineLaunchParameters.setParameterFile(parameterFile);
    }

    public void setBuildDir(String path) {
        if (StringUtils.isBlank(path)) {
            return;
        }
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }

        if (pipelineLaunchParameters.isNeedsBuildDir()) {
            pipelineLaunchParameters.getParameters().put("builddir", Collections.singletonList(path));
        }
        setNeedsBuildDir(false);
    }

    public void setNeedsBuildDir(boolean needsBuildDir) {
        pipelineLaunchParameters.setNeedsBuildDir(needsBuildDir);
    }



    /*
     * Use this method when you want the job to be executed after schedule
     * command gets hold of the command string. Schedule could log the string
     * into a file and/or submit to a GRID
     */

    @Override
    public boolean launch() {
        return launch(Paths.get(XDAT.getSiteConfigPreferences().getPipelinePath(), "bin", SCHEDULE).toString());
    }

    /*
     * Setting cmdPrefix to null will launch the job directly.
     */

    @Override
    public boolean launch(String cmdPrefix) {
        return pipelineLaunchParameters.isRunPipelineInProcess() ? launchInProcessPipelineExecution() : launchExternalPipelineExecution(cmdPrefix);
    }

    private boolean launchInProcessPipelineExecution() {
        boolean success;
        try {
            Integer workflowPrimaryKey = null;
            if (pipelineLaunchParameters.isRecordWorkflowEntries()) {
                workflowPrimaryKey = initiateWorkflowEntry();
            }

            List<String> parameters = getPipelineConfigurationArguments();
            parameters.addAll(getCommandLineArguments());
            if (pipelineLaunchParameters.isRecordWorkflowEntries() && workflowPrimaryKey !=null) {
                parameters.add("-workFlowPrimaryKey "+workflowPrimaryKey);
            }
            if (launchLogger.isInfoEnabled()) {
                launchLogger.info("Launching pipeline in-process with parameters: " + depasswordifize(convertArgumentListToCommandLine(parameters)));
            }
            XNATPipelineLauncher launcher = new XNATPipelineLauncher(parameters);
            success = launcher.run();
        } catch (Exception exception) {
            log.error("{} for in-process execution of pipeline {} with parameters: \n {}", exception.getMessage(), pipelineLaunchParameters.getPipelineName(), depasswordifize(pipelineLaunchParameters.getParameters()), exception);
            success = false;
        }
        return success;
    }

    private String depasswordifize(final Map<String, List<String>> parameters) {
        StringBuilder buffer = new StringBuilder();
        for (Map.Entry<String, List<String>> parameter : parameters.entrySet()) {
            buffer.append(" * ").append(parameter.getKey()).append(": ");
            if (parameter.getKey().equals("-pwd")) {
                buffer.append(NOT_A_PASSWORD);
            } else {
                boolean isFirst = true;
                for (String value : parameter.getValue()) {
                    if (isFirst) {
                        isFirst = false;
                    } else {
                        buffer.append(", ");
                    }
                    buffer.append(value);
                }
            }
            buffer.append(System.getProperty("line.separator"));
        }
        return buffer.toString();
    }

    private boolean launchExternalPipelineExecution(String cmdPrefix) {

        String command = buildPipelineLauncherScriptCommand(cmdPrefix) + " " + convertArgumentListToCommandLine(getCommandLineArguments());

        boolean success = true;

        try {
            Integer workflowPrimaryKey = null;
            if (pipelineLaunchParameters.isRecordWorkflowEntries()) {
                workflowPrimaryKey=initiateWorkflowEntry();
            }
            if (pipelineLaunchParameters.isRecordWorkflowEntries() && workflowPrimaryKey !=null) {
                command += " -workFlowPrimaryKey "+workflowPrimaryKey+" ";
            }

            if (launchLogger.isInfoEnabled()) {
                launchLogger.info("Launching pipeline with command: " + depasswordifize(command));
            }

            ProcessLauncher processLauncher = new ProcessLauncher();
            processLauncher.setCommand(command);
            processLauncher.start();
            if (pipelineLaunchParameters.isWaitFor()) {
                while (processLauncher.isAlive()) {
                } // wait for the thread to end
                success = processLauncher.getExitStatus();
            }
            if (!success) {
                log.error("Couldn't launch " + depasswordifize(command));
            }
        } catch (Exception e) {
            log.error(e.getMessage() + " for command " + depasswordifize(command), e);
            success = false;
        }

        return success;
    }

    private String depasswordifize(final String command) {
        Matcher matcher = DEPASSWORDIFIZER.matcher(command);
        if (matcher.matches()) {
            return matcher.group(1) + matcher.group(2) + NOT_A_PASSWORD + matcher.group(3);
        } else {
            return command;
        }
    }

    private String buildPipelineLauncherScriptCommand(String cmdPrefix) {
        String command;
        if (!StringUtils.isBlank(cmdPrefix)) {
            command = cmdPrefix + " ";
        } else {
            command = "";
        }

        command += Paths.get(XDAT.getSiteConfigPreferences().getPipelinePath(), "bin", "XnatPipelineLauncher").toString();

        if (System.getProperty("os.name").toUpperCase().startsWith("WINDOWS")) {
            command += ".bat";
        }
        return command;
    }

    private String convertArgumentListToCommandLine(List<String> arguments) {
        StringBuilder command = new StringBuilder();
        for (String argument : arguments) {
            command.append(argument).append(" ");
        }
        return command.toString().trim();
    }

    /**
     * This builds the arguments for {@link XNATPipelineLauncher} that are
     * contained in the script when launched externally. These are passed in
     * directly to the {@link XNATPipelineLauncher#main(String[])} instead of
     * implicitly through the launcher script.
     *
     * @return The pipeline configuration arguments.
     */
    private List<String> getPipelineConfigurationArguments() {
        List<String> arguments = new ArrayList<>();
        try {
            String pipelinePath = new File(XDAT.getSiteConfigPreferences().getPipelinePath()).getCanonicalPath();
            boolean requiresQuotes = pipelinePath.contains(" ");
            arguments.add("-config");
            String configPath = pipelinePath + File.separator + "pipeline.config";
            arguments.add(requiresQuotes ? "\"" + configPath + "\"" : configPath);
            arguments.add("-log");
            String logConfigPath = pipelinePath + File.separator + "log4j.properties";
            arguments.add(requiresQuotes ? "\"" + logConfigPath + "\"" : logConfigPath);
            arguments.add("-catalogPath");
            String catalogPath = pipelinePath + File.separator + "catalog";
            arguments.add(requiresQuotes ? "\"" + catalogPath + "\"" : catalogPath);
        } catch (IOException e) {
            // TODO: Do something useful in here
            e.printStackTrace();
        }

        return arguments;
    }

    /**
     * This builds all of the command-line arguments that are standard between
     * in-process and external launch mode. Use the {@link #convertArgumentListToCommandLine(List)}
     * method to convert the returned list to a command line.
     *
     * @return A list of the submitted command-line arguments.
     */
    private List<String> getCommandLineArguments() {
        AliasToken token = XDAT.getContextService().getBean(AliasTokenService.class).issueTokenForUser(pipelineLaunchParameters.getUser());

        List<String> arguments = new ArrayList<>();
        arguments.add("-pipeline");
        arguments.add(pipelineLaunchParameters.getPipelineName());
        arguments.add("-id");
        arguments.add(pipelineLaunchParameters.getId());
        arguments.add("-host");
        arguments.add(pipelineLaunchParameters.getHost());
        arguments.add("-u");
        arguments.add(token.getAlias());
        arguments.add("-pwd");
        arguments.add(token.getSecret());
        arguments.add("-dataType");
        arguments.add(pipelineLaunchParameters.getDataType());

        if (!pipelineLaunchParameters.isRecordWorkflowEntries()) {
            arguments.add("-recordWorkflow");
            arguments.add("false");
        }

        if (pipelineLaunchParameters.getLabel() != null) {
            arguments.add("-label");
            arguments.add(pipelineLaunchParameters.getLabel());
        }

        if (pipelineLaunchParameters.isUseAlias()) {
            arguments.add("-useAlias");
        }

        if (pipelineLaunchParameters.isSupressNotification()) {
            arguments.add("-supressNotification");
        }

        if (pipelineLaunchParameters.getExternalId() != null) {
            arguments.add("-project");
            arguments.add("\"" + pipelineLaunchParameters.getExternalId() + "\"");
        }

        if (pipelineLaunchParameters.getStartAt() != null) {
            arguments.add("-startAt");
            arguments.add(pipelineLaunchParameters.getStartAt());
        }

        if (pipelineLaunchParameters.getParameterFile() != null) {
            arguments.add("-parameterFile");
            arguments.add(pipelineLaunchParameters.getParameterFile());
        }

        for (String notificationEmailId : pipelineLaunchParameters.getNotificationEmailIds()) {
            if (!StringUtils.isBlank(notificationEmailId)) {
                arguments.add("-notify");
                arguments.add(notificationEmailId);
            }
        }

        setBuildDir();

        Map<String, List<String>> parameters = pipelineLaunchParameters.getParameters();
        Set<String> params = parameters.keySet();
        for (String param : params) {
            arguments.add("-parameter");
            List<String> values = parameters.get(param);
            StringBuilder paramArg = new StringBuilder(param).append("=");
            for (String value : values) {
                if (!StringUtils.isBlank(value)) {
                    paramArg.append(escapeSpecialShellCharacters(value)).append(",");
                }
            }
            if (paramArg.toString().endsWith(",")) {
                paramArg.deleteCharAt(paramArg.length() - 1);
            }
            arguments.add(paramArg.toString());
        }

        return arguments;
    }

    private Integer initiateWorkflowEntry() throws Exception {
        WrkWorkflowdata wrk = new WrkWorkflowdata(pipelineLaunchParameters.getUser());
        wrk.setDataType(pipelineLaunchParameters.getDataType());
        wrk.setId(pipelineLaunchParameters.getId());
        wrk.setExternalid(pipelineLaunchParameters.getExternalId());
        wrk.setCategory(EventUtils.CATEGORY.DATA);
        wrk.setType(EventUtils.TYPE.PROCESS);
        wrk.setPipelineName(pipelineLaunchParameters.getPipelineName());
        wrk.setLaunchTime(java.util.Calendar.getInstance().getTime());
        wrk.setStatus("Queued");
        WorkflowUtils.save(wrk, wrk.buildEvent());
        return wrk.getWorkflowId();
    }


    public static String getUserName(UserI user) {
        String rtn = "";
        try {
            if (user.getFirstname() != null && user.getLastname() != null) rtn = user.getFirstname().substring(0, 1) + "." + user.getLastname();
        } catch (Exception ignored) {}
        return rtn;
    }


    private void setBuildDir() {
        // TODO Set this to be the buildDir for the project
        String buildDir = pipelineLaunchParameters.getBuildDir();
        if (buildDir == null) {
            if (pipelineLaunchParameters.isNeedsBuildDir()) {
                String buildPath = ArcSpecManager.GetFreshInstance().getGlobalBuildPath() ;
                if (buildPath.endsWith(File.separator)) {
                    buildPath = buildPath.substring(0, buildPath.length() - 1);
                }
                buildDir = buildPath + File.separator + "Pipeline";
            }
        }
        if (buildDir != null)
            pipelineLaunchParameters.getParameters().put("builddir", Collections.singletonList(buildDir));
    }


    private boolean isLegalPath(String pipelineName) {
        final String pipelineHome = XDAT.getSiteConfigPreferences().getPipelinePath();
        Path path = Paths.get(pipelineHome).resolve(pipelineName).toAbsolutePath().normalize();
        if (path.startsWith(pipelineHome)) {
            return true;
        } else {
            log.error("The pipeline {} is not within the PIPELINE_HOME {}", pipelineName, pipelineHome);
            return false;
        }
    }

    private String escapeSpecialShellCharacters(String input) {
        String rtn = input;
        if (input == null) return rtn;
        if (!System.getProperty("os.name").toUpperCase().startsWith("WINDOWS")) {
            String[] pieces = input.split("'");
            rtn = "";
            for (final String piece : pieces) {
                rtn += "'" + piece + "'" + "\\'";
            }
            if (rtn.endsWith("\\'") && !input.endsWith("'")) {
                int indexOfLastQuote = rtn.lastIndexOf("\\'");
                if (indexOfLastQuote != -1) rtn = rtn.substring(0, indexOfLastQuote);
            }
        }
        return rtn;
    }



    public static DefaultXnatPipelineLauncher GetLauncherForExperiment(RunData data, Context context, XnatExperimentdata imageSession) throws Exception {
        return GetLauncher(buildPipelineLaunchParameters(data, context, imageSession));
    }

    public static DefaultXnatPipelineLauncher GetLauncher(RunData data, Context context, XnatImagesessiondata imageSession) throws Exception {
        PipelineLaunchParameters pipelineLaunchParameters = buildPipelineLaunchParameters(data, context, imageSession);
        String path = imageSession.getArchivePath();
        if (path.endsWith(File.separator)) {
            path = path.substring(0, path.length() - 1);
        }
        pipelineLaunchParameters.setParameter("archivedir", path);
        return GetLauncher(pipelineLaunchParameters);
    }

    public static DefaultXnatPipelineLauncher GetLauncher(final PipelineLaunchParameters pipelineLaunchParameters) {
        DefaultXnatPipelineLauncher pipelineLauncher = new DefaultXnatPipelineLauncher();
        pipelineLauncher.setPipelineLaunchParameters(pipelineLaunchParameters);
        return  pipelineLauncher;
    }

    private static PipelineLaunchParameters buildPipelineLaunchParameters(RunData data, Context context, XnatExperimentdata imageSession) {
        UserI user = XDAT.getUserDetails();
        PipelineLaunchParameters pipelineLaunchParameters = PipelineLaunchParameters.builder()
                .user(user)
                .build();
        pipelineLaunchParameters.setSupressNotification(true);

        pipelineLaunchParameters.setParameter("useremail", user.getEmail());
        pipelineLaunchParameters.setParameter("userfullname", getUserName(user));
        pipelineLaunchParameters.setParameter("adminemail", XDAT.getSiteConfigPreferences().getAdminEmail());
        pipelineLaunchParameters.setParameter("mailhost", XDAT.getNotificationsPreferences().getSmtpServer().getHostname());
        pipelineLaunchParameters.setParameter("xnatserver", TurbineUtils.GetSystemName());

        pipelineLaunchParameters.setId(imageSession.getId());
        pipelineLaunchParameters.setLabel(imageSession.getLabel());
        pipelineLaunchParameters.setDataType(imageSession.getXSIType());
        pipelineLaunchParameters.setExternalId(imageSession.getProject());
        pipelineLaunchParameters.setParameter("xnat_id", imageSession.getId());
        pipelineLaunchParameters.setParameter("project", imageSession.getProject());
        pipelineLaunchParameters.setParameter("cachepath", PipelineFileUtils.getQCCachePathForSession(imageSession.getProject()));

        List<String> emails = new ArrayList<>();
        emails.add(XDAT.getUserDetails().getEmail());

        String extraEmails = (String) TurbineUtils.GetPassedParameter("emailField", data);
        if (!StringUtils.isBlank(extraEmails)) {
            for (String extraEmail : extraEmails.split("[\\s]*,[\\s]*")) {
                if (!StringUtils.isBlank(extraEmail)) {
                    emails.add(extraEmail);
                }
            }
        }
        for (final String email : emails) {
            if (!StringUtils.isBlank(email)) {
                pipelineLaunchParameters.notificationEmailId(email);
            }
        }
        return pipelineLaunchParameters;
    }

    private PipelineLaunchParameters pipelineLaunchParameters;
}
