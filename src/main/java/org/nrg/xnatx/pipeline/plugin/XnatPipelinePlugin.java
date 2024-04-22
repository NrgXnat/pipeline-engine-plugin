package org.nrg.xnatx.pipeline.plugin;

import lombok.extern.slf4j.Slf4j;
import org.nrg.framework.annotations.XnatPlugin;
import org.springframework.context.annotation.ComponentScan;

@XnatPlugin(value          = "PipelinePlugin",
        name           = "XNAT Pipeline Engine Plugin",
        description    = "XNAT Pipeline Engine as a plugin",
        logConfigurationFile = "META-INF/resources/pipeline-logback.xml")
@ComponentScan({"org.nrg.xnatx.pipeline.component",
        "org.nrg.xnatx.pipeline.services.impl.legacy",
        "org.nrg.xnatx.pipeline.xapi"})
@Slf4j
public class XnatPipelinePlugin {

}
