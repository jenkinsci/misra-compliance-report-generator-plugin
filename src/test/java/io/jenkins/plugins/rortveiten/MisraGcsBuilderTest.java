package io.jenkins.plugins.rortveiten;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import hudson.FilePath;
import hudson.model.Label;
import hudson.model.Result;

public class MisraGcsBuilderTest {
    
    @Rule
    public JenkinsRule jenkins = new JenkinsRule();

    @Test
    public void testScriptedPipeline() throws Exception {
        String agentLabel = "my-agent";
        jenkins.createOnlineSlave(Label.get(agentLabel));
        WorkflowJob job = jenkins.createProject(WorkflowJob.class, "test-scripted-pipeline");
        String pipelineScript
                = "node {\n"
                + "  misraReport grpFile: '', projectName: 'UST3', ruleSet: '2012', softwareVersion: 'Rai', sourceListFile: 'filestolint.lnt', warningParser: 'PC-Lint', warningsFile: 'lint_output.txt'\n"
                + "}";
        job.setDefinition(new CpsFlowDefinition(pipelineScript, true));
        //Will fail because files not found
        WorkflowRun completedBuild = jenkins.assertBuildStatus(Result.FAILURE, job.scheduleBuild2(0));
        jenkins.assertLogContains("Misra GCS plugin: File not found:", completedBuild);
    }
    
    @Test
    public void testRelativizeOnAbsolutePath() throws Exception {
        String relative = "there/this/that.txt";
        String absolute = new File(relative).toPath().toAbsolutePath().toString();
        FilePath here = new FilePath(new File(""));
        
        List<String> relPaths = MisraGcsBuilderPlugin.relativePaths(Arrays.asList(absolute), here);
        
        assertEquals(relative, relPaths.get(0));
    }
    
    @Test
    public void testRelativizeOnRelativePath() throws Exception {
        String relative = "there/this/that.txt";
        FilePath subfolder = new FilePath(new File("./subfolder")).absolutize();
        
        List<String> relPaths = MisraGcsBuilderPlugin.relativePaths(Arrays.asList(relative), subfolder);
        
        assertEquals(relative, relPaths.get(0));
    }    
    
    
}
