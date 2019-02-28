package io.jenkins.plugins.rortveiten;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import hudson.model.Action;
import rortveiten.misra.*;
import hudson.model.Run;
import jenkins.tasks.SimpleBuildStep.LastBuildAction;

public class GcsAction implements Action, LastBuildAction {
    private Run<?,?> run;
    private List<Guideline> guidelines;
    private String parserName;
    private String softwareVersion;
    private String projectName;
    private String misraVersion;
    private boolean isCompliant;
    private String summary;
    private String tool;
    private String notes;
    
    public GcsAction(Run<?,?> run, List<Guideline> guidelines, String parserName, String softwareVersion,
            String projectName, String misraVersion, boolean isCompliant, String tool, String summary, String notes) {
        this.run = run;
        this.guidelines = guidelines;
        this.parserName = parserName;
        this.softwareVersion = softwareVersion;
        this.projectName = projectName;
        this.misraVersion = misraVersion;
        this.isCompliant = isCompliant;
        this.tool = tool;
        this.notes = notes;
        this.summary = summary;
    }    
    
	@Override
	public String getIconFileName() {
		return "/plugin/misra_gcs/img/MISRA-1.png";
	}

	@Override
	public String getDisplayName() {
		return "MISRA Guideline Compliance Summary";
	}

	@Override
	public String getUrlName() {
		return "MisraGcs";
	}    
	
	public List<Guideline> getGuidelines() {
	    return guidelines;
	}
	
	public String getParserName() {
	    return parserName;
	}
	
	public String getSoftwareVersion() {
	    return softwareVersion;
	}
	
	public String getProjectName() {
	    return projectName;	    
	}
	
	public String getMisraVersion() {
	    return misraVersion;
	}
	
	public boolean isCompliant() {
	    return isCompliant;	    
	}
	
	public String getNotes() {
	    return notes;
	}
	
	public String getSummary() {
	    return summary;
	}
	
	public String getTool() {
	    return tool;
	}
	
	public Run<?,?> getRun() {
	    return run;
	}
	
	public boolean isProjectAction() {
	    return false;
	}

    @Override
    public Collection<? extends Action> getProjectActions() {
        return Collections.singleton(new GcsProjectAction(this));
    }
}
