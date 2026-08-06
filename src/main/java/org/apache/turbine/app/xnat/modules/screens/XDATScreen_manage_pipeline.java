/*
 * web: org.nrg.xnat.turbine.modules.screens.XDATScreen_manage_pipeline
 * XNAT http://www.xnat.org
 * Copyright (c) 2005-2017, Washington University School of Medicine and Howard Hughes Medical Institute
 * All Rights Reserved
 *
 * Released under the Simplified BSD.
 */

package org.apache.turbine.app.xnat.modules.screens;

import org.apache.turbine.pipeline.PipelineData;
import org.apache.turbine.util.RunData;
import org.apache.velocity.context.Context;
import org.nrg.xnatx.pipeline.PipelineRepositoryManager;
import org.nrg.xdat.turbine.modules.screens.AdminScreen;
import org.nrg.xnatx.pipeline.helpers.PipelineRepositoryHelper;

public class XDATScreen_manage_pipeline extends AdminScreen {
	 
	protected void doBuildTemplate(PipelineData pipelineData, Context context)     throws Exception {
        final RunData data = pipelineData.getRunData();
			PipelineRepositoryHelper pipelineRepositoryHelper = PipelineRepositoryManager.GetInstance();
			context.put("repository", pipelineRepositoryHelper);
	}
}
