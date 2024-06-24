/*
 * web: org.nrg.viewer.QCImageCreator
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.nrg.viewer;

import org.nrg.pipeline.PipelineLaunchParameters;
import org.nrg.pipeline.XnatPipelineLauncher;
import org.nrg.xnat.plexiviewer.lite.xml.PlexiViewerSpecForSession;
import org.nrg.xnat.plexiviewer.manager.PlexiSpecDocReader;
import org.nrg.xdat.XDAT;
import org.nrg.xdat.om.XnatMrsessiondata;
import org.nrg.xdat.turbine.utils.TurbineUtils;
import org.nrg.xft.security.UserI;
import org.nrg.xnat.turbine.utils.ArcSpecManager;

import java.io.File;

public class QCImageCreator {

    XnatMrsessiondata mrSession;
    UserI user;

    public QCImageCreator(XnatMrsessiondata mrSession, UserI user) {
        this.mrSession = mrSession;
        this.user = user;
    }


    public boolean createQCImagesForScans() throws Exception {
        PipelineLaunchParameters pipelineLaunchParameters = new PipelineLaunchParameters(user);
        pipelineLaunchParameters.setAdmin_email(XDAT.getSiteConfigPreferences().getAdminEmail());
        pipelineLaunchParameters.setAlwaysEmailAdmin(ArcSpecManager.GetInstance().getEmailspecifications_pipeline());
        pipelineLaunchParameters.setWaitFor(true);
        pipelineLaunchParameters.setPipelineName("images/WebBasedQCImageCreator.xml");
        pipelineLaunchParameters.setId(mrSession.getId());
        pipelineLaunchParameters.setDataType(mrSession.getXSIType());
        pipelineLaunchParameters.setExternalId(mrSession.getProject());
        pipelineLaunchParameters.setLabel(mrSession.getLabel());

        pipelineLaunchParameters.setParameter("sessionLabel", mrSession.getLabel());
        pipelineLaunchParameters.setParameter("xnat_project", mrSession.getProject());
        pipelineLaunchParameters.setParameter("session", mrSession.getId() );
        pipelineLaunchParameters.setParameter("notify", "0" );
        pipelineLaunchParameters.setParameter("xnatserver", TurbineUtils.GetSystemName());
        pipelineLaunchParameters.setParameter("mailhost", XDAT.getNotificationsPreferences().getSmtpServer().getHostname());
        pipelineLaunchParameters.setParameter("useremail", user.getEmail());
        pipelineLaunchParameters.setParameter("adminemail", XDAT.getSiteConfigPreferences().getAdminEmail());

        XnatPipelineLauncher xnatPipelineLauncher = new XnatPipelineLauncher(pipelineLaunchParameters);
        return xnatPipelineLauncher.launch(null);
    }

    public static String GetPathToQCThumbnailFile(XnatMrsessiondata mrSession, String mrScanId) {
        PlexiViewerSpecForSession viewerSpec = PlexiSpecDocReader.GetInstance().getSpecDoc(mrSession.getSessionType());
        return viewerSpec.getThumbnailArchiveLocation() + File.separator + mrSession.getId() +"_" + mrScanId + "_qc_t.gif";
    }

    public static String GetPathToQCFile(XnatMrsessiondata mrSession, String mrScanId) {
        PlexiViewerSpecForSession viewerSpec = PlexiSpecDocReader.GetInstance().getSpecDoc(mrSession.getSessionType());
        return viewerSpec.getThumbnailArchiveLocation() + File.separator + mrSession.getId() + "_" + mrScanId + "_qc.gif";
    }


    public static String GetSnapshotPathForSession(String sessionArchivePath) {
        return sessionArchivePath + "SNAPSHOTS";
    }


    public static String getQCThumbnailPathForSession(String project) {
        String path;
        if (project!=null){
            path= ArcSpecManager.GetInstance().getCachePathForProject(project);
        }else{
            path= ArcSpecManager.GetInstance().getGlobalCachePath();
        }
        return path + "Thumbnail/";
    }

    public static String getQCCachePathForSession(String project) {
        String path;
        if (project!=null){
            path= ArcSpecManager.GetInstance().getCachePathForProject(project);
        }else{
            path= ArcSpecManager.GetInstance().getGlobalCachePath();
        }
        return path;
    }
}
