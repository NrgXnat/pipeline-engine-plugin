# README #

This plugin contains all the frontend and backend components in XNAT that allow the use of the Pipeline Engine. These files have been moved from XNAT version 1.8.10. 
From XNAT 1.9+, the pipeline engine features can not be used without deploying this plugin.

The Container Service is the preferred way of executing jobs in XNAT. 


### How do I get set up? ###

In order to use the Pipeline Engine via XNAT, from XNAT 1.9+ onwards, you would have to deploy:

1. The Pipeline Engine Plugin in the XNAT_HOME/plugins folder

AND

2. Install the Pipeline Engine on the XNAT host

### Building the plugin ###

Execute the command from the source folder:

./grawdlew clean xnatPluginJar