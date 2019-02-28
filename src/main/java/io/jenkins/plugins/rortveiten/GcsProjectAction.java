package io.jenkins.plugins.rortveiten;

import java.util.List;

import hudson.model.Job;
import hudson.model.Run;
import rortveiten.misra.Guideline;

public class GcsProjectAction extends GcsAction {
    
    public GcsProjectAction(Run<?,?> run, List<Guideline> guidelines, String parserName, String softwareVersion,
            String projectName, String misraVersion, boolean isCompliant, String tool, String summary, String notes) {
        super(run, guidelines, parserName, softwareVersion, projectName, misraVersion,
                isCompliant,tool, notes, summary);
    }
    
    public GcsProjectAction(GcsAction buildAction) {
        this(buildAction.getRun(), buildAction.getGuidelines(),
                buildAction.getParserName(), buildAction.getSoftwareVersion(),
                buildAction.getProjectName(), buildAction.getMisraVersion(),
                buildAction.isCompliant(), buildAction.getTool(), 
                buildAction.getNotes(), buildAction.getSummary());        
    }
    
    @Override
    public boolean isProjectAction() {
        return true;
    }
    
    public Job<?,?> getJob() {
        return getRun().getParent();
    }

}
